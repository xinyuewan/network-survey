package com.craxiom.networksurvey.logging.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;

import com.craxiom.networksurvey.logging.db.model.MessageEntity;

/**
 * 消息数据操作 Dao（Java 同步版）
 * 注意：必须在后台线程调用，禁止主线程直接使用
 */
@Dao
public interface MessageDao {

    /**
     * 插入消息数据，冲突时替换（与原 Kotlin 逻辑完全一致）
     * @param msg 消息实体类
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MessageEntity msg);
}