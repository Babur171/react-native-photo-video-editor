# Video editor

## Milestone 5 — single-video preview, trim, mute, cover frame, and export

- **Playback** — a custom player (Media3 `ExoPlayer`/`PlayerView` on Android, `AVPlayer`/`AVPlayerViewController` on iOS) with Play/Pause, a scrubber, and a current-time/duration label, polled every ~250ms (no push-based position API on either platform).
- **Trim** — a dual-handle range bar below the player, backed by a 12-thumbnail strip generated off the main thread (`MediaMetadataRetriever` / `AVAssetImageGenerator`). Dragging a handle updates the trim range live; a minimum-length gap between handles is enforced.
- **Mute** — toggles live preview volume and excludes the audio track at export.
- **Cover frame** — tap **Cover** while scrubbed to the desired frame to record it as `coverFrameMs`. The frame itself is not yet extracted to an image file — the timestamp is tracked but not returned on `EditorResult` yet (a small follow-up).
- **Rotate** — cycles 90° increments, applied at export via a rotation transform; the preview shows a live `rotation`/`transform` approximation on the player view rather than a full re-render.
- **Flip** — horizontal flip, same live-approximation preview treatment as rotate.
- **Aspect-ratio crop** (Original/1:1/4:5/9:16/16:9) — **Android only.** On iOS this is scoped out for now: combining a crop with the source's own orientation-correcting transform and an additional rotation inside `AVMutableVideoCompositionLayerInstruction`'s coordinate space is easy to get subtly wrong, and there is no device available in this environment to verify the pixel math, so it was not shipped unverified. Selecting a preset on iOS shows a toast noting the gap rather than silently doing nothing.
- **Export** — H.264 video / AAC audio MP4, via Media3 `Transformer` (Android) or `AVAssetExportSession` with `AVMutableComposition`/`AVMutableVideoComposition` (iOS). Progress is polled (~300ms) and shown as an indeterminate spinner with a **Cancel export** button; cancellation deletes the partial output file. `EditorResult` returns real `width`/`height`/`duration`/`fileSize`/`mimeType`.

## Milestone 6 — playback speed, timed overlays, and overlay editing

- **Speed** — cycles 0.5x → 1x → 1.5x → 2x → 0.5x…, applied live to the preview player (`ExoPlayer.setPlaybackSpeed` / `AVPlayer.rate`) and shown on the toolbar button itself (e.g. "Speed 1.5x") rather than a highlight, since it's a cycling value, not an on/off mode.
- **Export does not yet honor speed** — this is a deliberate, documented scope cut, not an oversight. Changing output speed correctly (video timestamp remapping + audio pitch-preserving time-stretch) is a meaningfully different problem from trim/mute/rotate/flip, with real risk of getting the Media3 `SpeedProvider`/`TimestampAdjustment` or AVFoundation `scaleTimeRange` math wrong without a device to verify against. Exporting after setting a non-1x speed currently produces a normal-speed file — the UI does not yet warn about this mismatch (a follow-up).
- **Text/Sticker/Shape overlays** — tap **Text** or **Stickers** on the video toolbar (the sticker picker also offers Rectangle/Oval/Line shapes) to add an overlay, reusing the exact same `PhotoLayer`/`PhotoLayerStack`/`PhotoLayerRenderer` machinery built for photo layers (Milestone 4) rather than a parallel system. `PhotoLayer` gained `startMs`/`endMs` fields for time-ranging, with an `isActiveAt`/`isActive(atMs:)` helper.
  - Preview: active overlays (those whose time range contains the current playhead) are rendered onto a transparent image placed over the player, refreshed on the same ~250ms position-poll tick already used for the scrubber.
  - Export: burned in via Media3 `OverlayEffect`/`BitmapOverlay` (Android) or `AVVideoCompositionCoreAnimationTool` (iOS).
  - Header Undo/Redo now works for video overlays too, same 50-entry snapshot history as photo layers.
