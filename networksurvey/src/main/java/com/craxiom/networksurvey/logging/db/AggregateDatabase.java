package com.craxiom.networksurvey.logging.db;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

import com.craxiom.networksurvey.logging.db.dao.GpsDao;
import com.craxiom.networksurvey.logging.db.dao.LteDao;
import com.craxiom.networksurvey.logging.db.dao.Nr5gDao;
import com.craxiom.networksurvey.logging.db.dao.MessageDao;
import com.craxiom.networksurvey.logging.db.model.GpsEntity;
import com.craxiom.networksurvey.logging.db.model.LteEntity;
import com.craxiom.networksurvey.logging.db.model.Nr5gEntity;
import com.craxiom.networksurvey.logging.db.model.MessageEntity;

/**
 * 聚合数据数据库，管理GPS、LTE、NR5G和消息实体的存储
 */
@Database(entities = {GpsEntity.class, LteEntity.class, Nr5gEntity.class, MessageEntity.class}, version = 1, exportSchema = false)
public abstract class AggregateDatabase extends RoomDatabase {
    private static volatile AggregateDatabase INSTANCE;
    private static final String DATABASE_NAME = "cellular_aggregate.db";

    public abstract GpsDao gpsDao();
    public abstract LteDao lteDao();
    public abstract Nr5gDao nr5gDao();
    public abstract MessageDao messageDao();

    /**
     * 获取数据库单例实例
     *
     * @param context 上下文
     * @return 数据库实例
     */
    public static AggregateDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AggregateDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AggregateDatabase.class, DATABASE_NAME)
                            .allowMainThreadQueries() // 谨慎使用：实际项目建议移除，通过后台线程操作
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 关闭数据库连接（在不需要时调用）
     */
    public void closeDatabase() {
        if (INSTANCE != null && INSTANCE.isOpen()) {
            INSTANCE.close();
            INSTANCE = null;
        }
    }
}