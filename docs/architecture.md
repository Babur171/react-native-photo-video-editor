# Architecture

The runtime flow is: consumer React Native app → validated public TypeScript API → serialized Codegen Turbo Module contract → Kotlin on Android or Swift through an Objective-C++ bridge on iOS → future native editor screen → future native export pipeline → progress events and a final local URI.

Turbo Modules provide typed autolinking and the current New Architecture contract. Fabric is unnecessary because the editor will be presented as a full-screen native screen, not embedded as JSX. Media bytes never cross the bridge: only URIs, serializable settings, errors, progress, and output metadata do.

The 0.63.0 generator does not expose its Kotlin/Swift preset for Turbo Modules, so the supported Kotlin/Objective-C scaffold is retained while the iOS implementation lives in Swift behind `PhotoVideoEditor.mm`. The ergonomic nested public types are serialized to JSON because Codegen's safe type subset is narrower.

Future UI presentation runs on the main thread; decoding, transforms, and export run off it. Each call must resolve or reject once. Platform exceptions map to stable codes. A future implementation will permit one active editor, define output ownership, and clean temporary files according to a documented retention policy. Legacy-architecture compatibility remains undecided.
