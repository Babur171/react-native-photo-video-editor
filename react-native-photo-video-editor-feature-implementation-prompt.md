# Feature Implementation Prompt: Native React Native Photo & Video Editor

> Use this prompt after the initial `react-native-photo-video-editor` Turbo Module project has been created and its Android/iOS example apps build successfully.

---

## Role and mission

Act as a principal mobile media SDK engineer, React Native New Architecture specialist, Kotlin engineer, Swift engineer, graphics engineer, and mobile UX architect.

Continue development of our existing package:

```text
react-native-photo-video-editor
```

Build an original, production-quality, on-device photo and video editing SDK for React Native. The desired product capabilities are broadly comparable to the feature categories demonstrated by these reference pages:

- Video editor reference: https://img.ly/docs/cesdk/react-native/prebuilt-solutions/video-editor-9e533a/
- Photo editor reference: https://img.ly/docs/cesdk/react-native/prebuilt-solutions/photo-editor-42ccb2/

Use those pages only as product and capability references. Do not copy IMG.LY source code, proprietary UI, branding, icons, assets, internal APIs, documentation text, scene formats, or trade dress. Do not install or wrap `@imgly/editor-react-native`, CE.SDK, or another commercial editor. Implement our own architecture, UI, data model, rendering, editing, and export behavior using lawful native platform frameworks and dependencies with compatible licenses.

The implementation must use:

- TypeScript for the React Native public API.
- A Codegen Turbo Module for commands, configuration, results, and events.
- Kotlin for Android native code and UI.
- Swift for iOS native code and UI.
- Native on-device processing; no backend is required for editing or export.
- A native full-screen editor launched from React Native.
- The existing example React Native app for live testing.

Do not attempt to implement every advanced capability in one unsafe change. Work milestone by milestone, keeping the repository buildable and testable after every milestone.

## First action: inspect, do not assume

Before changing code:

1. Read the complete repository structure.
2. Read `package.json`, Codegen configuration, podspec, Android Gradle files, iOS bridge files, TypeScript API, tests, example app, and documentation.
3. Run `git status` and preserve unrelated user changes.
4. Identify the installed React Native version and generator conventions.
5. Run the existing typecheck, lint, tests, Codegen, package build, and available native builds.
6. Record existing failures before introducing changes.
7. Do not regenerate the project or replace working configuration.
8. Do not use destructive Git commands.

If the initial Turbo Module foundation is missing or does not build, stop feature work and repair the smallest foundation issue first.

## Product requirements

The finished SDK should eventually provide two focused solutions sharing one core engine:

```ts
openPhotoEditor(options): Promise<EditorResult>
openVideoEditor(options): Promise<EditorResult>
```

It should also retain a unified method when useful:

```ts
openEditor(options): Promise<EditorResult>
```

The SDK must support:

### Photo editing

- Crop with freeform and aspect-ratio presets.
- Straighten.
- Rotate left/right.
- Horizontal and vertical flip.
- Scale and pan.
- Brightness.
- Contrast.
- Saturation.
- Exposure.
- Gamma.
- Temperature/warmth.
- Tint.
- Highlights.
- Shadows.
- Clarity/sharpness where natively feasible.
- Blur.
- Pixelate.
- Mirror and selected original effects.
- Preset filters.
- Custom filter/LUT registration as a later advanced capability.
- Text layers with fonts, color, size, alignment, opacity, rotation, scale, stroke, background, and shadow.
- Sticker, image, and shape layers.
- Freehand drawing with color, width, opacity, eraser, undo, and redo.
- Layer selection, movement, resize, rotation, duplication, reorder, lock, visibility, and deletion.
- Before/after preview.
- Reset individual adjustment and reset all.
- Export to JPEG, PNG, and WebP where platform codecs support it.
- Transparency preservation for compatible formats.
- Configurable size, quality, and metadata policy.

### Video editing

