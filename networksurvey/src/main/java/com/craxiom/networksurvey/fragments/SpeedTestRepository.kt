package com.craxiom.networksurvey.fragments

import com.craxiom.networksurvey.logging.db.SpeedTestBase
import com.craxiom.networksurvey.model.SpeedTestResult
import kotlinx.coroutines.flow.Flow

object SpeedTestRepository {
    // 延迟初始化 Dao
    private val dao by lazy {
        SpeedTestBase.getInstance().speedTestResultDao()
    }

    // 获取所有结果的 Flow（自动监听数据变化）
    val testResults: Flow<List<SpeedTestResult>>
        get() = dao.getAllResults()

    // 保存测试结果（挂起函数，需在协程中调用）
    suspend fun saveTestResult(result: SpeedTestResult) {
        dao.insertResult(result)
    }

    // 清除历史记录
    suspend fun clearHistory() {
        dao.clearAllResults()
    }
}
