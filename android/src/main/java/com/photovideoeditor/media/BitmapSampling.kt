package com.photovideoeditor.media

/** Pure sizing policy shared by previews and overlay decoding. */
object BitmapSampling {
  fun inSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    if (width <= 0 || height <= 0 || maxEdge <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
  }
}