- Import one video for the first stable video milestone.
- Preview with play, pause, seek, current time, duration, and frame preview.
- Trim start/end.
- Split clips.
- Reorder clips.
- Delete and duplicate clips.
- Join multiple clips in advanced milestones.
- Crop, scale, rotate, flip, and aspect-ratio presets.
- Mute original clip audio.
- Clip volume control.
- Playback speed within a documented safe range.
- Cover/poster-frame selection.
- Video adjustments, filters, effects, and blur where supported.
- Text, sticker, image, and shape overlays with time ranges.
- Audio/music tracks with trim, volume, fade-in, fade-out, and timeline positioning.
- Multi-track timeline for video, image, overlay, and audio items.
- Timeline zoom and horizontal scrolling.
- Selection-aware contextual controls.
- Export progress and cancellation.
- MP4/H.264 baseline export.
- Optional HEVC/H.265 only when supported and explicitly requested.
- MOV support on iOS and compatible Android input handling.
- Configurable resolution, bitrate, frame rate, quality, and audio inclusion.
- Social aspect ratios including 1:1, 4:5, 9:16, 16:9, and original.
- Story/Reel presets without using third-party branding in our UI.
- Handle orientation, rotation metadata, variable frame rate, and audio/video synchronization.

### Shared capabilities

- Fully on-device processing.
- Configurable feature visibility.
- Customizable theme and toolbar arrangement.
- Consumer-provided asset sources for stickers, images, fonts, audio, and templates.
- Save/load editable drafts using our own versioned project format.
- Undo/redo command history.
- Autosave recovery for long editing sessions in a later milestone.
- Lifecycle handling for background/foreground transitions.
- Deterministic temporary file cleanup.
- Stable errors and cancellation semantics.
- Accessibility and localization-ready UI.
- RTL-safe layouts.
- Dark and light themes.

## Reality and scope constraints

This is a large SDK. Follow this implementation order and do not start the next milestone until the current milestone meets its acceptance criteria:

1. Shared project model and full-screen native editor shell.
2. Single-photo transform and export.
3. Photo adjustments and filters.
4. Photo text, sticker, shape, drawing, and layers.
5. Single-video preview, trim, mute, crop, and export.
6. Video overlays, cover selection, speed, filters, and adjustments.
7. Multiple clips and timeline.
8. Audio tracks and mixing.
9. Drafts, templates, custom assets, and headless APIs.
10. Optimization, compatibility matrix, and release hardening.

If asked to execute this complete prompt in one session, implement Milestone 1 first, verify it, then continue only while the repository remains green. Report honestly what is completed and what remains.

## Architecture

Use this separation:

```text
React Native consumer
  -> TypeScript public API
  -> validation and default normalization
  -> Codegen Turbo Module
  -> native editor coordinator
  -> native editor UI
  -> platform editing/rendering engine
  -> native exporter
  -> output file URI and metadata
```

On each platform, use these logical layers:

```text
Bridge
Coordinator
Project model
Command/undo system
Preview renderer
Native editor UI
Asset repository
Export engine
File manager
Validation and errors
```

Do not pass frame buffers or complete media bytes through JavaScript. The React Native layer should send JSON-compatible configuration and URIs, then receive lifecycle/progress events and the final file URI.

## Suggested native directory layout

Adapt this to the existing generated package instead of creating duplicate architecture:

```text
android/src/main/java/com/photovideoeditor/
├── bridge/
├── coordinator/
├── model/
├── command/
├── photo/
│   ├── ui/
│   ├── render/
│   └── export/
├── video/
│   ├── ui/
│   ├── player/
│   ├── timeline/
│   └── export/
├── assets/
├── files/
├── validation/
└── errors/

ios/
├── Bridge/
├── Coordinator/
├── Models/
├── Commands/
├── Photo/
│   ├── UI/
│   ├── Rendering/
│   └── Export/
├── Video/
│   ├── UI/
│   ├── Player/
│   ├── Timeline/
│   └── Export/
├── Assets/
├── Files/
├── Validation/
└── Errors/
```

Avoid enormous Activity/ViewController or manager files. Split code by responsibility, not by arbitrary patterns.

## Technology evaluation gate

Before adding media dependencies, create `docs/technology-decisions.md` and compare candidates based on capability, maintenance, binary size, performance, New Architecture compatibility, minimum OS requirements, license, patent/codec considerations, and store compliance.

Preferred baseline:

### Android

- Jetpack Media3 Player for preview.
- Jetpack Media3 Transformer for supported video edits and export.
- Android graphics APIs, Canvas, Bitmap, and color matrices for foundational photo editing.
- OpenGL ES or another lightweight GPU path only when preview performance requires it.
- Kotlin coroutines for cancellable processing.
- `ContentResolver` for `content://` sources.

### iOS

- AVFoundation and AVPlayer for video playback/composition/export.
- Core Image for non-destructive filters and adjustments.
- Core Graphics for raster composition.
- Metal/Core Image kernels only where profiling proves necessary.
- Photos framework only for explicitly requested gallery operations.
- Swift concurrency with safe cancellation.

