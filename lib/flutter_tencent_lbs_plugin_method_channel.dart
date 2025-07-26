import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'flutter_tencent_lbs_plugin_platform_interface.dart';
import 'model/android_notification_options.dart';
import 'model/location.dart';
import 'model/status.dart';

class MethodChannelFlutterTencentLBSPlugin
    extends FlutterTencentLBSPluginPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('flutter_tencent_lbs_plugin');

  @override
  Future<bool> init({
    required String key,
    int? coordinateType,
    bool? mockEnable,
    int? requestLevel,
    int? locMode,
    bool? isAllowGPS,
    bool? isIndoorLocationMode,
    bool? isGpsFirst,
    int? gpsFirstTimeOut,
  }) async {
    methodChannel.setMethodCallHandler((methodCall) async {
      switch (methodCall.method) {
        case "receiveLocation":
          {
            Location location = Location();
            location.code = methodCall.arguments['code'];
            if (location.code == LocationCode.ERROR_OK) {
              location.name = methodCall.arguments['name'];
              location.latitude = methodCall.arguments['latitude'];
              location.longitude = methodCall.arguments['longitude'];
              location.altitude = methodCall.arguments['altitude'];
              location.accuracy = methodCall.arguments['accuracy'];
              location.speed = methodCall.arguments['speed'];
              location.bearing = methodCall.arguments['bearing'];
              location.time = methodCall.arguments['time'];
              location.sourceProvider = methodCall.arguments['sourceProvider'];
              location.fakeReason = methodCall.arguments['fakeReason'];
              location.fakeProbability = methodCall.arguments['fakeProbability'];
              location.nationCode = methodCall.arguments['nationCode'];
              location.province = methodCall.arguments['province'];
              location.city = methodCall.arguments['city'];
              location.area = methodCall.arguments['district'];
              location.street = methodCall.arguments['street'];
              location.streetNo = methodCall.arguments['streetNo'];
              location.town = methodCall.arguments['town'];
              location.village = methodCall.arguments['village'];
              location.cityCode = methodCall.arguments['cityCode'];
              for (var listener in state.listener) {
                listener(location);
              }
            } else {
              for (var listener in state.failListener) {
                listener(location);
              }
            }
            break;
          }
        case "receiveStatus":
          {
            final name = methodCall.arguments['name'];
            final status = methodCall.arguments['status'];
            LocationStatus? res;
            if (status is int && name is String) {
              res = LocationStatus(name: name, status: status);
            }
            for (var listener in state.statusListener) {
              listener(res);
            }
            break;
          }
      }
    });
    return await methodChannel.invokeMethod("init", {
      "key": key,
      "coordinateType": coordinateType,
      "mockEnable": mockEnable,
      "requestLevel": requestLevel,
      "locMode": locMode,
      "isAllowGPS": isAllowGPS,
      "isIndoorLocationMode": isIndoorLocationMode,
      "isGpsFirst": isGpsFirst,
      "gpsFirstTimeOut": gpsFirstTimeOut,
    });
  }

  @override
  void setUserAgreePrivacy() {
    methodChannel.invokeMethod('setUserAgreePrivacy');
  }

  @override
  Future<Location?> getLocationOnce() async {
    var data = await methodChannel.invokeMethod("getLocationOnce");
    if (data is! Map) {
      return null;
    }
    Location location = Location();
    location.name = data['name'];
    location.latitude = data['latitude'];
    location.longitude = data['longitude'];
    location.address = data['address'];
    location.city = data['city'];
    location.province = data['province'];
    location.area = data['area'];
    location.cityCode = data['cityCode'];
    return location;
  }

  @override
  Future<void> getLocation({
    required int interval,
    AndroidNotificationOptions? androidNotificationOptions,
    bool backgroundLocation = false,
  }) async {
    await methodChannel.invokeMethod("getLocation", {
      "interval": interval.toDouble(),
      "backgroundLocation": backgroundLocation,
      "androidNotificationOptions": androidNotificationOptions?.toJson()
    });
  }

  @override
  void stop() {
    methodChannel.invokeMethod("stopLocation");
  }
}
