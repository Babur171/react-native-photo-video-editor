package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.text.TextPaint
import com.photovideoeditor.R
import com.photovideoeditor.photo.render.OverlayGeometry
import com.photovideoeditor.photo.render.PhotoLayer
import com.photovideoeditor.photo.render.LayerType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Reusable native move/scale/rotate surface shared by every overlay type. */
class LayerOverlayView(context: Context) : View(context) {
  private enum class Gesture { NONE, DRAG, HANDLE, MULTI }
  private var imageBounds = RectF()
  var minScale = 0.2f
  var maxScale = 8f
  var layers: List<PhotoLayer> = emptyList()
    set(value) { field = value; invalidate() }
  var selectedLayerId: String? = null
    set(value) { field = value; invalidate() }
  var onLayerDelete: ((String) -> Unit)? = null
  var onLayerTapped: ((String?) -> Unit)? = null
  var onLayerDoubleTapped: ((String) -> Unit)? = null
  var onLayerTransformChanged: ((id: String, x: Float, y: Float, scale: Float, rotationDegrees: Float) -> Unit)? = null
  var onLayerTransformEnded: ((id: String) -> Unit)? = null

  private var gesture = Gesture.NONE
  private var gestureId: String? = null
  private var startLayer: PhotoLayer? = null
  private var startPointX = 0f
  private var startPointY = 0f
  private var startDistance = 1f
  private var startAngle = 0f
  private var startMidX = 0f
  private var startMidY = 0f
  private var movedDuringGesture = false
  private var lastTapTimeMs = 0L
  private var lastTapLayerId: String? = null
  private val density = resources.displayMetrics.density
  private val handleRadius = 11f * density
  private val handleTouchRadius = 30f * density
  private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1f * density
  }
  private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }


  fun setImageBounds(bounds: RectF) { imageBounds = RectF(bounds); invalidate() }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val selected = selectedLayer() ?: return
    val center = centerOf(selected)
    val half = selectionHalfExtents(selected)
    canvas.save()
    canvas.translate(center.first, center.second)
    canvas.rotate(selected.rotationDegrees)
    canvas.drawRect(-half.first, -half.second, half.first, half.second, selectionPaint)
    for ((x, y) in listOf(Pair(-half.first, -half.second), Pair(half.first, -half.second), Pair(-half.first, half.second), Pair(half.first, half.second))) {
      canvas.drawCircle(x, y, 4f * density, handlePaint)
    }
    drawControl(canvas, -half.first, -half.second, R.drawable.ic_close, Color.rgb(255, 100, 115))
    drawControl(canvas, half.first, half.second, R.drawable.ic_fullscreen, Color.rgb(167, 139, 250))
    canvas.restore()
  }

  private fun drawControl(canvas: Canvas, x: Float, y: Float, icon: Int, tint: Int) {
    canvas.drawCircle(x, y, handleRadius, handlePaint)
    context.getDrawable(icon)?.mutate()?.let { drawable ->
      drawable.setTint(tint)
      val size = (handleRadius * 0.7f).toInt()
      drawable.setBounds(x.toInt() - size, y.toInt() - size, x.toInt() + size, y.toInt() + size)
      drawable.draw(canvas)
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (imageBounds.width() <= 0 || imageBounds.height() <= 0) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val selected = selectedLayer()
        if (selected != null && !selected.locked) {
          val handle = handlePoint(selected)
          val center = centerOf(selected)
          if (hypot(event.x - (2 * center.first - handle.first), event.y - (2 * center.second - handle.second)) <= handleTouchRadius) {
            onLayerDelete?.invoke(selected.id); resetGesture(); return true
          }
        }
        val handleHit = selected?.takeIf { !it.locked && isHandleHit(it, event.x, event.y) }
        val hit = handleHit ?: hitTest(event.x, event.y)
        onLayerTapped?.invoke(hit?.id)
        if (hit == null || hit.locked) { resetGesture(); return false }
        gestureId = hit.id
        movedDuringGesture = false
        startLayer = hit.copy()
        startPointX = event.x; startPointY = event.y
        if (handleHit != null) {
          gesture = Gesture.HANDLE
          val center = centerOf(hit)
          startDistance = hypot(event.x - center.first, event.y - center.second).coerceAtLeast(1f)
          startAngle = angle(center.first, center.second, event.x, event.y)
        } else gesture = Gesture.DRAG
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        val layer = currentGestureLayer() ?: return false
        if (event.pointerCount >= 2) {
          startLayer = layer.copy()
          val metrics = multiMetrics(event)
          startDistance = metrics.distance.coerceAtLeast(1f); startAngle = metrics.angle
          startMidX = metrics.midX; startMidY = metrics.midY
          movedDuringGesture = true
          gesture = Gesture.MULTI
          return true
        }
      }
      MotionEvent.ACTION_MOVE -> {
        val initial = startLayer ?: return false
        if (hypot(event.x - startPointX, event.y - startPointY) > handleRadius) movedDuringGesture = true
        when (gesture) {
          Gesture.DRAG -> {
            val x = (initial.x + (event.x - startPointX) / imageBounds.width()).coerceIn(0f, 1f)
            val y = (initial.y + (event.y - startPointY) / imageBounds.height()).coerceIn(0f, 1f)
            emit(initial, x, y, initial.scale, initial.rotationDegrees)
          }
          Gesture.HANDLE -> {
            val center = centerOf(initial)
            val scale = (initial.scale * hypot(event.x - center.first, event.y - center.second) / startDistance).coerceIn(minScale, maxScale)
            val rotation = normalizeDegrees(initial.rotationDegrees + angleDelta(startAngle, angle(center.first, center.second, event.x, event.y)))
            emit(initial, initial.x, initial.y, scale, rotation)
          }
          Gesture.MULTI -> if (event.pointerCount >= 2) {
            val metrics = multiMetrics(event)
            val scale = (initial.scale * metrics.distance / startDistance).coerceIn(minScale, maxScale)
            val rotation = normalizeDegrees(initial.rotationDegrees + angleDelta(startAngle, metrics.angle))
            val x = (initial.x + (metrics.midX - startMidX) / imageBounds.width()).coerceIn(0f, 1f)
            val y = (initial.y + (metrics.midY - startMidY) / imageBounds.height()).coerceIn(0f, 1f)
            emit(initial, x, y, scale, rotation)
          }
          Gesture.NONE -> return false
        }
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val tappedId = gestureId
        if (event.actionMasked == MotionEvent.ACTION_UP && !movedDuringGesture && tappedId != null) {
          val now = android.os.SystemClock.uptimeMillis()
          if (lastTapLayerId == tappedId && now - lastTapTimeMs <= 350L) {
            onLayerDoubleTapped?.invoke(tappedId); lastTapLayerId = null; lastTapTimeMs = 0L
          } else { lastTapLayerId = tappedId; lastTapTimeMs = now }
        }
        finishGesture(); return true
      }
      MotionEvent.ACTION_POINTER_UP -> {
        // Rebase to the remaining finger so lifting a pinch finger does not jump/freeze the layer.
        val remaining = if (event.actionIndex == 0) 1 else 0
        if (remaining < event.pointerCount) {
          startLayer = currentGestureLayer()?.copy()
          startPointX = event.getX(remaining); startPointY = event.getY(remaining)
          gesture = Gesture.DRAG
        }
        movedDuringGesture = true
        return true
      }
    }
    return false
  }

  private fun emit(layer: PhotoLayer, x: Float, y: Float, scale: Float, rotation: Float) =
    onLayerTransformChanged?.invoke(layer.id, x, y, scale, rotation)

  private fun finishGesture() {
    gestureId?.let { onLayerTransformEnded?.invoke(it) }
    resetGesture()
    parent?.requestDisallowInterceptTouchEvent(false)
  }

  private fun resetGesture() { gesture = Gesture.NONE; gestureId = null; startLayer = null }
  private fun selectedLayer() = layers.firstOrNull { it.id == selectedLayerId && it.visible }
  private fun currentGestureLayer() = layers.firstOrNull { it.id == gestureId && it.visible && !it.locked }
  private fun centerOf(layer: PhotoLayer) = Pair(imageBounds.left + layer.x * imageBounds.width(), imageBounds.top + layer.y * imageBounds.height())
  private fun selectionHalfExtents(layer: PhotoLayer): Pair<Float, Float> {
    val shortSide = min(imageBounds.width(), imageBounds.height())
    val base = when (layer.type) {
      LayerType.TEXT -> {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
          textSize = layer.fontSize / 48f * shortSide * 0.06f
          layer.fontFamily?.let { typeface = android.graphics.Typeface.create(it, android.graphics.Typeface.NORMAL) }
        }
        val lines = layer.text.ifBlank { " " }.split("\n")
        Pair((lines.maxOfOrNull { paint.measureText(it) } ?: paint.textSize) / 2f + 8f * density, paint.fontSpacing * lines.size / 2f + 6f * density)
      }
      LayerType.STICKER -> {
        val (width, height) = OverlayGeometry.stickerSize(shortSide, layer.overlayAspectRatio ?: 1f)
        Pair(width / 2f + 4f * density, height / 2f + 4f * density)
      }
      LayerType.OVERLAY -> {
        val aspect = layer.overlayAspectRatio ?: 1f
        val baseSize = shortSide * 0.6f
        if (aspect >= 1f) Pair(baseSize / 2f, (baseSize / aspect) / 2f) else Pair((baseSize * aspect) / 2f, baseSize / 2f)
      }
      LayerType.DRAWING -> {
        val maxX = layer.drawPoints.maxOfOrNull { kotlin.math.abs(it.first) } ?: 0.08f
        val maxY = layer.drawPoints.maxOfOrNull { kotlin.math.abs(it.second) } ?: 0.08f
        Pair(maxX * imageBounds.width() + handleRadius, maxY * imageBounds.height() + handleRadius)
      }
    }
    return Pair((base.first * layer.scale).coerceAtLeast(24f * density), (base.second * layer.scale).coerceAtLeast(24f * density))
  }

  private fun handlePoint(layer: PhotoLayer): Pair<Float, Float> {
    val center = centerOf(layer); val half = selectionHalfExtents(layer)
    val radians = Math.toRadians(layer.rotationDegrees.toDouble())
    return Pair(center.first + (half.first * cos(radians) - half.second * sin(radians)).toFloat(), center.second + (half.first * sin(radians) + half.second * cos(radians)).toFloat())
  }

  private fun isHandleHit(layer: PhotoLayer, x: Float, y: Float): Boolean {
    val handle = handlePoint(layer)
    return hypot(x - handle.first, y - handle.second) <= handleTouchRadius
  }

  private fun hitTest(x: Float, y: Float): PhotoLayer? = layers.asReversed().firstOrNull { layer ->
    if (!layer.visible) return@firstOrNull false
    val center = centerOf(layer); val radians = Math.toRadians((-layer.rotationDegrees).toDouble())
    val dx = x - center.first; val dy = y - center.second
    val localX = dx * cos(radians).toFloat() - dy * sin(radians).toFloat()
    val localY = dx * sin(radians).toFloat() + dy * cos(radians).toFloat()
    val half = selectionHalfExtents(layer)
    localX in -half.first..half.first && localY in -half.second..half.second
  }

  private data class MultiMetrics(val midX: Float, val midY: Float, val distance: Float, val angle: Float)
  private fun multiMetrics(event: MotionEvent): MultiMetrics {
    val x0 = event.getX(0); val y0 = event.getY(0); val x1 = event.getX(1); val y1 = event.getY(1)
    return MultiMetrics((x0 + x1) / 2f, (y0 + y1) / 2f, hypot(x1 - x0, y1 - y0), angle(x0, y0, x1, y1))
  }
  private fun angle(x0: Float, y0: Float, x1: Float, y1: Float) = Math.toDegrees(atan2(y1 - y0, x1 - x0).toDouble()).toFloat()
  private fun angleDelta(from: Float, to: Float): Float = ((to - from + 540f) % 360f) - 180f
  private fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f
}
