import 'package:flutter/services.dart';

import 'model/android_notification_options.dart';
import 'model/location.dart';

/// Android recording that continues without a Flutter engine in the UI process.
///
/// Start while the app is visible, after location permission and privacy consent.
/// The native service persists points before exposing them through [readBatch].
/// Consume each batch durably before calling [acknowledge]. Reads do not remove
/// points; a consumer must tolerate receiving the same sequence more than once.
class AndroidLocationRecorder {
  const AndroidLocationRecorder();

  static const _channel = MethodChannel(
    'com.memolanes/tencent_location_recorder',
  );

  /// Completes after the native service has started the SDK, or throws on error.
  Future<void> startRecording({
    required String key,
    required AndroidNotificationOptions notificationOptions,
    int intervalMs = 1000,
    int coordinateType = 1,
    bool mockEnable = false,
    int requestLevel = 0,
    int locMode = 10,
    bool allowGps = true,
    bool allowCache = false,
    bool gpsFirst = false,
    int gpsFirstTimeOutMs = 5000,
  }) async {
    if (key.trim().isEmpty) {
      throw ArgumentError.value(key, 'key', 'Must not be empty');
    }
    if (notificationOptions.id <= 0 || notificationOptions.id > 0x7fffffff) {
      throw RangeError.range(
        notificationOptions.id,
        1,
        0x7fffffff,
        'notificationOptions.id',
      );
    }
    if (notificationOptions.channelId.trim().isEmpty ||
        notificationOptions.channelName.trim().isEmpty ||
        notificationOptions.notificationTitle.trim().isEmpty) {
      throw ArgumentError('Notification channel and title must not be empty');
    }
    if (intervalMs < 1000) {
      throw RangeError.value(intervalMs, 'intervalMs', 'Must be at least 1000');
    }
    if (coordinateType != 0 && coordinateType != 1) {
      throw ArgumentError.value(
        coordinateType,
        'coordinateType',
        'Must be 0 or 1',
      );
    }
    if (![0, 1, 3, 4].contains(requestLevel)) {
      throw ArgumentError.value(
        requestLevel,
        'requestLevel',
        'Unsupported level',
      );
    }
    if (![10, 11, 12].contains(locMode)) {
      throw ArgumentError.value(locMode, 'locMode', 'Unsupported mode');
    }
    RangeError.checkValueInInterval(
      gpsFirstTimeOutMs,
      0,
      60000,
      'gpsFirstTimeOutMs',
    );

    await _channel.invokeMethod<void>('startRecording', <String, Object?>{
      'key': key,
      'notificationOptions': notificationOptions.toJson(),
      'intervalMs': intervalMs,
      'coordinateType': coordinateType,
      'mockEnable': mockEnable,
      'requestLevel': requestLevel,
      'locMode': locMode,
      'allowGps': allowGps,
      'allowCache': allowCache,
      'gpsFirst': gpsFirst,
      'gpsFirstTimeOutMs': gpsFirstTimeOutMs,
    });
  }

  /// Completes after SDK callbacks stop and all accepted points are on disk.
  /// Pending points remain available to [readBatch].
  Future<void> stopRecording() => _channel.invokeMethod<void>('stopRecording');

  Future<AndroidRecordingState> getState() async {
    final result = await _channel.invokeMapMethod<String, Object?>('getState');
    if (result == null) throw StateError('Native recording state is missing');
    return AndroidRecordingState._fromMap(result);
  }

  /// Reads native state and the latest persisted point without importing data.
  ///
  /// The latest point survives journal acknowledgements. Use it for live UI
  /// display, with the sample's timestamp to determine freshness; it must not
  /// be fed into the recording importer a second time.
  Future<AndroidRecordingSnapshot> getSnapshot() async {
    final result = await _channel.invokeMapMethod<String, Object?>(
      'getSnapshot',
    );
    if (result == null) {
      throw StateError('Native recording snapshot is missing');
    }
    final latest = result['latestLocation'] as Map?;
    return AndroidRecordingSnapshot(
      journalId: result['journalId'] as String,
      state: AndroidRecordingState._fromMap(
        (result['state'] as Map).cast<String, Object?>(),
      ),
      latestLocation: latest == null
          ? null
          : RecordedLocation._fromMap(latest.cast<String, Object?>()),
    );
  }

