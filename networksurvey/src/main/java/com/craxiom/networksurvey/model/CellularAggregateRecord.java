package com.craxiom.networksurvey.model;

import android.location.Location;
import java.util.concurrent.atomic.AtomicLong;
import com.craxiom.networksurvey.fragments.model.CellularViewModel;
import com.craxiom.networksurvey.fragments.model.NrNeighbor;
import com.craxiom.networksurvey.fragments.model.LteNeighbor;
import java.util.Date;
import java.util.SortedSet;

/**
 * 主小区与邻区聚合数据模型，用于存储到本地CSV
 */
public class CellularAggregateRecord {
    // 静态原子计数器，用于实现offset的线程安全自增
    private static final AtomicLong GLOBAL_OFFSET = new AtomicLong(0);
    public long offset;
    // 基础信息
    public long timestamp; // 时间戳（毫秒）
    // 位置信息
    public double latitude; // 纬度
    public double longitude; // 经度
    public float altitude; // 高度（米）

    // 主小区信息
    public String servingProtocol; // 主小区网络类型（LTE/NR/UMTS/GSM）
    public Integer mcc; // 移动国家码
    public Integer mnc; // 移动网络码
    public Integer areaCode; // 区域码
    public long cellId; // 小区ID
    public Integer channelNumber; // 信道号（ARFCN/EARFCN）
    public String frequency; // 频率（MHz）
    public Integer band; // 频段
    public Integer bandwidth;
    public Integer pci; // 物理小区标识（仅LTE/NR）
    public Integer servingIndex;

    // 信号指标
    public Integer signalOne; // 主信号（RSRP/RSSI等）
    public Integer signalTwo; // 次要信号（RSRQ/RSCP等）
    public Integer signalThree; // 第三信号（SNR等）

    // 邻区聚合信息（多值用|分隔）
    public Integer num; // 邻区数量
    public String Narfcn; // 邻区NARFCN
    public String NPci; // 邻区PCI
    public String NRsrp; // 邻区Rsrp
    public String NRsrq; // 邻区Rsrq

    public CellularAggregateRecord() {
        // 获取当前计数器值并自增（原子操作，线程安全）
        this.offset = GLOBAL_OFFSET.getAndIncrement();
    }
    /**
     * 重置全局offset计数器（谨慎使用，通常用于数据清空场景）
     */
    public static void resetOffset() {
        GLOBAL_OFFSET.set(0);
    }

    /**
     * 获取当前最大的offset值（用于数据同步或校验）
     */
    public static long getCurrentMaxOffset() {
        return GLOBAL_OFFSET.get() - 1; // 减1是因为当前值已经是下一个要分配的值
    }


//
//    // 构造方法：从ViewModel数据转换
//    public CellularAggregateRecord(CellularViewModel viewModel) {
//        // 基础信息
//        this.timestamp = new Date().getTime();
////        this.dataNetworkType = viewModel.getDataNetworkType().getValue();
////        this.voiceNetworkType = viewModel.getVoiceNetworkType().getValue();
////        this.carrier = viewModel.getCarrier().getValue();
////        this.isAirplaneMode = viewModel.getAirplaneModeActive().getValue() == null ? false : viewModel.getAirplaneModeActive().getValue();
//
//        // 位置信息
//        Location location = viewModel.getLocation().getValue();
//        if (location != null) {
//            this.latitude = location.getLatitude();
//            this.longitude = location.getLongitude();
//            this.altitude = location.getAltitude();
//        }
//
//        // 主小区信息
//        CellularProtocol protocol = viewModel.getServingCellProtocol().getValue();
//        this.servingProtocol = protocol != null ? protocol.name() : "UNKNOWN";
//        this.mcc = viewModel.getMcc().getValue() != null ? viewModel.getMcc().getValue() : "";
//        this.mnc = viewModel.getMnc().getValue() != null ? viewModel.getMnc().getValue() : "";
//        this.areaCode = viewModel.getAreaCode().getValue();
//        this.cellId = viewModel.getCellId().getValue() == null ? -1 : viewModel.getCellId().getValue();
//        this.channelNumber = viewModel.getChannelNumber().getValue();
//        this.frequency = viewModel.getFrequency().getValue();
//        this.band = viewModel.getBand().getValue();
//        this.pci = viewModel.getPci().getValue();
//
//        // 信号指标
//        this.signalOne = viewModel.getSignalOne().getValue();
//        this.signalTwo = viewModel.getSignalTwo().getValue();
//        this.signalThree = viewModel.getSignalThree().getValue();
//
//        if (CellularProtocol.NR.name().equals(this.servingProtocol)) {
//            // 主小区为NR时，只处理NR邻区
//            SortedSet<NrNeighbor> nrNeighbors = viewModel.getNrNeighbors().getValue();
//            this.Narfcn = aggregateNrNarfcn(nrNeighbors);
//            this.NPci = aggregateNrPci(nrNeighbors);
//            this.NRsrp = aggregateNrRsrp(nrNeighbors);
//            this.NRsrq = aggregateNrSsRsrq(nrNeighbors);
//        } else if (CellularProtocol.LTE.name().equals(this.servingProtocol)) {
//            // 主小区为LTE时，只处理LTE邻区
//            SortedSet<LteNeighbor> lteNeighbors = viewModel.getLteNeighbors().getValue();
//            this.Narfcn = aggregateLteNarfcn(lteNeighbors);
//            this.NPci = aggregateLtePci(lteNeighbors);
//            this.NRsrp = aggregateLteRsrp(lteNeighbors);
//            this.NRsrq = aggregateLteRsrq(lteNeighbors); // 新增LTE RSRQ聚合
//        } else {
//            this.Narfcn = null;
//            this.NPci = null;
//            this.NRsrp = null;
//            this.NRsrq = null;
//        }
//
//    }
//
//    // 聚合NR邻区NARFCN
//    private String aggregateNrNarfcn(SortedSet<NrNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (NrNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.narfcn);
//        }
//        return sb.toString();
//    }
//
//    // 聚合NR邻区PCI
//    private String aggregateNrPci(SortedSet<NrNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (NrNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.pci);
//        }
//        return sb.toString();
//    }
//
//    // 聚合NR邻区RSRP
//    private String aggregateNrRsrp(SortedSet<NrNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (NrNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.ssRsrp);
//        }
//        return sb.toString();
//    }
//
//    // 聚合NR邻区RSRQ
//    private String aggregateNrSsRsrq(SortedSet<NrNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (NrNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.ssRsrq);
//        }
//        return sb.toString();
//    }
//
//    // 聚合LTE邻区earfcn
//    private String  aggregateLteNarfcn(SortedSet<LteNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (LteNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.earfcn);
//        }
//        return sb.toString();
//    }
//
//    // 聚合LTE邻区PCI
//    private String aggregateLtePci(SortedSet<LteNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (LteNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.pci);
//        }
//        return sb.toString();
//    }
//
//    // 聚合LTE邻区RSRP
//    private String aggregateLteRsrp(SortedSet<LteNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (LteNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.rsrp);
//        }
//        return sb.toString();
//    }
//    // 聚合LTE邻区RSRQ
//    private String aggregateLteRsrq(SortedSet<LteNeighbor> neighbors) {
//        if (neighbors == null || neighbors.isEmpty()) return "";
//        StringBuilder sb = new StringBuilder();
//        for (LteNeighbor neighbor : neighbors) {
//            if (sb.length() > 0) sb.append("|");
//            sb.append(neighbor.rsrq);
//        }
//        return sb.toString();
//    }
}