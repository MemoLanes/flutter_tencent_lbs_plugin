# flutter_tencent_lbs_plugin

Flutter 腾讯位置服务 / 定位插件（第三方），基于腾讯定位 SDK 封装，支持单次定位、连续定位、后台定位及运行中修改定位参数。跨端通信使用 **Pigeon**，与官方 SDK 参数对齐。

---

## 注意事项

1. **iOS 模拟器**：本插件在 iOS Simulator 中不生效，需真机调试。
2. **官方文档**：各平台配置与能力以腾讯官方文档为准。  
   - [Android 定位 SDK](https://lbs.qq.com/mobile/androidLocationSDK/androidGeoGuide/androidGeoOverview)  
   - [iOS 定位 SDK](https://lbs.qq.com/mobile/iosLocationSDK/iosGeoGuide/iosGeoOverview)

---

## SDK 版本

| 平台    | 版本号   |
|---------|----------|
| Android | 7.6.1.8（Maven: `com.tencent.map.geolocation:TencentLocationSdk-openplatform`） |
| iOS     | 4.3.1（flutter_tencent_lbs_plugin/TencentLBS.xcframework） |

---

## 安装

在 `pubspec.yaml` 中：

```yaml
dependencies:
  flutter_tencent_lbs_plugin: ^0.2.0  # 或 git 依赖
```

---

## 快速开始

```dart
import 'package:flutter_tencent_lbs_plugin/flutter_tencent_lbs_plugin.dart';

final plugin = FlutterTencentLBSPlugin();

// 1. 隐私协议（调用其他接口前必须调用）
plugin.setUserAgreePrivacy();

// 2. 初始化（传入腾讯地图开放平台申请的 key）
await plugin.init(key: "YOUR_KEY");

// 3. 单次定位
final location = await plugin.getLocationOnce();
print(location);  // Location(code: 0, lat: 39.9, lon: 116.4, ...)
```

### 连续定位 + 监听 + 运行中改间隔

```dart
plugin.addLocationListener((location) {
  if (location.code == 0) {
    print('位置: $location');
  }
});
plugin.addFailListener((location) {
  print('失败: ${location.code}');
});

await plugin.getLocation(
  interval: 10000,  // 10 秒
  backgroundLocation: true,
  androidNotificationOptions: AndroidNotificationOptions(
    id: 100,
    channelId: "location",
    channelName: "定位服务",
    notificationTitle: "正在定位",
  ),
);

// 运行中改为 5 秒间隔
await plugin.updateLocationRequest(intervalMs: 5000);

// 停止
plugin.stop();
```

---

## API 说明（供 Agent / 自动化参考）

### 1. 初始化 `init()`

在调用定位前调用一次；可传入与腾讯 SDK 一致的参数。

```dart
Future<bool> init({
  required String key,                    // 腾讯开放平台 apiKey，必填
  int? coordinateType,                     // 坐标系：见 TencentLBSLocationCoordinateType
  bool mockEnable = false,                 // 是否允许 Mock 定位
  int requestLevel = TencentLBSRequestLevel.AdminName,  // 结果级别
  int locMode = TencentLBSLocMode.HIGH_ACCURACY_MODE,   // 定位模式（仅 Android）
  bool isAllowGPS = true,                 // Android：是否允许 GPS
  bool isIndoorLocationMode = false,      // Android：室内定位
  bool isGpsFirst = false,                 // Android：首次是否等卫星
  int gpsFirstTimeOut = 5000,             // Android：卫星优先超时 ms，最多 60000
}) async
```

**常用常量（`lib/model/enum.dart`）：**

- **坐标系** `TencentLBSLocationCoordinateType`：`GCJ02 = 0`（火星），`WGS84 = 1`（地球）。
- **结果级别** `TencentLBSRequestLevel`：`Geo = 0`（仅经纬度），`Name = 1`，`AdminName = 3`，`Poi = 4`。
- **定位模式** `TencentLBSLocMode`（仅 Android）：`HIGH_ACCURACY_MODE = 10`，`ONLY_NETWORK_MODE = 11`（省电），`ONLY_GPS_MODE = 12`。

### 2. 隐私协议 `setUserAgreePrivacy()`

无参，调用其他接口前必须先调用一次。

### 3. 单次定位 `getLocationOnce()`

```dart
Future<Location?> getLocationOnce() async
```

返回 `Location?`，超时或失败为 `null`；成功时 `location.code == 0`。

### 4. 连续定位 `getLocation()`

```dart
Future<void> getLocation({
  required int interval,                           // 回调间隔 ms，建议 ≥ 5000
  AndroidNotificationOptions? androidNotificationOptions,  // Android 前台通知，后台定位时建议传
  bool backgroundLocation = false,                  // 是否后台定位
  int? requestLevel,
  int? locMode,
  bool? allowGps,
  bool? allowCache,
  bool? gpsFirst,
  int? gpsFirstTimeOutMs,
}) async
```

**Android 后台定位**：需传 `androidNotificationOptions`（通知 id、channelId、channelName、notificationTitle 等），并在 AndroidManifest 中声明前台 Service。

### 5. 运行中修改参数 `updateLocationRequest()`

连续定位已开启后可调用，仅传需要修改的字段。

```dart
Future<void> updateLocationRequest({
  int? intervalMs,
  int? requestLevel,
  int? locMode,
  int? gpsFirstTimeOutMs,
  bool? allowGps,
  bool? allowCache,
  bool? gpsFirst,
}) async
```

### 6. 监听与停止

```dart
// 定位成功回调（每次收到位置都会调用）
void addLocationListener(LocationCallBack listener);   // LocationCallBack = void Function(Location location)

// 定位失败回调（如 code != 0）
void addFailListener(LocationCallBack listener);

// 状态回调（仅 Android，如 GPS/WiFi 状态变化）
void addStatusListener(LocationStatusListener listener);  // void Function(LocationStatus? status)

// 停止连续定位
void stop();
```

---

## 数据类型（导出自插件）

- **Location**：定位结果。字段：`latitude`, `longitude`, `altitude`, `horizontalAccuracy`, `verticalAccuracy`, `speed`, `bearing`, `time`, `sourceProvider`（原生原始值）, `locationSource`（双端统一）, `code`（0 为成功）。
- **LocationStatus**：状态回调数据（Android）。`name`, `status`。
- **AndroidNotificationOptions**：Android 前台通知配置。`id`, `channelId`, `channelName`, `notificationTitle`, `notificationText`, `channelDescription`, `enableVibration`, `playSound`, `showWhen`。
- **枚举类**：`TencentLBSLocationCoordinateType`, `TencentLBSRequestLevel`, `TencentLBSLocMode`, **`LocationSource`**（定位来源统一常量：`Unknown=-1`, `Gps=0`, `Network=1`, `Simulated=2`, `AccessoryGps=3`, `AccessoryNetwork=4`，iOS 与 Android SDK 返回值已映射为此统一含义）。

---

## Android 配置

### Key

通过 `plugin.init(key: "YOUR_KEY")` 传入即可。

### 权限（AndroidManifest.xml）

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE"/>
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_LOCATION_EXTRA_COMMANDS"/>
```

### 后台定位（前台 Service）

```xml
<application>
    <service
        android:name="com.tencent.map.geolocation.s"
        android:foregroundServiceType="location" />
</application>
```

---

## iOS 配置

### Key

通过 `plugin.init(key: "YOUR_KEY")` 传入。

### Info.plist 定位权限

至少其一：

- `NSLocationWhenInUseUsageDescription`
- `NSLocationAlwaysAndWhenInUseUsageDescription`
- `NSLocationAlwaysUsageDescription`

### 后台定位

- Xcode：Signing & Capabilities → + Capability → Background Modes → 勾选 **Location updates**。
- Info.plist 中 `UIBackgroundModes` 包含 `location`。

---

## 省电建议（SDK 文档）

- **定位模式**：对精度要求不高时使用 `TencentLBSLocMode.ONLY_NETWORK_MODE`（11），不启 GPS，耗电更低。
- **间隔**：`interval` / `intervalMs` 适当加大（如 10s、15s）可降低耗电。
- **allowGps**：Android 可传 `false` 减少 GPS 使用。
- **gpsFirst / gpsFirstTimeOutMs**：信号差时缩短超时或关闭“GPS 优先”，避免长时间搜星耗电。

---

## 避坑

- **地址描述**：Android 需 WGS84 才能拿到地址描述；iOS 需 GCJ02。与 SDK 默认行为一致。
- **修改插件 iOS 源文件后**：若 example 编译报“找不到已删除文件”，在 example 下执行：  
  `flutter pub get` → `cd ios` → `rm -rf Pods Podfile.lock` → `pod install`，再编译。

---

## 项目结构（供 Agent 修改/扩展时参考）

- **对外入口**：`lib/flutter_tencent_lbs_plugin.dart`，仅暴露 `FlutterTencentLBSPlugin` 与导出 model。
- **平台抽象**：`lib/flutter_tencent_lbs_plugin_platform_interface.dart`，定义接口与默认占位实现；实际实现为 Pigeon 平台。
- **Pigeon 实现**：`lib/src/flutter_tencent_lbs_plugin_pigeon_platform.dart`，实现接口并调用 Pigeon 生成 API；将 Pigeon 的 `LocationData` 转为对外 `Location`。
- **Pigeon 定义**：`pigeons/location_api.dart`。修改后需执行：  
  `dart run pigeon --input pigeons/location_api.dart`  
  会生成：`lib/src/pigeon/location_pigeon.dart`、Android `pigeon/LocationPigeon.kt` 与 `TencentLBSHostApiImpl.kt`、iOS `ios/flutter_tencent_lbs_plugin/Sources/flutter_tencent_lbs_plugin/pigeon/LocationPigeon.swift`。**序列化格式为 List（按字段顺序），不可改为 Map；维护时只改 pigeons 定义并重新生成即可。**
- **原生层**：Android 实现位于 `android/.../pigeon/TencentLBSHostApiImpl.kt`；iOS 为 `ios/flutter_tencent_lbs_plugin/Sources/flutter_tencent_lbs_plugin/TencentLBSHostApiImpl.swift`、`FlutterTencentLbsPlugin.swift`（支持 CocoaPods + SPM 双模式），仅使用 Swift + Pigeon，无 ObjC Pigeon、无 Bridging Header。
- **已废弃**：无 MethodChannel 实现；iOS 无 ObjC Pigeon 输出；插件不再生成或依赖 `LocationCode`、Bridging Header。

---

## 参考

- [tencent_location](https://github.com/maxleexyz/tencent_location)
- [flutter_tencent_location](https://pub.dev/packages/flutter_tencent_location)

## Android 独立进程录制与可靠消费

`AndroidLocationRecorder` 是独立的 Android API；旧 Pigeon 定位 API 和 iOS 行为不变。
录制服务运行在应用私有的 `:location` 进程，不创建 FlutterEngine，不绑定 UI 进程，也不向
Flutter 推送每个位置。UI 可以退出前台，其进程被系统回收时，原生服务仍可继续采集。

调用前，应用必须取得定位权限、确认用户同意隐私政策，并在 Activity 可见时启动服务。
Manifest 自动合并 `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_LOCATION`、`WAKE_LOCK`
和非导出的 `LocationRecordingService`。应用仍需声明并请求适合自身需求的定位权限。
不需要为“从可见 Activity 启动后持续录制”增加 `ACCESS_BACKGROUND_LOCATION`。

```dart
final recorder = AndroidLocationRecorder();
await recorder.startRecording(
  key: 'YOUR_KEY',
  notificationOptions: AndroidNotificationOptions(
    id: 100,
    channelId: 'recording',
    channelName: '轨迹录制',
    notificationTitle: '正在录制轨迹',
  ),
);

final batch = await recorder.readBatch(limit: 256);
for (final row in batch.locations) {
  // 在业务数据库的同一事务中保存位置与 (batch.journalId, row.sequence) 消费检查点。
  // 这里是调用方持久化逻辑，不是本插件提供的方法。
  // await persistIdempotently(batch.journalId, row);
}
if (batch.locations.isNotEmpty) {
  // 仅在上面的业务事务已经成功提交后确认。读取本身不会删除数据。
  await recorder.acknowledge(
    journalId: batch.journalId,
    sequence: batch.locations.last.sequence,
  );
}

await recorder.stopRecording();
// stop 完成后再 drain 剩余批次，最后结束/保存业务轨迹。
```

服务先将原始位置及原生回调接收时间提交到 SQLite，再供 UI 分页读取。SQLite 文件锁和事务
协调两个进程的读写；队列 ID 是持久 UUID，序号使用 `AUTOINCREMENT`，删除已确认前缀后也不复用。
`receivedTimestampMs` 是原生收到 SDK 回调的时间，消费积压时不得替换为消费时间。
调用方应以 `(journalId, sequence)` 实现幂等导入：业务提交成功但原生 ACK 前崩溃，会再次读到相同点。
服务不会替调用方决定轨迹归属，也不会自动删除未确认位置或限制未确认队列大小。

`getSnapshot()` 在一次原生读取中返回 `journalId`、录制 `state` 和 `latestLocation`。
最新位置与队列 ACK 独立：已确认的点仍可作为最新位置供 UI 展示，调用方应按其采样时间判断新鲜度，
不要将该快照再送入轨迹导入。状态和当前位置的轮询不应等待历史队列消费或业务数据库写入成功。
需要消费一个有限快照时，先保存 `snapshot.latestLocation?.sequence`，再给每次 `readBatch`
传相同的 `throughSequence`，并核对批次的 `journalId` 与快照一致；之后采集的新点留给下一轮消费。
没有最新位置时无需消费队列。schema v1 升级会从尚未确认的最后一条队列记录恢复最新位置；
若旧队列已清空，则等下一次原生定位后才有最新位置。

`startRecording` 在 SDK 成功注册 listener 后完成，SDK 启动返回码、前台服务/权限异常会使 Future 失败。
相同配置的重复启动保持正在运行的 listener；配置变更会重新注册。`stopRecording` 先注销 listener、
拒绝迟到回调，再等所有先前已接受位置落盘及停止状态提交后回复。SDK 注销或写盘错误不会返回成功。
UI 进程不应自行删除该数据库，也不应使用旧 Pigeon 定位回调再次保存服务已录制的位置。

`getState()` 的 `initialized` 可用于旧 Flutter/MMKV 录制状态的一次迁移；`isRecording` 表示持久录制意图。
系统重建服务时使用 `START_STICKY` 恢复该意图和配置。它不代表设备重启后自动启动：应用恢复可见时，
若持久意图仍为录制中，应幂等调用 `startRecording`。不注册开机自启，也不绕过用户强制停止。
从最近任务划掉应用时，服务通过 `onTaskRemoved` 清除录制意图并停止。Manifest 使用
`stopWithTask=false` 是为了让该清理回调执行，保持原有“划掉即停止”的产品语义。

写盘失败时服务停止 SDK、记录 `lastError`，并发布独立错误通知；同时尝试保存独立错误标记，防止
SQLite 更新失败后仍把旧 active 位显示为正在录制。通知能否展示取决于用户的通知授权；存储设备整体不可写
时，错误标记也可能失败，此时系统日志和通知仍是独立错误渠道。应用恢复时应显示/记录 `lastError`。
OEM 电池管理、系统/用户停止仍可能终止服务；独立进程提高隔离性，不构成不间断运行保证。

Android 本地验证（需 Flutter SDK、Android SDK、网络依赖缓存）：

```sh
flutter pub get
flutter test test/android_location_recorder_test.dart
cd example/android
../../android/gradlew :flutter_tencent_lbs_plugin:compileDebugKotlin :flutter_tencent_lbs_plugin:testDebugUnitTest
```

Kotlin 测试使用 Robolectric 验证 SQLite 重开恢复、ACK 前缀删除、journal 身份、原生点校验和服务停止顺序。
长期锁屏、真实 SDK 采集、系统回收/重建和具体 OEM 电池策略仍需真机验证。
