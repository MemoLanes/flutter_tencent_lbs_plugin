import 'dart:async';

import 'package:flutter/services.dart';

import 'package:flutter_tencent_lbs_plugin/flutter_tencent_lbs_plugin_platform_interface.dart';
import 'package:flutter_tencent_lbs_plugin/model/android_notification_options.dart';
import 'package:flutter_tencent_lbs_plugin/model/location.dart';
import 'package:flutter_tencent_lbs_plugin/model/status.dart';
import 'package:flutter_tencent_lbs_plugin/src/pigeon/location_pigeon.dart' as pigeon;

/// 基于 Pigeon 的平台实现，参数与腾讯定位 SDK 文档对齐。
class FlutterTencentLBSPluginPigeonPlatform extends FlutterTencentLBSPluginPlatform {
  FlutterTencentLBSPluginPigeonPlatform() : super() {
    _hostApi = pigeon.TencentLBSHostApi();
    _flutterApi = _PigeonFlutterApi(this);
    pigeon.TencentLBSFlutterApi.setUp(_flutterApi);
  }

  late final pigeon.TencentLBSHostApi _hostApi;
  late final _PigeonFlutterApi _flutterApi;
  Completer<Location?>? _onceCompleter;

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
    final options = pigeon.InitOptions(
      apiKey: key,
      coordinateType: coordinateType,
      mockEnable: mockEnable,
      requestLevel: requestLevel,
      locMode: locMode,
      allowGps: isAllowGPS,
      indoorLocationMode: isIndoorLocationMode,
      gpsFirst: isGpsFirst,
      gpsFirstTimeOutMs: gpsFirstTimeOut,
    );
    return _hostApi.configure(options);
  }

  @override
  void setUserAgreePrivacy() {
    _hostApi.setUserAgreePrivacy(true);
  }

  @override
  Future<Location?> getLocationOnce() async {
    final completer = Completer<Location?>();
    _onceCompleter = completer;
    await _hostApi.requestLocationOnce();
    return completer.future.timeout(
      const Duration(seconds: 30),
      onTimeout: () {
        _onceCompleter = null;
        return null;
      },
    );
  }

  @override
  Future<void> getLocation({
    required int interval,
    AndroidNotificationOptions? androidNotificationOptions,
    bool backgroundLocation = false,
    int? requestLevel,
    int? locMode,
    bool? allowGps,
    bool? allowCache,
    bool? gpsFirst,
    int? gpsFirstTimeOutMs,
  }) async {
    final request = pigeon.ContinuousLocationRequest(
      intervalMs: interval,
      requestLevel: requestLevel,
      locMode: locMode,
      allowGps: allowGps,
      allowCache: allowCache,
      gpsFirst: gpsFirst,
      gpsFirstTimeOutMs: gpsFirstTimeOutMs,
      backgroundLocation: backgroundLocation,
    );
    pigeon.AndroidNotificationOptions? pigeonNotif;
    if (androidNotificationOptions != null) {
      pigeonNotif = pigeon.AndroidNotificationOptions(
        id: androidNotificationOptions.id,
        channelId: androidNotificationOptions.channelId,
        channelName: androidNotificationOptions.channelName,
        channelDescription: androidNotificationOptions.channelDescription,
        notificationTitle: androidNotificationOptions.notificationTitle,
        notificationText: androidNotificationOptions.notificationText,
        enableVibration: androidNotificationOptions.enableVibration,
        playSound: androidNotificationOptions.playSound,
        showWhen: androidNotificationOptions.showWhen,
        iconData: null,
      );
    }
    await _hostApi.startLocationUpdates(request, pigeonNotif);
  }

  @override
  Future<void> updateLocationRequest({
    int? intervalMs,
    int? requestLevel,
    int? locMode,
    int? gpsFirstTimeOutMs,
    bool? allowGps,
    bool? allowCache,
    bool? gpsFirst,
  }) async {
    final update = pigeon.LocationRequestUpdate(
      intervalMs: intervalMs,
      requestLevel: requestLevel,
      locMode: locMode,
      gpsFirstTimeOutMs: gpsFirstTimeOutMs,
      allowGps: allowGps,
      allowCache: allowCache,
      gpsFirst: gpsFirst,
    );
    await _hostApi.updateLocationRequest(update);
  }

  @override
  void stop() {
    _hostApi.stopLocationUpdates();
  }

  void _onLocation(pigeon.LocationData data) {
    final loc = _toLocation(data);
    if (_onceCompleter != null && !_onceCompleter!.isCompleted) {
      if ((data.code ?? 1) == 0) {
        _onceCompleter!.complete(loc);
      } else {
        _onceCompleter!.complete(null);
      }
      _onceCompleter = null;
      return;
    }
    for (final listener in state.listener) {
      listener(loc);
    }
    if ((data.code ?? 1) != 0) {
      for (final listener in state.failListener) {
        listener(loc);
      }
    }
  }

  void _onError(int code, String message) {
    final loc = Location()..code = code;
    if (_onceCompleter != null && !_onceCompleter!.isCompleted) {
      _onceCompleter!.complete(null);
      _onceCompleter = null;
      return;
    }
    for (final listener in state.failListener) {
      listener(loc);
    }
  }

  void _onStatus(pigeon.LocationStatusData? status) {
    if (status == null) return;
    LocationStatus? res;
    if (status.status != null && status.name != null) {
      res = LocationStatus(name: status.name!, status: status.status!);
    }
    for (final listener in state.statusListener) {
      listener(res);
    }
  }

  static Location _toLocation(pigeon.LocationData data) {
    final loc = Location();
    loc.code = data.code ?? 0;
    loc.latitude = data.latitude;
    loc.longitude = data.longitude;
    loc.altitude = data.altitude;
    loc.accuracy = data.accuracy;
    loc.speed = data.speed;
    loc.time = data.timeMs;
    loc.sourceProvider = data.sourceProvider;
    loc.locationSource = data.locationSource;
    return loc;
  }
}

class _PigeonFlutterApi extends pigeon.TencentLBSFlutterApi {
  _PigeonFlutterApi(this._platform);

  final FlutterTencentLBSPluginPigeonPlatform _platform;

  @override
  void onLocation(pigeon.LocationData location) {
    _platform._onLocation(location);
  }

  @override
  void onError(int code, String message) {
    _platform._onError(code, message);
  }

  @override
  void onStatus(pigeon.LocationStatusData? status) {
    _platform._onStatus(status);
  }
}
