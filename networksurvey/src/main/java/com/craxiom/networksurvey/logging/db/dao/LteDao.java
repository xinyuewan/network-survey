package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.craxiom.networksurvey.logging.db.model.LteEntity;

import java.util.List;

@Dao
public interface LteDao {

    /**
     * 插入 LTE 数据，冲突时替换（与原 Kotlin 逻辑一致）
     * @param lte LTE 实体类
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LteEntity lte);

    /**
     * 按时间范围查询 NR5G 数据（与原 Kotlin SQL 逻辑一致）
     * @param start 开始时间戳（毫秒）
     * @param end 结束时间戳（毫秒）
     * @return 时间范围内的 NR5G 数据列表
     */
    @Query("SELECT * FROM Dev0_LTEIE WHERE time BETWEEN :start AND :end")
    List<LteEntity> getByTimeRange(long start, long end);
}