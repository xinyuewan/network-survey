package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * GPS 数据实体类（Java 版本）
 * 与原 Kotlin 版本字段、注解完全一致，适配 Room 数据库映射
 */
@Entity(tableName = "GPS")
public class GpsEntity {
    @PrimaryKey // 主键
    public long offset;
    public int gpsType;
    public long time;
    public Double longitude;
    public Double latitude;
    public Double altitude; 

    
}
