package com.craxiom.networksurvey.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class NetworkSpeedTester(private val context: Context) {
    private var isCancelled = false

    // 检查网络是否可用
    fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // 检查是否是蜂窝网络
    fun isCellularNetwork(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    // 获取蜂窝网络类型
    fun getCellularNetworkType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return "未知"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "未知"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                // 这里可以根据需要进一步区分4G/3G/2G
                "蜂窝网络"
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "以太网"
            else -> "其他网络"
        }
    }

    // 测试下载速度 (公开可见性)
    suspend fun testDownloadSpeed(): Double = withContext(Dispatchers.IO) {
        isCancelled = false
        val downloadUrl = URL("https://speed.hetzner.de/100MB.bin")
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var fileOutputStream: FileOutputStream? = null

        try {
            connection = downloadUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("服务器响应错误: $responseCode")
            }

            val fileSize = connection.contentLengthLong
            if (fileSize <= 0) {
                throw Exception("无法获取文件大小")
            }

            inputStream = connection.inputStream
            val tempFile = File.createTempFile("speedtest", ".tmp", context.cacheDir)
            fileOutputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L
            val startTime = System.currentTimeMillis()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled) {
                    throw Exception("测试已取消")
                }

                fileOutputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead.toLong()

                // 限制测试时间为10秒
                if (System.currentTimeMillis() - startTime > 10000) {
                    break
                }
            }

            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000.0

            // 计算Mbps (1字节 = 8位, 1MB = 1024*1024字节)
            val speedMbps = (totalBytesRead * 8.0) / (1024.0 * 1024.0 * durationSeconds)

            // 清理临时文件
            tempFile.delete()

            speedMbps
        } finally {
            inputStream?.close()
            fileOutputStream?.close()
            connection?.disconnect()
        }
    }

    // 测试上传速度 (公开可见性)
    suspend fun testUploadSpeed(): Double = withContext(Dispatchers.IO) {
        isCancelled = false
        val uploadUrl = URL("https://httpbin.org/post")
        var connection: HttpURLConnection? = null
        var fileOutputStream: FileOutputStream? = null

        try {
            // 创建一个临时文件用于上传
            val tempFile = File.createTempFile("speedtest_upload", ".tmp", context.cacheDir)
            fileOutputStream = FileOutputStream(tempFile)

            // 写入10MB数据
            val buffer = ByteArray(1024 * 1024) // 1MB缓冲区
            for (i in 0 until 10) { // 10MB
                fileOutputStream.write(buffer)
                if (isCancelled) {
                    throw Exception("测试已取消")
                }
            }
            fileOutputStream.close()

            val fileSize = tempFile.length()
            connection = uploadUrl.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Length", fileSize.toString())

            val startTime = System.currentTimeMillis()
            val outputStream = connection.outputStream
            tempFile.inputStream().use { input ->
                input.copyTo(outputStream, 8192)
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("服务器响应错误: $responseCode")
            }

            val endTime = System.currentTimeMillis()
            val durationSeconds = (endTime - startTime) / 1000.0

            // 计算Mbps
            val speedMbps = (fileSize * 8.0) / (1024.0 * 1024.0 * durationSeconds)

            // 清理临时文件
            tempFile.delete()

            speedMbps
        } finally {
            fileOutputStream?.close()
            connection?.disconnect()
        }
    }

    // 取消测试
    fun cancelTest() {
        isCancelled = true
    }
}
