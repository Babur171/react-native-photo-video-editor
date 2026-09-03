package com.photovideoeditor.video.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Dual-handle trim range selector drawn over a horizontal thumbnail strip.
 * `startFraction`/`endFraction` are fractions (0..1) of the video's total
 * duration; a minimum gap between them is enforced so a trim can never
 * collapse to (or past) zero length.
 */
class TrimRangeView(context: Context) : View(context) {
  var thumbnails: List<Bitmap> = emptyList()
    set(value) {
      field = value
      invalidate()
    }
  var startFraction: Float = 0f
    private set
  var endFraction: Float = 1f
    private set
  var minDurationFraction: Float = 0.02f
  var onRangeChanged: ((start: Float, end: Float) -> Unit)? = null

  private val handleWidthPx = 28f
  private var activeHandle = Handle.NONE

  private enum class Handle { NONE, START, END }

  private val dimPaint = Paint().apply { color = Color.argb(160, 0, 0, 0) }
  private val handlePaint = Paint().apply { color = Color.WHITE }
  private val borderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
  private val backgroundPaint = Paint().apply { color = Color.DKGRAY }

  fun setRange(start: Float, end: Float) {
    startFraction = start.coerceIn(0f, 1f)
    endFraction = end.coerceIn(startFraction, 1f)
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0 || h <= 0) return
    if (thumbnails.isNotEmpty()) {
      val thumbWidth = w / thumbnails.size
      thumbnails.forEachIndexed { index, bitmap ->
        canvas.drawBitmap(bitmap, null, RectF(index * thumbWidth, 0f, (index + 1) * thumbWidth, h), null)
      }
    } else {
      canvas.drawRect(0f, 0f, w, h, backgroundPaint)
    }
    val startX = startFraction * w
    val endX = endFraction * w
    canvas.drawRect(0f, 0f, startX, h, dimPaint)
    canvas.drawRect(endX, 0f, w, h, dimPaint)
    canvas.drawRect(startX, 0f, endX, h, borderPaint)
    canvas.drawRect(startX - handleWidthPx / 2, 0f, startX + handleWidthPx / 2, h, handlePaint)
    canvas.drawRect(endX - handleWidthPx / 2, 0f, endX + handleWidthPx / 2, h, handlePaint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    val w = width.toFloat()
    if (w <= 0) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val startX = startFraction * w
        val endX = endFraction * w
        activeHandle = when {
          abs(event.x - startX) < handleWidthPx -> Handle.START
          abs(event.x - endX) < handleWidthPx -> Handle.END
          else -> Handle.NONE
        }
        return activeHandle != Handle.NONE
      }
      MotionEvent.ACTION_MOVE -> {
        if (activeHandle == Handle.NONE) return false
        val fraction = (event.x / w).coerceIn(0f, 1f)
        when (activeHandle) {
          Handle.START -> startFraction = fraction.coerceAtMost(endFraction - minDurationFraction).coerceAtLeast(0f)
          Handle.END -> endFraction = fraction.coerceAtLeast(startFraction + minDurationFraction).coerceAtMost(1f)
          Handle.NONE -> return false
        }
        invalidate()
        onRangeChanged?.invoke(startFraction, endFraction)
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val handled = activeHandle != Handle.NONE
        activeHandle = Handle.NONE
        return handled
      }
    }
    return false
  }
}
