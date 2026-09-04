package com.photovideoeditor.photo.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.photovideoeditor.photo.render.PhotoAdjustmentRenderer
import com.photovideoeditor.photo.render.PhotoAdjustments
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Loads filter-card previews serially without blocking layout or touch input. */
class FilterThumbnailLoader {
  private val executor = Executors.newSingleThreadExecutor { task ->
    Thread(task, "filter-thumbnail-loader").apply { priority = Thread.NORM_PRIORITY - 1 }
  }
  private val mainHandler = Handler(Looper.getMainLooper())
  private val closed = AtomicBoolean(false)
  private val cache = mutableMapOf<String, Bitmap>()

  fun load(source: Bitmap?, presetId: String, sizePx: Int, deliver: (Bitmap?) -> Unit) {
    if (source == null || closed.get()) {
      deliver(null)
      return
    }
    synchronized(cache) { cache[presetId] }?.let {
      deliver(it)
      return
    }
    // Capture a tiny independent input while the session bitmap is guaranteed alive.
    val square = Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
    executor.execute {
      if (closed.get()) {
        if (square !== source && !square.isRecycled) square.recycle()
        return@execute
      }
      val rendered = PhotoAdjustmentRenderer.apply(
        square,
        PhotoAdjustments(filterPreset = presetId, filterStrength = 100f),
      )
      if (rendered !== square && !square.isRecycled) square.recycle()
      synchronized(cache) { cache[presetId] = rendered }
      mainHandler.post { if (!closed.get()) deliver(rendered) }
    }
  }

  fun close() {
    if (!closed.compareAndSet(false, true)) return
    mainHandler.removeCallbacksAndMessages(null)
    executor.execute {
      synchronized(cache) {
        cache.values.distinct().forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
      }
    }
    executor.shutdown()
  }
}
