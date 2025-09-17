package com.craxiom.networksurvey.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.FragmentSpeedTestHistoryBinding
import com.craxiom.networksurvey.model.SpeedTestResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SpeedTestHistoryFragment : Fragment() {
    private var _binding: FragmentSpeedTestHistoryBinding? = null
    private val binding get() = _binding!!

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private lateinit var historyAdapter: SpeedTestHistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSpeedTestHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化适配器（加载独立Item布局）
        historyAdapter = SpeedTestHistoryAdapter(emptyList(), dateFormatter)

        // 配置RecyclerView（关键：必须是LinearLayoutManager，纵向排列）
        binding.historyRecyclerView.apply {
            layoutManager = LinearLayoutManager(context) // 纵向列表，确保Item单行显示
            adapter = historyAdapter
            isNestedScrollingEnabled = false // 解决ScrollView内滚动问题
            // 添加水平分隔线（可选，美化表格）
            addItemDecoration(
                androidx.recyclerview.widget.DividerItemDecoration(
                    context,
                    LinearLayoutManager.VERTICAL
                )
            )
        }

        // 清除历史按钮
        binding.clearHistoryButton.setOnClickListener {
            lifecycleScope.launch {
                SpeedTestRepository.clearHistory()
                showToast("历史记录已清除")
            }
        }

        // 监听数据变化
        lifecycleScope.launch {
            SpeedTestRepository.testResults.collectLatest { testResults ->
                historyAdapter.updateData(testResults)
                binding.historyRecyclerView.visibility = if (testResults.isNotEmpty()) View.VISIBLE else View.GONE
                binding.emptyState.visibility = if (testResults.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showToast(message: String) {
        if (isAdded && context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    // 历史记录适配器（每个Item是1行4列的表格）
    class SpeedTestHistoryAdapter(
        private var data: List<SpeedTestResult>,
        private val dateFormatter: SimpleDateFormat
    ) : RecyclerView.Adapter<SpeedTestHistoryAdapter.HistoryViewHolder>() {

        // ViewHolder：直接绑定Item布局中的4个TextView
        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvNetwork: TextView = itemView.findViewById(R.id.tv_network)
            val tvTime: TextView = itemView.findViewById(R.id.tv_time)
            val tvUpload: TextView = itemView.findViewById(R.id.tv_upload)
            val tvDownload: TextView = itemView.findViewById(R.id.tv_download)
        }

        // 加载独立的Item布局（item_speed_test_history.xml）
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val itemView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_speed_test_history, parent, false) // 加载正确的Item布局
            return HistoryViewHolder(itemView)
        }

        // 绑定数据到表格行（1行4列）
        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val result = data[position]
            holder.tvNetwork.text = result.networkType
            holder.tvTime.text = dateFormatter.format(Date(result.timestamp)) // 时间戳转日期
            holder.tvUpload.text = String.format(Locale.getDefault(), "%.2f", result.uploadSpeedMbps)
            holder.tvDownload.text = String.format(Locale.getDefault(), "%.2f", result.downloadSpeedMbps)
        }

        override fun getItemCount() = data.size

        fun updateData(newData: List<SpeedTestResult>) {
            data = newData
            notifyDataSetChanged()
        }
    }
}