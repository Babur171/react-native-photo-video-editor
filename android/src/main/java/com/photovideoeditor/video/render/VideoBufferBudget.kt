package com.photovideoeditor.video.render

/** Leave heap space for the host React Native app, decoded frames, and editor bitmaps. */
internal object VideoBufferBudget {
  private const val MIB = 1024L * 1024L

  // A target rather than a hard heap cap: Media3 can finish a sample after reaching it.
  fun targetBytes(maxHeapBytes: Long): Int =
    (maxHeapBytes / 16).coerceIn(2 * MIB, 16 * MIB).toInt()
}
