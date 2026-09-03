package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.photovideoeditor.photo.render.PhotoLayer
import kotlin.math.min

/**
 * Draws a selection outline around the selected layer and handles tap-to-select
 * and single-finger drag-to-move. Hit testing uses an axis-aligned box around
 * each layer's center (it does not account for the layer's own rotation) — a
 * documented simplification.
 */
class LayerOverlayView(context: Context) : View(context) {
  private var imageBounds = RectF()

  var layers: List<PhotoLayer> = emptyList()
    set(value) {
      field = value
      invalidate()
    }
  var selectedLayerId: String? = null
    set(value) {
      field = value
      invalidate()
    }

  /** Fires on ACTION_DOWN with the tapped layer's id, or null when the tap hit nothing. */
  var onLayerTapped: ((String?) -> Unit)? = null

  /** Fires on every touch-move while dragging, with the new normalized (0..1) center. */
  var onLayerDragged: ((id: String, x: Float, y: Float) -> Unit)? = null

  /** Fires once when a drag gesture ends, so the caller can commit one undo entry. */
  var onLayerDragEnded: ((id: String) -> Unit)? = null

  private var draggingId: String? = null
  private var lastX = 0f
  private var lastY = 0f

  private val selectionPaint = Paint().apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 3f
    pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
  }

  fun setImageBounds(bounds: RectF) {
    imageBounds = RectF(bounds)
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (imageBounds.width() <= 0) return
    val selected = layers.firstOrNull { it.id == selectedLayerId } ?: return
    val cx = imageBounds.left + selected.x * imageBounds.width()
    val cy = imageBounds.top + selected.y * imageBounds.height()
    val halfSize = min(imageBounds.width(), imageBounds.height()) * 0.16f * selected.scale
    canvas.save()
    canvas.translate(cx, cy)
    canvas.rotate(selected.rotationDegrees)
    canvas.drawRect(-halfSize, -halfSize, halfSize, halfSize, selectionPaint)
    canvas.restore()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (imageBounds.width() <= 0) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val hit = hitTest(event.x, event.y)
        draggingId = hit?.takeIf { !it.locked }?.id
        lastX = event.x
        lastY = event.y
        onLayerTapped?.invoke(hit?.id)
        return hit != null
      }
      MotionEvent.ACTION_MOVE -> {
        val id = draggingId ?: return false
        val layer = layers.firstOrNull { it.id == id } ?: return false
        val dx = (event.x - lastX) / imageBounds.width()
        val dy = (event.y - lastY) / imageBounds.height()
        lastX = event.x
        lastY = event.y
        val newX = (layer.x + dx).coerceIn(0f, 1f)
        val newY = (layer.y + dy).coerceIn(0f, 1f)
        onLayerDragged?.invoke(id, newX, newY)
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val id = draggingId
        draggingId = null
        if (id != null) onLayerDragEnded?.invoke(id)
        return true
      }
    }
    return false
  }

  private fun hitTest(x: Float, y: Float): PhotoLayer? {
    val halfSizeBase = min(imageBounds.width(), imageBounds.height()) * 0.16f
    return layers.filter { it.visible }.asReversed().firstOrNull { layer ->
      val cx = imageBounds.left + layer.x * imageBounds.width()
      val cy = imageBounds.top + layer.y * imageBounds.height()
      val halfSize = halfSizeBase * layer.scale
      x in (cx - halfSize)..(cx + halfSize) && y in (cy - halfSize)..(cy + halfSize)
    }
  }
}
