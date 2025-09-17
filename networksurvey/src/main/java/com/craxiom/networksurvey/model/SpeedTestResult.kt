package com.craxiom.networksurvey.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

// 定义数据库表名
@Entity(tableName = "speed_test_results")
data class SpeedTestResult(
    @PrimaryKey val id: String,
    // 使用Long存储时间戳（比Date更适合持久化）
    val timestamp: Long,
    val networkType: String,
    val downloadSpeedMbps: Double,
    val uploadSpeedMbps: Double,
    val latencyMs: Long
) : Serializable
