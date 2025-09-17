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
    abstract fun speedTestResultDao(): SpeedTestResultDao

    companion object {
        // 单例模式，防止创建多个数据库实例
        @Volatile
        private var INSTANCE: SpeedTestBase? = null

        /**
         * 初始化数据库（必须传入 Application Context）
         */
        fun init(context: Context) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(
                            context.applicationContext,
                            SpeedTestBase::class.java,
                            "speed_test_database"
                        ).build()
                    }
                }
            }
        }

        /**
         * 获取数据库实例
         */
        fun getInstance(): SpeedTestBase {
            return INSTANCE ?: throw IllegalStateException("SpeedTestBase 未初始化，请先调用 init(context)")
        }
    }
}
