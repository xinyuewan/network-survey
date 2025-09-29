package com.craxiom.networksurvey.logging.db.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * NR5G 数据实体类（Java 版本）
 * 与原 Kotlin 版本字段、注解完全一致，适配 Room 数据库映射
 */
@Entity(tableName = "Dev0_NR5GIE") // 表名与原 Kotlin 一致
public class Nr5gEntity {
    @PrimaryKey // 主键与原 Kotlin 一致（offset 作为主键）
    public long offset; // Kotlin 的 Long 对应 Java 的 long（非空）

    public long time; // Kotlin 的 Long 对应 Java 的 long（非空）

    public Integer type; // Kotlin 的 String? 对应 Java 的 String（可空，如"NR/LTE"）

    public Integer pci; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer earfcn; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Float dl_earfcn; // Kotlin 的 Float? 对应 Java 的 Float（可空）

    public Float ul_earfcn; // Kotlin 的 Float? 对应 Java 的 Float（可空）

    public Integer dl_bw; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer ul_bw; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer tac; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer band; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer mcc; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer mnc; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer cgi; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer serv_cell_index; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer serv_cell_rsrp; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer serv_cell_rsrq; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer serv_cell_tx0_rsrp; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer serv_cell_tx1_rsrp; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer serv_cell_rssi; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public Integer num_cell; // Kotlin 的 Int? 对应 Java 的 Integer（可空）

    public String n_pci; // Kotlin 的 String? 对应 Java 的 String（可空，邻区PCI聚合字符串）

    public String n_rsrp; // Kotlin 的 String? 对应 Java 的 String（可空，邻区RSRP聚合字符串）

    public String n_rsrq; // Kotlin 的 String? 对应 Java 的 String（可空，邻区RSRQ聚合字符串）
    public String n_earfcn;

}
