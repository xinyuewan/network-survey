package com.craxiom.networksurvey.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import android.telephony.TelephonyManager
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class NetworkSpeedTester(private val context: Context) {
    private var isCancelled = false
    private val TEST_DURATION = 10000 // 10秒
    // 多个下载服务器地址，避免单一服务器不可用
    private val DOWNLOAD_URLS = listOf(
        "https://nbg1-speed.hetzner.com/100MB.bin",
        "https://fsn1-speed.hetzner.com/100mb.bin",
        "https://ash-speed.hetzner.com/100mb.bin",
        "https://hil-speed.hetzner.com/100mb.bin",
        "https://sin-speed.hetzner.com/100mb.bin",
        "https://download.thinkbroadband.com/100MB.zip",
        "https://hel1-speed.hetzner.com/100MB.bin"
    )
    private val UPLOAD_DATA_SIZE = 5 * 1024 * 1024 // 5MB上传测试数据

    // 检查网络是否可用
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 获取网络类型
    fun getNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "未知"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "未知"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

                try {
                    when (telephonyManager.networkType) {
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

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "其他网络"
        }
    }



    // 测试下载速度
    suspend fun testDownloadSpeed(): Double = withContext(Dispatchers.IO) {
        isCancelled = false
        var totalBytesRead = 0L
        val startTime = System.currentTimeMillis()

        // 尝试多个下载服务器
        for (urlString in DOWNLOAD_URLS) {
            if (isCancelled) break

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection

                // 应用SSL证书处理
                if (url.protocol == "https") {
                    handleSSLCertificate(connection)
                }

                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.useCaches = false

                val inputStream = BufferedInputStream(connection.inputStream)
                val buffer = ByteArray(8192)
                var bytesRead: Int

                // 读取数据，持续指定时间
                while (System.currentTimeMillis() - startTime < TEST_DURATION && !isCancelled) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break // 文件读取完毕

                    totalBytesRead += bytesRead.toLong()
                }

                inputStream.close()
                connection.disconnect()

                // 如果测试时间已到，退出循环
                if (System.currentTimeMillis() - startTime >= TEST_DURATION || isCancelled) {
                    break
                }
            } catch (e: Exception) {
                // 继续尝试下一个服务器
                continue
            }
        }

        if (totalBytesRead == 0L && !isCancelled) {
            throw Exception("所有下载服务器测试失败")
        }

        val durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        if (durationSeconds <= 0) return@withContext 0.0

        // 计算Mbps (1字节 = 8位, 1MB = 1024*1024字节)
        (totalBytesRead * 8.0) / (1024.0 * 1024.0 * durationSeconds)
    }

    // 测试上传速度
    suspend fun testUploadSpeed(): Double = withContext(Dispatchers.IO) {
        isCancelled = false
        var totalBytesWritten = 0L
        val startTime = System.currentTimeMillis()

        try {
            // 创建随机数据用于上传测试
            val uploadData = ByteArray(UPLOAD_DATA_SIZE)
            for (i in uploadData.indices) {
                uploadData[i] = (Math.random() * 256).toInt().toByte()
                if (isCancelled) throw Exception("测试已取消")
            }

            val url = URL("https://postman-echo.com/post")
            val connection = url.openConnection() as HttpURLConnection

            // 应用SSL证书处理
            if (url.protocol == "https") {
                handleSSLCertificate(connection)
            }

            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Content-Length", uploadData.size.toString())

            val outputStream: OutputStream = connection.outputStream
            val bufferSize = 8192
            var bytesWritten = 0

            // 写入数据，持续指定时间或直到数据写完
            while (bytesWritten < uploadData.size &&
                System.currentTimeMillis() - startTime < TEST_DURATION &&
                !isCancelled
            ) {
                val bytesToWrite = Math.min(bufferSize, uploadData.size - bytesWritten)
                outputStream.write(uploadData, bytesWritten, bytesToWrite)
                bytesWritten += bytesToWrite
                totalBytesWritten += bytesToWrite.toLong()
            }

            outputStream.flush()
            outputStream.close()
            connection.disconnect()

            if (isCancelled) throw Exception("测试已取消")

            val durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
            if (durationSeconds <= 0) return@withContext 0.0

            // 计算Mbps
            (totalBytesWritten * 8.0) / (1024.0 * 1024.0 * durationSeconds)
        } catch (e: Exception) {
            if (isCancelled) throw Exception("测试已取消")
            else throw Exception("上传测试失败: ${e.message}")
        }
    }

    // 测试延迟
    suspend fun testLatency(): Long = withContext(Dispatchers.IO) {
        isCancelled = false
        try {
            val url = URL("https://www.baidu.com")
            val start = System.currentTimeMillis()
            val connection = url.openConnection() as HttpURLConnection

            // 应用SSL证书处理
            if (url.protocol == "https") {
                handleSSLCertificate(connection)
            }

            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "HEAD"
            connection.connect()

            val latency = System.currentTimeMillis() - start
            connection.disconnect()

            if (isCancelled) throw Exception("测试已取消")

            latency
        } catch (e: Exception) {
            if (isCancelled) throw Exception("测试已取消")
            else throw Exception("延迟测试失败: ${e.message}")
        }
    }

    // SSL证书处理方法
    @SuppressLint("BadHostnameVerifier")
    private fun handleSSLCertificate(connection: HttpURLConnection) {
        if (connection is HttpsURLConnection) {
            connection.hostnameVerifier = HostnameVerifier { _, _ -> true }

            // 信任所有证书
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            try {
                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                connection.sslSocketFactory = sslContext.socketFactory
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 取消测试
    fun cancelTest() {
        isCancelled = true
    }
}