Do not add FFmpeg automatically. Before considering it, document LGPL/GPL configuration, codecs, binary size, build complexity, maintenance, and App Store/Play Store implications. Obtain approval before introducing it.

## Public TypeScript API

Design a stable, documented API. Preserve backward compatibility with the existing setup API when possible.

Required top-level exports:

```ts
openEditor(options: EditorOptions): Promise<EditorResult>;
openPhotoEditor(options: PhotoEditorOptions): Promise<EditorResult>;
openVideoEditor(options: VideoEditorOptions): Promise<EditorResult>;
cancelExport(jobId?: string): Promise<boolean>;
isAvailable(): boolean | Promise<boolean>;
addEditorEventListener(listener: EditorEventListener): Subscription;
```

Add headless editing/export APIs only in the relevant later milestone:

```ts
exportProject(options: HeadlessExportOptions): Promise<EditorResult>;
saveDraft(options: SaveDraftOptions): Promise<DraftResult>;
loadDraft(options: LoadDraftOptions): Promise<EditorResult>;
```

### Source types

Support local native URIs first:

```ts
type EditorSource = {
  uri: string;
  type: 'photo' | 'video';
  mimeType?: string;
  fileName?: string;
};
```

Document handling for:

- Android `content://` and `file://`.
- iOS sandbox file URLs and photo-library identifiers when supported.
- Remote `https://` media as opt-in download-to-cache behavior only, not silent streaming into the editor.
- Security-scoped resources on iOS if applicable.

### Configuration

Include typed configuration for:

- Enabled tools.
- Theme colors and appearance.
- Toolbar groups and ordering.
- Allowed aspect ratios.
- Output formats.
- Maximum duration and dimensions.
- Export quality, resolution, bitrate, frame rate, codec, and metadata.
- Asset providers.
- Localization strings/locale.
- Save-to-gallery behavior.
- Draft/autosave behavior in later milestones.

Do not expose platform-native objects through the public API.

### Results

Return:

```ts
type EditorResult = {
  uri: string;
  type: 'photo' | 'video';
  mimeType: string;
  width: number;
  height: number;
  duration?: number;
  fileSize: number;
  cancelled: false;
  projectId?: string;
};
```

Cancellation should either resolve a clearly typed cancellation result or reject with `E_EDITOR_CANCELLED`. Choose one behavior for the stable API, document it, and use it consistently on both platforms. Prefer a typed cancellation result for user-initiated editor dismissal and rejection for operational failures.

### Events

Support typed events:

- `editorOpened`
- `editorReady`
- `selectionChanged`
- `projectChanged`
- `exportStarted`
- `exportProgress`
- `exportCompleted`
- `exportCancelled`
- `editorClosed`
- `error`

Progress must be normalized to `0...1`, monotonic per export job when possible, and include a stable `jobId`. Throttle high-frequency events so the bridge is not flooded.

## Project and scene model

Create our own serializable, versioned project format. Do not imitate a proprietary scene schema.

Suggested model:

```text
EditorProject
├── schemaVersion
├── id
├── canvas
│   ├── width
│   ├── height
│   ├── backgroundColor
│   └── durationMs
├── assets[]
├── tracks[]
│   └── items[]
├── adjustments
├── exportDefaults
└── metadata
```

Each layer/timeline item should have:

- Stable UUID.
- Type.
- Source reference.
- Transform: position, size, scale, rotation, crop, flip, opacity.
- Z-index or track order.
- Start/end time for timed projects.
- Optional effects/adjustments.
- Lock/visibility state.
- Type-specific payload.

Use non-destructive editing: actions update project state, while the original source remains unchanged until export.

Version the schema from day one. Validate drafts and provide future migration hooks.

## Undo and redo

Implement a command-based history system shared conceptually across platforms:

- Each user mutation is a reversible command.
- Group continuous gestures into a single history operation.
- Do not create hundreds of undo entries while a slider or drag gesture moves.
- Set a configurable history limit.
- Reset redo when a new command follows an undo.
- Do not store full bitmap/video copies for ordinary history entries.
- Exclude playback-only state and transient selection from project history.

## Native editor UX

Create an original modern mobile UI. Do not visually clone the reference product.

### Shared layout

