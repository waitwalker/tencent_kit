// swift-tools-version: 5.9

import PackageDescription

let package = Package(
  name: "tencent_kit",
  platforms: [
    .iOS(.v13)
  ],
  products: [
    .library(name: "tencent-kit", targets: ["tencent_kit"])
  ],
  dependencies: [
    .package(name: "FlutterFramework", path: "../FlutterFramework")
  ],
  targets: [
    .target(
      name: "tencent_kit",
      dependencies: [
        .product(name: "FlutterFramework", package: "FlutterFramework"),
        "TencentOpenAPI",
      ],
      path: "Sources/tencent_kit",
      publicHeadersPath: ".",
      linkerSettings: [
        .linkedFramework("Security"),
        .linkedFramework("SystemConfiguration"),
        .linkedFramework("CoreGraphics"),
        .linkedFramework("CoreTelephony"),
        .linkedFramework("WebKit"),
        .linkedLibrary("iconv"),
        .linkedLibrary("sqlite3"),
        .linkedLibrary("stdc++"),
        .linkedLibrary("z"),
        .unsafeFlags(["-ObjC", "-all_load"]),
      ]
    ),
    .binaryTarget(
      name: "TencentOpenAPI",
      path: "TencentOpenAPI.xcframework"
    ),
  ]
)
