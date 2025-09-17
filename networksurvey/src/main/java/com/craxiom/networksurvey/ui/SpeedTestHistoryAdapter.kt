package com.craxiom.networksurvey.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.ItemSpeedTestHistoryBinding
import com.craxiom.networksurvey.model.SpeedTestResult
import java.text.SimpleDateFormat
import java.util.*

// 历史记录适配器（正确版本）
class SpeedTestHistoryAdapter(
    private var results: List<SpeedTestResult>,
    private val dateFormatter: SimpleDateFormat
) : androidx.recyclerview.widget.RecyclerView.Adapter<SpeedTestHistoryAdapter.HistoryViewHolder>() {

    // ViewHolder：绑定表格行的每个TextView
    inner class HistoryViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val tvNetwork: View = itemView.findViewById(R.id.tv_network) // 网络类型
        val tvTime: View = itemView.findViewById(R.id.tv_time) // 时间
        val tvUpload: View = itemView.findViewById(R.id.tv_upload) // 上传速度
        val tvDownload: View = itemView.findViewById(R.id.tv_download) // 下载速度
        // 可选：添加延迟显示
//        val tvLatency: View = itemView.findViewById(R.id.tv_latency) // 延迟
    }

    // 加载表格行的布局（需单独创建）
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_speed_test_history, parent, false)
        return HistoryViewHolder(itemView)
    }

    // 绑定数据到表格行
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val result = results[position]
        (holder.tvNetwork as android.widget.TextView).text = result.networkType
        (holder.tvTime as android.widget.TextView).text = dateFormatter.format(result.timestamp)
        (holder.tvUpload as android.widget.TextView).text = String.format(Locale.getDefault(), "%.2f", result.uploadSpeedMbps)
        (holder.tvDownload as android.widget.TextView).text = String.format(Locale.getDefault(), "%.2f", result.downloadSpeedMbps)
//        (holder.tvLatency as android.widget.TextView).text = "${result.latencyMs} ms"
    }

    // 数据总数
    override fun getItemCount() = results.size

    // 更新适配器数据（外部调用）
    fun updateData(newResults: List<SpeedTestResult>) {
        results = newResults
        notifyDataSetChanged() // 简单更新（优化：可用DiffUtil提升性能）
    }
}
