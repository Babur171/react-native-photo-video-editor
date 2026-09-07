# Customization

Pass `features` to control which tool entries appear. Photo-only normalization hides trim.

`theme` is applied by both native shells in Milestone 1:

- `backgroundColor`, `toolbarColor`, `textColor`, `primaryColor` accept `#RRGGBB`/`#AARRGGBB` hex strings. Android parses them with `Color.parseColor`; iOS parses them itself. An unset or malformed value falls back to the original dark shell colors, so partial themes are safe.
- `statusBarStyle` (`'light' | 'dark'`) sets the status bar content color (Android `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR`, iOS `preferredStatusBarStyle`).

Toolbar ordering/grouping beyond the fixed tool list is not yet implemented. The crop aspect-ratio preset list (Free, 1:1, 4:5, 3:4, 9:16, 16:9) and the filter/adjustment list are currently fixed natively and not yet configurable from the TypeScript API — a documented gap for a later milestone.

## Selected-state feedback

Every mode-based tool button (Crop, Adjust, Filters, Text on photo; Crop on video) and every chip inside a tool's sub-bar (crop/video aspect presets, filter adjustment/preset chips, layer property chips) highlights itself — `primaryColor` text on a low-alpha `primaryColor` background — while active, so it's clear at a glance which one is selected. This uses `theme.primaryColor` (or the default purple) rather than a separate configurable color.

## Consumer assets (Milestone 4)

- `fonts?: { family: string; uri: string }[]` — accepted and forwarded to the native layer, but **not yet wired to load the font file**. Text layers only resolve `fontFamily` against system-installed font names today (Android `Typeface.create`, iOS `UIFont(name:)`). Loading the actual file (`Typeface.createFromFile` / `CTFontManagerRegisterFontsForURL`) is a planned follow-up, not silently dropped — see `docs/photo-editor.md`.
