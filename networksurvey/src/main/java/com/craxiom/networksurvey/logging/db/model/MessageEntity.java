package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;


/**
 * 消息数据实体类（Java 版本）
 * 与原 Kotlin 版本字段、注解完全一致，适配 Room 数据库映射
 */
@Entity(tableName = "Dev0_MESSAGE") // 表名与原 Kotlin 一致
public class MessageEntity {
    @PrimaryKey // 主键与原 Kotlin 一致（offset 作为主键）
    public long offset; // Kotlin 的 Long 对应 Java 的 long（非空）

    public long time; // Kotlin 的 Long 对应 Java 的 long（非空）

    public Integer indexID; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer messageNo; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public String messageName; // Kotlin 的 String? 对应 Java 的 String（可空）

    public Integer Direction; // Kotlin 的 Int? 对应 Java 的 Integer（可空，保持原字段名大写）

    public Integer channelID; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public String channelName; // Kotlin 的 String? 对应 Java 的 String（可空）

    public Integer networkType; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer msgLen; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public String msgContent; // Kotlin 的 String? 对应 Java 的 String（可空）

    public String msgPaser; // Kotlin 的 String? 对应 Java 的 String（可空）


}
