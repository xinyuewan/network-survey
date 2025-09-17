package com.craxiom.networksurvey.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.FragmentSpeedTestHistoryBinding
import com.craxiom.networksurvey.fragments.SpeedTestRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class SpeedTestHistoryFragment : Fragment() {
    private var _binding: FragmentSpeedTestHistoryBinding? = null
    private val binding get() = _binding!!
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private lateinit var speedTestRepository: SpeedTestRepository
    private lateinit var historyAdapter: SpeedTestHistoryAdapter

    // 提供外部设置仓库的方法，便于共享数据
    // 添加这个方法用于接收Repository实例
    fun setRepository(repository: SpeedTestRepository) {
        this.speedTestRepository = repository
        // 重新加载数据
        loadTestHistory()
    }

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

        // 初始化适配器
        historyAdapter = SpeedTestHistoryAdapter(emptyList(), dateFormatter)

        // 设置RecyclerView
        binding.historyRecyclerView.adapter = historyAdapter
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(context)


        // 清除历史按钮点击事件
        binding.clearHistoryButton.setOnClickListener {
            if (::speedTestRepository.isInitialized) {
                lifecycleScope.launch {
                    speedTestRepository.clearHistory() // 挂起函数必须在协程中执行
                    // 清除完成后显示Toast（lifecycleScope默认在主线程，可直接更新UI）
                    Toast.makeText(context, "历史记录已清除", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadTestHistory() {
        lifecycleScope.launch {
            speedTestRepository.testResults.collectLatest { results ->
                historyAdapter.updateData(results)
                updateEmptyStateVisibility(results.isEmpty())
            }
        }
    }

    // 更新空状态显示
    private fun updateEmptyStateVisibility(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.historyRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 避免内存泄漏
    }
}