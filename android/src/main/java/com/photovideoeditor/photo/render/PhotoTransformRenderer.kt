package com.photovideoeditor.photo.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

/** The same upright, auto-filled straighten and normalized crop pipeline for preview and export. */
object PhotoTransformRenderer {
  fun transform(source: Bitmap, state: PhotoTransformState): Bitmap {
    val oriented = if (state.rotationDegrees % 360 == 0) source else Bitmap.createBitmap(
      source, 0, 0, source.width, source.height, Matrix().apply { postRotate(state.rotationDegrees.toFloat()) }, true)
    if (state.straightenDegrees == 0f) return oriented
    val output = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val fill = CropGeometry.fillScale(oriented.width.toFloat(), oriented.height.toFloat(), state.straightenDegrees)
    canvas.translate(oriented.width / 2f, oriented.height / 2f)
    canvas.rotate(state.straightenDegrees)
    canvas.scale(fill, fill)
    canvas.drawBitmap(oriented, -oriented.width / 2f, -oriented.height / 2f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
    if (oriented !== source) oriented.recycle()
    return output
  }

  fun crop(bitmap: Bitmap, state: PhotoTransformState): Bitmap {
    val left = (state.cropLeft * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val top = (state.cropTop * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
    val right = (state.cropRight * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
    val bottom = (state.cropBottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
  }
}
