// 与腾讯定位 SDK 文档保持一致，避免与原生常量混淆
// Android: https://mapapi.qq.com/sdk/locationSDK/Android/doc/com/tencent/map/geolocation/package-summary.html
// iOS: TencentLBSLocationManager.h

/// 坐标系类型（双端统一：0=GCJ02 火星坐标，1=WGS84 地球坐标）
/// 原生层会映射到各自 SDK 常量：
/// Android: COORDINATE_TYPE_GCJ02=1, COORDINATE_TYPE_WGS84=0
/// iOS: TencentLBSLocationCoordinateTypeGCJ02=0, TencentLBSLocationCoordinateTypeWGS84=1
class TencentLBSLocationCoordinateType {
  /// 火星坐标（国测局坐标）
  static const int GCJ02 = 0;

  /// 地球坐标（GPS 坐标）；海外无论设置哪种均返回地球坐标
  static const int WGS84 = 1;
}

/// 定位结果信息级别（与 TencentLocationRequest.requestLevel / iOS requestLevel 一致）
class TencentLBSRequestLevel {
  /// 0 号接口：仅经纬度
  static const int Geo = 0;

  /// 1 号接口：经纬度 + 位置名称 + 地址
  static const int Name = 1;

  /// 3 号接口：经纬度 + 行政区划 + 地址 + 名称
  static const int AdminName = 3;

  /// 4 号接口：经纬度 + 行政区划 + 周边 POI（无 name/address）
  static const int Poi = 4;
}

/// 定位模式（与 Android TencentLocationRequest 一致：HIGH_ACCURACY_MODE=10 等）
/// iOS 无直接对应，传参在 iOS 端忽略
class TencentLBSLocMode {
  /// 高精度：网络 + 卫星，优先高精度
  static const int HIGH_ACCURACY_MODE = 10;

  /// 仅网络：不启 GPS，省电但精度较低
  static const int ONLY_NETWORK_MODE = 11;

  /// 仅 GPS：室外精度约 3–10 米，无 GPS 时超时后回退网络（默认 8s）
  static const int ONLY_GPS_MODE = 12;
}
