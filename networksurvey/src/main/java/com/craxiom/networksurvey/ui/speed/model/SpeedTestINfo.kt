package com.craxiom.networksurvey.ui.speed.model
import com.craxiom.networksurvey.model.CellularRecordWrapper
import java.io.Serializable



data class SpeedTestINfo(
    val servingCell: CellularRecordWrapper?,
    val subscriptionId: Int,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
