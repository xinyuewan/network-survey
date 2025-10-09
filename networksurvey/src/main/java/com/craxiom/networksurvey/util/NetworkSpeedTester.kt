package com.craxiom.networksurvey.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.math.min

/**
 * 优化后的网络测速工具类（支持延迟/下载/上传）
 */
class NetworkSpeedTester(private val context: Context) {

    companion object {
        private const val TAG = "NetworkSpeedTester"
    }

    @Volatile
    private var isCancelled = false

    private var bestServer: SpeedtestServer? = null

    private val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    private val SPEEDTEST_SERVER_LIST_URL = "https://www.speedtest.net/api/js/servers"
    private val DOWNLOAD_SIZES = listOf(350, 500, 750, 1000, 1500) // KB
    private val UPLOAD_CHUNKS = listOf(250000, 500000, 1000000) // B
    private val MAX_CANDIDATES = 10
    private val LATENCY_TEST_COUNT = 3

    // 复用 SecureRandom
    private val secureRandom = SecureRandom()

    data class SpeedtestServer(
        val id: String,
        val host: String,
        val name: String,
        val country: String,
        val sponsor: String,
        val cc: String
    )

    /**
     * 获取中国境内 speedtest.net 服务器
     */
    suspend fun getSpeedtestCNServers(limit: Int = 200): List<SpeedtestServer> =
        withContext(Dispatchers.IO) {
            if (isCancelled) return@withContext emptyList()

            try {
                val url = URL("$SPEEDTEST_SERVER_LIST_URL?limit=$limit&engine=js")
                val conn = url.openConnection() as HttpURLConnection
                if (url.protocol == "https") handleSSLCertificate(conn)

                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.connectTimeout = 15000
                conn.readTimeout = 15000

                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val type = object : TypeToken<Array<Map<String, String>>>() {}.type
                val serverJsonArray = Gson().fromJson<Array<Map<String, String>>>(responseBody, type)

                serverJsonArray.filterNotNull()
                    .filter { serverMap ->
                        listOf("id", "host", "name", "country", "sponsor", "cc")
                            .all { serverMap.containsKey(it) }
                    }
                    .filter { it["cc"].equals("CN", ignoreCase = true) }
                    .map {
                        SpeedtestServer(
                            id = it["id"] ?: "",
                            host = it["host"] ?: "",
                            name = it["name"] ?: "",
                            country = it["country"] ?: "",
                            sponsor = it["sponsor"] ?: "",
                            cc = it["cc"] ?: ""
                        )
                    }
            } catch (e: Exception) {
                Log.e(TAG, "获取服务器列表失败", e)
                emptyList()
            }
        }

