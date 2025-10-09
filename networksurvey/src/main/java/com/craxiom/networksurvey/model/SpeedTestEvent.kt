package com.craxiom.networksurvey.model

sealed class SpeedTestEvent {
    data class Initializing(val message: String) : SpeedTestEvent() // 新增这行
    data class Latency(val pingMs: Long) : SpeedTestEvent()
    data class Download(val speedMbps: Double) : SpeedTestEvent()
    data class Upload(val speedMbps: Double) : SpeedTestEvent()
    data class Completed(val record: SpeedTestResult) : SpeedTestEvent()
    data class Error(val message: String) : SpeedTestEvent()
}