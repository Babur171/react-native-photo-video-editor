package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

data class DrawStroke(val points: MutableList<Pair<Float, Float>>, val color: Int, val widthPx: Float)

/** Captures freehand strokes over the visible image while Draw mode is active. */
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

  fun setImageBounds(bounds: RectF) { imageBounds = RectF(bounds) }
  fun clearStrokes() { strokes.clear(); current = null; invalidate(); onStrokesChanged?.invoke(strokes) }
  fun undoLastStroke() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); invalidate(); onStrokesChanged?.invoke(strokes) }
  val hasStrokes: Boolean get() = strokes.isNotEmpty()

  fun normalizedStrokes(): List<Pair<List<Pair<Float, Float>>, DrawStroke>> {
    if (imageBounds.width() <= 0 || imageBounds.height() <= 0) return emptyList()
    return strokes.map { stroke ->
      stroke.points.map { (x, y) ->
        (x - imageBounds.left) / imageBounds.width() to (y - imageBounds.top) / imageBounds.height()
      } to stroke
    }
  }

  override fun onDraw(canvas: Canvas) {
    strokes.forEach { stroke ->
      if (stroke.points.size < 2) return@forEach
      paint.color = stroke.color; paint.strokeWidth = stroke.widthPx
      val path = Path()
      stroke.points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.first, point.second) else path.lineTo(point.first, point.second) }
      canvas.drawPath(path, paint)
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!imageBounds.contains(event.x, event.y) && event.actionMasked == MotionEvent.ACTION_DOWN) return false
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        current = DrawStroke(mutableListOf(event.x to event.y), strokeColor, strokeWidthPx).also(strokes::add)
        parent?.requestDisallowInterceptTouchEvent(true); invalidate(); return true
      }
      MotionEvent.ACTION_MOVE -> { current?.points?.add(event.x to event.y); invalidate(); return true }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        current = null; parent?.requestDisallowInterceptTouchEvent(false); onStrokesChanged?.invoke(strokes); return true
      }
    }
    return false
  }
}
