package com.craxiom.networksurvey.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 后台测速服务：负责执行下载、上传、时延三步。
 */
class SpeedTestService : Service() {

    // Binder
    inner class LocalBinder : Binder() {
        fun getService(): SpeedTestService = this@SpeedTestService
    }

    private val binder = LocalBinder()

    // 协程作用域（随 Service 生命周期取消）
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 测试进度状态流
    private val _progressFlow = MutableStateFlow<TestState>(TestState.Idle)
    val progressFlow: StateFlow<TestState> = _progressFlow

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel() // 确保退出时取消协程
    }

    /**
     * 一次性执行完整测速流程
     */
    fun startFullTest() {
        serviceScope.launch {
            // Step 1: 下载测速
            repeat(100) { i ->
                delay(50) // 模拟下载耗时
                val speed = Random.nextDouble(50.0, 150.0)
                _progressFlow.value = TestState.Downloading(speed, i + 1)
            }

            // Step 2: 上传测速
            repeat(100) { i ->
                delay(50) // 模拟上传耗时
                val speed = Random.nextDouble(20.0, 80.0)
                _progressFlow.value = TestState.Uploading(speed, i + 1)
            }

            // Step 3: 时延测试
            delay(500)
            val latency = Random.nextLong(20, 100)
            _progressFlow.value = TestState.Pinging(latency)

            // Step 4: 完成
            _progressFlow.value = TestState.Finished(
                downloadSpeed = Random.nextDouble(50.0, 150.0),
                uploadSpeed = Random.nextDouble(20.0, 80.0),
                latency = latency
            )
        }
    }

    /**
     * 定义测试状态机
     */
    sealed class TestState {
        object Idle : TestState()
        data class Downloading(val speedMbps: Double, val progressPercent: Int) : TestState()
        data class Uploading(val speedMbps: Double, val progressPercent: Int) : TestState()
        data class Pinging(val latencyMs: Long) : TestState()
        data class Finished(
            val downloadSpeed: Double,
            val uploadSpeed: Double,
            val latency: Long
        ) : TestState()
    }
}
