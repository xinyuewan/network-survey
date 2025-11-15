android-network-survey 是一个用于 Android 设备的网络动态监控库，主要功能是收集、记录和分析蜂窝网络（Cellular）、Wi-Fi、蓝牙和 GNSS（全球导航卫星系统）的信号数据，并支持本地存储或实时传输。其网络动态监控的核心流程可分为 **服务初始化、数据采集、数据处理、数据存储 / 传输、UI 展示** 五个主要阶段，以下是详细解释：

### 一、核心服务初始化（NetworkSurveyService）

网络监控的核心逻辑由 NetworkSurveyService 服务实现，它是整个流程的入口，负责统筹所有监控任务。初始化流程如下：

1. **服务创建与基础配置**

在 onCreate() 方法中，服务会完成基础资源初始化：

- - 初始化 **唤醒锁（WakeLock）**：防止设备休眠，确保监控任务持续运行（通过 PowerManager 创建）。

- - 启动后台线程（HandlerThread）：用于处理耗时操作（如数据读写、网络请求），避免阻塞 UI 线程。

- - 初始化设备 ID：生成唯一设备标识，用于数据关联。

1. **控制器初始化**

服务会创建针对不同网络类型的控制器，负责具体的信号数据采集：

- - CellularController：监控蜂窝网络（GSM、CDMA、LTE、NR 等）的信号强度、小区信息（如 MCC、MNC、Cell ID）等。

- - WifiController：收集 Wi-Fi 网络的 SSID、BSSID、信号强度（RSSI）、信道等信息。

- - BluetoothController：扫描周围蓝牙设备的名称、MAC 地址、信号强度等。

- - GnssController：获取 GNSS 卫星（如 GPS、北斗）的定位信息、卫星数量、信噪比等。

1. **位置信息关联**

网络监控数据通常需要结合地理位置，因此服务初始化了多个位置监听器：

- - GpsListener：作为主要位置监听器，获取设备的经纬度、海拔等信息。

- - ExtraLocationListener：分别监听 GNSS 和网络定位（如基站、Wi-Fi）的补充位置数据，提高定位精度。

1. **服务启动与绑定**

外部通过发送 ACTION_START_SURVEY 广播启动服务，NetworkSurveyActivity（主界面）会绑定到该服务，实现 UI 与服务的交互（如启动 / 停止监控、获取实时数据）。

### 二、数据采集（各控制器实现）

各控制器通过 Android 系统 API 或框架，实时采集对应类型的网络数据：

1. **蜂窝网络数据采集（****CellularController****）**

- - 利用 Android 的 TelephonyManager 监听蜂窝网络状态变化，获取服务小区（Serving Cell）和邻区（Neighboring Cells）的信息。

- - 解析不同制式（GSM、LTE、NR 等）的信号参数，如 LTE 的 RSRP（参考信号接收功率）、RSRQ（参考信号接收质量），NR 的 SS-RSRP 等。

- - 示例：在 processNrServingCell() 方法中，解析 NR（5G）小区的 NCI（小区标识）、PCI（物理小区标识）、信号强度等，并更新到 ViewModel 供 UI 展示。

1. **Wi-Fi 数据采集（****WifiController****）**

- - 通过 WifiManager 扫描周围 Wi-Fi 热点，获取 SSID、BSSID、信道、信号强度（RSSI）等信息。

- - 监听 Wi-Fi 连接状态变化，记录当前连接的网络详情。

- - 示例：WifiDetailsFragment 通过注册 IWifiSurveyRecordListener，实时接收 Wi-Fi 数据并更新 UI。

1. **蓝牙与 GNSS 数据采集**

- - 蓝牙：通过 BluetoothAdapter 扫描周围蓝牙设备，获取设备名称、MAC 地址、信号强度（RSSI）等。

- - GNSS：利用 Android 的 LocationManager 或 GNSS 相关 API（如 GnssStatus.Callback），获取卫星数量、信噪比（CN0）、定位坐标等。

### 三、数据处理（SurveyRecordProcessor）

采集到的原始数据（如信号强度、小区 ID、位置坐标）会由 SurveyRecordProcessor 统一处理：

1. **数据标准化**：将不同网络类型的原始数据转换为统一格式（如 Protobuf 消息），便于后续存储或传输。

1. **位置关联**：将采集到的网络数据与当前位置信息（经纬度）绑定，确保每条记录都包含地理位置上下文。

1. **过滤与校验**：例如通过 SsidExclusionManager 过滤不需要监控的 Wi-Fi SSID，或校验数据完整性（如忽略缺失位置的记录）。