- **Overlay selection, transform, and time-range editing** — tap an overlay on the video preview to select it (reuses the same `LayerOverlayView` built for photo layers: dashed bounding box, tap-to-select, drag-to-move). Once selected, a mini toolbar offers Scale/Rotate/Opacity (shared slider, same pattern as photo) plus **Start**/**End** chips that scrub the overlay's time range against the video's duration, and Delete.
  - Android export now genuinely respects per-overlay time ranges: `BitmapOverlay.getBitmap(presentationTimeUs:)` recomputes the active layer set per callback (cached by an active-layer-ID signature so it only re-renders when that set actually changes, not on every frame). **One unverified detail**: this assumes Transformer's effect callbacks receive presentation timestamps rebased to the trimmed output's own zero point, so `trimStartMs` is added back before comparing against `startMs`/`endMs` (which are authored against the untrimmed source's timeline) — there's no device here to confirm this assumption.
  - **iOS export does not yet respect time ranges** — every overlay is still burned in for the whole clip regardless of its configured range, even though the editor UI lets you set one. `AVVideoCompositionCoreAnimationTool` only composites a fixed `CALayer` tree; making it time-varying like Android's per-frame callback needs a custom `AVVideoCompositing` implementation, a distinctly bigger change than anything else in this pass. This is the one remaining gap in Milestone 6.
- **Video-level filters** (Brightness/Contrast/Saturation) — **Android only, export-only (no live preview)**. Verified against the actual Media3 1.4.1 bytecode (via `javap`, not guessed) before writing any code: `androidx.media3.effect.Brightness(Float)`, `Contrast(Float)`, and `HslAdjustment.Builder().adjustSaturation(Float)` all exist and implement `Effect` in this exact version. iOS is scoped out: applying per-frame color filters requires `AVMutableVideoComposition(asset:applyingCIFiltersWithHandler:)`, which creates its own composition and conflicts architecturally with the manual `AVVideoCompositionLayerInstruction`-based composition already used here for rotate/flip/crop — combining them needs the same custom `AVVideoCompositing` implementation noted above. Tapping Filters on iOS video shows a "coming in a later milestone" message.
- A real API-version lesson from this pass: an attempt to also apply `speed` at export via `SpeedProvider`/`TimestampAdjustment(SpeedProvider)` found that API doesn't exist in Media3 1.4.1. Bumping the dependency to 1.9.4 to get it broke three other already-verified call sites (`Transformer.Builder.setTransformationRequest`, `OverlayEffect`'s constructor list type, and `SpeedProvider` still wasn't found even there). The version bump was reverted rather than chased further mid-session — `speed` stays preview-only, documented above, and 1.4.1 remains pinned since everything else against it is verified working.

**Still stubs**: Filters on video (iOS only — Android has it, see above).

### Known gaps

- iOS aspect-ratio crop is not implemented (see above) — Android has it.
- iOS video-level filters (Brightness/Contrast/Saturation) are not implemented (see above) — Android has it, export-only.
- **iOS video overlay export does not respect per-overlay time ranges** (see above) — the editor UI supports setting them on both platforms, but only Android's export honors them. This is the one remaining Milestone 6 gap.
- Cover-frame image extraction (saving the selected frame as a JPEG and returning its URI) is not implemented yet; only the timestamp is tracked internally.
- Playback speed is preview-only on both platforms; export ignores it (see above for why).
- Export progress is a spinner, not wired to the JS `addExportProgressListener` event bridge yet — consistent with how photo export currently works, but a real gap for a later milestone.
- No variable-frame-rate handling beyond what each platform's default export pipeline does automatically.
- `cancelExport()` (the public JS API) is still a no-op stub — cancellation only works via the in-editor Cancel button while the exporter is running inside the native UI.

**Verification status**: Android is compiled and built via Gradle (`:react-native-photo-video-editor:assembleDebug`, `:app:assembleDebug`) against Media3 1.4.1, resolved live from Google's Maven (this environment has network access, unlike the fully-offline assumption of earlier milestones), including the `OverlayEffect`/`BitmapOverlay` overlay export path (now time-aware) and the `Brightness`/`Contrast`/`HslAdjustment` filter export path. It has not been exercised on an emulator/device with a real video file — in particular, the presentation-timestamp-rebasing assumption in the overlay time-range logic is unverified. iOS mirrors the overlay selection/transform/time-range UI (minus time-range-aware export, aspect crop, and video-level filters) using AVFoundation but is unverified — no macOS/Xcode toolchain is available here.

## Milestone 7 (partial) — multi-clip timeline

The single implicit video is replaced by an ordered `VideoClip` list (`id`, `sourceUri`, `originalDurationMs`, `trimStartMs`/`trimEndMs`). Every clip in this pass shares one source file — new clips only come from **Split** and **Duplicate**, not from importing separate additional source files (a follow-up). Audio tracks and transitions, the other two pieces the master spec bundles into Milestone 7, are **deliberately deferred** — per the spec's own guidance to add transitions only after gapless multi-clip export is stable.

- **Clip strip** — a horizontal strip below the trim range view lists every clip as a chip (`Clip N` + trimmed duration); tapping one selects it, seeks the player to its start, and rebinds the trim range view to that clip's own `originalDurationMs` (previously the trim view was bound to the whole video).
- **Split** — cuts the selected clip into two at the current playhead position, both sharing the original source file and together covering the exact same trimmed range as before the split.
- **Duplicate** — inserts a copy of the selected clip immediately after itself (new `id`, same source/trim).
- **Delete** — removes the selected clip; refuses to leave the timeline with zero clips.
- **Move Left / Move Right** — reorders the selected clip by one position.
- **Trim** — now edits whichever clip is selected (fractions relative to that clip's own `originalDurationMs`), not the whole video.

### Android

ExoPlayer plays a genuine multi-item playlist (`player.setMediaItems(List<MediaItem>)`, one `MediaItem` per clip with its own `ClippingConfiguration`). Because ExoPlayer's own `currentPosition`/`seekTo` are per-item, `VideoEditSession` adds `globalPositionMs()` (preceding clips' trimmed durations + position in the current item) and `seekToGlobalMs(Long)` (the inverse) so the rest of the UI — scrubber, time label, cover-frame, overlay-preview timing — can keep working against one composed timeline exactly as before. `coverFrameMs` and overlay `startMs`/`endMs` are now authored against this global timeline.

Export concatenates every clip into a single `EditedMediaItemSequence` passed to `Composition`, replacing the old single-`EditedMediaItem` call. Verified against the actual Media3 1.4.1 bytecode (`javap` on the real `media3-transformer-1.4.1.aar`, not guessed) before writing the code:
- `EditedMediaItemSequence(EditedMediaItem, EditedMediaItem...)` and `EditedMediaItemSequence(List<EditedMediaItem>)` both exist.
- `Composition.Builder(EditedMediaItemSequence, EditedMediaItemSequence...)` and `Composition.Builder(List<EditedMediaItemSequence>)` both exist.
- `Transformer.start(Composition, String)` exists alongside the previously-used `start(EditedMediaItem, String)`.

Each clip becomes its own `EditedMediaItem` with the same global rotate/flip/aspect/filter effects; the per-overlay time-range `BitmapOverlay` callback now offsets its own item-local `presentationTimeUs` by the summed trimmed duration of the preceding clips to recover the global position that overlay `startMs`/`endMs` are authored against — the same unverified-without-a-device rebasing assumption carried over from Milestone 6, just generalized to per-clip offsets instead of one global `trimStartMs`.

Gradle-built: `:react-native-photo-video-editor:compileDebugKotlin`, `:react-native-photo-video-editor:assembleDebug`, and `:app:assembleDebug` all pass. Not exercised on an emulator/device with a real multi-clip export.

### iOS

Takes an architecturally simpler path than Android: rather than a multi-item player, **both preview and export concatenate every clip's trimmed range into a single `AVMutableComposition`** via `insertTimeRange(_:of:at:)`, which supports multi-segment concatenation natively. This means `player.currentTime()`/`.seek(to:)` already **are** the composed-timeline position — there is no separate global-vs-per-item position to reconcile, unlike Android's playlist approach, so no `globalPositionMs()`/`seekToGlobalMs()` equivalent was needed. This is a deliberate, documented platform difference, not an inconsistency. `VideoEditSession.asset(for:)` resolves any one clip's own `AVURLAsset` (used for natural-size lookups and per-clip thumbnail generation). Not locally built — no macOS/Xcode toolchain here.

### Known gaps (this slice)

- All clips share one source file — no system file picker to import additional separate videos yet.
- Audio tracks (adding/mixing separate audio) — deferred, not started.
- Transitions between clips — deferred, not started; the master spec recommends only adding these once gapless export is stable, which this slice establishes.
- Android's per-clip overlay-offset rebasing and iOS's whole-timeline composition are both unverified on-device, same caveat as every other Transformer/AVFoundation timing assumption in this project so far.
