# react-native-photo-video-editor

> Early development: Milestone 6 is complete on Android and nearly complete on iOS, and Milestone 7's multi-clip timeline slice (clip list, split/duplicate/delete/reorder, gapless multi-clip export) is now complete on both platforms — audio tracks and transitions are deliberately deferred to a later slice. Android is compile-verified via Gradle throughout, including live-resolved Media3 dependencies, a time-aware `OverlayEffect` export path, the `Brightness`/`Contrast`/`HslAdjustment` filter export path, and multi-clip export via `EditedMediaItemSequence`/`Composition` (all verified against actual library bytecode, not guessed); iOS implements the same editing UI via AVFoundation but is unbuilt (no macOS toolchain here), uses a simpler `AVMutableComposition`-based approach for multi-clip preview/export, and its overlay export still burns overlays in for the whole clip regardless of their configured time range — the one remaining Milestone 6 gap, needing a custom `AVVideoCompositing` implementation to fix. Video aspect-ratio crop, video-level filters, and export-time speed change are Android-only/not-yet-implemented, each a documented, deliberate scope cut rather than an oversight.

A React Native Android/iOS Turbo Module with focused `openPhotoEditor`, `openVideoEditor`, and unified `openEditor` APIs. The full-screen native shell previews local media, honors feature visibility and theme, edits photos on-device (crop/rotate/flip/straighten, adjustments/filters, and layers) and video on-device (trim/mute/rotate/flip/speed/text-sticker-shape-overlays/filters/export), and returns a typed result with real output metadata on Done or a typed cancellation result on Cancel. See [docs/photo-editor.md](docs/photo-editor.md) and [docs/video-editor.md](docs/video-editor.md) for details.

## Requirements and installation

React Native 0.85-compatible New Architecture applications are the verified target of this scaffold. React and React Native are peer dependencies. Autolinking handles native registration.

```sh
npm install react-native-photo-video-editor
# or: yarn add react-native-photo-video-editor
# or: pnpm add react-native-photo-video-editor
cd ios && pod install
```

No permissions are required by the placeholder. Future gallery saving will require consumer-app Photos/media usage descriptions or permissions; the library will not add broad storage permissions.

## Usage

```ts
import {
  openVideoEditor,
  cancelExport,
  addExportProgressListener,
  PhotoVideoEditorError,
} from 'react-native-photo-video-editor';

const subscription = addExportProgressListener((event) => {
  console.log(event.jobId, event.progress); // progress: 0..1
});

try {
  const result = await openVideoEditor({
    source: { uri: 'file:///path/to/video.mp4', type: 'video' },
    features: { crop: true, rotate: true, trim: true, mute: true },
    export: { quality: 'high', videoFormat: 'mp4', frameRate: 30 },
    saveToGallery: false,
  });
  console.log(result.uri, result.cancelled);
  await cancelExport();
} catch (error) {
  if (error instanceof PhotoVideoEditorError)
    console.error(error.code, error.message);
} finally {
  subscription.remove();
}
```

See [API](docs/api.md), [architecture](docs/architecture.md), [photo editor](docs/photo-editor.md), [video editor](docs/video-editor.md), and [roadmap](docs/roadmap.md). Known limitations are non-functional tool placeholders, no transformed export, local-URI-only input, and no verified legacy-architecture support.

For local work, run `yarn`, then `yarn typecheck`, `yarn lint`, `yarn test`, and `yarn prepare`. See [CONTRIBUTING.md](CONTRIBUTING.md). MIT licensed; see [LICENSE](LICENSE).
