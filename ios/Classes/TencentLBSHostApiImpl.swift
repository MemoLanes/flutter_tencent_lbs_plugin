import Flutter
import UIKit
import TencentLBS

/// Pigeon Host API 实现，与腾讯定位 SDK 文档对齐。
/// 坐标系：Flutter 统一 0=GCJ02, 1=WGS84 → iOS TencentLBSLocationCoordinateTypeGCJ02=0, WGS84=1
final class TencentLBSHostApiImpl: NSObject, FLTTencentLBSHostApi, TencentLBSLocationManagerDelegate {
    private var locationManager: TencentLBSLocationManager?
    private var flutterApi: FLTTencentLBSFlutterApi?
    private var isListenLocationUpdates = false

    override init() {
        super.init()
    }

    func initOptions(_ options: FLTInitOptions, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) -> NSNumber? {
        let manager = TencentLBSLocationManager()
        manager.delegate = self
        manager.apiKey = options.apiKey
        manager.enableAntiMockLocation = options.mockEnable?.boolValue ?? false

        if let ct = options.coordinateType?.intValue {
            // Flutter: 0=GCJ02, 1=WGS84 与 iOS 一致
            manager.coordinateType = ct == 0 ? .GCJ02 : .WGS84
        }
        if let rl = options.requestLevel?.intValue {
            let level: TencentLBSRequestLevel = {
                switch rl {
                case 0: return .geo
                case 1: return .name
                case 3: return .adminName
                case 4: return .poi
                default: return .adminName
                }
            }()
            manager.requestLevel = level
        }
        locationManager = manager
        return NSNumber(value: true)
    }

    func setUserAgreePrivacyAgree(_ agree: Bool, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        TencentLBSLocationManager.setUserAgreePrivacy(agree)
    }

    func requestLocationOnceWithError(_ error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        locationManager?.requestLocation(completionBlock: { [weak self] location, err in
            guard let self = self else { return }
            if let e = err {
                let code = (e as NSError).code
                self.flutterApi?.onErrorCode(NSInteger(code), message: e.localizedDescription, completion: { _ in })
            } else if let loc = location {
                self.sendLocationToFlutter(loc)
            }
        })
    }

    func startLocationUpdatesRequest(_ request: FLTContinuousLocationRequest, androidNotificationOptions: FLTAndroidNotificationOptions?, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        guard let manager = locationManager, !isListenLocationUpdates else { return }
        isListenLocationUpdates = true
        manager.locationCallbackInterval = UInt64(request.intervalMs)
        if request.backgroundLocation?.boolValue == true {
            manager.allowsBackgroundLocationUpdates = true
            manager.pausesLocationUpdatesAutomatically = false
        }
        if let rl = request.requestLevel?.intValue {
            let level: TencentLBSRequestLevel = {
                switch rl {
                case 0: return .geo
                case 1: return .name
                case 3: return .adminName
                case 4: return .poi
                default: return .adminName
                }
            }()
            manager.requestLevel = level
        }
        manager.startUpdatingLocation()
    }

    func updateLocationRequestUpdate(_ update: FLTLocationRequestUpdate?, error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        guard let manager = locationManager, let update = update, isListenLocationUpdates else { return }
        if let interval = update.intervalMs?.uint64Value, interval > 0 {
            manager.locationCallbackInterval = interval
        }
        if let rl = update.requestLevel?.intValue {
            let level: TencentLBSRequestLevel = {
                switch rl {
                case 0: return .geo
                case 1: return .name
                case 3: return .adminName
                case 4: return .poi
                default: return .adminName
                }
            }()
            manager.requestLevel = level
        }
    }

    func stopLocationUpdatesWithError(_ error: AutoreleasingUnsafeMutablePointer<FlutterError?>) {
        isListenLocationUpdates = false
        locationManager?.stopUpdatingLocation()
    }

    func setBinaryMessenger(_ messenger: FlutterBinaryMessenger) {
        flutterApi = FLTTencentLBSFlutterApi(binaryMessenger: messenger)
    }

    // MARK: - TencentLBSLocationManagerDelegate
    func tencentLBSLocationManager(_ manager: TencentLBSLocationManager, didUpdate location: TencentLBSLocation) {
        sendLocationToFlutter(location)
    }

    func tencentLBSLocationManager(_ manager: TencentLBSLocationManager, didFailWithError error: Error) {
        let code = (error as NSError).code
        flutterApi?.onErrorCode(NSInteger(code), message: error.localizedDescription, completion: { _ in })
    }

    private func sendLocationToFlutter(_ location: TencentLBSLocation) {
        let lat = location.location?.coordinate.latitude ?? 0
        let lon = location.location?.coordinate.longitude ?? 0
        let data = FLTLocationData.make(
            withCode: NSNumber(value: 0),
            latitude: NSNumber(value: lat),
            longitude: NSNumber(value: lon),
            altitude: NSNumber(value: location.location?.altitude ?? 0),
            accuracy: NSNumber(value: location.location?.horizontalAccuracy ?? 0),
            speed: NSNumber(value: location.location?.speed ?? 0),
            bearing: nil,
            address: location.address,
            name: location.name,
            timeIso: nil,
            timeMs: NSNumber(value: Int((location.location?.timestamp.timeIntervalSince1970 ?? 0) * 1000)),
            sourceProvider: nil
        )
        flutterApi?.onLocationLocation(data, completion: { _ in })
    }
}
