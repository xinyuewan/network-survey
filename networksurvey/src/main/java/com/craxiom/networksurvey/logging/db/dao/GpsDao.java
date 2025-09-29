package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.craxiom.networksurvey.logging.db.model.GpsEntity;
import com.craxiom.networksurvey.logging.db.model.LteEntity;
import com.craxiom.networksurvey.logging.db.model.MessageEntity;
import com.craxiom.networksurvey.logging.db.model.Nr5gEntity;
import java.util.List;

/**
 * GPS 数据操作 Dao（Java 同步版）
 * 注意：同步方法需在后台线程调用，禁止主线程直接使用
 */
@Dao
public interface GpsDao {
    // 原有单表插入方法
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GpsEntity gps);

    // 多表事务方法（核心修改）
    @Transaction // 标记为事务，保证原子性
    default void insertWithRelations( // default方法无需强制注解
                                      GpsEntity gps,
                                      LteEntity lte,
                                      Nr5gEntity nr5g,
                                      MessageEntity message,
                                      // 手动传入其他表的DAO实例（关键）
                                      LteDao lteDao,
                                      Nr5gDao nr5gDao,
                                      MessageDao messageDao
    ) {
        // 1. 插入GPS表
        this.insert(gps);

        // 2. 插入LTE/NR表（二选一）
        if (lte != null) lteDao.insert(lte);
        if (nr5g != null) nr5gDao.insert(nr5g);

        // 3. 插入Message表
        messageDao.insert(message);
    }
}