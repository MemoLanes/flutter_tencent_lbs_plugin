// ignore_for_file: constant_identifier_names

class LocationCode {
  /// 定位成功
  static const int ERROR_OK = 0;

  /// 网络问题引起的定位失败
  static const int ERROR_NETWORK = 1;

  /// GPS, Wi-Fi 或基站错误引起的定位失败：
  /// 1、用户的手机确实采集不到定位凭据，比如偏远地区比如地下车库电梯内等;
  /// 2、开关跟权限问题，比如用户关闭了位置信息，关闭了Wi-Fi，未授予app定位权限等。
  static const int ERROR_BAD_JSON = 2;

  /// 无法将WGS84坐标转换成GCJ-02坐标时的定位失败
  static const int ERROR_WGS84 = 4;

  /// 未知原因引起的定位失败
  static const int ERROR_UNKNOWN = 404;
}

class Location {
  /// 纬度
  double? latitude;

  ///经度
  double? longitude;
  ///海拔
  double? altitude;
  ///精度
  double? accuracy;
  ///速度
  double? speed;
  ///时间
  int? time;
  ///来源
  String? sourceProvider;

  int code = 1;

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{};
    map['latitude'] = latitude;
    map['longitude'] = longitude;
    map['accuracy'] = accuracy;
    map['altitude'] = altitude;
    map['speed'] = speed;
    map['time'] = time;
    map['sourceProvider'] = sourceProvider;

    return map;
  }
}