### 四、数据存储与传输

处理后的数据可通过两种方式输出：**本地存储** 或 **实时传输**。

1. **本地存储**

- - 支持写入 **GeoPackage**（地理信息数据库）或 **CSV** 文件，由各类日志记录器（如 CellularSurveyRecordLogger）实现：

- - - CellularSurveyRecordLogger：针对蜂窝网络数据，创建对应的数据表（如 GSM、LTE 表），并将数据写入 GeoPackage，包含信号参数、位置坐标等字段。

- - - 文件存储路径：默认在应用私有目录下，按网络类型分类（如蜂窝数据文件前缀为 cellular_）。

1. **实时传输**

- - 通过 **MQTT** 或 **gRPC** 协议将数据实时发送到服务器：

- - - MQTT 连接：由 MqttConnection 类管理，实现 IConnectionStateListener 监听连接状态（连接、断开、重连），并通过 IMqttService 接口发送数据。

- - - 数据格式：使用 Protobuf 序列化（如 DeviceStatusData、LteRecord 等消息类型），确保传输效率。

### 五、UI 展示与用户交互

用户通过 UI 实时监控网络状态，核心界面由 SurveyMonitorScreen 和 NetworkSurveyActivity 实现：

1. **实时状态展示**

- - SurveyMonitorScreen 是监控主界面，包含两个标签页：

- - - **Status 页**：显示当前各网络类型的监控状态（如是否正在记录、信号强度、小区数量）。

- - - **Map 页**：通过地图展示蜂窝基站位置（结合 TowerMapScreen），标记新发现的基站（通过 TowerDetectionManager 检测）。

1. **用户操作交互**

- - 用户可通过开关控制监控的启动 / 停止（如开启蜂窝网络日志记录），操作会通过 SurveyMonitorViewModel 传递给 NetworkSurveyService。

- - 权限管理：NetworkSurveyActivity 会检查并请求必要权限（如 ACCESS_FINE_LOCATION 用于定位、READ_PHONE_STATE 用于蜂窝网络信息），无权限时无法启动监控。

### 六、数据上传与扩展

除实时传输外，应用还支持将本地存储的历史数据上传到第三方数据库：

- 支持的平台：OpenCelliD（全球蜂窝基站数据库）和 BeaconDB（众包地理数据库）。

- 上传逻辑：借鉴 TowerCollector 项目的代码，通过 DbUploadStore 管理上传记录计数，确保数据不重复上传。

### 总结

android-network-survey 的网络动态监控流程可概括为：

**服务初始化（统筹资源）→ 多控制器并行采集（分网络类型）→ 数据处理（标准化 + 位置关联）→ 存储 / 传输（本地文件或实时推送）→ UI 展示（实时状态与地图）**。通过模块化设计（控制器、处理器、日志器），实现了对多种网络类型的全面监控，同时支持灵活的输出方式（本地存储 / 实时传输）。

要在现有网络监控仓库中添加网络测速功能，需结合现有架构（服务、控制器、数据处理、存储/上传机制）进行扩展，以下是详细实现方案：


### **一、功能架构设计**
基于现有代码的分层架构（`Service`-`Controller`-`Processor`-`Storage/MQTT`），新增测速功能的核心模块包括：
1. **UI层**：新增测速界面，展示位置、网络信息、实时测速结果及历史记录。
2. **控制器**：`SpeedTestController` 负责发起测速、收集测速数据。
3. **数据模型**：定义测速记录结构体，包含位置、网络信息、测速指标。
4. **存储与上传**：扩展本地数据库和MQTT上传，支持测速数据的持久化和实时推送。


### **二、具体实现步骤**


#### **1. 新增测速界面（UI层）**
在现有界面体系中添加 `SpeedTestFragment`，包含以下元素：
- **基础信息区**：展示当前位置（经纬度）、服务小区信息（如LTE/NR的基站ID、信号强度RSRP）或WiFi信息（SSID、BSSID、RSSI）。
- **控制区**：「开始测试」/「停止测试」按钮。
- **实时结果区**：显示下载速度、上传速度、时延、抖动、丢包率。
- **历史记录区**：列表展示过往测速记录，支持下拉刷新。

