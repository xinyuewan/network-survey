package com.craxiom.networksurvey.model

sealed class SpeedTestEvent {
    data class Download(val speedMbps: Double) : SpeedTestEvent()
    data class Upload(val speedMbps: Double) : SpeedTestEvent()
    data class Latency(val pingMs: Long) : SpeedTestEvent()
    data class Completed(val record: SpeedTestResult) : SpeedTestEvent()
    data class Error(val message: String) : SpeedTestEvent()
}
