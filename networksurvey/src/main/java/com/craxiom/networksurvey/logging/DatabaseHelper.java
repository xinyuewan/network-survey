package com.craxiom.networksurvey.logging;

import android.content.Context;

import com.craxiom.networksurvey.logging.db.AggregateDatabase;
import com.craxiom.networksurvey.logging.db.model.GpsEntity;
import com.craxiom.networksurvey.logging.db.model.LteEntity;
import com.craxiom.networksurvey.logging.db.model.MessageEntity;
import com.craxiom.networksurvey.logging.db.model.Nr5gEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

/**
 * 聚合数据数据库辅助类，负责安全地执行数据库操作
 */
class DatabaseHelper {
    private final AggregateDatabase database;
    private final ExecutorService dbExecutor; // 统一管理数据库操作线程

    public DatabaseHelper(Context context) {
        this.database = AggregateDatabase.getInstance(context);
        this.dbExecutor = Executors.newSingleThreadExecutor(); // 单线程避免并发冲突
    }

    // 插入GPS数据
    public void insertGps(GpsEntity entity) {
        dbExecutor.execute(() -> {
            try {
                database.gpsDao().insert(entity);
            } catch (Exception e) {
                Timber.e(e, "插入GPS数据失败");
            }
        });
    }

    // 插入LTE数据
    public void insertLte(LteEntity entity) {
        dbExecutor.execute(() -> {
            try {
                database.lteDao().insert(entity);
            } catch (Exception e) {
                Timber.e(e, "插入LTE数据失败");
            }
        });
    }

    // 插入NR5G数据
    public void insertNr5g(Nr5gEntity entity) {
        dbExecutor.execute(() -> {
            try {
                database.nr5gDao().insert(entity);
            } catch (Exception e) {
                Timber.e(e, "插入NR5G数据失败");
            }
        });
    }

    // 插入消息数据
    public void insertMessage(MessageEntity entity) {
        dbExecutor.execute(() -> {
            try {
                database.messageDao().insert(entity);
            } catch (Exception e) {
                Timber.e(e, "插入消息数据失败");
            }
        });
    }

    /**
     * 关闭数据库连接和线程池
     */
    public void close() {
        dbExecutor.shutdown();
        database.closeDatabase();
        Timber.d("数据库连接已关闭");
    }
}