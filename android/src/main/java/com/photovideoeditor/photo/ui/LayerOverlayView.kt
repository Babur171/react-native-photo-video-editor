package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.text.TextPaint
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
  var onLayerTapped: ((String?) -> Unit)? = null
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
  private val density = resources.displayMetrics.density
  private val handleRadius = 11f * density
  private val handleTouchRadius = 30f * density
  private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f * density
    pathEffect = DashPathEffect(floatArrayOf(8f * density, 5f * density), 0f)
  }
  private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
  private val handleGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(87, 55, 245); style = Paint.Style.STROKE; strokeWidth = 2f * density
  }

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
    canvas.drawCircle(half.first, half.second, handleRadius, handlePaint)
    val glyph = handleRadius * 0.52f
    canvas.drawLine(half.first - glyph, half.second + glyph, half.first + glyph, half.second - glyph, handleGlyphPaint)
    canvas.restore()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (imageBounds.width() <= 0 || imageBounds.height() <= 0) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val selected = selectedLayer()
        val handleHit = selected?.takeIf { !it.locked && isHandleHit(it, event.x, event.y) }
        val hit = handleHit ?: hitTest(event.x, event.y)
        onLayerTapped?.invoke(hit?.id)
        if (hit == null || hit.locked) { resetGesture(); return false }
        gestureId = hit.id
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
          gesture = Gesture.MULTI
          return true
        }
      }
      MotionEvent.ACTION_MOVE -> {
        val initial = startLayer ?: return false
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
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { finishGesture(); return true }
      MotionEvent.ACTION_POINTER_UP -> return true
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
        val size = shortSide * 0.18f
        Pair(size / 2f, size / 2f)
      }
      LayerType.OVERLAY -> {
        val aspect = layer.overlayAspectRatio ?: 1f
        val baseSize = shortSide * 0.6f
        if (aspect >= 1f) Pair(baseSize / 2f, (baseSize / aspect) / 2f) else Pair((baseSize * aspect) / 2f, baseSize / 2f)
      }
    }
    return Pair((base.first * layer.scale).coerceAtLeast(handleRadius), (base.second * layer.scale).coerceAtLeast(handleRadius))
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