**关键代码示例**（`SpeedTestFragment.java`）：
```java
public class SpeedTestFragment extends Fragment implements LocationListener {
    private FragmentSpeedTestBinding binding;
    private SpeedTestViewModel viewModel;
    private GpsListener gpsListener; // 复用现有位置监听
    private CellularController cellularController; // 复用蜂窝网络信息
    private WifiController wifiController; // 复用WiFi信息

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSpeedTestBinding.inflate(inflater);
        
        // 初始化ViewModel，关联测速控制器
        viewModel = new ViewModelProvider(this).get(SpeedTestViewModel.class);
        
        // 绑定基础信息（位置、网络信息）
        bindLocationInfo();
        bindNetworkInfo();
        
        // 开始测试按钮点击事件
        binding.startTestButton.setOnClickListener(v -> startSpeedTest());
        
        // 观察测速结果
        viewModel.getSpeedTestResult().observe(getViewLifecycleOwner(), this::updateTestResultUI);
        
        // 加载历史记录
        loadHistoryRecords();
        
        return binding.getRoot();
    }

    // 绑定位置信息（复用现有GpsListener）
    private void bindLocationInfo() {
        gpsListener = ((NetworkSurveyActivity) getActivity()).getGpsListener();
        gpsListener.registerLocationListener(this);
    }

    // 绑定网络信息（蜂窝或WiFi）
    private void bindNetworkInfo() {
        // 示例：获取当前服务小区信息
        cellularController = ((NetworkSurveyService) getService()).getCellularController();
        cellularController.addCellularListener(cellInfo -> {
            String cellInfoStr = formatCellInfo(cellInfo); // 格式化基站信息
            binding.cellInfoText.setText(cellInfoStr);
            binding.signalStrengthText.setText("RSRP: " + cellInfo.getSignalStrength());
        });
        
        // 同理绑定WiFi信息...
    }

    // 开始测速
    private void startSpeedTest() {
        SpeedTestController speedTestController = ((NetworkSurveyService) getService()).getSpeedTestController();
        speedTestController.startTest(new SpeedTestCallback() {
            @Override
            public void onProgress(SpeedTestProgress progress) {
                // 更新实时进度（如下载中、上传中）
                binding.testStatusText.setText(progress.getStatus());
            }

            @Override
            public void onComplete(SpeedTestResult result) {
                viewModel.saveResult(result); // 保存结果到本地
                updateTestResultUI(result);
            }
        });
    }

    // 更新UI显示测速结果
    private void updateTestResultUI(SpeedTestResult result) {
        binding.downloadSpeedText.setText(result.getDownloadSpeed() + " Mbps");
        binding.uploadSpeedText.setText(result.getUploadSpeed() + " Mbps");
        binding.latencyText.setText(result.getLatency() + " ms");
        binding.jitterText.setText(result.getJitter() + " ms");
        binding.packetLossText.setText(result.getPacketLoss() + "%");
    }

    // 加载历史记录
    private void loadHistoryRecords() {
        viewModel.getHistoryRecords().observe(getViewLifecycleOwner(), records -> {
            SpeedTestHistoryAdapter adapter = new SpeedTestHistoryAdapter(records);
            binding.historyRecyclerView.setAdapter(adapter);
        });
    }
}
```


#### **2. 测速核心逻辑（控制器层）**
新增 `SpeedTestController`，负责执行测速任务（依赖现有网络库如OkHttp），并收集指标：
- **下载速度**：通过HTTP GET请求下载指定大小的测试文件，计算平均速率。
- **上传速度**：通过HTTP POST上传随机数据，计算平均速率。
- **时延/抖动**：通过多次TCP连接测试或ICMP Ping（需root权限，替代方案用TCP）计算往返时间（RTT），抖动为RTT标准差。
- **丢包率**：发送多个测试包，统计丢失比例。

**关键代码示例**（`SpeedTestController.java`）：