- Top app bar: close/back, project title/status, undo, redo, export/done.
- Central canvas/preview using maximum available space.
- Selection overlay with transform handles only when appropriate.
- Bottom primary toolbar with horizontally scrollable tools.
- Contextual inspector panel/sheet for the selected tool or layer.
- Video-only timeline between preview and bottom toolbar.
- Loading overlay for media preparation.
- Export sheet with progress and cancel action.
- Accessible error/permission dialogs.

### UI behavior

- Use platform-native UI patterns while keeping names, icons, behavior, and structure consistent.
- Support portrait first; handle landscape without broken controls.
- Respect safe areas, cutouts, system bars, keyboards, and gesture navigation.
- Minimum accessible touch target sizes.
- Screen-reader labels and logical focus order.
- Visible selected, disabled, pressed, and loading states.
- Dark/light theme and consumer colors.
- RTL-safe layout.
- Keep preview interactions at a smooth frame rate.
- Avoid blocking the main thread during decode, analysis, or export.

## Milestone 1: Native editor shell

Implement first:

- `openPhotoEditor()` and `openVideoEditor()` routing.
- One active editor at a time.
- Full-screen Android native editor Activity or current suitable native container.
- Full-screen iOS native editor ViewController.
- Safe presentation/dismissal from the current React Native host.
- Source validation and loading state.
- Header, preview placeholder/media preview, toolbar shell, cancel, and done.
- Native result/cancellation delivery exactly once.
- Lifecycle-safe promise/request tracking.
- Basic theming and feature visibility.
- Example buttons for photo and video.

Reject concurrent presentation with `E_EDITOR_ALREADY_OPEN`.

Acceptance criteria:

- Both editors can open and close repeatedly without crashes.
- Cancel produces the documented cancellation result.
- Done returns a valid result without corrupting the source.
- Rotation/background/foreground does not orphan the request.
- No retained Activity/ViewController leak.
- Android and iOS API behavior matches.

## Milestone 2: Photo transform and export

Implement:

- Decode with orientation respected.
- Pan and pinch-to-zoom.
- Crop overlay.
- Freeform crop.
- Aspect ratios: original, free, 1:1, 4:5, 3:4, 9:16, 16:9.
- Rotate 90 degrees.
- Fine straightening within a safe range.
- Horizontal/vertical flip.
- Reset transforms.
- Before/after preview.
- JPEG and PNG export.
- WebP only where reliable and documented.
- Quality and maximum-dimension options.
- Correct output metadata.
- Memory-aware decoding/downsampling.
- EXIF orientation handling and configurable metadata preservation.

Never load an unrestricted full-resolution bitmap multiple times. Use subsampling/preview representations and render full resolution only during export.

Acceptance criteria:

- Portrait images are not unexpectedly rotated.
- Crop coordinates match the exported image.
- Large images do not cause avoidable OOM crashes.
- Transparency is preserved for PNG.
- Export cancellation cleans partial files.
- Source remains untouched.

## Milestone 3: Photo adjustments and filters

Implement a non-destructive adjustment stack:

- Brightness.
- Contrast.
- Saturation.
- Exposure.
- Gamma.
- Temperature.
- Tint.
- Highlights.
- Shadows.
- Sharpen/clarity when technically valid.
- Blur.
- Pixelate.
- Mirror effect.
- A small original set of preset filters with neutral names.

Requirements:

- Real-time preview uses an efficient resolution.
- Full-quality effects are applied during export.
- Slider changes are debounced/grouped for history.
- Every tool has reset and numerical default.
- Filter strength is adjustable.
- Android/iOS output should be visually close; document unavoidable rendering differences.
- Add golden/reference image tests where maintainable.

## Milestone 4: Photo layers

Implement:

- Text.
- Stickers/images.
- Basic shapes.
- Freehand drawing.
- Layer selection and bounding boxes.
- Translate, scale, rotate, duplicate, reorder, lock, hide, and delete.
- Undo/redo for every committed operation.
- Consumer asset provider interface.
- Font registration interface with licensing left to the consumer.

Sanitize asset names and validate all local/remote URIs. Cache downloaded assets with size/time limits and cancellation.

## Milestone 5: Single-video editor

Implement:

- Native video playback.
- Play/pause and seeking.
- Accurate duration and current-time display.
- Thumbnail-strip generation off the main thread.
- Trim start/end with minimum-duration validation.
- Mute and volume.
- Crop, rotate, flip, and aspect ratio.
- Cover-frame selection.
- H.264/AAC MP4 export as the baseline.
- Export resolution/quality/bitrate/frame-rate options.
- Progress, cancellation, cleanup, and typed errors.
- Preserve audio/video synchronization.
- Handle source rotation metadata.

