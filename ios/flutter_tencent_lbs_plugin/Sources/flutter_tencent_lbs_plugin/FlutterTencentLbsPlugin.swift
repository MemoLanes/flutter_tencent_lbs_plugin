import Flutter
import UIKit

public class FlutterTencentLBSPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let api = TencentLBSHostApiImpl()
        api.setBinaryMessenger(registrar.messenger())
        TencentLBSHostApiSetup.setUp(binaryMessenger: registrar.messenger(), api: api)
    }
}
