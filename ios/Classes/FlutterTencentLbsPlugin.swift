import Flutter
import UIKit

public class FlutterTencentLbsPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let api = TencentLBSHostApiImpl()
        api.setBinaryMessenger(registrar.messenger())
        SetUpFLTTencentLBSHostApi(registrar.messenger(), api)
    }
}
