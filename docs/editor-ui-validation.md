# Native editor UI

The Kotlin and UIKit editors reuse their existing layer stacks, normalized media coordinates, renderers, and export paths. Tool selection is transient UI state; opening a palette or slider does not mutate a layer. Slider gestures commit one history snapshot, and duplication copies all layer properties including timing and drawing data.

Both editors use contextual icon rails, an explicit Color palette for text, and custom multiline text entry. Unsupported font-family and alignment controls are omitted. Existing crop, adjustment, filtering, drawing, and layer actions remain available.

New stickers start at 30% of rendered media width. Their original image aspect ratio is retained using the existing `overlayAspectRatio` field. Existing layer scales retain their meaning. Bitmap stickers now render at their source aspect ratio instead of stretching into squares. Selection controls belong to a separate view and are not part of the bitmap/video compositor.

## Device validation

The following needs device/simulator verification, particularly on iOS where UIKit cannot be built on Linux:

- Open portrait, landscape, and square photos/videos; check complete media visibility and centered placement.
- Add multiline text with the keyboard open in portrait and landscape. Check Add, Edit, Cancel, focus, and keyboard dismissal.
- Change text color, size, rotation, and opacity; switch tools; check one-step undo/redo and retained position/timing.
- Add a non-square sticker, drag, pinch, rotate, release one finger, duplicate, and delete through the canvas handle.
- Tap outside to dismiss selection; check the main toolbar returns without extra panels.
- Seek/play/pause video; verify overlay visibility at its start/end times.
- Export at original resolution and compare overlay centers, dimensions, colors, opacity, rotation, and ordering with the preview. Selection chrome must be absent.
- Check small screens, large text settings, navigation insets, VoiceOver/TalkBack, and horizontal toolbar scrolling.

Android unit coverage includes portrait/landscape/square sticker sizing, preview/export proportional geometry, layer-edit state preservation, and undo/redo. These tests do not replace rendered export comparison or keyboard/gesture checks on devices.