```java
public class SpeedTestController {
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean isTesting = false;

    // 测试服务器配置（可从配置文件或偏好设置读取）
    private static final String DOWNLOAD_TEST_URL = "https://speedtest.example.com/testfile.bin";
    private static final String UPLOAD_TEST_URL = "https://speedtest.example.com/upload";
    private static final int TEST_DATA_SIZE = 10 * 1024 * 1024; // 10MB

    public SpeedTestController(Context context) {
        this.context = context;
    }

    public void startTest(SpeedTestCallback callback) {
        if (isTesting) return;
        isTesting = true;

        executor.execute(() -> {
            try {
                // 1. 获取当前位置和网络信息（从现有服务中获取）
                Location location = getCurrentLocation();
                NetworkInfo networkInfo = getCurrentNetworkInfo(); // 包含蜂窝/WiFi信息

                // 2. 执行测速任务
                callback.onProgress(new SpeedTestProgress("开始下载测试..."));
                double downloadSpeed = testDownload();

                callback.onProgress(new SpeedTestProgress("开始上传测试..."));
                double uploadSpeed = testUpload();

                callback.onProgress(new SpeedTestProgress("测试时延和丢包率..."));
                LatencyResult latencyResult = testLatencyAndLoss();

                // 3. 封装结果
                SpeedTestResult result = new SpeedTestResult(
                        System.currentTimeMillis(),
                        location,
                        networkInfo,
                        downloadSpeed,
                        uploadSpeed,
                        latencyResult.getLatency(),
                        latencyResult.getJitter(),
                        latencyResult.getPacketLoss()
                );

                callback.onComplete(result);
            } catch (Exception e) {
                callback.onError(e.getMessage());
            } finally {
                isTesting = false;
            }
        });
    }

    // 下载速度测试
    private double testDownload() throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        Request request = new Request.Builder().url(DOWNLOAD_TEST_URL).build();
        long startTime = System.nanoTime();
        Response response = client.newCall(request).execute();
        long contentLength = response.body().contentLength();
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1e9;
        return (contentLength / (1024 * 1024)) / durationSeconds; // Mbps
    }

    // 上传速度测试（类似下载，发送随机数据）
    private double testUpload() throws IOException {
        // 实现逻辑类似下载，使用POST发送测试数据
        // ...
    }

    // 时延和丢包率测试（示例：TCP连接测试）
    private LatencyResult testLatencyAndLoss() {
        List<Long> rttList = new ArrayList<>();
        int totalPackets = 10;
        int lostPackets = 0;

        for (int i = 0; i < totalPackets; i++) {
            try {
                long start = System.currentTimeMillis();
                // 连接测试服务器的端口（如80）
                Socket socket = new Socket(new InetSocketAddress("speedtest.example.com", 80), 5000);
                socket.close();
                long rtt = System.currentTimeMillis() - start;
                rttList.add(rtt);
            } catch (Exception e) {
                lostPackets++;
            }
        }

        double latency = rttList.stream().mapToLong(Long::longValue).average().orElse(0);
        double jitter = calculateJitter(rttList); // 计算标准差
        double packetLoss = (lostPackets / (double) totalPackets) * 100;

        return new LatencyResult(latency, jitter, packetLoss);
    }

    // 从现有服务获取当前位置（复用GpsListener）
    private Location getCurrentLocation() {
        NetworkSurveyService service = ((NetworkSurveyApplication) context.getApplicationContext()).getNetworkSurveyService();
        return service.getPrimaryLocationListener().getLatestLocation();
    }

    // 从现有控制器获取网络信息（蜂窝/WiFi）
    private NetworkInfo getCurrentNetworkInfo() {
        // 示例：获取当前服务小区信息
        NetworkSurveyService service = ((NetworkSurveyApplication) context.getApplicationContext()).getNetworkSurveyService();
        CellInfo servingCell = service.getCellularController().getServingCellInfo();
        // 或获取WiFi信息
        ScanResult wifiInfo = service.getWifiController().getCurrentWifiInfo();
        // 封装为NetworkInfo对象
        return new NetworkInfo(servingCell, wifiInfo);
    }

    public interface SpeedTestCallback {
        void onProgress(SpeedTestProgress progress);
        void onComplete(SpeedTestResult result);
        void onError(String message);
    }
}
```


#### **3. 数据模型与处理**
新增测速相关数据类，并集成到现有数据处理流程：

- **`SpeedTestResult`**：存储单次测速结果，包含时间戳、位置、网络信息、测速指标。
- **`SpeedTestRecord`**：protobuf定义（扩展现有消息格式），用于MQTT上传。

**Protobuf定义示例**（`speedtest_message.proto`）：
```protobuf
syntax = "proto3";
package com.craxiom.messaging;

import "location.proto";
import "network_info.proto";

message SpeedTestRecord {
  SpeedTestData data = 1;
  Location location = 2; // 复用现有位置消息
  NetworkInfo network_info = 3; // 复用现有网络信息消息
  int64 timestamp = 4; // 毫秒级时间戳
  string device_id = 5;
}

message SpeedTestData {
  double download_speed_mbps = 1; // 下载速度（Mbps）
  double upload_speed_mbps = 2;   // 上传速度（Mbps）
  double latency_ms = 3;          // 时延（ms）
  double jitter_ms = 4;           // 抖动（ms）
  double packet_loss_percent = 5; // 丢包率（%）
}
```

