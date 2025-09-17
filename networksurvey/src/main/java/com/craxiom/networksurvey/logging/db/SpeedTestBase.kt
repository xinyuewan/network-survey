package com.craxiom.networksurvey.logging.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.craxiom.networksurvey.logging.db.dao.SpeedTestResultDao
import com.craxiom.networksurvey.model.SpeedTestResult

// 定义数据库版本和包含的实体类
@Database(entities = [SpeedTestResult::class], version = 1, exportSchema = false)
abstract class SpeedTestBase : RoomDatabase() {
    // 提供Dao接口的访问
    abstract fun SpeedTestResultDao(): SpeedTestResultDao

    companion object {
        // 单例模式，防止创建多个数据库实例
        @Volatile
        private var INSTANCE: SpeedTestBase? = null

        // 初始化数据库（接收Application上下文）
        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    INSTANCE = Room.databaseBuilder(
                        context.applicationContext, // 必须用Application上下文
                        SpeedTestBase::class.java,
                        "speed_test_database"
                    ).build()
                }
            }
        }

        // 获取实例
        fun getInstance(): SpeedTestBase {
            return INSTANCE ?: throw IllegalStateException("AppDatabase未初始化，请先调用init()")
        }
    }
}
