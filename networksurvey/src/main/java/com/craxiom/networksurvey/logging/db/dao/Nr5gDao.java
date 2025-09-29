package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.Nr5gEntity;

import java.util.List;

/**
 * NR5G 数据操作 Dao（Java 同步版）
 * 注意：必须在后台线程调用，禁止主线程直接使用
 */
@Dao
public interface Nr5gDao {

    /**
     * 插入 NR5G 数据，冲突时替换（与原 Kotlin 逻辑一致）
     * @param nr5g NR5G 实体类
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Nr5gEntity nr5g);

    /**
     * 按时间范围查询 NR5G 数据（与原 Kotlin SQL 逻辑一致）
     * @param start 开始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return 时间范围内的 NR5G 数据列表
     */
    @Query("SELECT * FROM Dev0_NR5GIE WHERE time BETWEEN :start AND :end")
    List<Nr5gEntity> getByTimeRange(long start, long end);
}