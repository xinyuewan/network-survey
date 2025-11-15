package com.craxiom.networksurvey.logging;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;

import com.craxiom.messaging.CdmaRecord;
import com.craxiom.messaging.GsmRecord;
import com.craxiom.messaging.GsmRecordData;
import com.craxiom.messaging.LteBandwidth;
import com.craxiom.messaging.LteRecord;
import com.craxiom.messaging.NrRecord;
import com.craxiom.messaging.UmtsRecord;
import com.craxiom.networksurvey.constants.GsmMessageConstants;
import com.craxiom.networksurvey.constants.NetworkSurveyConstants;
import com.craxiom.networksurvey.constants.csv.CellularCsvConstants;
import com.craxiom.networksurvey.constants.csv.CsvConstants;
import com.craxiom.networksurvey.constants.csv.GsmCsvConstants;
import com.craxiom.networksurvey.listeners.ICellularSurveyRecordListener;
import com.craxiom.networksurvey.model.CellularAggregateRecord;
import com.craxiom.networksurvey.model.CellularProtocol;
import com.craxiom.networksurvey.model.CellularRecordWrapper;
import com.craxiom.networksurvey.services.NetworkSurveyService;
import com.craxiom.networksurvey.util.CellularUtils;
import com.craxiom.networksurvey.util.MathUtils;
import com.craxiom.networksurvey.util.NsUtils;

import com.google.common.base.Strings;
import com.google.protobuf.Int32Value;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.contents.Contents;
import mil.nga.geopackage.contents.ContentsDao;
import mil.nga.geopackage.contents.ContentsDataType;
import mil.nga.geopackage.db.GeoPackageDataType;
import mil.nga.geopackage.features.columns.GeometryColumns;
import mil.nga.geopackage.features.columns.GeometryColumnsDao;
import mil.nga.geopackage.features.user.FeatureColumn;
import mil.nga.geopackage.features.user.FeatureCursor;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.features.user.FeatureRow;
import mil.nga.geopackage.features.user.FeatureTable;
import mil.nga.geopackage.geom.GeoPackageGeometryData;
import mil.nga.geopackage.srs.SpatialReferenceSystem;
import mil.nga.sf.GeometryType;
import mil.nga.sf.Point;
import timber.log.Timber;

/**
 * 蜂窝网络数据聚合日志器（GeoPackage 版本）
 * 工作流程与 CellularSurveyRecordLogger 对齐：
 * 1. 继承 SurveyRecordLogger 复用 GeoPackage 初始化、日志轮转、文件管理能力
 * 2. 重写 createTables 定义聚合数据的 GeoPackage 表结构
 * 3. 接收蜂窝数据批次，聚合主邻区后写入 GeoPackage
 */
public class CellularRecordLogger extends SurveyRecordLogger implements ICellularSurveyRecordListener {

    // 聚合数据 GeoPackage 表名（与 CellularSurveyRecordLogger 表名风格一致）
    private static final String AGGREGATE_TABLE_NAME = "cellular_aggregate-";
    // WGS84 空间坐标系（GeoPackage 标准空间参考）
    private static final int WGS84_SRS_ID = 4326;

    private final CellularUtils cellularUtils;
    private int currentMaxIndexId = 0;
    private boolean isMaxIndexInitialized = false;

    /**
     * 构造函数（与 CellularSurveyRecordLogger 构造逻辑对齐）
     * 调用父类构造初始化 GeoPackage 日志目录、文件名前缀、线程模型
     */
    public CellularRecordLogger(NetworkSurveyService service, Looper looper) {
        super(service, looper,
                NetworkSurveyConstants.LOG_DIRECTORY_NAME,  // 复用默认日志目录
                AGGREGATE_TABLE_NAME);  // 非延迟创建文件（与 CellularSurveyRecordLogger 一致）

        this.cellularUtils = new CellularUtils();
    }

