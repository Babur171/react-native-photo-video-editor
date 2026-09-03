# API

`openEditor(options)` validates defaults, invokes native code, and returns `Promise<EditorResult>`. In milestone one it returns the source URI/type and `cancelled: false`; it does not present UI or export. `cancelExport(jobId?)` currently returns `false`. `isAvailable()` synchronously checks native linkage. `addExportProgressListener(listener)` returns `{ remove() }`; no fake events are emitted.

Defaults enable all feature flags, set quality to `high`, preserve metadata, and disable gallery saving. For photos, `trim` and `mute` normalize to `false`. Photo/video format mismatches and unknown enum values are rejected.

Dimensions use pixels, duration milliseconds, file size bytes, bitrate bits/second, frame rate frames/second, and progress ranges from 0 to 1. Public types and option-level JSDoc live in `src/types.ts`.

Errors are `PhotoVideoEditorError`: `E_INVALID_OPTIONS`, `E_INVALID_URI`, `E_UNSUPPORTED_MEDIA_TYPE`, `E_EDITOR_UNAVAILABLE`, `E_EDITOR_CANCELLED`, `E_EXPORT_FAILED`, `E_PERMISSION_DENIED`, or `E_INTERNAL`. Android and iOS use the same keys and codes.
