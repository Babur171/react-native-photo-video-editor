# Changelog

## Unreleased

## 0.3.3

- **Direct photo PNG export on iOS**: Removed the "Export & Share" action sheet confirmation dialog on iOS. Tapping the header completion button (Done / Export) now directly exports the photo as a PNG file without requiring format confirmation.

## 0.3.2

- **Fix iOS video export crash**: Resolved `EXC_BREAKPOINT` / `_xpc_api_misuse` crash on iOS 26+ in `basicvideocompositor.output` by replacing `AVVideoCompositionCoreAnimationTool` with `AVVideoComposition(asset:applyingCIFiltersWithHandler:)` and Core Image compositing in `VideoExporter`.
- **Fix exported video overlay orientation on iOS**: Removed coordinate inversion that was causing stickers and text overlays to export upside down. Overlays now match preview orientation.
- **Video editor UI alignment with Android**:
  - Simplified iOS video editor dock to only Text and Stickers tools, matching Android Kotlin.
  - Removed video trim timeline bar from the main editing screen.
  - Fixed layer property slider height, layout constraints, and toolbar spacing to prevent squashing and clipping across all device sizes.
- **Photo editor bottom toolbar enhancements**:
  - Increased icon size (20pt SF Symbol in 36x36 tile) and font size (12.5pt medium) across the main bottom tool list.
  - Increased button spacing and item width in the horizontal tool rail for improved legibility and touch ergonomics.
- **Crop panel adjustments**:
  - Reduced font sizes of "Crop" header title (14pt semibold) and "Reset" button (12.5pt medium).
  - Fixed text wrapping on the "Reset" button (previously breaking into `Re-` / `set`).
  - Added insets and adjusted width on aspect ratio chips so "Original" stays on a single line.
- **Configurable completion button text**:
  - Added `doneButtonText` (and alias `exportButtonText`) prop to `EditorOptions` and `EditorTheme`.
  - Consumers can now pass custom strings (e.g. `'Done'`, `'Export'`, `'Save'`, `'Next'`) to customize the top-right header button on both iOS and Android (defaults to `'Done'`).
- **Standardized editor header title**:
  - Standardized the default header title to `"Editor"` with 16pt font size on both iOS and Android.

## 0.3.1

- Reduce Android large-video memory pressure with a heap-aware preview sample-buffer target (up to 16 MiB), size-first buffering, and release of preview samples/decoders during export. Restore the paused preview after export cancellation or failure.

## 0.3.0

- Breaking API change: migrate `initialStickerId: 'brand'` to `initialStickerIds: ['brand']`.
- Replace `initialStickerId` with `initialStickerIds` and add a top-right control on the selected default sticker to cycle through default stickers on Android and iOS. Swaps preserve transforms and support undo/redo; the button is enabled only for multiple IDs.
- Place the swap control on the sticker’s top-right corner, above the scale control, so it follows dragging and rotation.
- Remove dummy sticker data from the example; default stickers are supplied by the caller.

## 0.2.2

- Add `initialStickerId` to place a matching `stickerAssets` image at the center when opening the photo or video editor on Android and iOS.
- Validate initial sticker IDs before opening the editor and report unreadable initial images.
- Support base64 PNG data URIs for locally embedded sticker images.
- Keep initial stickers optional: the editor opens without a sticker unless one is supplied.

## 0.2.1

- Fix an iOS build failure in `TextEditorSheet`: its private stored property `editing` collided with `UIViewController.isEditing`, which is exported to Objective-C as `editing`, so the compiler treated it as an invalid override ("cannot override with a stored property" / "overriding property must be as accessible as its enclosing type"). The property is now `isEditingExisting`; the `editing:` initialiser label is unchanged, so call sites are unaffected.
- Fix three more iOS build failures in `PhotoVideoEditorViewController`:
  - `ZoomableImageView()` had no matching initialiser. The class overrides `init(frame:)`, so it does not inherit `UIView`'s no-argument initialiser; the call is now `ZoomableImageView(frame: .zero)`. This also cleared the cascading "cannot infer type of closure parameter 'bounds'" error on the `onBoundsChanged` assignment.
  - Two layer-transform handlers used an immediately-applied closure inside a ternary (`{ var layer = $0; ... }()`). The `$0` inside bound to the inner closure's own parameter rather than the enclosing `map`'s element, making it a one-argument closure invoked with none. Both are rewritten as an explicit `map { current in ... }` with a `guard`.
