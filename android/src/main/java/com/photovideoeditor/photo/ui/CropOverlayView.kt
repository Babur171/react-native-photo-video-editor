package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Draws a draggable/resizable crop rectangle over [imageBounds] (the on-screen
 * rect of the preview image, in this view's own coordinate space) and reports
 * the crop edges back normalized to that rect via [onCropChanged].
 */
class CropOverlayView(context: Context) : View(context) {
  private var imageBounds = RectF()
  private var cropRect = RectF()
  private var aspectRatio: Float? = null
  var onCropChanged: ((left: Float, top: Float, right: Float, bottom: Float) -> Unit)? = null

  private val dimPaint = Paint().apply { color = Color.argb(153, 0, 0, 0) }
  private val borderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f }
  private val gridPaint = Paint().apply { color = Color.argb(120, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = 1f }
  private val handlePaint = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }

  private val handleTouchRadius = 56f
  private val minSizePx = 96f
  private var activeHandle = Handle.NONE
  private var lastX = 0f
  private var lastY = 0f

  private enum class Handle { NONE, MOVE, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

  /** Sets the on-screen image rect this overlay crops against. Resets the crop to full-frame when [resetCrop] is true. */
  fun setImageBounds(bounds: RectF, resetCrop: Boolean) {
    imageBounds = RectF(bounds)
    if (resetCrop || cropRect.width() <= 0f || cropRect.height() <= 0f) {
      cropRect = RectF(imageBounds)
      reportCrop()
    }
    invalidate()
  }

  /** Restores a normalized crop rectangle, used when reopening or cancelling crop mode. */
  fun setCrop(left: Float, top: Float, right: Float, bottom: Float) {
    if (imageBounds.width() <= 0 || imageBounds.height() <= 0) return
    cropRect = RectF(
      imageBounds.left + left * imageBounds.width(),
      imageBounds.top + top * imageBounds.height(),
      imageBounds.left + right * imageBounds.width(),
      imageBounds.top + bottom * imageBounds.height()
    )
    clampToImageBounds(); invalidate()
  }

  fun setAspectRatio(ratio: Float?) {
    aspectRatio = ratio
    if (ratio != null && imageBounds.width() > 0 && imageBounds.height() > 0) {
      val centerX = cropRect.centerX()
      val centerY = cropRect.centerY()
      var width = cropRect.width()
      var height = width / ratio
      if (height > imageBounds.height()) {
        height = cropRect.height()
        width = height * ratio
      }
      cropRect = RectF(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
      clampToImageBounds()
      invalidate()
    }
    reportCrop()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (imageBounds.width() <= 0 || imageBounds.height() <= 0) return
    canvas.drawRect(imageBounds.left, imageBounds.top, imageBounds.right, cropRect.top, dimPaint)
    canvas.drawRect(imageBounds.left, cropRect.bottom, imageBounds.right, imageBounds.bottom, dimPaint)
    canvas.drawRect(imageBounds.left, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
    canvas.drawRect(cropRect.right, cropRect.top, imageBounds.right, cropRect.bottom, dimPaint)
    canvas.drawRect(cropRect, borderPaint)
    val thirdWidth = cropRect.width() / 3
    val thirdHeight = cropRect.height() / 3
    for (i in 1..2) {
      canvas.drawLine(cropRect.left + thirdWidth * i, cropRect.top, cropRect.left + thirdWidth * i, cropRect.bottom, gridPaint)
      canvas.drawLine(cropRect.left, cropRect.top + thirdHeight * i, cropRect.right, cropRect.top + thirdHeight * i, gridPaint)
    }
    listOf(
      cropRect.left to cropRect.top,
      cropRect.centerX() to cropRect.top,
      cropRect.right to cropRect.top,
      cropRect.right to cropRect.centerY(),
      cropRect.right to cropRect.bottom,
      cropRect.centerX() to cropRect.bottom,
      cropRect.left to cropRect.bottom,
      cropRect.left to cropRect.centerY()
    ).forEach { (x, y) -> canvas.drawCircle(x, y, 8f, handlePaint) }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        activeHandle = hitTest(event.x, event.y)
        lastX = event.x
        lastY = event.y
        return activeHandle != Handle.NONE
      }
      MotionEvent.ACTION_MOVE -> {
        if (activeHandle == Handle.NONE) return false
        val dx = event.x - lastX
        val dy = event.y - lastY
        lastX = event.x
        lastY = event.y
        applyDrag(activeHandle, dx, dy)
        invalidate()
        reportCrop()
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val handled = activeHandle != Handle.NONE
        activeHandle = Handle.NONE
        return handled
      }
    }
    return super.onTouchEvent(event)
  }

  private fun hitTest(x: Float, y: Float): Handle {
    fun near(px: Float, py: Float) = abs(x - px) < handleTouchRadius && abs(y - py) < handleTouchRadius
    return when {
      near(cropRect.left, cropRect.top) -> Handle.TOP_LEFT
      near(cropRect.right, cropRect.top) -> Handle.TOP_RIGHT
      near(cropRect.left, cropRect.bottom) -> Handle.BOTTOM_LEFT
      near(cropRect.right, cropRect.bottom) -> Handle.BOTTOM_RIGHT
      near(cropRect.centerX(), cropRect.top) -> Handle.TOP
      near(cropRect.right, cropRect.centerY()) -> Handle.RIGHT
      near(cropRect.centerX(), cropRect.bottom) -> Handle.BOTTOM
      near(cropRect.left, cropRect.centerY()) -> Handle.LEFT
      cropRect.contains(x, y) -> Handle.MOVE
      else -> Handle.NONE
    }
  }

  private fun applyDrag(handle: Handle, dx: Float, dy: Float) {
    val proposed = RectF(cropRect)
    when (handle) {
      Handle.MOVE -> proposed.offset(dx, dy)
      Handle.TOP_LEFT -> { proposed.left += dx; proposed.top += dy }
      Handle.TOP_RIGHT -> { proposed.right += dx; proposed.top += dy }
      Handle.BOTTOM_LEFT -> { proposed.left += dx; proposed.bottom += dy }
      Handle.BOTTOM_RIGHT -> { proposed.right += dx; proposed.bottom += dy }
      Handle.TOP -> proposed.top += dy
      Handle.RIGHT -> proposed.right += dx
      Handle.BOTTOM -> proposed.bottom += dy
      Handle.LEFT -> proposed.left += dx
      Handle.NONE -> return
    }
    if (proposed.width() < minSizePx || proposed.height() < minSizePx) return
    aspectRatio?.let { ratio ->
      when (handle) {
        Handle.TOP_LEFT, Handle.BOTTOM_RIGHT -> proposed.bottom = proposed.top + proposed.width() / ratio
        Handle.TOP_RIGHT -> proposed.top = proposed.bottom - proposed.width() / ratio
        Handle.BOTTOM_LEFT -> proposed.bottom = proposed.top + proposed.width() / ratio
        Handle.LEFT, Handle.RIGHT -> {
          val centerY = cropRect.centerY(); val height = proposed.width() / ratio
          proposed.top = centerY - height / 2; proposed.bottom = centerY + height / 2
        }
        Handle.TOP, Handle.BOTTOM -> {
          val centerX = cropRect.centerX(); val width = proposed.height() * ratio
          proposed.left = centerX - width / 2; proposed.right = centerX + width / 2
        }
        else -> {}
      }
    }
    cropRect = proposed
    clampToImageBounds()
  }

  private fun clampToImageBounds() {
    if (cropRect.left < imageBounds.left) cropRect.offset(imageBounds.left - cropRect.left, 0f)
    if (cropRect.top < imageBounds.top) cropRect.offset(0f, imageBounds.top - cropRect.top)
    if (cropRect.right > imageBounds.right) cropRect.offset(imageBounds.right - cropRect.right, 0f)
    if (cropRect.bottom > imageBounds.bottom) cropRect.offset(0f, imageBounds.bottom - cropRect.bottom)
    cropRect.left = cropRect.left.coerceAtLeast(imageBounds.left)
    cropRect.top = cropRect.top.coerceAtLeast(imageBounds.top)
    cropRect.right = cropRect.right.coerceAtMost(imageBounds.right)
    cropRect.bottom = cropRect.bottom.coerceAtMost(imageBounds.bottom)
  }

  private fun reportCrop() {
    if (imageBounds.width() <= 0 || imageBounds.height() <= 0) return
    val left = (cropRect.left - imageBounds.left) / imageBounds.width()
    val top = (cropRect.top - imageBounds.top) / imageBounds.height()
    val right = (cropRect.right - imageBounds.left) / imageBounds.width()
    val bottom = (cropRect.bottom - imageBounds.top) / imageBounds.height()
    onCropChanged?.invoke(left, top, right, bottom)
  }
}