Use Media3 on Android and AVFoundation on iOS unless the technology decision record finds a verified blocker.

Acceptance criteria:

- Exported duration matches trim selection within a documented tolerance.
- No black frame is unintentionally inserted at the beginning or end.
- Audio remains synchronized.
- Muted export contains no audible original audio.
- Progress does not exceed 1.
- Cancellation removes incomplete output.
- App stays responsive during export.

## Milestone 6: Video overlays and effects

Implement timed:

- Text overlays.
- Stickers/images/shapes.
- Filters and adjustments.
- Blur/effects where the native pipeline supports them reliably.
- Playback speed.
- Overlay start/end ranges.
- Keyframes only as a separately approved advanced feature.

Ensure preview and export share the same project interpretation to prevent visual differences.

## Milestone 7: Multi-clip timeline

Implement a scalable timeline model and UI:

- Multiple video/image clips.
- Trim, split, duplicate, delete, and reorder.
- Overlay tracks.
- Audio tracks.
- Playhead and snapping.
- Timeline zoom.
- Thumbnail/waveform caching.
- Selection-aware contextual tools.
- Consistent total duration calculation.

Do not render every thumbnail at once. Virtualize timeline content and cache bounded results.

Add transitions only after gapless multi-clip export is stable.

## Milestone 8: Audio

Implement:

- Import local audio.
- Consumer-provided audio asset library.
- Position/trim audio in timeline.
- Per-track volume.
- Fade-in/fade-out.
- Original audio mixing.
- Waveform preview.
- Duration boundaries and looping policy.

Never ship copyrighted music. The package provides APIs and demo-safe original/public-domain assets only.

## Milestone 9: Templates, drafts, and assets

Implement our own template/project system:

- Create a project from a versioned JSON template.
- Text variables/placeholders.
- Media placeholders.
- Canvas/aspect-ratio presets.
- Consumer asset providers.
- Save editable draft.
- Load/edit draft.
- Optional archive format containing project JSON and owned assets.
- Clear ownership rules for copied, linked, cached, and temporary assets.

Validate untrusted project files, block path traversal, enforce asset limits, and reject unsupported schema versions gracefully.

## File, permission, and privacy requirements

- Editing is local by default.
- Do not upload user media.
- Request only permissions required by the action the user initiated.
- Prefer system pickers that minimize broad library permission needs.
- Saving to gallery must be opt-in.
- Use app cache for transient files and app documents/support storage for drafts.
- Track every temporary artifact created by a request.
- Clean incomplete outputs after failure/cancellation.
- Provide a documented cache-cleaning API in a later milestone.
- Never log raw media content, full sensitive paths, or user-entered text in production.

## Performance budgets and profiling

Define measurable budgets in `docs/performance.md` and measure on representative mid-range physical devices.

At minimum track:

- Editor open time.
- First preview frame time.
- UI frame responsiveness during gestures.
- Peak memory for large photos.
- Thumbnail-generation time.
- Export speed relative to media duration.
- Output file size and visual quality.
- Thermal/battery behavior for long exports.
- Cache growth.

Use platform profilers before introducing complex GPU or caching layers. Never optimize solely from assumptions.

## Error contract

Keep stable cross-platform codes, including:

```text
E_INVALID_OPTIONS
E_INVALID_URI
E_SOURCE_NOT_FOUND
E_SOURCE_UNREADABLE
E_UNSUPPORTED_MEDIA_TYPE
E_UNSUPPORTED_FORMAT
E_PERMISSION_DENIED
E_EDITOR_ALREADY_OPEN
E_EDITOR_UNAVAILABLE
E_EDITOR_CANCELLED
E_EXPORT_IN_PROGRESS
E_EXPORT_CANCELLED
E_EXPORT_FAILED
E_INSUFFICIENT_STORAGE
E_OUT_OF_MEMORY
E_CODEC_UNAVAILABLE
E_PROJECT_INVALID
E_PROJECT_VERSION_UNSUPPORTED
E_INTERNAL
```

Every error should include a safe human-readable message and optional structured details. Map native exceptions internally; do not expose unstable exception class names as the public contract.

## Testing strategy

### TypeScript

- Public option validation.
- Defaults.
- Native DTO conversion.
- Result and error normalization.
- Event subscription/removal.
- Backward compatibility.

### Android