    /**
     * 重写 createTables：创建聚合数据的 GeoPackage 表
     * 包含：基础信息、主小区信息、邻区聚合信息、空间位置（Point 类型）
     */
    @Override
    protected void createTables(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException {
        createGpsRecordTable(geoPackage, srs);
        createMsgRecordTable(geoPackage, srs);
        createLteRecordTable(geoPackage, srs);
        createNrRecordTable(geoPackage, srs);
    }

    /**
     * Creates an GeoPackage Table that can be populated with GPS Records.
     *
     * @param geoPackage The GeoPackage to create the table in.
     * @param srs        The SRS to use for the table coordinates.
     * @throws SQLException If there is a problem working with the GeoPackage SQLite DB.
     */
    private void createGpsRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException {
        createTable("GPS", geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "offset", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "gpsType", GeoPackageDataType.INTEGER, false, null));
//            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "time", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "longitude", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "latitude", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "altitude", GeoPackageDataType.REAL, false, null));
        });
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_gps_offset ON GPS (offset ASC)";
        geoPackage.execSQL(indexSql);
    }

    private void createMsgRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException {
        createTable("Dev0_MESSAGE", geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "offset", GeoPackageDataType.INTEGER, false, null));
//            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "time", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "indexID", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "messageNo", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "messageName", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "Direction", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "channelID", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "channelName", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "networkType", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "msgLen", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "msgContent", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "msgPaser", GeoPackageDataType.TEXT, false, null));
        });
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_dev0_message_offset ON Dev0_MESSAGE (offset ASC)";
        geoPackage.execSQL(indexSql);
    }

    private void createNrRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException {
        createTable("Dev0_NR5GIE", geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "offset", GeoPackageDataType.INTEGER, false, null));