- Fix an iOS build failure in `PhotoExporter`: `(exportOptions?["maxWidth"] as? NSNumber)?.doubleValue.map { ... }` applied `map` to the non-optional `Double` inside the optional chain rather than to the optional itself. `map` now runs on the optional `NSNumber`, keeping the `CGFloat?` type that `resize(_:maxWidth:maxHeight:)` expects.
- Fix an iOS build failure in `VideoEditSession`: inside `private extension Int64`, the bare `min`/`max` in `clamped(to:)` resolved to the static properties `Int64.min`/`Int64.max` instead of the global functions. They are now qualified as `Swift.min`/`Swift.max`.
- Fix an iOS build failure in `PhotoVideoEditor.mm`: the TurboModule class subclassed the Swift `PhotoVideoEditorSwift`, which Objective-C cannot do — Swift emits every class into the generated header with `objc_subclassing_restricted`. The TurboModule now holds a `PhotoVideoEditorSwift` instance and forwards `openEditor`, `cancelExport` and `isAvailable` to it.

## 0.2.0

- Fix video export placing text/sticker overlays at the wrong size and position on Android. Media3 composites an overlay texture 1:1 in pixels rather than stretching it to fill the frame, so the overlay bitmap's resolution cap shrank every layer toward the frame centre on sources above the cap (4K/1440p); 1080p and below were unaffected. The overlay canvas is now scaled back to the full frame via `OverlaySettings`, making preview and export match at any resolution. iOS was never affected (its `CALayer` scales to fit).
- Expand filter presets from 5 to 17: Vivid/Vivid Warm/Vivid Cool, Warm, Cool, Natural, Soft, Fade, Mono, Noir, Silvertone, Vintage, Sepia, Retro, Dramatic/Dramatic Warm/Dramatic Cool. Existing preset ids still resolve, so previously edited photos render unchanged.
- Redesign the photo editor's bottom UI around one reusable panel system (`EditorPanel.kt`/`.swift`): a compact `Title / Reset / Done` header, a single slider that re-points at whichever adjustment is selected, and horizontally scrollable icon+label strips. Replaces the full-height rectangular adjustment buttons and oversized Done button.
- Add a filter carousel with per-preset thumbnails of the current photo, a purple selection outline, and an intensity slider (iOS previously had no filter thumbnails at all).
- Redesign the video editor: playback controls (circular play/pause, thin seek bar, time readout) now sit inside the media preview and auto-hide during playback; the preview has no border; the bottom toolbar is a compact Text/Stickers dock.
- Size main-dock tools to roughly a fifth of the screen width and mark selection with a purple icon/label over a subtle disc instead of a filled tile.

Note: iOS native code in this release is not compiler-verified — no macOS/Xcode toolchain was available. Android is Gradle-verified (`compileDebugKotlin`, 21 unit tests).

## 0.1.1

- Add bundled offline sticker libraries and custom searchable bottom sheets on Android and iOS.
- Support consumer-provided runtime stickers from HTTPS URLs and device URIs for photo and video layers.
- Add freehand drawing plus movable, pinch-scalable, rotatable text and sticker layers.
- Improve crop controls and remove unused photo tools.
- Simplify the video editor to Text and Stickers, remove clip/trim UI, improve replay behavior, and add play/pause icons.
- Remove the third-party OpenMoji catalog/API integration.

