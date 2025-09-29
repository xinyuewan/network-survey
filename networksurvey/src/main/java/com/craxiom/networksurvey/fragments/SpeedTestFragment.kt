package com.craxiom.networksurvey.fragments

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.craxiom.networksurvey.R
import com.craxiom.networksurvey.databinding.FragmentSpeedTestDashboardBinding
import com.craxiom.networksurvey.model.SpeedTestEvent
import com.craxiom.networksurvey.service.SpeedTestService
import com.craxiom.networksurvey.util.NetworkSpeedTester
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class SpeedTestFragment : Fragment() {
    private var _binding: FragmentSpeedTestDashboardBinding? = null
    private val binding get() = _binding!!

    private var speedTestService: SpeedTestService? = null
    private var isServiceBound = false
    private val networkSpeedTester by lazy { NetworkSpeedTester(requireContext()) }
    private val decimalFormat = DecimalFormat("#.##")

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SpeedTestService.LocalBinder
            speedTestService = binder.getService()
            isServiceBound = true

            // ✅ 恢复上次测试结果
            speedTestService?.let { svc ->
                svc.lastLatency.value?.let { binding.latencyText.text = "$it ms" }
                svc.lastDownload.value?.let {
                    binding.downloadSpeedText.text = "${decimalFormat.format(it)} Mbps"
                    binding.downloadProgress.progress = (it.coerceAtMost(100.0) / 100 * 100).toInt()
                }
                svc.lastUpload.value?.let {
                    binding.uploadSpeedText.text = "${decimalFormat.format(it)} Mbps"
                    binding.uploadProgress.progress = (it.coerceAtMost(50.0) / 50 * 100).toInt()
                }
                svc.lastCompleted.value?.let { binding.statusText.text = getString(R.string.test_complete) }
            }

            // 监听事件流
            lifecycleScope.launch {
                speedTestService?.events?.collect { event ->
                    handleSpeedTestEvent(event)
                }
            }

            updateTestState()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isServiceBound = false
            speedTestService = null
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSpeedTestDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        setupButtonListener()

        val intent = Intent(requireContext(), SpeedTestService::class.java)
        requireContext().startForegroundService(intent)
        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        updateNetworkType()
    }

    private fun initUI() {
        binding.downloadProgress.max = 100
        binding.uploadProgress.max = 100
        resetTestResults()
        binding.statusText.text = getString(R.string.ready_to_test)
        binding.latencyText.text = "0 ms" // 初始化显示
    }

    private fun setupButtonListener() {
        binding.startTestButton.setOnClickListener {
            if (speedTestService?.isTesting() == true) {
                speedTestService?.cancelTest()
            } else {
                startSpeedTest()
            }
        }
    }

    private fun startSpeedTest() {

        if (!networkSpeedTester.isNetworkAvailable()) {
            showToast(getString(R.string.error_no_network))
            return
        }

        val networkType = networkSpeedTester.getNetworkType()
        binding.networkTypeText.text = getString(R.string.network_type_format, networkType)
        speedTestService?.startSpeedTest(networkType)
        updateTestButtonState()
    }

    private fun updateNetworkType() {
        val networkType = networkSpeedTester.getNetworkType()
        binding.networkTypeText.text = getString(R.string.network_type_format, networkType)
    }

    private fun handleSpeedTestEvent(event: SpeedTestEvent) {
        when (event) {
            is SpeedTestEvent.Download -> {
                binding.statusText.text = getString(R.string.testing_download)
                binding.downloadSpeedText.text = "${decimalFormat.format(event.speedMbps)} Mbps"
                binding.downloadProgress.progress = (event.speedMbps.coerceAtMost(100.0) / 100 * 100).toInt()
            }
            is SpeedTestEvent.Upload -> {
                binding.statusText.text = getString(R.string.testing_upload)
                binding.uploadSpeedText.text = "${decimalFormat.format(event.speedMbps)} Mbps"
                binding.uploadProgress.progress = (event.speedMbps.coerceAtMost(50.0) / 50 * 100).toInt()
            }
            is SpeedTestEvent.Latency -> {
                binding.statusText.text = getString(R.string.testing_latency)
                binding.latencyText.text = "${event.pingMs} ms"
            }
            is SpeedTestEvent.Completed -> {
                binding.statusText.text = getString(R.string.test_complete)
                updateTestButtonState()
                showToast("测速完成")
            }
            is SpeedTestEvent.Error -> {
                binding.statusText.text = getString(R.string.test_failed, event.message)
                updateTestButtonState()
                showToast(event.message)
            }
        }
    }

    private fun updateTestState() {
        val isTesting = speedTestService?.isTesting() == true
        binding.startTestButton.text = if (isTesting) getString(R.string.stop_test) else getString(R.string.start_test)
    }

    private fun updateTestButtonState() = lifecycleScope.launchWhenResumed { updateTestState() }

    private fun resetTestResults() {
        binding.downloadSpeedText.text = "0 Mbps"
        binding.uploadSpeedText.text = "0 Mbps"
        binding.latencyText.text = "0 ms"
        binding.downloadProgress.progress = 0
        binding.uploadProgress.progress = 0
    }

    private fun showToast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }
        // ❌ 不 stopService，保持测速不中断
    }
}
