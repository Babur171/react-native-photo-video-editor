# Photo editor

## Milestone 2 — transform and export

- **Crop** — dedicated ZLImageEditor-inspired crop mode with a dimmed mask, rule-of-thirds grid, four corner and four edge handles, movable crop frame, and aspect presets (Free, 1:1, 4:5, 3:4, 9:16, 16:9). **Cancel** restores the exact pre-crop state; **Done** commits the crop.
- **Rotate** — rotates 90° per tap. Resets the current crop (a rotated image has a different frame of reference for the crop rectangle).
- **Flip** — cycles none → horizontal → vertical → none.
- **Straighten** — a fine -45°...45° slider, shown while crop mode is active.
- **Reset** (crop sub-bar) — restores crop, rotation, straighten, and flip to identity.
- **Pan/pinch-to-zoom** — available on the general preview outside crop/draw mode.

## Milestone 3 — adjustments and filters

Tap **Filters** to enter adjustment mode: a horizontal chip row selects which control the slider drives.

- **Adjustments**: Brightness, Contrast, Saturation, Exposure, Gamma, Temperature, Tint, Highlights, Shadows, Sharpen, Blur, Pixelate.
- **Mirror** toggle, plus 5 original preset filters (Mono, Noir, Fade, Chrome, Warmth) with a strength slider.
- **Reset** (filters sub-bar) — restores all adjustments to their zero/no-op defaults, independent of crop/transform/layers.
- Same pipeline and formulas applied in preview and export on both platforms.

## Milestone 4 — layers: text, stickers, images, drawing

- **Text** — tap **Text**, type a string in the dialog, and it's added as a centered, draggable text layer.
- **Stickers** — tap **Stickers** for the searchable, categorized sticker collection bundled with the package. No third-party sticker API or network connection is used.
- **Draw** — tap **Draw** for a freehand canvas: pick a color and thin/thick width, draw multiple strokes, undo the last stroke or clear all, then **Done** commits every stroke as its own layer (so strokes with different colors/widths keep their own settings).
- **Selection** — tap any layer on the canvas to select it (shown with a dashed bounding box) and open its mini toolbar.
- **Transform** — drag layers to move them, pinch with two fingers to scale and rotate, or use the corner handle. Scale/Rotate/Opacity sliders remain available for precise changes.
- **Duplicate / Lock / Hide / Front / Back / Delete** — per-layer actions in the mini toolbar.
- **Undo/redo** — header buttons, backed by a snapshot history (50 entries). One entry is recorded per *committed* operation — add/delete/duplicate/reorder, or a drag/slider gesture's final value — never per intermediate touch-move frame.
- Layers are burned into the image identically in preview and at export, positioned in the same full-frame coordinate space as the crop overlay (so cropping after adding layers can crop them out, matching what the crop preview would suggest).

### Known gaps in Milestone 4

- **Font registration is not wired to custom font files yet.** `EditorOptions.fonts` is accepted structurally (passed through to the native layer) but text layers currently only resolve `fontFamily` against system-installed font names (Android `Typeface.create`, iOS `UIFont(name:)`) — a consumer-provided font *file* is not loaded. This is an honest scope cut, not a silent gap: closing it means adding a font-file loader (`Typeface.createFromFile` / `CTFontManagerRegisterFontsForURL`) on each platform, planned as a small follow-up.
- No sealed per-type layer model — one flat `PhotoLayer` struct/data class covers text/sticker/image/drawing, a deliberate simplification.
- Text styling is limited to color and font size for now (no stroke/background/shadow/alignment controls yet, though the fields exist on `PhotoLayer` for a later pass).

Export applies the combined transform + adjustments + layers to the full-resolution source (never mutated) and writes JPEG or PNG (WebP on Android only; iOS falls back to JPEG). Output width/height/fileSize/mimeType are returned on the `EditorResult`.

**Verification status**: Android is compiled and built via Gradle (`:react-native-photo-video-editor:assembleDebug`, `:app:assembleDebug`) in this environment for all of Milestones 2–4; it has not been exercised on an emulator/device (none attached). iOS mirrors the same architecture and formulas but is unverified — no macOS/Xcode toolchain is available here, so all Swift code (including Milestone 4's layer system) has been carefully reviewed but never compiled. Treat the entire iOS side as implemented-but-unverified until built and tested on a Mac.
