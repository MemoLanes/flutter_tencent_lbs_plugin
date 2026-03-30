import Flutter
import UIKit
import TencentLBS

/// Pigeon Host API 实现（Swift 协议），与腾讯定位 SDK 文档对齐。
/// 坐标系：Flutter 统一 0=GCJ02, 1=WGS84 → iOS TencentLBSLocationCoordinateTypeGCJ02=0, WGS84=1
final class TencentLBSHostApiImpl: NSObject, TencentLBSHostApi, TencentLBSLocationManagerDelegate {
    private var locationManager: TencentLBSLocationManager?
    private var flutterApi: TencentLBSFlutterApi?
    private var isListenLocationUpdates = false

    override init() {
        super.init()
    }

    func configure(options: InitOptions) throws -> Bool {
        let manager = TencentLBSLocationManager()
        manager.delegate = self
        manager.apiKey = options.apiKey
        // enableAntiMockLocation：开启反作弊检查。mockEnable=true 表示允许 Mock，故应关闭反作弊。
        manager.enableAntiMockLocation = !(options.mockEnable ?? false)

        if let ct = options.coordinateType {
            manager.coordinateType = ct == 0 ? .GCJ02 : .WGS84
        }
        if let rl = options.requestLevel {
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
        return true
    }

    func setUserAgreePrivacy(agree: Bool) throws {
        TencentLBSLocationManager.setUserAgreePrivacy(agree)
    }

    func requestLocationOnce() throws {
        locationManager?.requestLocation(completionBlock: { [weak self] location, err in
            guard let self = self else { return }
            if let e = err {
                let code = (e as NSError).code
                self.flutterApi?.onError(code: Int64(code), message: e.localizedDescription) { _ in }
            } else if let loc = location {
                self.sendLocationToFlutter(loc)
            }
        })
    }

    func startLocationUpdates(request: ContinuousLocationRequest, androidNotificationOptions: AndroidNotificationOptions?) throws {
        guard let manager = locationManager, !isListenLocationUpdates else { return }
        guard request.intervalMs >= 1000 else {
            throw FlutterError(code: "invalid-argument", message: "intervalMs 必须大于等于 1000，当前值: \(request.intervalMs)", details: nil)
        }
        isListenLocationUpdates = true
        manager.locationCallbackInterval = UInt64(request.intervalMs)
        if request.backgroundLocation == true {
            manager.allowsBackgroundLocationUpdates = true
            manager.pausesLocationUpdatesAutomatically = false
        }
        if let rl = request.requestLevel {
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

    func updateLocationRequest(update: LocationRequestUpdate?) throws {
        guard let manager = locationManager, let update = update, isListenLocationUpdates else { return }
        var needRestart = false
        if let interval = update.intervalMs {
            guard interval >= 1000 else {
                throw FlutterError(code: "invalid-argument", message: "intervalMs 必须大于等于 1000，当前值: \(interval)", details: nil)
            }
            manager.locationCallbackInterval = UInt64(interval)
            needRestart = true
        }
        if let rl = update.requestLevel {
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
            needRestart = true
        }
        // iOS 上修改 locationCallbackInterval / requestLevel 后需重启定位才会生效。
        // 在后台时用 background task 保护，避免 stop 与 start 之间被系统挂起导致定位未重新开启。
        if needRestart {
            let isBackground = UIApplication.shared.applicationState == .background
            var taskId: UIBackgroundTaskIdentifier = .invalid
            if isBackground {
                taskId = UIApplication.shared.beginBackgroundTask(withName: "TencentLBSRestart") {
                    if Self._restartTaskId != .invalid {
                        UIApplication.shared.endBackgroundTask(Self._restartTaskId)
                        Self._restartTaskId = .invalid
                    }
                }
                Self._restartTaskId = taskId
            }

            manager.stopUpdatingLocation()
            manager.startUpdatingLocation()

            if isBackground, taskId != .invalid {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                    if Self._restartTaskId != .invalid {
                        UIApplication.shared.endBackgroundTask(Self._restartTaskId)
                        Self._restartTaskId = .invalid
                    }
                }
            }
        }
    }

    private static var _restartTaskId: UIBackgroundTaskIdentifier = .invalid

    func stopLocationUpdates() throws {
        isListenLocationUpdates = false
        locationManager?.stopUpdatingLocation()
    }

    func setBinaryMessenger(_ messenger: FlutterBinaryMessenger) {
        flutterApi = TencentLBSFlutterApi(binaryMessenger: messenger)
    }

    // MARK: - TencentLBSLocationManagerDelegate
    func tencentLBSLocationManager(_ manager: TencentLBSLocationManager, didUpdate location: TencentLBSLocation) {
        sendLocationToFlutter(location)
    }

    func tencentLBSLocationManager(_ manager: TencentLBSLocationManager, didFailWithError error: Error) {
        let code = (error as NSError).code
        flutterApi?.onError(code: Int64(code), message: error.localizedDescription) { _ in }
    }

    private func sendLocationToFlutter(_ location: TencentLBSLocation) {
        let cl = location.location
        let provider = location.locationProvider
        let unifiedSource = Int64(provider.rawValue)
        let sourceProviderString: String? = {
            switch provider {
            case .GPS: return "gps"
            case .netWork: return "network"
            case .simulated: return "simulated"
            case .accessoryGPS: return "accessory_gps"
            case .accessoryNetwork: return "accessory_network"
            case .unkown: return nil
            @unknown default: return nil
            }
        }()
        let data = LocationData(
            code: 0,
            latitude: cl.coordinate.latitude,
            longitude: cl.coordinate.longitude,
            altitude: cl.altitude,
            accuracy: cl.horizontalAccuracy,
            horizontalAccuracy: cl.horizontalAccuracy,
            verticalAccuracy: cl.verticalAccuracy,
            speed: cl.speed >= 0 ? cl.speed : nil,
            bearing: cl.course >= 0 ? cl.course : nil,
            address: location.address,
            name: location.name,
            timeIso: nil,
            timeMs: Int64(cl.timestamp.timeIntervalSince1970 * 1000),
            sourceProvider: sourceProviderString,
            locationSource: unifiedSource
        )
        flutterApi?.onLocation(location: data) { _ in }
    }
}
