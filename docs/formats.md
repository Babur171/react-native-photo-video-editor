# Verified formats

## Import

Local JPEG images and MP4 videos are read through platform decoders (`BitmapFactory`/`ExifInterface` on Android, `ImageIO`/`CGImageSource` on iOS). `content://`/`file://` sources are supported; `https://` and Photos-library identifiers (`ph://`) are not yet implemented.

## Photo export (Milestone 2)

| Format | Android | iOS |
| --- | --- | --- |
| JPEG | Supported, quality presets (`low`/`medium`/`high`/`original` → 50/75/90/100), EXIF metadata preservation opt-in | Supported, same quality presets |
| PNG | Supported, transparency preserved | Supported, transparency preserved |
| WebP | Supported (`Bitmap.CompressFormat.WEBP_LOSSY` on API 30+, legacy `WEBP` below) | **Not supported** — no first-party iOS encoder; falls back to JPEG |

Compile-verified on Android via Gradle in this environment (no emulator/device attached for pixel-level verification). Not built or run on iOS (no macOS/Xcode toolchain available here) — treat the iOS export path as implemented-but-unverified until built and tested on a Mac.

## Video export (Milestones 5–6)

| Format/feature | Android | iOS |
| --- | --- | --- |
| MP4 (H.264 video / AAC audio) | Supported via Media3 `Transformer` | Supported via `AVAssetExportSession` (`AVAssetExportPresetHighestQuality`) |
| Aspect-ratio crop | Supported | **Not implemented** — see `docs/video-editor.md` |
| Text/sticker/shape overlay burn-in | Supported via `OverlayEffect`/`BitmapOverlay` | Supported via `AVVideoCompositionCoreAnimationTool` |
| Brightness/Contrast/Saturation filters | Supported via `Brightness`/`Contrast`/`HslAdjustment` | **Not implemented** — see `docs/video-editor.md` |
| Playback speed | **Not applied at export** (preview only, both platforms) | **Not applied at export** (preview only, both platforms) |

Compile-verified on Android via Gradle against Media3 1.4.1, resolved live over the network (this environment has network access) — including the overlay and filter effect classes, confirmed against the actual library bytecode rather than assumed. Not exercised on an emulator/device with a real video file. Not built or run on iOS — treat the iOS export path as implemented-but-unverified until built and tested on a Mac.
