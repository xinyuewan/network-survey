package com.craxiom.networksurvey.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.FragmentSpeedTestDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import android.annotation.SuppressLint
import com.craxiom.networksurvey.model.SpeedTestResult
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt

class SpeedTestFragment : Fragment() {
    private var _binding: FragmentSpeedTestDashboardBinding? = null
    private val binding get() = _binding!!

    // 测速相关变量
    private var isTesting = false
    private var testJob: Job? = null
    private val TEST_DURATION = 10000 // 10秒
    // 增加多个下载服务器地址，避免单一服务器不可用
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

    // 测速结果
    private var downloadSpeed = 0.0 // Mbps
    private var uploadSpeed = 0.0 // Mbps
    private var latency = 0L // ms

    private val decimalFormat = DecimalFormat("#.##")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedTestDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化UI
        initUI()

        // 设置按钮点击事件
        binding.startTestButton.setOnClickListener {
            if (!isTesting) {
                startSpeedTest()
            } else {
                cancelSpeedTest()
            }
        }
    }

    private fun initUI() {
        // 初始化进度条
        binding.downloadProgress.max = 100
        binding.uploadProgress.max = 100

        // 初始化显示
        binding.downloadSpeedText.text = "0 Mbps"
        binding.uploadSpeedText.text = "0 Mbps"
        binding.latencyText.text = "0 ms"
        binding.statusText.text = getString(R.string.ready_to_test)
    }
    private fun generateResultId() = UUID.randomUUID().toString()

    private fun startSpeedTest() {
        if (isTesting) return

        isTesting = true
        updateTestButtonState()
        resetTestResults()
        binding.statusText.text = getString(R.string.testing_latency)

        // 在协程中执行测速
        testJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. 测试延迟
                testLatency()

                // 2. 测试下载速度
                withContext(Dispatchers.Main) {
                    binding.statusText.text = getString(R.string.testing_download)
                }
                testDownloadSpeed()

                // 3. 测试上传速度
                withContext(Dispatchers.Main) {
                    binding.statusText.text = getString(R.string.testing_upload)
                }
                testUploadSpeed()

                // 🌟 测试完成：构造结果并保存到Repository
                withContext(Dispatchers.Main) {
                    val testResult = SpeedTestResult(
                        id = generateResultId(),
                        timestamp = System.currentTimeMillis(), // 使用当前时间戳
                        networkType = "null",
                        downloadSpeedMbps = downloadSpeed,
                        uploadSpeedMbps = uploadSpeed,
                        latencyMs = latency
                    )

                    // 保存到Repository（注意：saveTestResult是挂起函数）
                    lifecycleScope.launch {
                        SpeedTestRepository.saveTestResult(testResult)
                    }

                    // 更新UI
                    binding.statusText.text = getString(R.string.test_complete)
                    isTesting = false
                    updateTestButtonState()
                    showToast("测速完成")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.statusText.text =
                        getString(R.string.test_failed, e.message ?: "未知错误")
                    isTesting = false
                    updateTestButtonState()
                    showToast("测试失败: ${e.message}")
                }
            }
        }
    }

    private fun cancelSpeedTest() {
        testJob?.cancel()
        isTesting = false
        updateTestButtonState()
        binding.statusText.text = getString(R.string.test_cancelled)
        showToast("已取消测试")
    }

    private suspend fun testLatency() {
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

            latency = System.currentTimeMillis() - start

            withContext(Dispatchers.Main) {
                binding.latencyText.text = "$latency ms"
            }
        } catch (e: Exception) {
            throw Exception("延迟测试失败: ${e.message}")
        }
    }

    private suspend fun testDownloadSpeed() {
        var totalBytesRead = 0L
        val startTime = System.currentTimeMillis()

        // 尝试多个下载服务器
        for (urlString in DOWNLOAD_URLS) {
            if (!isTesting) break  // 如果测试已取消，退出循环

            try {
                val url = URL(urlString)
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "正在从 ${url.host} 测试下载..."
                }

                val connection = url.openConnection() as HttpURLConnection

                // 应用SSL证书处理
                if (url.protocol == "https") {
                    handleSSLCertificate(connection)
                }

                // 增加超时时间
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                // 禁用缓存，确保实时下载
                connection.useCaches = false

                val inputStream = BufferedInputStream(connection.inputStream)
                val buffer = ByteArray(8192)
                var bytesRead: Int

                // 读取数据，持续指定时间
                while (System.currentTimeMillis() - startTime < TEST_DURATION && isTesting) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break // 文件读取完毕

                    totalBytesRead += bytesRead

                    // 计算当前速度并更新UI
                    val elapsedTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                    downloadSpeed = (totalBytesRead * 8.0) / (1024 * 1024 * elapsedTimeSeconds) // 转换为Mbps

                    withContext(Dispatchers.Main) {
                        updateDownloadUI()
                    }
                }

                inputStream.close()
                connection.disconnect()

                // 如果成功读取了数据且测试时间已到，退出循环
                if (System.currentTimeMillis() - startTime >= TEST_DURATION) {
                    break
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("从 ${urlString} 下载失败，尝试下一个服务器...")
                }
                // 继续尝试下一个服务器
                continue
            }
        }

        // 检查是否有有效数据
        if (totalBytesRead == 0L) {
            throw Exception("所有下载服务器测试失败")
        }
    }

    private suspend fun testUploadSpeed() {
        var totalBytesWritten = 0L
        val startTime = System.currentTimeMillis()

        try {
            // 创建随机数据用于上传测试
            val uploadData = ByteArray(UPLOAD_DATA_SIZE)
            for (i in uploadData.indices) {
                uploadData[i] = (Math.random() * 256).toInt().toByte()
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
                isTesting) {
                val bytesToWrite = Math.min(bufferSize, uploadData.size - bytesWritten)
                outputStream.write(uploadData, bytesWritten, bytesToWrite)
                bytesWritten += bytesToWrite
                totalBytesWritten += bytesToWrite

                // 计算当前速度并更新UI
                val elapsedTimeSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                uploadSpeed = (totalBytesWritten * 8.0) / (1024 * 1024 * elapsedTimeSeconds) // 转换为Mbps

                withContext(Dispatchers.Main) {
                    updateUploadUI()
                }
            }

            outputStream.flush()
            outputStream.close()
            connection.disconnect()
        } catch (e: Exception) {
            throw Exception("上传测试失败: ${e.message}")
        }
    }

    // SSL证书处理方法
    @SuppressLint("BadHostnameVerifier")
    private fun handleSSLCertificate(connection: HttpURLConnection) {
        if (connection is HttpsURLConnection) {
            // 明确指定参数类型
            connection.hostnameVerifier = HostnameVerifier { hostname: String?, session: javax.net.ssl.SSLSession? ->
                true // 信任所有主机名
            }

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

    private fun updateDownloadUI() {
        binding.downloadSpeedText.text = "${decimalFormat.format(downloadSpeed)} Mbps"
        // 进度条显示相对速度，假设最大100Mbps
        val progress = (downloadSpeed.coerceAtMost(100.0) / 100 * 100).roundToInt()
        binding.downloadProgress.progress = progress
    }

    private fun updateUploadUI() {
        binding.uploadSpeedText.text = "${decimalFormat.format(uploadSpeed)} Mbps"
        // 进度条显示相对速度，假设最大50Mbps
        val progress = (uploadSpeed.coerceAtMost(50.0) / 50 * 100).roundToInt()
        binding.uploadProgress.progress = progress
    }

    private fun updateTestButtonState() {
        binding.startTestButton.text = if (isTesting) {
            getString(R.string.cancel_test)
        } else {
            getString(R.string.start_test)
        }
    }

    private fun resetTestResults() {
        downloadSpeed = 0.0
        uploadSpeed = 0.0
        latency = 0L

        binding.downloadSpeedText.text = "0 Mbps"
        binding.uploadSpeedText.text = "0 Mbps"
        binding.latencyText.text = "0 ms"
        binding.downloadProgress.progress = 0
        binding.uploadProgress.progress = 0
    }

    private fun showToast(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 取消测试以防内存泄漏
        cancelSpeedTest()
        _binding = null
    }
}
