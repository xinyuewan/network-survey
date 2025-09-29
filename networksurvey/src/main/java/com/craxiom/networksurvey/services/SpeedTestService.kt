package com.craxiom.networksurvey.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.craxiom.networksurvey.NetworkSurveyActivity
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.logging.db.SpeedTestBase
import com.craxiom.networksurvey.logging.db.dao.SpeedTestResultDao
import com.craxiom.networksurvey.model.SpeedTestEvent
import com.craxiom.networksurvey.model.SpeedTestResult
import com.craxiom.networksurvey.util.NetworkSpeedTester
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

class SpeedTestService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var networkSpeedTester: NetworkSpeedTester
    private var isTesting = false

    fun isTesting(): Boolean = isTesting

    private lateinit var dao: SpeedTestResultDao

    // 实时事件
    private val _events = MutableSharedFlow<SpeedTestEvent>(replay = 0)
    val events = _events.asSharedFlow()

    // 每个指标缓存
    private val _lastLatency = MutableStateFlow<Long?>(null)
    val lastLatency: StateFlow<Long?> = _lastLatency

    private val _lastDownload = MutableStateFlow<Double?>(null)
    val lastDownload: StateFlow<Double?> = _lastDownload

    private val _lastUpload = MutableStateFlow<Double?>(null)
    val lastUpload: StateFlow<Double?> = _lastUpload

    private val _lastCompleted = MutableStateFlow<SpeedTestResult?>(null)
    val lastCompleted: StateFlow<SpeedTestResult?> = _lastCompleted

    inner class LocalBinder : Binder() {
        fun getService(): SpeedTestService = this@SpeedTestService
    }

    override fun onCreate() {
        super.onCreate()
        networkSpeedTester = NetworkSpeedTester(this)
        SpeedTestBase.init(applicationContext)
        dao = SpeedTestBase.getInstance().speedTestResultDao()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private suspend fun emitEvent(event: SpeedTestEvent) {
        _events.emit(event)
        when(event){
            is SpeedTestEvent.Latency -> _lastLatency.value = event.pingMs
            is SpeedTestEvent.Download -> _lastDownload.value = event.speedMbps
            is SpeedTestEvent.Upload -> _lastUpload.value = event.speedMbps
            is SpeedTestEvent.Completed -> _lastCompleted.value = event.record
            else -> {}
        }
    }

    fun startSpeedTest(networkType: String) {
        if (isTesting) return

        isTesting = true
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            try {
                emitEvent(SpeedTestEvent.Latency(0)) // 重置显示
                emitEvent(SpeedTestEvent.Download(0.0)) // 重置显示
                emitEvent(SpeedTestEvent.Upload(0.0))

                val latency = networkSpeedTester.testLatency()
                emitEvent(SpeedTestEvent.Latency(latency))

                val downloadSpeed = networkSpeedTester.testDownloadSpeed()
                emitEvent(SpeedTestEvent.Download(downloadSpeed))

                val uploadSpeed = networkSpeedTester.testUploadSpeed()
                emitEvent(SpeedTestEvent.Upload(uploadSpeed))

                val record = SpeedTestResult(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    networkType = networkType,
                    downloadSpeedMbps = downloadSpeed,
                    uploadSpeedMbps = uploadSpeed,
                    latencyMs = latency
                )
                dao.insertResult(record)
                emitEvent(SpeedTestEvent.Completed(record))

            } catch (e: Exception) {
                emitEvent(SpeedTestEvent.Error(e.message ?: "测速失败"))
            } finally {
                isTesting = false
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    fun cancelTest() {
        if (isTesting) {
            networkSpeedTester.cancelTest()
            isTesting = false
            serviceScope.launch { emitEvent(SpeedTestEvent.Error("测试已取消")) }
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "网络测速",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "网络测速服务正在运行"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, NetworkSurveyActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("网络测速中")
            .setContentText("正在进行网络速度测试")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "SPEED_TEST_CHANNEL"
        const val NOTIFICATION_ID = 1001
    }
}