**数据处理集成**：在 `SurveyRecordProcessor` 中添加对测速结果的处理，生成protobuf消息并分发给存储和MQTT模块：
```java
public class SurveyRecordProcessor {
    // 新增测速记录处理器
    public void processSpeedTestResult(SpeedTestResult result, String deviceId) {
        // 转换为protobuf消息
        SpeedTestRecord speedTestRecord = SpeedTestRecord.newBuilder()
                .setData(SpeedTestData.newBuilder()
                        .setDownloadSpeedMbps(result.getDownloadSpeed())
                        .setUploadSpeedMbps(result.getUploadSpeed())
                        .setLatencyMs(result.getLatency())
                        .setJitterMs(result.getJitter())
                        .setPacketLossPercent(result.getPacketLoss())
                        .build())
                .setLocation(convertToProtobufLocation(result.getLocation())) // 复用现有位置转换方法
                .setNetworkInfo(convertToProtobufNetworkInfo(result.getNetworkInfo()))
                .setTimestamp(result.getTimestamp())
                .setDeviceId(deviceId)
                .build();

        // 通知存储和MQTT listeners
        notifySpeedTestRecordListeners(speedTestRecord);
    }

    // 新增测速记录监听器（类似蜂窝/WiFi的监听器）
    private final Set<ISpeedTestRecordListener> speedTestRecordListeners = new CopyOnWriteArraySet<>();

    public void addSpeedTestRecordListener(ISpeedTestRecordListener listener) {
        speedTestRecordListeners.add(listener);
    }

    private void notifySpeedTestRecordListeners(SpeedTestRecord record) {
        for (ISpeedTestRecordListener listener : speedTestRecordListeners) {
            listener.onSpeedTestRecord(record);
        }
    }
}
```


#### **4. 存储与上传扩展**
- **本地存储**：扩展现有数据库（`SurveyDatabase`），新增 `SpeedTestRecordEntity` 表，用于存储历史记录：
  ```java
  @Entity(tableName = "speed_test_records")
  public class SpeedTestRecordEntity {
      @PrimaryKey(autoGenerate = true)
      public long id;
      public long timestamp;
      public double latitude;
      public double longitude;
      public String networkType; // "CELLULAR" or "WIFI"
      public String networkInfo; // 服务小区或WiFi的详细信息
      public double downloadSpeed;
      public double uploadSpeed;
      public double latency;
      public double jitter;
      public double packetLoss;
  }
  ```

- **MQTT上传**：在 `MqttConnection` 中添加测速记录的发布逻辑，新增主题 `speed_test_message`：
  ```java
  public class MqttConnection {
      private static final String MQTT_SPEED_TEST_TOPIC = "speed_test_message";

      @Override
      public void onSpeedTestRecord(SpeedTestRecord record) {
          if (effectiveDeviceName != null) {
              record = record.toBuilder().setDeviceId(effectiveDeviceName).build();
          }
          publishMessage(MQTT_SPEED_TEST_TOPIC, record);
      }
  }
  ```


#### **5. 集成到核心服务**
在 `NetworkSurveyService` 中初始化 `SpeedTestController`，并关联现有模块：
```java
public class NetworkSurveyService extends Service {
    private SpeedTestController speedTestController;

    @Override
    public void onCreate() {
        super.onCreate();
        // 初始化测速控制器
        speedTestController = new SpeedTestController(this);
        // 将测速结果处理器添加到SurveyRecordProcessor
        surveyRecordProcessor.addSpeedTestRecordListener(speedTestRecord -> {
            // 存储到本地数据库
            dbUploadStore.saveSpeedTestRecord(speedTestRecord);
            // 上传到MQTT（如果连接正常）
            if (mqttConnection != null && mqttConnection.isConnected()) {
                mqttConnection.onSpeedTestRecord(speedTestRecord);
            }
        });
    }

    public SpeedTestController getSpeedTestController() {
        return speedTestController;
    }
}
```


### **三、关键注意事项**
1. **权限适配**：确保应用具有网络访问权限（`INTERNET`），测速无需新增权限（现有网络权限已覆盖）。
2. **性能优化**：测速任务耗时较长（约10-30秒），需在后台线程执行，避免阻塞UI；同时通过 `WakeLock` 防止设备休眠（复用现有唤醒锁逻辑）。
3. **错误处理**：网络异常时需提示用户（如“测试服务器不可达”），并记录错误日志。
4. **配置灵活性**：测试服务器地址、数据大小等参数应支持通过 `SharedPreferences` 配置，方便用户自定义。


### **四、总结**
通过新增测速界面、控制器和数据模型，复用现有位置监听、网络信息收集、存储及MQTT上传机制，可快速实现网络测速功能。核心是将测速结果与位置、网络信息关联，统一纳入现有数据处理流程，确保功能扩展的同时保持架构一致性。