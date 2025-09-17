package com.craxiom.networksurvey.fragments

import android.content.Context
import com.craxiom.networksurvey.logging.db.SpeedTestBase
import com.craxiom.networksurvey.logging.db.dao.SpeedTestResultDao
import com.craxiom.networksurvey.model.SpeedTestResult
import kotlinx.coroutines.flow.Flow

object SpeedTestRepository {
    // 延迟初始化Dao
    private val dao by lazy {
        SpeedTestBase.getInstance().SpeedTestResultDao()
    }

    // 获取所有结果的Flow（自动监听数据变化）
    val testResults: Flow<List<SpeedTestResult>>
        get() = dao.getAllResults()

    // 保存测试结果（挂起函数，需在协程中调用）
    suspend fun saveTestResult(result: SpeedTestResult) {
        dao.insertResult(result)
    }

    // 清除历史（挂起函数，调用Room的suspend方法）
    suspend fun clearHistory() {
        dao.clearAllResults()
    }

}
