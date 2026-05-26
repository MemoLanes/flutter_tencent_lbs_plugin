// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "flutter_tencent_lbs_plugin",
    platforms: [
        .iOS("13.0")
    ],
    products: [
        .library(name: "flutter-tencent-lbs-plugin", targets: ["flutter_tencent_lbs_plugin"])
    ],
    dependencies: [
        .package(name: "FlutterFramework", path: "../FlutterFramework")
    ],
    targets: [
        .binaryTarget(
            name: "TencentLBS",
            path: "TencentLBS.xcframework"
        ),
        .target(
            name: "flutter_tencent_lbs_plugin",
            dependencies: [
                "TencentLBS",
                .product(name: "FlutterFramework", package: "FlutterFramework")
            ],
            linkerSettings: [
                .linkedLibrary("c++")
            ]
        )
    ]
)
