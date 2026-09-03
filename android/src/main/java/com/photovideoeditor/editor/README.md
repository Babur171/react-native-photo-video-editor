# Planned Android editor

Milestone one contains no editing engine. A future full-screen editor will use Media3 Player for preview, Media3 Transformer for supported video transformations and export, Canvas/Bitmap for basic photo composition, and coroutines for asynchronous work. OpenGL ES or a deliberate GPU abstraction will be introduced only if measured real-time filter requirements justify it.

`content://` inputs will be read through `ContentResolver`. Cache and output files will be streamed and lifecycle-managed without loading entire videos into memory. Gallery permissions will only be requested by a consuming app when gallery saving is explicitly enabled.
