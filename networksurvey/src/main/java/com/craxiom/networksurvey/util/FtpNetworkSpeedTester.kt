package com.craxiom.networksurvey.util

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class FtpNetworkSpeedTester(private val context: Context) {
    private var isCancelled = false
    private val TEST_DURATION = 10000 // 10秒
    private val UPLOAD_DATA_SIZE = 5 * 1024 * 1024 // 5MB上传测试数据
    private val DOWNLOAD_FILE_NAME = "speedtest.bin" // 服务器上用于测试的文件
    private val MAX_RECONNECT_ATTEMPTS = 3 // 最大重连次数
    private val HEARTBEAT_INTERVAL = 30 // 心跳间隔(秒)

    // FTP服务器配置 - 请根据实际情况修改
    private val FTP_SERVER = "ftp.example.com"
    private val FTP_PORT = 21
    private val FTP_USER = "username"
    private val FTP_PASSWORD = "password"

    // 心跳调度器
    private var heartbeatExecutor: ScheduledExecutorService? = null

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
        var ftpClient: FTPClient? = null
        var reconnectAttempts = 0
        var lastFilePosition = 0L // 用于断点续传

        try {
            ftpClient = connectToFtpServer()
            if (ftpClient == null) {
                throw Exception("无法连接到FTP服务器")
            }

            startHeartbeat(ftpClient)

            // 进入被动模式，适用于防火墙环境
            ftpClient.enterLocalPassiveMode()

            // 获取文件大小（用于断点续传）
            val fileSize = ftpClient.listFiles(DOWNLOAD_FILE_NAME).firstOrNull()?.size ?: 0L

            val buffer = ByteArray(8192)
            var bytesRead: Int

            // 循环进行下载，包含重连机制
            while (System.currentTimeMillis() - startTime < TEST_DURATION && !isCancelled) {
                try {
                    // 如果连接已断开，尝试重连
                    if (ftpClient == null || !ftpClient.isConnected) {
                        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                            throw Exception("达到最大重连次数，无法继续下载")
                        }

                        ftpClient = reconnect()
                        reconnectAttempts++
                        if (ftpClient == null) continue

                        // 断点续传：移动到上次断开的位置
                        if (lastFilePosition > 0 && lastFilePosition < fileSize) {
                            ftpClient.setRestartOffset(lastFilePosition)
                        }
                    }

                    // 获取文件输入流（支持断点续传）
                    val inputStream: InputStream? = ftpClient.retrieveFileStream(DOWNLOAD_FILE_NAME)
                    if (inputStream == null) {
                        throw Exception("无法获取下载文件: ${ftpClient.replyString}")
                    }

                    // 读取数据
                    while (System.currentTimeMillis() - startTime < TEST_DURATION && !isCancelled) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break // 文件读取完毕

                        totalBytesRead += bytesRead.toLong()
                        lastFilePosition += bytesRead.toLong()
                    }

                    inputStream.close()
                    ftpClient.completePendingCommand()

                    // 如果测试时间已到或被取消，退出循环
                    if (System.currentTimeMillis() - startTime >= TEST_DURATION || isCancelled) {
                        break
                    }
                } catch (e: Exception) {
                    // 发生异常，标记连接为断开
                    disconnectFromFtpServer(ftpClient)
                    ftpClient = null
                    if (isCancelled) break

                    // 等待一段时间再重连
                    Thread.sleep(1000)
                }
            }

            if (isCancelled) {
                throw Exception("测试已取消")
            }
        } catch (e: Exception) {
            if (!isCancelled) {
                throw Exception("下载测试失败: ${e.message}")
            }
        } finally {
            stopHeartbeat()
            disconnectFromFtpServer(ftpClient)
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
        var ftpClient: FTPClient? = null
        var reconnectAttempts = 0
        var remoteFileName = "upload_test_${System.currentTimeMillis()}.bin"

        // 创建随机数据用于上传测试
        val uploadData = ByteArray(UPLOAD_DATA_SIZE)
        for (i in uploadData.indices) {
            uploadData[i] = (Math.random() * 256).toInt().toByte()
            if (isCancelled) throw Exception("测试已取消")
        }

        try {
            ftpClient = connectToFtpServer()
            if (ftpClient == null) {
                throw Exception("无法连接到FTP服务器")
            }

            startHeartbeat(ftpClient)
            ftpClient.enterLocalPassiveMode()

            // 循环进行上传，包含重连机制
            while (totalBytesWritten < uploadData.size &&
                System.currentTimeMillis() - startTime < TEST_DURATION &&
                !isCancelled
            ) {
                try {
                    // 如果连接已断开，尝试重连
                    if (ftpClient == null || !ftpClient.isConnected) {
                        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                            throw Exception("达到最大重连次数，无法继续上传")
                        }

                        ftpClient = reconnect()
                        reconnectAttempts++
                        if (ftpClient == null) continue

                        ftpClient.enterLocalPassiveMode()
                    }

                    val bufferSize = 8192
                    val remainingBytes = uploadData.size - totalBytesWritten.toInt()
                    val bytesToWrite = Math.min(bufferSize, remainingBytes)

                    // 从上次断开的位置继续上传
                    val inputStream = ByteArrayInputStream(uploadData, totalBytesWritten.toInt(), bytesToWrite)

                    val success = ftpClient.appendFile(remoteFileName, inputStream)
                    if (!success) {
                        // 如果追加模式失败，尝试重新上传整个文件
                        inputStream.reset()
                        if (!ftpClient.storeFile(remoteFileName, inputStream)) {
                            throw Exception("上传失败: ${ftpClient.replyString}")
                        }
                    }

                    totalBytesWritten += bytesToWrite.toLong()
                    inputStream.close()
                } catch (e: Exception) {
                    // 发生异常，标记连接为断开
                    disconnectFromFtpServer(ftpClient)
                    ftpClient = null
                    if (isCancelled) break

                    // 等待一段时间再重连
                    Thread.sleep(1000)
                }
            }

            // 清理测试文件
            if (ftpClient != null && ftpClient.isConnected) {
                ftpClient.deleteFile(remoteFileName)
            }

            if (isCancelled) {
                throw Exception("测试已取消")
            }
        } catch (e: Exception) {
            if (!isCancelled) {
                throw Exception("上传测试失败: ${e.message}")
            }
        } finally {
            stopHeartbeat()
            disconnectFromFtpServer(ftpClient)
        }

        val durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0
        if (durationSeconds <= 0) return@withContext 0.0

        // 计算Mbps
        (totalBytesWritten * 8.0) / (1024.0 * 1024.0 * durationSeconds)
    }

    // 测试延迟（通过测量连接时间）
    suspend fun testLatency(): Long = withContext(Dispatchers.IO) {
        isCancelled = false
        var latency = -1L
        var attempts = 0

        try {
            while (attempts < MAX_RECONNECT_ATTEMPTS && !isCancelled) {
                attempts++
                val start = System.currentTimeMillis()
                val ftpClient = connectToFtpServer()

                if (ftpClient != null) {
                    latency = System.currentTimeMillis() - start
                    disconnectFromFtpServer(ftpClient)
                    break
                }

                if (isCancelled) throw Exception("测试已取消")

                // 等待一段时间再重试
                Thread.sleep(1000)
            }

            if (latency == -1L) {
                throw Exception("无法连接到FTP服务器，尝试了${MAX_RECONNECT_ATTEMPTS}次")
            }

            if (isCancelled) throw Exception("测试已取消")
        } catch (e: Exception) {
            if (isCancelled) throw Exception("测试已取消")
            else throw Exception("延迟测试失败: ${e.message}")
        }

        latency
    }

    // 连接到FTP服务器
    private fun connectToFtpServer(): FTPClient? {
        val ftpClient = FTPClient()
        try {
            // 连接服务器
            ftpClient.connect(FTP_SERVER, FTP_PORT)

            // 检查连接响应
            val reply = ftpClient.replyCode
            if (!FTPReply.isPositiveCompletion(reply)) {
                ftpClient.disconnect()
                throw IOException("连接FTP服务器失败，响应代码: $reply")
            }

            // 登录
            if (!ftpClient.login(FTP_USER, FTP_PASSWORD)) {
                ftpClient.disconnect()
                throw IOException("FTP登录失败")
            }

            // 设置二进制传输模式
            ftpClient.setFileType(FTPClient.BINARY_FILE_TYPE)
            return ftpClient
        } catch (e: Exception) {
            if (ftpClient.isConnected) {
                try {
                    ftpClient.disconnect()
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
            return null
        }
    }

    // 重新连接到FTP服务器
    private fun reconnect(): FTPClient? {
        try {
            // 先确保旧连接已断开
            val ftpClient = connectToFtpServer()
            if (ftpClient != null && ftpClient.isConnected) {
                return ftpClient
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    // 断开FTP连接
    private fun disconnectFromFtpServer(ftpClient: FTPClient?) {
        if (ftpClient != null && ftpClient.isConnected) {
            try {
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // 启动心跳机制
    private fun startHeartbeat(ftpClient: FTPClient) {
        stopHeartbeat() // 先停止可能存在的心跳

        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor()
        heartbeatExecutor?.scheduleAtFixedRate({
            try {
                if (ftpClient.isConnected && !isCancelled) {
                    // 发送NOOP命令保持连接活跃
                    ftpClient.noop()
                }
            } catch (e: Exception) {
                // 心跳失败，连接可能已断开
                e.printStackTrace()
            }
        }, HEARTBEAT_INTERVAL.toLong(), HEARTBEAT_INTERVAL.toLong(), TimeUnit.SECONDS)
    }

    // 停止心跳机制
    private fun stopHeartbeat() {
        heartbeatExecutor?.shutdownNow()
        heartbeatExecutor = null
    }

    // 取消测试
    fun cancelTest() {
        isCancelled = true
        stopHeartbeat()
    }
}
