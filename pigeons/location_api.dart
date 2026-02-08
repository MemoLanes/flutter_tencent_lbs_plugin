import 'package:pigeon/pigeon.dart';

// 生成命令: flutter pub run pigeon --input pigeons/location_api.dart
// 参数与腾讯定位 SDK 文档对齐:
// Android: https://mapapi.qq.com/sdk/locationSDK/Android/doc/com/tencent/map/geolocation/package-summary.html
// iOS: https://mapapi.qq.com/sdk/locationSDK/iOS/doc/appledoc/html/index.html

@ConfigurePigeon(PigeonOptions(
  dartOut: 'lib/src/pigeon/location_pigeon.dart',
  kotlinOut: 'android/src/main/kotlin/com/flutter_tencent_lbs_plugin/pigeon/LocationPigeon.kt',
  kotlinOptions: KotlinOptions(
    package: 'com.flutter_tencent_lbs_plugin.pigeon',
  ),
  objcHeaderOut: 'ios/Classes/pigeon/LocationPigeon.h',
  objcSourceOut: 'ios/Classes/pigeon/LocationPigeon.m',
  objcOptions: ObjcOptions(prefix: 'FLT'),
))

// ========== 与 Android TencentLocationRequest / iOS TencentLBSLocationManager 对齐 ==========
// 坐标系: 0=GCJ02, 1=WGS84（原生层按各自 SDK 常量映射）
// requestLevel: 0=GEO, 1=NAME, 3=ADMIN_AREA, 4=POI
// locMode(Android): 10=HIGH_ACCURACY, 11=ONLY_NETWORK, 12=ONLY_GPS

/// 初始化参数（对应 init 时设置，与 SDK 文档一致）
class InitOptions {
  String apiKey;
  /// 0=GCJ02, 1=WGS84
  int? coordinateType;
  bool? mockEnable;
  /// 0/1/3/4 对应 GEO/NAME/ADMIN_AREA/POI
  int? requestLevel;
  /// Android: 10/11/12；iOS 忽略
  int? locMode;
  bool? allowGps;
  bool? indoorLocationMode;
  /// 首次是否等待卫星定位结果
  bool? gpsFirst;
  /// 卫星定位优先时的超时时间 ms，最多 60000
  int? gpsFirstTimeOutMs;
  InitOptions({
    required this.apiKey,
    this.coordinateType,
    this.mockEnable,
    this.requestLevel,
    this.locMode,
    this.allowGps,
    this.indoorLocationMode,
    this.gpsFirst,
    this.gpsFirstTimeOutMs,
  });
}

/// 连续定位请求参数（与 TencentLocationRequest 对应）
class ContinuousLocationRequest {
  /// 定位周期/回调周期，单位 ms。>0 时按周期回调；0 仅在有新结果时回调。建议 5000–10000
  int intervalMs;
  /// 与 InitOptions 一致，不传则用 init 时的值
  int? requestLevel;
  int? locMode;
  bool? allowGps;
  bool? allowCache;
  bool? gpsFirst;
  int? gpsFirstTimeOutMs;
  /// 是否后台定位（Android 前台通知等）
  bool? backgroundLocation;
  ContinuousLocationRequest({
    required this.intervalMs,
    this.requestLevel,
    this.locMode,
    this.allowGps,
    this.allowCache,
    this.gpsFirst,
    this.gpsFirstTimeOutMs,
    this.backgroundLocation,
  });
}

/// 连续定位过程中可更新的参数（仅传需要修改的字段）
/// Android: 间隔可通过 changeCallbackInterval 修改；其它需重新 requestLocationUpdates
/// iOS: 可动态改 locationCallbackInterval、requestLevel 等属性
class LocationRequestUpdate {
  int? intervalMs;
  int? requestLevel;
  int? locMode;
  int? gpsFirstTimeOutMs;
  bool? allowGps;
  bool? allowCache;
  bool? gpsFirst;
  LocationRequestUpdate({
    this.intervalMs,
    this.requestLevel,
    this.locMode,
    this.gpsFirstTimeOutMs,
    this.allowGps,
    this.allowCache,
    this.gpsFirst,
  });
}

/// Android 前台定位通知配置
class AndroidNotificationOptions {
  int id;
  String channelId;
  String channelName;
  String? channelDescription;
  String notificationTitle;
  String? notificationText;
  bool? enableVibration;
  bool? playSound;
  bool? showWhen;
  NotificationIconData? iconData;
  AndroidNotificationOptions({
    required this.id,
    required this.channelId,
    required this.channelName,
    this.channelDescription,
    required this.notificationTitle,
    this.notificationText,
    this.enableVibration,
    this.playSound,
    this.showWhen,
    this.iconData,
  });
}

class NotificationIconData {
  String? bitmapPath;
  int? resourceId;
  NotificationIconData({this.bitmapPath, this.resourceId});
}

/// 定位结果（与 TencentLocation / TencentLBSLocation 对应）
class LocationData {
  int? code;  // 0=成功
  double? latitude;
  double? longitude;
  double? altitude;
  double? accuracy;
  double? speed;
  double? bearing;
  String? address;
  String? name;
  String? timeIso;
  int? timeMs;
  String? sourceProvider;
  LocationData({
    this.code,
    this.latitude,
    this.longitude,
    this.altitude,
    this.accuracy,
    this.speed,
    this.bearing,
    this.address,
    this.name,
    this.timeIso,
    this.timeMs,
    this.sourceProvider,
  });
}

/// 状态回调（仅 Android onStatusUpdate）
class LocationStatusData {
  String? name;
  int? status;
  LocationStatusData({this.name, this.status});
}

@HostApi()
abstract class TencentLBSHostApi {
  bool init(InitOptions options);
  void setUserAgreePrivacy(bool agree);
  void requestLocationOnce();
  void startLocationUpdates(ContinuousLocationRequest request, AndroidNotificationOptions? androidNotificationOptions);
  /// 连续定位开启后可调用，更新部分参数（如超时时间、定位间隔等）
  void updateLocationRequest(LocationRequestUpdate? update);
  void stopLocationUpdates();
}

@FlutterApi()
abstract class TencentLBSFlutterApi {
  void onLocation(LocationData location);
  void onError(int code, String message);
  void onStatus(LocationStatusData? status);
}
