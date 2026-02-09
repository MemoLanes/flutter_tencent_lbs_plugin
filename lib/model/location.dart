/// 定位结果（与 SDK 回调一致）
class Location {
  /// 纬度
  double? latitude;

  /// 经度
  double? longitude;
  /// 海拔
  double? altitude;
  /// 精度
  double? accuracy;
  /// 速度
  double? speed;
  /// 时间（毫秒时间戳）
  int? time;

  /// 定位来源（原生原始值）。Android 为 getSourceProvider() 字符串；iOS 可能为空，以 [locationSource] 为准。
  String? sourceProvider;

  /// 定位来源（双端统一）。见 [LocationSource]：-1=未知，0=GPS，1=网络，2=模拟，3=外设GPS，4=外设网络。
  int? locationSource;

  /// 结果码：0=成功，非 0 为失败（如 1 网络问题、2 权限/采集失败、4 WGS84 转换失败、404 未知）
  int code = 1;

  @override
  String toString() =>
      'Location(code: $code, lat: $latitude, lon: $longitude, accuracy: $accuracy, time: $time)';
}
