package com.photovideoeditor.photo.render

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Resolution-independent crop constraints shared by gestures and rendering. */
object CropGeometry {
  data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
  }
  enum class Handle { MOVE, TOP, RIGHT, BOTTOM, LEFT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

  fun fillScale(width: Float, height: Float, degrees: Float): Float {
    val radians = Math.toRadians(degrees.toDouble())
    val c = abs(cos(radians)).toFloat(); val s = abs(sin(radians)).toFloat()
    return maxOf(c + height / width * s, c + width / height * s)
  }

  fun fit(bounds: Rect, ratio: Float?): Rect {
    if (ratio == null || ratio <= 0) return bounds
    val width = minOf(bounds.width, bounds.height * ratio)
    val height = width / ratio
    val x = (bounds.left + bounds.right - width) / 2
    val y = (bounds.top + bounds.bottom - height) / 2
    return Rect(x, y, x + width, y + height)
  }

  /** Keeps the opposite edge/corner anchored; clamps motion instead of shifting a resized box. */
  fun resize(r: Rect, bounds: Rect, handle: Handle, dx: Float, dy: Float, ratio: Float?, minimum: Float): Rect {
    if (handle == Handle.MOVE) {
      val x = dx.coerceIn(bounds.left - r.left, bounds.right - r.right)
      val y = dy.coerceIn(bounds.top - r.top, bounds.bottom - r.bottom)
      return Rect(r.left + x, r.top + y, r.right + x, r.bottom + y)
    }
    val left = handle in listOf(Handle.LEFT, Handle.TOP_LEFT, Handle.BOTTOM_LEFT)
    val right = handle in listOf(Handle.RIGHT, Handle.TOP_RIGHT, Handle.BOTTOM_RIGHT)
    val top = handle in listOf(Handle.TOP, Handle.TOP_LEFT, Handle.TOP_RIGHT)
    val bottom = handle in listOf(Handle.BOTTOM, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT)
    var l = r.left + if (left) dx else 0f
    var t = r.top + if (top) dy else 0f
    var rr = r.right + if (right) dx else 0f
    var b = r.bottom + if (bottom) dy else 0f
    if (ratio != null && ratio > 0) {
      if ((left || right) && (top || bottom)) {
        val deltaW = if (left) -dx else dx
        val deltaH = if (top) -dy else dy
        val width = r.width + if (abs(deltaW) > abs(deltaH * ratio)) deltaW else deltaH * ratio
        if (left) l = r.right - width else rr = r.left + width
        if (top) t = r.bottom - width / ratio else b = r.top + width / ratio
      } else if (left || right) {
        val height = (rr - l) / ratio
        t = (r.top + r.bottom - height) / 2; b = t + height
      } else {
        val width = (b - t) * ratio
        l = (r.left + r.right - width) / 2; rr = l + width
      }
    }
    // All constraints are linear along the drag; interpolation preserves the ratio and anchor.
    var fraction = 1f
    fun limit(before: Float, after: Float, minimumValue: Float) {
      if (after < minimumValue && after < before) fraction = minOf(fraction, ((before - minimumValue) / (before - after)).coerceIn(0f, 1f))
    }
    limit(r.left, l, bounds.left); limit(r.top, t, bounds.top)
    limit(-r.right, -rr, -bounds.right); limit(-r.bottom, -b, -bounds.bottom)
    limit(r.width, rr - l, minOf(minimum, r.width)); limit(r.height, b - t, minOf(minimum, r.height))
    return Rect(r.left + (l - r.left) * fraction, r.top + (t - r.top) * fraction,
      r.right + (rr - r.right) * fraction, r.bottom + (b - r.bottom) * fraction)
  }
}