  /// Reads the oldest unacknowledged points in sequence order.
  ///
  /// [throughSequence] fixes an inclusive upper boundary, so consuming a
  /// snapshot does not wait for points collected after that snapshot. Check
  /// that the batch and snapshot have the same journal ID before importing.
  Future<RecordedLocationBatch> readBatch({
    int limit = 256,
    int? throughSequence,
  }) async {
    RangeError.checkValueInInterval(limit, 1, 256, 'limit');
    if (throughSequence != null && throughSequence <= 0) {
      throw RangeError.value(
        throughSequence,
        'throughSequence',
        'Must be positive',
      );
    }
    final result = await _channel
        .invokeMapMethod<String, Object?>('readBatch', <String, Object?>{
          'limit': limit,
          if (throughSequence != null) 'throughSequence': throughSequence,
        });
    if (result == null) throw StateError('Native recording batch is missing');
    final rows = result['locations'] as List<Object?>;
    return RecordedLocationBatch(
      journalId: result['journalId'] as String,
      locations: List<RecordedLocation>.unmodifiable(
        rows.map(
          (row) =>
              RecordedLocation._fromMap((row as Map).cast<String, Object?>()),
        ),
      ),
    );
  }

  /// Removes points through [sequence] only from the matching native journal.
  ///
  /// Call after the consumer's durable transaction commits. A journal mismatch
  /// fails instead of deleting points from a newly created journal.
  Future<void> acknowledge({
    required String journalId,
    required int sequence,
  }) async {
    if (journalId.isEmpty) {
      throw ArgumentError.value(journalId, 'journalId', 'Must not be empty');
    }
    if (sequence <= 0) {
      throw RangeError.value(sequence, 'sequence', 'Must be positive');
    }
    await _channel.invokeMethod<void>('acknowledge', <String, Object?>{
      'journalId': journalId,
      'sequence': sequence,
    });
  }
}

class AndroidRecordingState {
  const AndroidRecordingState({
    required this.initialized,
    required this.isRecording,
    this.lastError,
  });

  factory AndroidRecordingState._fromMap(Map<String, Object?> values) {
    return AndroidRecordingState(
      initialized: values['initialized'] as bool,
      isRecording: values['isRecording'] as bool,
      lastError: values['lastError'] as String?,
    );
  }

  /// Whether native recording state has ever been explicitly initialized.
  final bool initialized;
  final bool isRecording;
  final String? lastError;
}

/// One native read of the recording state and latest persisted location.
class AndroidRecordingSnapshot {
  const AndroidRecordingSnapshot({
    required this.journalId,
    required this.state,
    required this.latestLocation,
  });

  final String journalId;
  final AndroidRecordingState state;

  /// Null until a native point is available. Acknowledging the journal does
  /// not clear this point; callers must apply their own UI freshness rule.
  final RecordedLocation? latestLocation;
}

class RecordedLocationBatch {
  const RecordedLocationBatch({
    required this.journalId,
    required this.locations,
  });

  /// Stable across service/process restarts, changes if the journal is replaced.
  final String journalId;
  final List<RecordedLocation> locations;
}

class RecordedLocation {
  const RecordedLocation({
    required this.sequence,
    required this.receivedTimestampMs,
    required this.location,
  });

  factory RecordedLocation._fromMap(Map<String, Object?> row) {
    final values = (row['location'] as Map).cast<String, Object?>();
    final location = Location()
      ..code = values['code'] as int
      ..latitude = (values['latitude'] as num).toDouble()
      ..longitude = (values['longitude'] as num).toDouble()
      ..horizontalAccuracy = (values['horizontalAccuracy'] as num).toDouble()
      ..altitude = (values['altitude'] as num?)?.toDouble()
      ..speed = (values['speed'] as num?)?.toDouble()
      ..bearing = (values['bearing'] as num?)?.toDouble()
      ..time = values['time'] as int
      ..sourceProvider = values['sourceProvider'] as String?
      ..locationSource = values['locationSource'] as int?;
    return RecordedLocation(
      sequence: row['sequence'] as int,
      receivedTimestampMs: row['receivedTimestampMs'] as int,
      location: location,
    );
  }

  final int sequence;

  /// Native callback receipt time, preserved when the UI consumes a backlog.
  final int receivedTimestampMs;
  final Location location;
}
