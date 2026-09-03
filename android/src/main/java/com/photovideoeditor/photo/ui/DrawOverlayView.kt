package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** One freehand stroke: its points (view coordinates while drawing) plus the color/width active when it started. */
data class DrawStroke(val points: MutableList<Pair<Float, Float>>, val color: Int, val widthPx: Float)

/**
 * Captures freehand strokes over the image while in draw mode. Each
 * finger-down..up gesture is one stroke, recorded with the color/width active
 * at the moment it started (so changing color mid-session only affects new
 * strokes, not ones already drawn).
 */
class DrawOverlayView(context: Context) : View(context) {
  private var imageBounds = RectF()
  var strokeColor: Int = Color.RED
  var strokeWidthPx: Float = 12f
  private val strokes = mutableListOf<DrawStroke>()
  private var current: DrawStroke? = null
  var onStrokesChanged: ((List<DrawStroke>) -> Unit)? = null

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }

  fun setImageBounds(bounds: RectF) {
    imageBounds = RectF(bounds)
  }

  fun clearStrokes() {
    strokes.clear()
    current = null
    invalidate()
    onStrokesChanged?.invoke(strokes)
  }

  fun undoLastStroke() {
    if (strokes.isNotEmpty()) strokes.removeAt(strokes.size - 1)
    invalidate()
    onStrokesChanged?.invoke(strokes)
  }

  val hasStrokes: Boolean get() = strokes.isNotEmpty()

  /** Each stroke's points normalized (0..1) against the current image bounds. */
  fun normalizedStrokes(): List<Pair<List<Pair<Float, Float>>, DrawStroke>> {
    if (imageBounds.width() <= 0) return emptyList()
    return strokes.map { stroke ->
      val normalized = stroke.points.map { (x, y) ->
        (x - imageBounds.left) / imageBounds.width() to (y - imageBounds.top) / imageBounds.height()
      }
      normalized to stroke
    }
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    strokes.forEach { stroke -> drawStroke(canvas, stroke) }
  }

  private fun drawStroke(canvas: Canvas, stroke: DrawStroke) {
    if (stroke.points.size < 2) return
    paint.color = stroke.color
    paint.strokeWidth = stroke.widthPx
    val path = Path()
    stroke.points.forEachIndexed { index, (x, y) -> if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
    canvas.drawPath(path, paint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (imageBounds.width() <= 0) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        val stroke = DrawStroke(mutableListOf(event.x to event.y), strokeColor, strokeWidthPx)
        current = stroke
        strokes.add(stroke)
        invalidate()
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        current?.points?.add(event.x to event.y)
        invalidate()
        return true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        current = null
        onStrokesChanged?.invoke(strokes)
        return true
      }
    }
    return false
  }
}