- Adopt the "Studio Violet" design system exported from Stitch (`stitch_react_native_photo_editor_sdk/`) for the photo editor shell and every existing tool: new color/spacing/radius tokens (`DesignTokens.kt`/`DesignTokens.swift`), a real Material Symbols icon set (46 icons fetched from source SVGs and converted to Android vector drawables; SF Symbols on iOS), an icon-tile main tool rail (expanded to the full 16-tool set from the mockup: Crop/Adjust/Filters/Effects/Blur/Text/Stickers/Shapes/Draw/Frames/Overlays/Background/Retouch/Remove/Layers/Resize), a circular-close/pill-Export header, Rotate/Flip moved into the Crop sub-bar, Adjust and Filters split into separate tools (previously combined), real per-preset thumbnail cards for Filters, a card grid for Stickers/Shapes, color-swatch controls for Draw, icon actions for the Layer property bar, and Color/Size properties for text layers. Android is Gradle-verified end to end (`compileDebugKotlin`, `assembleDebug` for the library and example app); iOS mirrors the header, tool rail, and Crop sub-bar but is unbuilt (no macOS/Xcode here) — the remaining tool sub-bars (Filters cards, Stickers/Shapes grid, Draw, Layers, Text) are visually unchanged on iOS for now. Toolbar entries with no mockup-independent functionality yet (Effects, Frames, Overlays, Background, Retouch) still show "coming in a later milestone".
- Add two new photo tools shown in the mockups but not previously implemented: **Blur & Depth** (synthetic Radial/Linear tilt-shift blur via `RadialGradient`/`LinearGradient` + `PorterDuff.DST_IN` alpha-mask compositing — no ML/device depth sensing, since that's unverifiable without a device, so the mockup's ML-driven "Portrait" mode is intentionally out of scope) and **Canvas Resize & Social Presets** (IG Post/Story/Square, YouTube, Twitter presets, reusing the existing crop-aspect-ratio mechanism plus an exact export pixel-size override). Both are Android-only so far, Gradle-verified, wired into both preview and export. AI Object Removal / Magic Eraser was explicitly excluded from this pass per instruction.
- Add focused photo/video editor APIs, native full-screen preview shells, feature-aware toolbars, typed editor events, and the versioned project model foundation.
- Expand the example into separate pick and launch actions.
- Apply `theme` (background/toolbar/text/primary colors, status bar style) in the Android and iOS native editor shells; previously accepted but ignored.
- Fix an Android Kotlin build failure in the toolbar layout (`orientation = HORIZONTAL` did not resolve; now `LinearLayout.HORIZONTAL`).
- Fix the Android header and toolbar rendering underneath the status/gesture bars on targetSdk 35+ (enforced edge-to-edge draws the window behind system bars by default). The header/toolbar now consume `WindowInsetsCompat` system bar insets and grow by that amount instead of being obscured.
- Add real photo editing (Milestone 2): freeform + aspect-ratio-preset crop, 90° rotate, horizontal/vertical flip, fine straighten, pan/pinch-to-zoom preview, and JPEG/PNG/WebP export with EXIF orientation handling and optional metadata preservation. `EditorResult` now returns real `width`/`height`/`fileSize`/`mimeType` for photo edits. Filters/Text/Stickers/Draw remain stubs (Milestone 3/4) but now show a "coming in a later milestone" message instead of doing nothing.
- Add photo adjustments and filters (Milestone 3): Brightness, Contrast, Saturation, Exposure, Gamma, Temperature, Tint, Highlights, Shadows, Sharpen, Blur, Pixelate, Mirror, and five original preset filters (Mono, Noir, Fade, Chrome, Warmth) with an adjustable blend strength. Applied identically in preview and export on both platforms via matching hand-implemented formulas (no CoreImage/RenderScript dependency). Text/Stickers/Draw remain stubs (Milestone 4).
- Add photo layers (Milestone 4): text, stickers (8 built-in + consumer `stickerAssets`), shapes (rect/oval/line), and freehand drawing, with tap-to-select, drag-to-move, and Scale/Rotate/Opacity sliders per layer. Duplicate/Lock/Hide/Front/Back/Delete actions, plus header Undo/Redo backed by a 50-entry snapshot history (one entry per committed operation, never per drag frame). Layers are burned in identically at preview and export resolution. Added `EditorOptions.stickerAssets` and `EditorOptions.fonts` to the public TypeScript API; `fonts` is accepted and forwarded but not yet wired to load custom font files (documented gap, see `docs/photo-editor.md`).
- Fix Android tool sub-bars (crop/filters/stickers/draw/layers/video-aspect) rendering under the gesture nav bar when first shown. Only the main toolbar had edge-to-edge bottom-inset handling; every sub-bar that can replace it at the bottom of the screen now gets the same treatment, applied up front to all of them (Android dispatches insets regardless of visibility, so whichever bar becomes visible when a tool is selected is already correctly padded).
- Add real video editing (Milestone 5): custom player with play/pause/scrub, a dual-handle trim range bar backed by a background-generated thumbnail strip, mute, cover-frame timestamp selection, 90° rotate, horizontal flip, and H.264/AAC MP4 export via Media3 `Transformer` (Android) / `AVAssetExportSession` (iOS), with an in-editor progress spinner and cancel button that deletes the partial output file. `EditorResult` now returns real `width`/`height`/`duration`/`fileSize`/`mimeType` for video exports too. Added the `androidx.media3` dependency family (exoplayer/ui/transformer/effect/common, resolved live over the network). Aspect-ratio crop is Android-only for now — deliberately not shipped on iOS, where the transform math to combine it correctly with rotation was not verifiable without a device.
- Add selected-state highlighting for every mode toolbar button (Crop/Filters/Stickers/Draw on photo, Crop/Mute on video) and every chip in a tool's sub-bar (aspect presets, filter adjustment/preset chips, layer property chips) so the active one is visible at a glance, using `primaryColor` text on a low-alpha `primaryColor` background.
- Add playback speed control (Milestone 6, in progress): cycles 0.5x/1x/1.5x/2x on the preview player (`ExoPlayer.setPlaybackSpeed` / `AVPlayer.rate`). Export does not yet honor the selected speed — a deliberate, documented scope cut (see `docs/video-editor.md`) rather than an oversight, since getting Media3/AVFoundation speed-change-at-export math right was not verifiable without a device.
- Add timed text/sticker/shape overlays on video (Milestone 6): reuses the exact `PhotoLayer`/`PhotoLayerStack`/`PhotoLayerRenderer` machinery from photo layers rather than a parallel system (added `startMs`/`endMs` + `isActiveAt`/`isActive(atMs:)` to `PhotoLayer`). Preview composites active overlays over the player on the existing position-poll tick; export burns them in via Media3 `OverlayEffect`/`BitmapOverlay` (Android) or `AVVideoCompositionCoreAnimationTool` (iOS). Header Undo/Redo now works for video too. Scoped to full-duration overlays only for this pass — no per-overlay time-range trim UI or drag/scale/rotate/delete yet (the largest remaining Milestone 6 item), which let the export side flatten every overlay into one static composited image instead of a genuinely time-varying one.
- Add video-level Brightness/Contrast/Saturation filters (Milestone 6, Android only, export-only — no live preview): applied via `androidx.media3.effect.Brightness`/`Contrast`/`HslAdjustment`, verified to exist in the pinned Media3 1.4.1 by inspecting the actual library bytecode (`javap`) before writing any code, rather than guessing. iOS is scoped out — per-frame CIFilter application needs a different AVFoundation composition pathway (`AVMutableVideoComposition(asset:applyingCIFiltersWithHandler:)`) that conflicts with the manual layer-instruction composition already used for rotate/flip/crop; combining them would need a custom `AVVideoCompositing` implementation.
- An attempt to also apply `speed` at export (via `SpeedProvider`/`TimestampAdjustment`) found that API isn't in Media3 1.4.1; bumping to 1.9.4 to get it broke three other already-verified call sites, so the bump was reverted and `speed` stays preview-only, documented in `docs/video-editor.md`.
- Add video overlay selection, transform, and time-range editing (Milestone 6, closing the last gap): tap an overlay on the video preview to select it, reusing the exact `LayerOverlayView` built for photo layers. A mini toolbar offers Scale/Rotate/Opacity (shared slider) plus Start/End chips to scrub the overlay's time range, and Delete. Android export now genuinely respects per-overlay time ranges via a `BitmapOverlay.getBitmap(presentationTimeUs:)` callback that recomputes and caches the active layer set per timestamp (previously every overlay was flattened into one static full-duration image); iOS gets the same editing UI but its export still burns overlays in for the whole clip regardless of range — documented as the one remaining Milestone 6 gap, since fixing it needs a custom `AVVideoCompositing` implementation.
- Add a multi-clip timeline (Milestone 7, partial — audio tracks and transitions deliberately deferred per the master spec's own sequencing advice): the single implicit video is replaced by an ordered `VideoClip` list (id/source/trim), with a clip-strip UI (Split at the playhead/Duplicate/Delete/Move-Left/Move-Right) and the trim range view now bound to whichever clip is selected instead of the whole video. Android plays a genuine multi-item ExoPlayer playlist and adds `VideoEditSession.globalPositionMs()`/`seekToGlobalMs()` to bridge the composed timeline and the player's own per-item position; export concatenates every clip into one `EditedMediaItemSequence`/`Composition`, verified against the actual Media3 1.4.1 bytecode (`javap`) before writing the code. iOS takes an architecturally simpler route — both preview and export concatenate every clip into a single `AVMutableComposition` via native multi-segment `insertTimeRange(_:of:at:)`, so `player.currentTime()`/`.seek(to:)` already are the composed-timeline position with no separate global-position bookkeeping needed, unlike Android's playlist approach. Gradle-verified on Android (`compileDebugKotlin`, `assembleDebug` for both the library and example app); iOS reviewed but not locally built (no macOS/Xcode here). Every clip in this pass still shares one source file — importing separate additional source files is a follow-up. See `docs/video-editor.md` for the full gap list.

## 0.1.0

- Initial Turbo Module foundation and typed placeholder contract.