- Kotlin model/validation tests.
- Project serialization tests.
- Command undo/redo tests.
- Transform math tests.
- Instrumented Activity/editor launch tests.
- Export tests on representative media.
- Cancellation and cleanup tests.

### iOS

- Swift model/validation tests.
- Project Codable tests.
- Command history tests.
- Transform math tests.
- ViewController presentation tests.
- AVFoundation/Core Image export tests.
- Cancellation and cleanup tests.

### Shared fixture matrix

Test:

- Small and very large photos.
- JPEG, PNG, WebP where supported.
- Transparent images.
- EXIF-rotated photos.
- Short/long videos.
- Portrait/landscape/square video.
- Videos with and without audio.
- H.264 and supported HEVC inputs.
- Variable frame rate.
- Unicode/RTL text.
- Corrupt and missing sources.
- Low storage.
- Repeated open/close.
- Background/foreground transitions.
- Process/activity recreation where applicable.

Never commit third-party copyrighted test media without permission. Use generated, original, or appropriately licensed fixtures with attribution.

## Example application requirements

Expand the existing example into a manual QA harness with:

- Pick Photo.
- Pick Video.
- Launch Photo Editor.
- Launch Video Editor.
- Feature-toggle screen.
- Theme customization example.
- Export settings example.
- Progress display.
- Cancel export.
- Result preview.
- File metadata display.
- Share/save action where permissions allow.
- Error simulator for invalid/missing input.
- Draft save/load when implemented.
- Asset-provider example when implemented.

The example must import only from `react-native-photo-video-editor`, never private source paths.

## Documentation requirements

Continuously update:

- `README.md`: honest current capabilities and usage.
- `docs/api.md`: public contract and defaults.
- `docs/architecture.md`: engine and UI architecture.
- `docs/technology-decisions.md`: native framework/dependency decisions.
- `docs/photo-editor.md`: photo tools and examples.
- `docs/video-editor.md`: video tools and examples.
- `docs/customization.md`: features, theme, toolbar, assets, localization.
- `docs/formats.md`: tested import/export matrix, not aspirational support.
- `docs/performance.md`: benchmarks and memory strategy.
- `docs/permissions.md`: consumer configuration by platform.
- `docs/roadmap.md`: milestone status.
- `CHANGELOG.md`: user-visible additions and breaking changes.

Do not claim format or feature support until it has automated or documented physical-device verification.

## Validation after every milestone

Run the project-supported equivalents of:

```bash
yarn typecheck
yarn lint
yarn test
yarn prepare
npm pack --dry-run
```

Run Codegen and Android build after native contract changes. On macOS, install pods and run the iOS build after Swift, podspec, or Codegen changes.

Verify:

- No stale generated symbols.
- No mismatched module names.
- No accidental legacy bridge fallback.
- No unbounded event emission.
- No swallowed errors.
- No `any`, `@ts-ignore`, disabled checks, or force unwraps without justification.
- No source-media mutation.
- No incomplete export left after failure/cancellation.
- Correct package contents.
- Example app uses the workspace package.

## Completion report for each coding session

Return:

1. Exact milestone and features completed.
2. Relevant files created/changed.
3. Architecture decisions and tradeoffs.
4. Public API changes.
5. Test/build commands and actual results.
6. Android verification status.
7. iOS verification status; say `not tested` when not built on macOS.
8. Performance or memory measurements collected.
9. Known limitations and unfinished work.
10. Next smallest milestone.

Use this verification table:

| Check | Result | Evidence |
| --- | --- | --- |
| TypeScript | Pass/Fail/Not run | Command/result |
| ESLint | Pass/Fail/Not run | Command/result |
| Jest | Pass/Fail/Not run | Command/result |
| Codegen | Pass/Fail/Not run | Command/result |
| Package build | Pass/Fail/Not run | Command/result |
| Android unit tests | Pass/Fail/Not run | Command/result |
| Android example | Pass/Fail/Not run | Emulator/device |
| iOS unit tests | Pass/Fail/Not run | Command/result |
| iOS example | Pass/Fail/Not run | Simulator/device |
| Package dry run | Pass/Fail/Not run | Contents summary |

Never report planned, mocked, or visually assumed behavior as implemented. Never mark a build passed unless the build command completed successfully.

## Start now

Begin by inspecting and validating the current repository. Then implement **Milestone 1: Native editor shell** completely. Keep the package buildable, update the example and documentation, run all applicable verification, and provide the required evidence-based completion report. Do not begin Milestone 2 until Milestone 1 passes its acceptance criteria.