    /**
     * 测试单个服务器延迟
     */
    private suspend fun testServerLatency(server: SpeedtestServer): Long =
        withContext(Dispatchers.IO) {
            if (isCancelled) return@withContext Long.MAX_VALUE

            val (host, port) = runCatching {
                val parts = server.host.split(":")
                parts[0] to parts[1].toInt()
            }.getOrElse { server.host to 80 }

            val latencies = (1..LATENCY_TEST_COUNT).map {
                runCatching {
                    val start = System.currentTimeMillis()
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 2000)
                    }
                    System.currentTimeMillis() - start
                }.getOrElse { -1L }
            }.filter { it >= 0 }

            if (latencies.isEmpty()) Long.MAX_VALUE else latencies.average().toLong()
        }

    /**
     * 选择最优服务器
     */
    suspend fun selectBestServer(servers: List<SpeedtestServer>): SpeedtestServer? =
        withContext(Dispatchers.IO) {
            if (servers.isEmpty() || isCancelled) {
                bestServer = null
                return@withContext null
            }

            val candidates = if (servers.size <= MAX_CANDIDATES) servers
            else servers.shuffled().take(MAX_CANDIDATES)

            val serverLatencyList = candidates.map { server ->
                async { server to testServerLatency(server) }
            }.awaitAll()

            val selectedServer = serverLatencyList
                .filter { it.second != Long.MAX_VALUE }
                .minByOrNull { it.second }?.first

            bestServer = selectedServer
            selectedServer
        }

    fun getCurrentBestServer(): SpeedtestServer? = bestServer

    /**
     * 测试时延
     */
    suspend fun testLatency(): Long = withContext(Dispatchers.IO) {
        val targetServer = bestServer ?: throw IllegalStateException("请先调用 selectBestServer()")
        if (isCancelled) return@withContext 0L

        val latency = testServerLatency(targetServer)
        if (latency == Long.MAX_VALUE) throw Exception("无法连接到服务器")
        latency
    }

    /**
     * 下载测速
     */
    suspend fun testDownloadSpeed(): Double = withContext(Dispatchers.IO) {
        val targetServer = bestServer ?: throw IllegalStateException("请先调用 selectBestServer()")
        if (isCancelled || targetServer.host.isEmpty()) return@withContext 0.0

        val semaphore = Semaphore(3)
        val downloadUrls = DOWNLOAD_SIZES.map { size ->
            "https://${targetServer.host}/random${size}x${size}.jpg"
        }

        val speedList = downloadUrls.map { urlString ->
            async {
                semaphore.withPermit {
                    runCatching {
                        val url = URL(urlString)
                        val conn = url.openConnection() as HttpURLConnection
                        if (url.protocol == "https") handleSSLCertificate(conn)

                        conn.setRequestProperty("User-Agent", USER_AGENT)
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        conn.useCaches = false

                        var totalBytes = 0L
                        val startTime = System.currentTimeMillis()
                        try {
                            BufferedInputStream(conn.inputStream).use { input ->
                                val buffer = ByteArray(32 * 1024)
                                while (!isCancelled) {
                                    val bytesRead = input.read(buffer)
                                    if (bytesRead == -1) break
                                    totalBytes += bytesRead
                                }
                            }
                        } finally {
                            conn.disconnect()
                        }

                        val durationSec =
                            (System.currentTimeMillis() - startTime) / 1000.0
                        if (durationSec > 0 && totalBytes > 0) {
                            (totalBytes * 8.0) / (1024 * 1024 * durationSec)
                        } else 0.0
                    }.getOrElse {
                        Log.e(TAG, "下载测速失败: $urlString", it)
                        0.0
                    }
                }
            }
        }.awaitAll()

        val validSpeeds = speedList.filter { it > 0.0 }
        if (validSpeeds.isEmpty()) 0.0 else validSpeeds.median()
    }

    /**
     * 上传测速
     */
    suspend fun testUploadSpeed(): Double = withContext(Dispatchers.IO) {
        val targetServer = bestServer ?: throw IllegalStateException("请先调用 selectBestServer()")
        if (isCancelled || targetServer.host.isEmpty()) return@withContext 0.0

        val semaphore = Semaphore(2)
        val uploadUrl = "https://${targetServer.host}/upload.php"

        val speedList = UPLOAD_CHUNKS.map { chunkSize ->
            async {
                semaphore.withPermit {
                    runCatching {
                        val url = URL(uploadUrl)
                        val conn = url.openConnection() as HttpURLConnection
                        if (url.protocol == "https") handleSSLCertificate(conn)

                        conn.setRequestProperty("User-Agent", USER_AGENT)
//                        conn.setRequestProperty("Content-Type", "application/octet-stream")
                        conn.requestMethod = "POST"
                        conn.doOutput = true
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000

                        val randomData = ByteArray(chunkSize)
                        secureRandom.nextBytes(randomData)

                        val startTime = System.currentTimeMillis()
                        try {
                            conn.outputStream.use { out ->
                                out.write(randomData)
                                out.flush()
                            }
                            conn.inputStream.readBytes()
                        } finally {
                            conn.disconnect()
                        }

                        val durationSec =
                            (System.currentTimeMillis() - startTime) / 1000.0
                        if (durationSec > 0) {
                            (chunkSize.toLong() * 8.0) / (1024 * 1024 * durationSec)
                        } else 0.0
                    }.getOrElse {
                        Log.e(TAG, "上传测速失败", it)
                        0.0
                    }
                }
            }
        }.awaitAll()

        val validSpeeds = speedList.filter { it > 0.0 }
        if (validSpeeds.isEmpty()) 0.0 else validSpeeds.median()
    }

    /**
     * 网络可用性
     */
    fun isNetworkAvailable(): Boolean {
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * 网络类型
     */
    fun getNetworkType(): String {
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "未知"
        val caps = cm.getNetworkCapabilities(network) ?: return "未知"

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val tm =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                try {
                    when (tm.networkType) {
                        TelephonyManager.NETWORK_TYPE_GPRS,
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_CDMA,
                        TelephonyManager.NETWORK_TYPE_1xRTT,
                        TelephonyManager.NETWORK_TYPE_IDEN -> "2G"

                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_EVDO_0,
                        TelephonyManager.NETWORK_TYPE_EVDO_A,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_EVDO_B,
                        TelephonyManager.NETWORK_TYPE_EHRPD,
                        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"

                        TelephonyManager.NETWORK_TYPE_LTE,
                        TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        else -> "未知蜂窝网络"
                    }
                } catch (e: SecurityException) {
                    "蜂窝网络"
                }
            }

            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "其他网络"
        }
    }

    /**
     * SSL 信任所有证书（仅限调试使用）
     */
    @SuppressLint("BadHostnameVerifier")
    private fun handleSSLCertificate(connection: HttpURLConnection) {
        if (connection !is HttpsURLConnection) return

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<X509Certificate>, authType: String
            ) {
            }

            override fun checkServerTrusted(
                chain: Array<X509Certificate>, authType: String
            ) {
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        try {
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, secureRandom)
            connection.sslSocketFactory = sslContext.socketFactory
            connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.e(TAG, "SSL 配置失败", e)
        }
    }

    fun cancelTest() {
        isCancelled = true
        bestServer = null
    }

    fun clearBestServer() {
        bestServer = null
    }

    /**
     * 中位数扩展方法
     */
    private fun List<Double>.median(): Double {
        if (isEmpty()) return 0.0
        val sorted = sorted()
        val mid = size / 2
        return if (size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2
        } else {
            sorted[mid]
        }
    }
}
