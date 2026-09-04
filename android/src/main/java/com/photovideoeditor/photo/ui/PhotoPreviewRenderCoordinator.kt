package com.photovideoeditor.photo.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the expensive photo pipeline away from the main thread and collapses a
 * burst of slider/drag updates into the newest requested frame.
 */
class PhotoPreviewRenderCoordinator(
  private val render: () -> Bitmap?,
  private val display: (Bitmap?) -> Unit,
) {
  private val executor = Executors.newSingleThreadExecutor { task ->
    Thread(task, "photo-preview-renderer").apply { priority = Thread.NORM_PRIORITY - 1 }
  }
  private val mainHandler = Handler(Looper.getMainLooper())
  private val rendering = AtomicBoolean(false)
  private val dirty = AtomicBoolean(false)
  private val closed = AtomicBoolean(false)

  fun request() {
    if (closed.get()) return
    dirty.set(true)
    startIfIdle()
  }

  private fun startIfIdle() {
    if (!rendering.compareAndSet(false, true)) return
    executor.execute {
      try {
        do {
          dirty.set(false)
          val bitmap = if (closed.get()) null else render()
          if (!closed.get()) mainHandler.post {
            if (!closed.get()) display(bitmap)
          }
        } while (dirty.get() && !closed.get())
      } finally {
        rendering.set(false)
        if (dirty.get() && !closed.get()) startIfIdle()
      }
    }
  }

  /** Runs cleanup after any in-flight render, preventing bitmap recycle races. */
  fun close(onRendererDrained: () -> Unit) {
    if (!closed.compareAndSet(false, true)) return
    dirty.set(false)
    mainHandler.removeCallbacksAndMessages(null)
    executor.execute(onRendererDrained)
    executor.shutdown()
  }
}
