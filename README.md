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
| Android | 7.6.1.7（Maven: `com.tencent.map.geolocation:TencentLocationSdk-openplatform`） |
| iOS     | 4.3.0（Frameworks/TencentLBS.framework） |

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
  会生成：`lib/src/pigeon/location_pigeon.dart`、Android `pigeon/LocationPigeon.kt` 与 `TencentLBSHostApiImpl.kt`、iOS `Classes/pigeon/LocationPigeon.swift`。**序列化格式为 List（按字段顺序），不可改为 Map；维护时只改 pigeons 定义并重新生成即可。**
- **原生层**：Android 实现位于 `android/.../pigeon/TencentLBSHostApiImpl.kt`；iOS 为 `ios/Classes/TencentLBSHostApiImpl.swift`、`FlutterTencentLbsPlugin.swift`，仅使用 Swift + Pigeon，无 ObjC Pigeon、无 Bridging Header。
- **已废弃**：无 MethodChannel 实现；iOS 无 ObjC Pigeon 输出；插件不再生成或依赖 `LocationCode`、Bridging Header。

---

## 参考

- [tencent_location](https://github.com/maxleexyz/tencent_location)
- [flutter_tencent_location](https://pub.dev/packages/flutter_tencent_location)
