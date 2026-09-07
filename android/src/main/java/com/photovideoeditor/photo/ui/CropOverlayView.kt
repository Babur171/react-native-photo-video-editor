package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.photovideoeditor.photo.render.CropGeometry
import com.photovideoeditor.photo.render.PhotoTransformState
import kotlin.math.abs
import kotlin.math.hypot

/** Crop handles own resize gestures; interior drags/pinches move the media behind a fixed box. */
class CropOverlayView(context: Context) : View(context) {
  private var imageBounds = RectF()
  private var fittedBounds = RectF()
  private var cropRect = RectF()
  private var aspectRatio: Float? = null
  var onCropChanged: ((Float, Float, Float, Float) -> Unit)? = null
  var onMediaBoundsChanged: ((RectF) -> Unit)? = null
  var onViewportChanged: ((Float, Float, Float) -> Unit)? = null
  private val density = resources.displayMetrics.density
  private val dimPaint = Paint().apply { color = Color.argb(165, 0, 0, 0) }
  private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = density }
  private val gridPaint = Paint().apply { color = Color.argb(85, 255, 255, 255); strokeWidth = density * .5f }
  private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 3 * density; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
  private var activeHandle: CropGeometry.Handle? = null
  private var lastX = 0f; private var lastY = 0f; private var lastDistance = 0f
  private var movingMedia = false

  fun restore(bounds: RectF, state: PhotoTransformState) {
    if (bounds.width() <= 0 || bounds.height() <= 0) return
    fittedBounds = RectF(bounds)
    val zoom = state.zoom.coerceIn(1f, 8f)
    val cx = bounds.centerX() + state.panX * bounds.width()
    val cy = bounds.centerY() + state.panY * bounds.height()
    imageBounds = RectF(cx - bounds.width() * zoom / 2, cy - bounds.height() * zoom / 2,
      cx + bounds.width() * zoom / 2, cy + bounds.height() * zoom / 2)
    aspectRatio = state.aspectRatio
    setCrop(state.cropLeft, state.cropTop, state.cropRight, state.cropBottom)
    onMediaBoundsChanged?.invoke(RectF(imageBounds))
    invalidate()
  }

  fun setImageBounds(bounds: RectF, resetCrop: Boolean) {
    val old = normalized()
    imageBounds = RectF(bounds); fittedBounds = RectF(bounds)
    if (resetCrop || cropRect.isEmpty) cropRect = RectF(bounds) else setCrop(old[0], old[1], old[2], old[3])
    invalidate()
  }

  fun setCrop(left: Float, top: Float, right: Float, bottom: Float) {
    cropRect = RectF(imageBounds.left + left * imageBounds.width(), imageBounds.top + top * imageBounds.height(),
      imageBounds.left + right * imageBounds.width(), imageBounds.top + bottom * imageBounds.height())
    invalidate()
  }

  fun setAspectRatio(ratio: Float?) {
    aspectRatio = ratio
    // A new preset starts with the largest centered rectangle in the visible media.
    val available = RectF(imageBounds)
    available.intersect(0f, 0f, width.toFloat(), height.toFloat())
    cropRect = CropGeometry.fit(available.geometry(), ratio).native()
    reportCrop(); invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    if (cropRect.isEmpty) return
    canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
    canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
    canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
    canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)
    canvas.drawRect(cropRect, borderPaint)
    for (i in 1..2) {
      val x = cropRect.left + cropRect.width() * i / 3; val y = cropRect.top + cropRect.height() * i / 3
      canvas.drawLine(x, cropRect.top, x, cropRect.bottom, gridPaint)
      canvas.drawLine(cropRect.left, y, cropRect.right, y, gridPaint)
    }
    val corner = 14 * density
    for ((x, y) in listOf(cropRect.left to cropRect.top, cropRect.right to cropRect.top, cropRect.left to cropRect.bottom, cropRect.right to cropRect.bottom)) {
      canvas.drawLine(x, y, x + if (x == cropRect.left) corner else -corner, y, handlePaint)
      canvas.drawLine(x, y, x, y + if (y == cropRect.top) corner else -corner, handlePaint)
    }
    val edge = 8 * density
    for (y in listOf(cropRect.top, cropRect.bottom)) canvas.drawLine(cropRect.centerX() - edge, y, cropRect.centerX() + edge, y, handlePaint)
    for (x in listOf(cropRect.left, cropRect.right)) canvas.drawLine(x, cropRect.centerY() - edge, x, cropRect.centerY() + edge, handlePaint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (imageBounds.isEmpty) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        activeHandle = hitTest(event.x, event.y); movingMedia = activeHandle == null
        lastX = event.x; lastY = event.y; lastDistance = 0f
        parent?.requestDisallowInterceptTouchEvent(true)
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        activeHandle = null; movingMedia = true
        lastX = (event.getX(0) + event.getX(1)) / 2; lastY = (event.getY(0) + event.getY(1)) / 2
        lastDistance = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
      }
      MotionEvent.ACTION_MOVE -> {
        val multi = event.pointerCount > 1
        val x = if (multi) (event.getX(0) + event.getX(1)) / 2 else event.x
        val y = if (multi) (event.getY(0) + event.getY(1)) / 2 else event.y
        val distance = if (multi) hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1)) else 0f
        val handle = activeHandle
        if (handle != null) {
          val valid = RectF(imageBounds).apply { intersect(0f, 0f, width.toFloat(), height.toFloat()) }
          cropRect = CropGeometry.resize(cropRect.geometry(), valid.geometry(), handle, x - lastX, y - lastY, aspectRatio, 48 * density).native()
        } else if (movingMedia) {
          moveMedia(x - lastX, y - lastY, if (lastDistance > 0 && distance > 0) distance / lastDistance else 1f, x, y)
        }
        lastX = x; lastY = y; lastDistance = distance
        reportCrop(); invalidate()
      }
      MotionEvent.ACTION_POINTER_UP -> {
        val index = if (event.actionIndex == 0) 1 else 0
        lastX = event.getX(index); lastY = event.getY(index); lastDistance = 0f
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        activeHandle = null; movingMedia = false; lastDistance = 0f
        parent?.requestDisallowInterceptTouchEvent(false)
      }
    }
    return true
  }

  private fun moveMedia(dx: Float, dy: Float, factor: Float, focusX: Float, focusY: Float) {
    val minimumZoom = maxOf(1f, cropRect.width() / fittedBounds.width(), cropRect.height() / fittedBounds.height())
    val zoom = imageBounds.width() / fittedBounds.width()
    val scale = (zoom * factor).coerceIn(minimumZoom, maxOf(8f, minimumZoom)) / zoom
    val w = imageBounds.width() * scale; val h = imageBounds.height() * scale
    val left = (focusX + (imageBounds.left - focusX) * scale + dx).coerceIn(cropRect.right - w, cropRect.left)
    val top = (focusY + (imageBounds.top - focusY) * scale + dy).coerceIn(cropRect.bottom - h, cropRect.top)
    imageBounds = RectF(left, top, left + w, top + h)
    onMediaBoundsChanged?.invoke(RectF(imageBounds))
    onViewportChanged?.invoke(w / fittedBounds.width(), (imageBounds.centerX() - fittedBounds.centerX()) / fittedBounds.width(), (imageBounds.centerY() - fittedBounds.centerY()) / fittedBounds.height())
  }

  private fun hitTest(x: Float, y: Float): CropGeometry.Handle? {
    val points = listOf(
      Triple(cropRect.left, cropRect.top, CropGeometry.Handle.TOP_LEFT), Triple(cropRect.right, cropRect.top, CropGeometry.Handle.TOP_RIGHT),
      Triple(cropRect.left, cropRect.bottom, CropGeometry.Handle.BOTTOM_LEFT), Triple(cropRect.right, cropRect.bottom, CropGeometry.Handle.BOTTOM_RIGHT),
      Triple(cropRect.centerX(), cropRect.top, CropGeometry.Handle.TOP), Triple(cropRect.right, cropRect.centerY(), CropGeometry.Handle.RIGHT),
      Triple(cropRect.centerX(), cropRect.bottom, CropGeometry.Handle.BOTTOM), Triple(cropRect.left, cropRect.centerY(), CropGeometry.Handle.LEFT))
    val nearest = points.minByOrNull { hypot(x - it.first, y - it.second) } ?: return null
    return nearest.third.takeIf { hypot(x - nearest.first, y - nearest.second) <= 24 * density }
  }
  private fun normalized(): FloatArray = if (imageBounds.isEmpty) floatArrayOf(0f, 0f, 1f, 1f) else floatArrayOf(
    (cropRect.left - imageBounds.left) / imageBounds.width(), (cropRect.top - imageBounds.top) / imageBounds.height(),
    (cropRect.right - imageBounds.left) / imageBounds.width(), (cropRect.bottom - imageBounds.top) / imageBounds.height())
  private fun reportCrop() { val r = normalized(); onCropChanged?.invoke(r[0].coerceIn(0f, 1f), r[1].coerceIn(0f, 1f), r[2].coerceIn(0f, 1f), r[3].coerceIn(0f, 1f)) }
  private fun RectF.geometry() = CropGeometry.Rect(left, top, right, bottom)
  private fun CropGeometry.Rect.native() = RectF(left, top, right, bottom)
}
