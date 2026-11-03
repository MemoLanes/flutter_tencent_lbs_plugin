#!/bin/bash
set -euo pipefail

# Usage: test/native/ios/run.sh /path/to/Flutter.xcframework [booted-simulator-UDID]
plugin_dir="$(cd "$(dirname "$0")/../../.." && pwd)"
flutter_frameworks="${1:?Pass the Flutter.xcframework directory}/ios-arm64_x86_64-simulator"
simulator="${2:-booted}"
sdk_path="$(xcrun --sdk iphonesimulator --show-sdk-path)"
sources="$plugin_dir/ios/flutter_tencent_lbs_plugin/Sources/flutter_tencent_lbs_plugin"
tencent_frameworks="$plugin_dir/ios/flutter_tencent_lbs_plugin/TencentLBS.xcframework/ios-arm64_x86_64-simulator"
test_build="$(mktemp -d "${TMPDIR:-/tmp}/tencent-lbs-ios-tests.XXXXXX")"
trap 'rm -rf "$test_build"' EXIT

xcrun --sdk iphonesimulator swiftc -swift-version 5 -sdk "$sdk_path" \
    -target "$(uname -m)-apple-ios16.0-simulator" \
    -F "$flutter_frameworks" -F "$tencent_frameworks" \
    -framework Flutter -framework TencentLBS -lc++ \
    -Xlinker -rpath -Xlinker "$flutter_frameworks" \
    "$sources/pigeon/LocationPigeon.swift" \
    "$sources/TencentLBSHostApiImpl.swift" \
    "$plugin_dir/test/native/ios/main.swift" \
    -o "$test_build/ios-location-tests"
xcrun simctl spawn "$simulator" "$test_build/ios-location-tests"
