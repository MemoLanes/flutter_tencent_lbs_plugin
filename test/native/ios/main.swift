import CoreLocation
import TencentLBS

// Exercise the real Pigeon host against Tencent's SDK types. The spy prevents
// location requests and records the SDK listener lifecycle.
final class RecordingLocationManager: TencentLBSLocationManager {
    var starts = 0
    var stops = 0
    private var background = false

    override var allowsBackgroundLocationUpdates: Bool {
        get { background }
        set { background = newValue }
    }

    override func startUpdatingLocation() { starts += 1 }
    override func stopUpdatingLocation() { stops += 1 }
}

var checks = 0
func expect(_ condition: @autoclosure () -> Bool, _ message: String) {
    precondition(condition(), message)
    checks += 1
}

func expectError(_ message: String, _ operation: () throws -> Void) {
    do {
        try operation()
        preconditionFailure(message)
    } catch is PigeonError {
        checks += 1
    } catch {
        preconditionFailure("Unexpected error: \(error)")
    }
}

TencentLBSLocationManager.setUserAgreePrivacy(true)
let manager = RecordingLocationManager()
var creations = 0
let host = TencentLBSHostApiImpl(makeLocationManager: {
    creations += 1
    return manager
})
let configured = try host.configure(options: InitOptions(apiKey: "native-settings-test", requestLevel: 0, locMode: 10))
expect(configured, "Configure the SDK before starting")
try host.startLocationUpdates(
    request: ContinuousLocationRequest(
        intervalMs: 1000,
        backgroundLocation: true,
        iosDesiredAccuracy: kCLLocationAccuracyBest,
        iosDistanceFilter: kCLDistanceFilterNone
    ),
    androidNotificationOptions: nil
)
expect(manager.locationCallbackInterval == 1000, "Start must configure the SDK interval, not a plugin throttle")
expect(manager.desiredAccuracy == kCLLocationAccuracyBest, "Start must request best accuracy")
expect(manager.distanceFilter == kCLDistanceFilterNone, "Keep distance filtering disabled")
expect(!manager.pausesLocationUpdatesAutomatically, "Keep automatic pausing disabled")
expect(manager.allowsBackgroundLocationUpdates, "Keep the background location request")
expect(manager.starts == 1 && manager.stops == 0, "Start the SDK once")

let saving = LocationRequestUpdate(
    intervalMs: 60000,
    iosDesiredAccuracy: kCLLocationAccuracyKilometer,
    iosDistanceFilter: kCLDistanceFilterNone
)
try host.updateLocationRequest(update: saving)
expect(manager.locationCallbackInterval == 60000, "Saving must directly set the SDK to 60 seconds")
expect(manager.desiredAccuracy == kCLLocationAccuracyKilometer, "Saving must request kilometer accuracy")
expect(manager.requestLevel == .geo, "Changing power settings must preserve the requested information level")
expect(manager.allowsBackgroundLocationUpdates && !manager.pausesLocationUpdatesAutomatically,
       "Changing power settings must preserve background and pause settings")
expect(manager.starts == 1 && manager.stops == 0 && creations == 1,
       "Changing power settings must not restart or replace the SDK listener")

try host.updateLocationRequest(update: saving)
expect(manager.starts == 1 && manager.stops == 0, "Reapplying settings must not restart the listener")
try host.updateLocationRequest(update: LocationRequestUpdate(iosDistanceFilter: 10))
expect(manager.locationCallbackInterval == 60000 && manager.desiredAccuracy == kCLLocationAccuracyKilometer,
       "A partial update must preserve the SDK interval and accuracy")
expect(manager.distanceFilter == 10, "A partial distance update must reach the SDK")

expectError("Invalid intervals must be rejected") {
    try host.updateLocationRequest(update: LocationRequestUpdate(intervalMs: 0, iosDesiredAccuracy: kCLLocationAccuracyBest))
}
expectError("Invalid accuracy must be rejected before changing the interval") {
    try host.updateLocationRequest(update: LocationRequestUpdate(intervalMs: 1000, iosDesiredAccuracy: .nan))
}
expectError("Invalid distance must be rejected before changing other settings") {
    try host.updateLocationRequest(update: LocationRequestUpdate(intervalMs: 1000, iosDistanceFilter: -2))
}
expect(manager.locationCallbackInterval == 60000 && manager.desiredAccuracy == kCLLocationAccuracyKilometer,
       "Rejected requests must leave the active SDK settings intact")
expect(manager.distanceFilter == 10 && manager.starts == 1 && manager.stops == 0,
       "Rejected requests must preserve the listener and distance filter")

try host.updateLocationRequest(update: LocationRequestUpdate(
    intervalMs: 1000,
    iosDesiredAccuracy: kCLLocationAccuracyBest,
    iosDistanceFilter: kCLDistanceFilterNone
))
expect(manager.locationCallbackInterval == 1000 && manager.desiredAccuracy == kCLLocationAccuracyBest,
       "Movement must restore the SDK's one-second interval and best accuracy")
expect(manager.starts == 1 && manager.stops == 0 && creations == 1,
       "Restoring high accuracy must preserve the active listener")

host.stopLocationUpdates()
expect(manager.stops == 1, "Explicit stop must stop the SDK")
expectError("Updates after stop must be rejected") { try host.updateLocationRequest(update: saving) }
expect(manager.locationCallbackInterval == 1000, "An update after stop must not mutate the SDK")
print("\(checks) iOS SDK location configuration checks passed")
