# Technology decisions

The SDK remains an original implementation and does not install or wrap a commercial editor. Android uses Canvas/Bitmap/ColorMatrix for photo work (Milestones 2–4). iOS uses UIKit, Core Graphics, and hand-implemented per-pixel formulas for the same (chosen deliberately over Core Image so both platforms compute identical math and stay visually close, and so the exact filters applied are auditable rather than opaque built-in kernels).

## Video (Milestone 5)

| Concern | Android | iOS |
| --- | --- | --- |
| Preview playback | Media3 `ExoPlayer` + `PlayerView` | `AVPlayer` + `AVPlayerViewController` |
| Thumbnail strip | `MediaMetadataRetriever` (background thread) | `AVAssetImageGenerator` (background queue) |
| Trim/mute/rotate/flip export | Media3 `Transformer` + `EditedMediaItem` + `Effects` | `AVMutableComposition` + `AVMutableVideoComposition` + `AVAssetExportSession` |
| Aspect-ratio crop | `Presentation.createForAspectRatio` | **Not implemented** — see `docs/video-editor.md` |
| Output codec | H.264 video / AAC audio, MP4 | H.264 video / AAC audio, MP4 (`AVAssetExportPresetHighestQuality`) |

**Why Media3 over FFmpeg or a custom `MediaCodec` pipeline**: Media3 is Apache-2.0, actively maintained by Google, targets Android 7+ (well under this package's `minSdk 24`), and its `Transformer` API is purpose-built for exactly this job (trim, effects, re-encode) without hand-rolling codec plumbing. It resolves live from Google's Maven (`androidx.media3:*:1.4.1`) — this development environment has network access, so this was verified by an actual successful Gradle dependency resolution and compile, not assumed. FFmpeg remains excluded: no LGPL/GPL binary-size or store-compliance review has been done, and Media3 covers Milestone 5's requirements without it.

**Why AVFoundation over a custom `AVAssetWriter` pipeline on iOS**: `AVAssetExportSession` is the standard, stable, high-level API for exactly this class of edit (trim + composition + re-encode) and needs far less state-machine code than driving `AVAssetReader`/`AVAssetWriter` directly — lower risk to get right without a device to test on. The tradeoff is less control (e.g., no live progress `Timer`-free callback, hence the 0.3s polling loop, and no straightforward path to combining crop with rotation that was verifiable without a device — see the aspect-ratio gap above).
