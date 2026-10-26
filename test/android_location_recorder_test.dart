import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_tencent_lbs_plugin/flutter_tencent_lbs_plugin.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('com.memolanes/tencent_location_recorder');
  const recorder = AndroidLocationRecorder();
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  final notification = AndroidNotificationOptions(
    id: 31,
    channelId: 'location',
    channelName: 'Location',
    notificationTitle: 'Recording',
  );

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  test(
    'start waits for native completion and sends explicit SDK options',
    () async {
      final completion = Completer<void>();
      MethodCall? call;
      messenger.setMockMethodCallHandler(channel, (value) async {
        call = value;
        await completion.future;
        return null;
      });
      var completed = false;
      final result = recorder.startRecording(
        key: 'key',
        notificationOptions: notification,
      )..then((_) => completed = true);
      await Future<void>.delayed(Duration.zero);
      expect(completed, isFalse);
      expect(call!.method, 'startRecording');
      expect(call!.arguments, {
        'key': 'key',
        'notificationOptions': notification.toJson(),
        'intervalMs': 1000,
        'coordinateType': 1,
        'mockEnable': false,
        'requestLevel': 0,
        'locMode': 10,
        'allowGps': true,
        'allowCache': false,
        'gpsFirst': false,
        'gpsFirstTimeOutMs': 5000,
      });
      completion.complete();
      await result;
      expect(completed, isTrue);
    },
  );

  test('stop waits for native flush acknowledgement', () async {
    final completion = Completer<void>();
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'stopRecording');
      await completion.future;
      return null;
    });
    var completed = false;
    final result = recorder.stopRecording()..then((_) => completed = true);
    await Future<void>.delayed(Duration.zero);
    expect(completed, isFalse);
    completion.complete();
    await result;
    expect(completed, isTrue);
  });

  test('native start and stop errors propagate', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(code: 'recording-failed', message: call.method);
    });
    await expectLater(
      recorder.startRecording(key: 'key', notificationOptions: notification),
      throwsA(
        isA<PlatformException>().having(
          (e) => e.code,
          'code',
          'recording-failed',
        ),
      ),
    );
    await expectLater(
      recorder.stopRecording(),
      throwsA(isA<PlatformException>()),
    );
  });

  test('state includes migration marker and persistent error', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getState');
      return {
        'initialized': true,
        'isRecording': false,
        'lastError': 'disk full',
      };
    });
    final state = await recorder.getState();
    expect(state.initialized, isTrue);
    expect(state.isRecording, isFalse);
    expect(state.lastError, 'disk full');
  });

  test('snapshot decodes native state and latest location together', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getSnapshot');
      expect(call.arguments, isNull);
      return {
        'journalId': 'journal-1',
        'state': {'initialized': true, 'isRecording': true, 'lastError': null},
        'latestLocation': {
          'sequence': 42,
          'receivedTimestampMs': 1700000001234,
          'location': {
            'code': 0,
            'latitude': 22,
            'longitude': 114.2,
            'horizontalAccuracy': 4.5,
            'altitude': null,
            'speed': 1,
            'bearing': 180.5,
            'time': 1700000000000,
            'sourceProvider': 'gps',
            'locationSource': 0,
          },
        },
      };
    });

    final snapshot = await recorder.getSnapshot();
    expect(snapshot.journalId, 'journal-1');
    expect(snapshot.state.initialized, isTrue);
    expect(snapshot.state.isRecording, isTrue);
    expect(snapshot.state.lastError, isNull);
    final point = snapshot.latestLocation!;
    expect(point.sequence, 42);
    expect(point.receivedTimestampMs, 1700000001234);
    expect(point.location.latitude, 22.0);
    expect(point.location.longitude, 114.2);
    expect(point.location.horizontalAccuracy, 4.5);
    expect(point.location.altitude, isNull);
    expect(point.location.speed, 1.0);
    expect(point.location.time, 1700000000000);
  });

  test('snapshot supports native state with no location yet', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getSnapshot');
      return {
        'journalId': 'journal-2',
        'state': {
          'initialized': true,
          'isRecording': false,
          'lastError': 'disk full',
        },
        'latestLocation': null,
      };
    });

    final snapshot = await recorder.getSnapshot();
    expect(snapshot.journalId, 'journal-2');
    expect(snapshot.state.initialized, isTrue);
    expect(snapshot.state.isRecording, isFalse);
    expect(snapshot.state.lastError, 'disk full');
    expect(snapshot.latestLocation, isNull);
  });

  test('bounded batch sends the captured inclusive sequence', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'readBatch');
      expect(call.arguments, {'limit': 17, 'throughSequence': 42});
      return {'journalId': 'journal-1', 'locations': <Object?>[]};
    });

    final batch = await recorder.readBatch(limit: 17, throughSequence: 42);
    expect(batch.journalId, 'journal-1');
    expect(batch.locations, isEmpty);
  });

  test(
    'batch preserves journal, sequence and native receipt timestamp',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'readBatch');
        expect(call.arguments, {'limit': 17});
        return {
          'journalId': 'journal-1',
          'locations': [
            {
              'sequence': 42,
              'receivedTimestampMs': 1700000001234,
              'location': {
                'code': 0,
                'latitude': 22,
                'longitude': 114.2,
                'horizontalAccuracy': 4.5,
                'altitude': null,
                'speed': 1,
                'bearing': 180.5,
                'time': 1700000000000,
                'sourceProvider': 'gps',
                'locationSource': 0,
              },
            },
          ],
        };
      });
      final batch = await recorder.readBatch(limit: 17);
      final point = batch.locations.single;
      expect(batch.journalId, 'journal-1');
      expect(point.sequence, 42);
      expect(point.receivedTimestampMs, 1700000001234);
      expect(point.location.latitude, 22.0);
      expect(point.location.longitude, 114.2);
      expect(point.location.horizontalAccuracy, 4.5);
      expect(point.location.altitude, isNull);
      expect(point.location.speed, 1.0);
      expect(point.location.bearing, 180.5);
      expect(point.location.time, 1700000000000);
      expect(point.location.sourceProvider, 'gps');
      expect(point.location.locationSource, 0);
      expect(() => batch.locations.clear(), throwsUnsupportedError);
    },
  );

  test(
    'acknowledgement passes journal identity and committed sequence',
    () async {
      messenger.setMockMethodCallHandler(channel, (call) async {
        expect(call.method, 'acknowledge');
        expect(call.arguments, {'journalId': 'journal-1', 'sequence': 42});
        return null;
      });
      await recorder.acknowledge(journalId: 'journal-1', sequence: 42);
    },
  );

  test('invalid parameters never reach native code', () async {
    var calls = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls++;
      return null;
    });
    for (final limit in [-1, 0, 257]) {
      await expectLater(recorder.readBatch(limit: limit), throwsRangeError);
    }
    for (final sequence in [-1, 0]) {
      await expectLater(
        recorder.readBatch(throughSequence: sequence),
        throwsRangeError,
      );
    }
    await expectLater(
      recorder.acknowledge(journalId: '', sequence: 1),
      throwsArgumentError,
    );
    await expectLater(
      recorder.acknowledge(journalId: 'journal', sequence: 0),
      throwsRangeError,
    );
    await expectLater(
      recorder.startRecording(key: '', notificationOptions: notification),
      throwsArgumentError,
    );
    await expectLater(
      recorder.startRecording(
        key: 'key',
        notificationOptions: notification,
        intervalMs: 999,
      ),
      throwsRangeError,
    );
    await expectLater(
      recorder.startRecording(
        key: 'key',
        notificationOptions: notification,
        coordinateType: 2,
      ),
      throwsArgumentError,
    );
    await expectLater(
      recorder.startRecording(
        key: 'key',
        notificationOptions: notification,
        gpsFirstTimeOutMs: 60001,
      ),
      throwsRangeError,
    );
    expect(calls, 0);
  });
}
