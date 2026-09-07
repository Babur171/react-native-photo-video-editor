package com.photovideoeditor.photo.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Applies the non-destructive [PhotoAdjustments] stack to a bitmap. The live
 * preview and the exporter both call this, in this exact order, so what the
 * user sees always matches what gets exported:
 *
 * color matrix (brightness/contrast/saturation/exposure/temperature) ->
 * blur -> mirror -> preset filter blend.
 */
object PhotoAdjustmentRenderer {
  fun apply(source: Bitmap, adjustments: PhotoAdjustments): Bitmap {
    if (adjustments.isIdentity) return source
    var bitmap = applyColorMatrix(source, adjustments)
    if (adjustments.blurRadius >= 1f) bitmap = applyBoxBlur(bitmap, adjustments.blurRadius.roundToInt())
    if (adjustments.mirror) bitmap = applyMirror(bitmap)
    adjustments.filterPreset?.let { preset -> bitmap = applyPreset(bitmap, preset, adjustments.filterStrength / 100f) }
    return bitmap
  }

  private fun applyColorMatrix(source: Bitmap, adjustments: PhotoAdjustments): Bitmap {
    if (adjustments.brightness == 0f && adjustments.contrast == 0f && adjustments.saturation == 0f &&
      adjustments.exposure == 0f && adjustments.temperature == 0f
    ) return source

    val matrix = ColorMatrix()
    matrix.postConcat(scaleMatrix(2f.pow(adjustments.exposure / 100f)))
    matrix.postConcat(translateMatrix(adjustments.brightness / 100f * 80f))
    matrix.postConcat(contrastMatrix((adjustments.contrast + 100f) / 100f))
    matrix.postConcat(ColorMatrix().apply { setSaturation((adjustments.saturation + 100f) / 100f) })
    matrix.postConcat(temperatureMatrix(adjustments.temperature / 100f * 40f))

    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(result).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
    return result
  }

  private fun scaleMatrix(scale: Float) = ColorMatrix(
    floatArrayOf(
      scale, 0f, 0f, 0f, 0f,
      0f, scale, 0f, 0f, 0f,
      0f, 0f, scale, 0f, 0f,
      0f, 0f, 0f, 1f, 0f
    )
  )

  private fun translateMatrix(offset: Float) = ColorMatrix(
    floatArrayOf(
      1f, 0f, 0f, 0f, offset,
      0f, 1f, 0f, 0f, offset,
      0f, 0f, 1f, 0f, offset,
      0f, 0f, 0f, 1f, 0f
    )
  )

  private fun contrastMatrix(scale: Float): ColorMatrix {
    val translate = 128f * (1f - scale)
    return ColorMatrix(
      floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
      )
    )
  }

  private fun temperatureMatrix(tempShift: Float) = ColorMatrix(
    floatArrayOf(
      1f, 0f, 0f, 0f, tempShift,
      0f, 1f, 0f, 0f, 0f,
      0f, 0f, 1f, 0f, -tempShift,
      0f, 0f, 0f, 1f, 0f
    )
  )

  /** Same box-blur approximation used by the Blur adjustment slider. */
  fun blur(source: Bitmap, radiusPx: Int): Bitmap = applyBoxBlur(source, radiusPx.coerceIn(1, 40))

  /** Approximate Gaussian blur via three passes of separable box blur — a standard, cheap approximation. */
  private fun applyBoxBlur(source: Bitmap, radius: Int): Bitmap {
    val width = source.width
    val height = source.height
    var pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(3) {
      pixels = boxBlurPass(pixels, width, height, radius, horizontal = true)
      pixels = boxBlurPass(pixels, width, height, radius, horizontal = false)
    }
    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
  }

  private fun boxBlurPass(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean): IntArray {
    val output = IntArray(pixels.size)
    val outerLimit = if (horizontal) height else width
    val innerLimit = if (horizontal) width else height
    for (outer in 0 until outerLimit) {
      for (inner in 0 until innerLimit) {
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        var count = 0
        for (offset in -radius..radius) {
          val sampleInner = (inner + offset).coerceIn(0, innerLimit - 1)
          val index = if (horizontal) outer * width + sampleInner else sampleInner * width + outer
          val pixel = pixels[index]
          alpha += (pixel shr 24) and 0xFF
          red += (pixel shr 16) and 0xFF
          green += (pixel shr 8) and 0xFF
          blue += pixel and 0xFF
          count++
        }
        val outIndex = if (horizontal) outer * width + inner else inner * width + outer
        output[outIndex] = ((alpha / count) shl 24) or ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
      }
    }
    return output
  }

  /** Mirrors the left half of the image onto the right half. */
  private fun applyMirror(source: Bitmap): Bitmap {
    if (source.width < 2) return source
    val halfWidth = source.width / 2
    val leftHalf = Bitmap.createBitmap(source, 0, 0, halfWidth, source.height)
    val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawBitmap(leftHalf, 0f, 0f, null)
    val flipMatrix = Matrix().apply {
      postScale(-1f, 1f)
      postTranslate(source.width.toFloat(), 0f)
    }
    canvas.drawBitmap(leftHalf, flipMatrix, null)
    return result
  }

  private fun applyPreset(source: Bitmap, preset: String, strength: Float): Bitmap {
    val matrix = PhotoFilterPresets.matrixFor(preset) ?: return source
    val filtered = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(filtered).drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
    if (strength >= 1f) return filtered

    val blended = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(blended)
    canvas.drawBitmap(source, 0f, 0f, null)
    canvas.drawBitmap(filtered, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = (strength * 255f).roundToInt().coerceIn(0, 255) })
    return blended
  }
}
