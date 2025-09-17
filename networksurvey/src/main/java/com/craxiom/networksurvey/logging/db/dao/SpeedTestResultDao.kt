package com.craxiom.networksurvey.logging.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.craxiom.networksurvey.model.SpeedTestResult

@Dao
interface SpeedTestResultDao {
    // 查询所有结果，按时间戳降序排列（最新的在前面）
    @Query("SELECT * FROM speed_test_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<SpeedTestResult>>

    // 插入新结果，若ID冲突则替换
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: SpeedTestResult)

    // 清除所有历史记录
    @Query("DELETE FROM speed_test_results")
    suspend fun clearAllResults()

    // 可选：按ID删除单个结果
    @Query("DELETE FROM speed_test_results WHERE id = :id")
    suspend fun deleteResultById(id: String)
}
