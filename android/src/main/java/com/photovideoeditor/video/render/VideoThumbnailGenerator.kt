package com.photovideoeditor.video.render

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File

/**
 * Extracts a fixed number of evenly spaced thumbnails for the trim strip.
 * Must be called from a background thread — decoding is not cheap.
 */
object VideoThumbnailGenerator {
  fun generate(context: Context, sourceUri: String, durationMs: Long, count: Int = 12): List<Bitmap> {
    if (durationMs <= 0) return emptyList()
    val retriever = MediaMetadataRetriever()
    return try {
      val parsed = Uri.parse(sourceUri)
      retriever.setDataSource(context, if (parsed.scheme == null) Uri.fromFile(File(sourceUri)) else parsed)
      val step = durationMs / count
      (0 until count).mapNotNull { index ->
        val timeUs = (index * step + step / 2) * 1000
        retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      }
    } catch (_: Exception) {
      emptyList()
    } finally {
      retriever.release()
    }
  }
}