//            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "time", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "type", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "pci", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "earfcn", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "dl_earfcn", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "ul_earfcn", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "dl_bw", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "ul_bw", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "tac", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "band", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "mcc", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "mnc", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "cgi", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_index", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_rsrp", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_rsrq", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_tx0_rsrp", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_tx1_rsrp", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_rssi", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "num_cell", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_pci", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_rsrp", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_rsrq", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_earfcn", GeoPackageDataType.TEXT, false, null));
        });
        // 为offset列创建索引（表创建后执行）
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_dev0_nr5gie_offset ON Dev0_NR5GIE (offset ASC)";
        geoPackage.execSQL(indexSql);
    }

    private void createLteRecordTable(GeoPackage geoPackage, SpatialReferenceSystem srs) throws SQLException {
        createTable("Dev0_LTEIE", geoPackage, srs, true, (tableColumns, columnNumber) -> {
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "offset", GeoPackageDataType.INTEGER, false, null));
//            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "time", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "type", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "pci", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "earfcn", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "dl_earfcn", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "ul_earfcn", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "dl_bw", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "ul_bw", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "tac", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "band", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "mcc", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "mnc", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "cgi", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_index", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_rsrp", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_rsrq", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_tx0_rsrp", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_tx1_rsrp", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "serv_cell_rssi", GeoPackageDataType.REAL, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "num_cell", GeoPackageDataType.INTEGER, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_pci", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_rsrp", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_rsrq", GeoPackageDataType.TEXT, false, null));
            tableColumns.add(FeatureColumn.createColumn(columnNumber++, "n_earfcn", GeoPackageDataType.TEXT, false, null));
        });

        // 为offset列创建索引（表创建后执行）
        String indexSql = "CREATE INDEX IF NOT EXISTS idx_dev0_lteie_offset ON Dev0_LTEIE (offset ASC)";
        geoPackage.execSQL(indexSql);
    }

    /**
     * 核心方法：接收蜂窝数据批次，处理主邻区聚合后写入 GeoPackage
     * 与 CellularSurveyRecordLogger#onCellularBatch 逻辑对齐（批次处理、线程安全）
     */
    @Override
    public void onCellularBatch(List<CellularRecordWrapper> cellularGroup, int subscriptionId) {
        if (cellularGroup.isEmpty() || !loggingEnabled) {
            Timber.d("蜂窝数据批次为空或日志未启用，跳过处理");
            return;
        }

        // 1. 分离主小区和邻区（保持原聚合逻辑）
        CellularRecordWrapper servingWrapper = null;
        List<CellularRecordWrapper> neighborWrappers = new ArrayList<>();
        for (CellularRecordWrapper wrapper : cellularGroup) {
            if (isServingCell(wrapper)) {
                servingWrapper = wrapper;
            } else {
                neighborWrappers.add(wrapper);
            }
        }
        if (servingWrapper == null) {
            Timber.w("未找到主小区数据，跳过聚合");
            return;
        }

        // 2. 构建聚合记录（复用原聚合逻辑）
        CellularAggregateRecord aggregateRecord = buildAggregateRecord(servingWrapper, neighborWrappers);
        if (aggregateRecord == null) {
            Timber.w("聚合记录构建失败，跳过写入");
            return;
        }

        // 3. 写入 GeoPackage（与 CellularSurveyRecordLogger 一致：后台线程 + 数据库锁）
        handler.post(() -> {
            synchronized (geoPackageLock) {
                try {
                    writeAggregateToGeoPackage(aggregateRecord);
                    checkIfRolloverNeeded(); // 检查日志轮转（复用父类能力）
                } catch (Exception e) {
                    Timber.e(e, "聚合数据写入 GeoPackage 失败");
                }
            }
        });
    }

    private void writeGPSRecordToLogFile(final CellularAggregateRecord record) {
        if (!loggingEnabled) return;



        try {
            if (geoPackage != null) {
                FeatureDao featureDao = geoPackage.getFeatureDao("GPS");
                FeatureRow row = featureDao.newRow();

                row.setValue("offset", record.offset);
                row.setValue("gpsType", 0);
                row.setValue("time", record.timestamp);
                row.setValue("longitude", record.longitude);
                row.setValue("latitude", record.latitude);
                row.setValue("altitude", record.altitude);


                featureDao.insert(row);

                checkIfRolloverNeeded();
            }
        } catch (Exception e) {
            Timber.e(e, "Something went wrong when trying to write a GSM survey record");
        }


    }

    private void writeMsgRecordToLogFile(final CellularAggregateRecord record) {
        if (!loggingEnabled) return;

        try {
            if (geoPackage != null) {
                FeatureDao featureDao = geoPackage.getFeatureDao("Dev0_MESSAGE");
                FeatureRow row = featureDao.newRow();
                // 初始化最大indexID（仅首次执行）
                if (!isMaxIndexInitialized) {
                    // 1. 创建查询条件：只查询indexID字段
                    String[] projection = {"indexID"};

                    // 2. 按indexID降序排序，取第一条就是最大值
                    String sortOrder = "indexID DESC";

                    // 3. 执行查询（限制只返回1条结果）
                    FeatureCursor cursor = featureDao.query(projection, null, null, sortOrder, "1");

                    if (cursor.moveToFirst()) {
                        // 4. 从结果中获取最大indexID
                        currentMaxIndexId = cursor.getInt(cursor.getColumnIndex("indexID"));
                    } else {
                        // 表为空时初始化为0
                        currentMaxIndexId = 0;
                    }

                    cursor.close(); // 关闭游标释放资源
                    isMaxIndexInitialized = true;
                }

                // 自增indexID（从1开始，每次+1）
                currentMaxIndexId++;

                row.setValue("offset", record.offset);
                row.setValue("time", record.timestamp);
                Integer a = "LTE".equals(record.servingProtocol) ? 0 : 1;

                row.setValue("networkType", a);
                row.setValue("msgPaser", "第" + record.offset + "记录");
                row.setValue("indexID", currentMaxIndexId); // 临时默认值，需替换为真实数据
                row.setValue("messageNo", 0);
                row.setValue("messageName", "Unknown");
                row.setValue("Direction", 0);
                row.setValue("channelID", 0);
                row.setValue("channelName", "Unknown");
                row.setValue("msgLen", 0);
                row.setValue("msgContent", "");

                featureDao.insert(row);

                checkIfRolloverNeeded();
            }
        } catch (Exception e) {
            Timber.e(e, "Something went wrong when trying to write a MESSAGE survey record");
        }

    }

    private void writeNr5GRecordToLogFile(final CellularAggregateRecord record) {
        if (!loggingEnabled) return;


        try {
            if (geoPackage != null) {
                FeatureDao featureDao = geoPackage.getFeatureDao("Dev0_NR5GIE");
                FeatureRow row = featureDao.newRow();

                row.setValue("offset", record.offset);
                row.setValue("time", record.timestamp);
                Integer a = "LTE".equals(record.servingProtocol) ? 0 : 1;
                row.setValue("type", a);
                row.setValue("pci", record.pci);
                row.setValue("earfcn", record.channelNumber);
                row.setValue("dl_earfcn", parseDouble(record.frequency));
                row.setValue("ul_earfcn", null);
                row.setValue("dl_bw", record.bandwidth != null ? Double.valueOf(record.bandwidth) : null);
                row.setValue("ul_bw", null);
                row.setValue("tac", record.areaCode);
                row.setValue("band", record.band);
                row.setValue("mcc", record.mcc);
                row.setValue("mnc", record.mnc);
                row.setValue("cgi", record.cellId);
                row.setValue("serv_cell_index", record.servingIndex!= null?record.servingIndex:-1);
                row.setValue("serv_cell_rsrp", record.signalOne != null ? Double.valueOf(record.signalOne) : null);
                row.setValue("serv_cell_rsrq", record.signalTwo != null ? Double.valueOf(record.signalTwo) : null);
                row.setValue("serv_cell_tx0_rsrp", null);
                row.setValue("serv_cell_tx1_rsrp", null);
                row.setValue("serv_cell_rssi", null);
                row.setValue("num_cell", record.num);
                row.setValue("n_pci", record.NPci);
                row.setValue("n_rsrp", record.NRsrp);
                row.setValue("n_rsrq", record.NRsrq);
                row.setValue("n_earfcn", record.Narfcn);

                featureDao.insert(row);

                checkIfRolloverNeeded();
            }
        } catch (Exception e) {
            Timber.e(e, "Something went wrong when trying to write a Nr5G survey record");
        }

    }

    private void writeLTERecordToLogFile(final CellularAggregateRecord record) {
        if (!loggingEnabled) return;


        try {
            if (geoPackage != null) {
                FeatureDao featureDao = geoPackage.getFeatureDao("Dev0_LTEIE");
                FeatureRow row = featureDao.newRow();

                row.setValue("offset", record.offset);
                row.setValue("time", record.timestamp);
                Integer a = "LTE".equals(record.servingProtocol) ? 0 : 1;
                row.setValue("type", a);
                row.setValue("pci", record.pci);
                row.setValue("earfcn", record.channelNumber);
                row.setValue("dl_earfcn", parseDouble(record.frequency));
                row.setValue("ul_earfcn", null);
                row.setValue("dl_bw", record.bandwidth != null ? Double.valueOf(record.bandwidth) : null);
                row.setValue("ul_bw", null);
                row.setValue("tac", record.areaCode);
                row.setValue("band", record.band);
                row.setValue("mcc", record.mcc);
                row.setValue("mnc", record.mnc);
                row.setValue("cgi", record.cellId);
                row.setValue("serv_cell_index", record.servingIndex!= null?record.servingIndex:-1);
                row.setValue("serv_cell_rsrp", record.signalOne != null ? Double.valueOf(record.signalOne) : null);
                row.setValue("serv_cell_rsrq", record.signalTwo != null ? Double.valueOf(record.signalTwo) : null);
                row.setValue("serv_cell_tx0_rsrp", null);
                row.setValue("serv_cell_tx1_rsrp", null);
                row.setValue("serv_cell_rssi", null);
                row.setValue("num_cell", record.num);
                row.setValue("n_pci", record.NPci);
                row.setValue("n_rsrp", record.NRsrp);
                row.setValue("n_rsrq", record.NRsrq);
                row.setValue("n_earfcn", record.Narfcn);

                featureDao.insert(row);

                checkIfRolloverNeeded();
            }
        } catch (Exception e) {
            Timber.e(e, "Something went wrong when trying to write a LTE survey record");
        }

    }

    /**
     * 将聚合记录写入 GeoPackage
     * 核心逻辑与 CellularSurveyRecordLogger#writeXxxRecordToLogFile 对齐
     */
    private void writeAggregateToGeoPackage(CellularAggregateRecord record) throws Exception {
        if (geoPackage == null) {
            Timber.e("GeoPackage 实例为空，无法写入");
            return;
        }
        handler.post(() -> {
            synchronized (geoPackageLock) {
                try {
                    // 一次性处理所有表写入，避免多次加锁
                    writeGPSRecordToLogFile(record);
                    writeMsgRecordToLogFile(record);
                    if (Objects.equals(record.servingProtocol, "LTE")) {
                        writeLTERecordToLogFile(record);
//                        writeNr5GRecordToLogFile(record);
                    }
                    else if (Objects.equals(record.servingProtocol, "NR")){
                        writeNr5GRecordToLogFile(record);}

                } catch (Exception e) {
                    Timber.e(e, "聚合数据写入 GeoPackage 失败");
                }
            }
        });


    }

    // 工具方法：提取 band 中的数字（如 "B3"→3，"n78"→78）
    private Integer extractBandNumber(String bandStr) {
        if (Strings.isNullOrEmpty(bandStr)) return null;
        return Integer.parseInt(bandStr.replaceAll("[^0-9]", "")); // 移除非数字字符
    }

    // -------------------------- 复用原聚合逻辑（仅适配 GeoPackage 数据类型）--------------------------

    /**
     * 判断是否为主小区（复用原逻辑）
     */
    private boolean isServingCell(CellularRecordWrapper wrapper) {
        try {
            switch (wrapper.cellularProtocol) {
                case LTE:
                    return ((LteRecord) wrapper.cellularRecord).getData().getServingCell() != null
                            && ((LteRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
                case NR:
                    return ((NrRecord) wrapper.cellularRecord).getData().getServingCell() != null
                            && ((NrRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
                case GSM:
                    return ((GsmRecord) wrapper.cellularRecord).getData().getServingCell() != null
                            && ((GsmRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
                case UMTS:
                    return ((UmtsRecord) wrapper.cellularRecord).getData().getServingCell() != null
                            && ((UmtsRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
                case CDMA:
                    return ((CdmaRecord) wrapper.cellularRecord).getData().getServingCell() != null
                            && ((CdmaRecord) wrapper.cellularRecord).getData().getServingCell().getValue();
                default:
                    Timber.w("不支持的协议类型：%s", wrapper.cellularProtocol);
                    return false;
            }
        } catch (Exception e) {
            Timber.e(e, "判断主小区状态失败");
            return false;
        }
    }

    /**
     * 构建聚合记录（主小区+邻区，复用原逻辑）
     */
    private CellularAggregateRecord buildAggregateRecord(CellularRecordWrapper serving, List<CellularRecordWrapper> neighbors) {
        CellularAggregateRecord record = new CellularAggregateRecord();
        // 提取主小区数据
        try {
            switch (serving.cellularProtocol) {
                case LTE:
                    fillLteData(record, (LteRecord) serving.cellularRecord);
                    break;
                case NR:
                    fillNrData(record, (NrRecord) serving.cellularRecord);
                    break;
                case GSM:
                    fillGsmData(record, (GsmRecord) serving.cellularRecord);
                    break;
                case UMTS:
                    fillUmtsData(record, (UmtsRecord) serving.cellularRecord);
                    break;
                case CDMA:
                    Timber.w("CDMA协议暂未实现聚合处理");
                    return null;
                default:
                    Timber.w("不支持的聚合协议：%s", serving.cellularProtocol);
                    return null;
            }
        } catch (Exception e) {
            Timber.e(e, "处理主小区数据失败");
            return null;
        }

        // 处理邻区数据
        processNeighbors(record, neighbors);

        return record;
    }

    /**
     * 处理邻区数据并聚合（复用原逻辑）
     */
    private void processNeighbors(CellularAggregateRecord record, List<CellularRecordWrapper> neighbors) {
        List<String> nArfcnList = new ArrayList<>();
        List<String> nPciList = new ArrayList<>();
        List<String> nRsrpList = new ArrayList<>();
        List<String> nRsrqList = new ArrayList<>();

        // 主小区作为第一个元素（便于后续识别）
        if (Objects.equals(record.servingProtocol, "NR")){
            nArfcnList.add(String.valueOf(record.channelNumber));
            nPciList.add(String.valueOf(record.pci));
            nRsrpList.add(String.valueOf(record.signalOne));
            nRsrqList.add(String.valueOf(record.signalTwo));
            record.servingIndex = 0;}

        // 追加邻区数据
        for (CellularRecordWrapper neighbor : neighbors) {
            try {
                if (Objects.equals(record.servingProtocol, "LTE") && neighbor.cellularProtocol == CellularProtocol.LTE) {
                    LteRecord lte = (LteRecord) neighbor.cellularRecord;
                    nArfcnList.add(getValueOrEmpty(lte.getData().getEarfcn()));
                    nPciList.add(getValueOrEmpty(lte.getData().getPci()));
                    nRsrpList.add(getValueOrEmpty(lte.getData().getRsrp()));
                    nRsrqList.add(getValueOrEmpty(lte.getData().getRsrq()));
                } else if (Objects.equals(record.servingProtocol, "NR") && neighbor.cellularProtocol == CellularProtocol.NR) {
                    NrRecord nr = (NrRecord) neighbor.cellularRecord;
                    nArfcnList.add(getValueOrEmpty(nr.getData().getNarfcn()));
                    nPciList.add(getValueOrEmpty(nr.getData().getPci()));
                    nRsrpList.add(getValueOrEmpty(nr.getData().getSsRsrp()));
                    nRsrqList.add(getValueOrEmpty(nr.getData().getSsRsrq()));
                }
            } catch (Exception e) {
                Timber.e(e, "处理邻区数据失败，跳过该邻区");
            }
        }

        record.num = nPciList.size();
        // 聚合为 | 分隔的字符串
        record.Narfcn = String.join("|", nArfcnList);
        record.NPci = String.join("|", nPciList);
        record.NRsrp = String.join("|", nRsrpList);
        record.NRsrq = String.join("|", nRsrqList);
    }

    // -------------------------- 主小区数据填充（复用原逻辑）--------------------------

    private void fillLteData(CellularAggregateRecord record, LteRecord lteRecord) {
        var data = lteRecord.getData();
        LteBandwidth lteBandwidth = data.getLteBandwidth();

        record.servingProtocol = "LTE";
        record.timestamp = NsUtils.getEpochFromRfc3339(data.getDeviceTime());
        record.latitude = data.getLatitude();
        record.longitude = data.getLongitude();
        record.altitude = data.getAltitude();
        record.mcc = convertProtoInt32ToInteger(data.getMcc());
        record.mnc = convertProtoInt32ToInteger(data.getMnc());
        record.areaCode = convertProtoInt32ToInteger(data.getTac());
        record.cellId = data.hasEci() ? data.getEci().getValue() : 0;
        record.channelNumber = convertProtoInt32ToInteger(data.getEarfcn());
        record.frequency = data.hasEarfcn() ? String.valueOf(cellularUtils.earfcnToFrequencyMhz(data.getEarfcn().getValue())) : "null";
        record.band = data.hasEarfcn() ? cellularUtils.downlinkEarfcnToBand(data.getEarfcn().getValue()) : null;
        record.bandwidth = lteBandwidth == LteBandwidth.UNRECOGNIZED ? null : lteBandwidth.getNumber();
        record.pci = convertProtoInt32ToInteger(data.getPci());
        record.signalOne = data.hasRsrp() ? (int) data.getRsrp().getValue() : null;
        record.signalTwo = data.hasRsrq() ? (int) data.getRsrq().getValue() : null;
        record.signalThree = data.hasSnr() ? (int) data.getSnr().getValue() : null;
    }

    private void fillNrData(CellularAggregateRecord record, NrRecord nrRecord) {
        var data = nrRecord.getData();
        record.servingProtocol = "NR";
        record.timestamp = NsUtils.getEpochFromRfc3339(data.getDeviceTime());
        record.latitude = data.getLatitude();
        record.longitude = data.getLongitude();
        record.altitude = data.getAltitude();
        record.mcc = convertProtoInt32ToInteger(data.getMcc());
        record.mnc = convertProtoInt32ToInteger(data.getMnc());
        record.areaCode = convertProtoInt32ToInteger(data.getTac());
        record.cellId = data.hasNci() ? data.getNci().getValue() : 0;
        record.channelNumber = convertProtoInt32ToInteger(data.getNarfcn());
        record.pci = convertProtoInt32ToInteger(data.getPci());
        record.signalOne = data.hasSsRsrp() ? (int) data.getSsRsrp().getValue() : null;
        record.signalTwo = data.hasSsRsrq() ? (int) data.getSsRsrq().getValue() : null;
        record.signalThree = data.hasSsSinr() ? (int) data.getSsSinr().getValue() : null;
        record.frequency = data.hasNarfcn() ? String.valueOf(cellularUtils.narfcnToFrequencyMhz(data.getNarfcn().getValue())) : null;
        record.band = data.hasNarfcn() ? cellularUtils.narfcnToNrBand(data.getNarfcn().getValue()) : null;
    }

    private void fillGsmData(CellularAggregateRecord record, GsmRecord gsmRecord) {
        var data = gsmRecord.getData();
        record.servingProtocol = "GSM";
        record.timestamp = NsUtils.getEpochFromRfc3339(data.getDeviceTime());
        record.latitude = data.getLatitude();
        record.longitude = data.getLongitude();
        record.altitude = data.getAltitude();
        record.mcc = convertProtoInt32ToInteger(data.getMcc());
        record.mnc = convertProtoInt32ToInteger(data.getMnc());
        record.areaCode = convertProtoInt32ToInteger(data.getLac());
        record.cellId = data.hasCi() ? data.getCi().getValue() : 0;
        record.channelNumber = convertProtoInt32ToInteger(data.getArfcn());
        record.signalOne = data.hasSignalStrength() ? (int) data.getSignalStrength().getValue() : null;
    }

    private void fillUmtsData(CellularAggregateRecord record, UmtsRecord umtsRecord) {
        var data = umtsRecord.getData();
        record.servingProtocol = "UMTS";
        record.timestamp = NsUtils.getEpochFromRfc3339(data.getDeviceTime());
        record.latitude = data.getLatitude();
        record.longitude = data.getLongitude();
        record.altitude = data.getAltitude();
        record.mcc = convertProtoInt32ToInteger(data.getMcc());
        record.mnc = convertProtoInt32ToInteger(data.getMnc());
        record.areaCode = convertProtoInt32ToInteger(data.getLac());
        record.cellId = data.hasCid() ? data.getCid().getValue() : 0;
        record.channelNumber = convertProtoInt32ToInteger(data.getUarfcn());
        record.signalOne = data.hasRscp() ? (int) data.getRscp().getValue() : null;
        record.signalTwo = data.hasEcno() ? (int) data.getEcno().getValue() : null;
    }

    // -------------------------- 工具方法（适配 GeoPackage 数据类型）--------------------------

    /**
     * 获取 protobuf 字段值（处理 null）
     */
    private String getValueOrEmpty(Object protobufValue) {
        if (protobufValue == null) return "";
        try {
            Object value = protobufValue.getClass().getMethod("getValue").invoke(protobufValue);
            return value != null ? value.toString() : "";
        } catch (Exception e) {
            Timber.w(e, "获取protobuf字段值失败");
            return "";
        }
    }

    /**
     * 安全处理字符串值（GeoPackage 空值兼容）
     */
    private String safeValue(String value) {
        return value == null || "null".equalsIgnoreCase(value) ? null : value;
    }

    /**
     * 字符串转 Float（GeoPackage 字段类型适配）
     */
    private Float parseFloat(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) return null;
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            Timber.w(e, "无效的浮点数格式: %s", value);
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) return null;
        try {
            // 解析原始数值
            double originalValue = Double.parseDouble(value);
            // 四舍五入保留1位小数：乘以10后取整，再除以10.0
            double roundedValue = Math.round(originalValue * 10) / 10.0;
            return roundedValue;
        } catch (NumberFormatException e) {
            Timber.w(e, "无效的浮点数格式: %s", value);
            return null;
        }
    }


    /**
     * 字符串转 Integer（GeoPackage 字段类型适配）
     */
    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty() || "null".equalsIgnoreCase(value)) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Timber.w(e, "无效的整数格式: %s", value);
            return null;
        }
    }

    public static Integer convertProtoInt32ToInteger(Int32Value protoInt32) {
        // 处理 null 情况：如果输入为 null，直接返回 null
        if (protoInt32 == null) {
            return null;
        }
        // 从 Int32Value 中获取 int 值，自动装箱为 Integer
        return protoInt32.getValue();
    }


}