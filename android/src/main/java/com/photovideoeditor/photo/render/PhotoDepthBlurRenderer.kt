package com.photovideoeditor.photo.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders [PhotoDepthBlur]'s synthetic tilt-shift/portrait-blur mask: a fully
 * blurred copy of the image with a sharp region cut back in via an alpha
 * gradient mask (RadialGradient for the circular focus point, LinearGradient
 * for the horizontal band) — standard `PorterDuff.Mode.DST_IN` compositing,
 * not device- or library-version-dependent so it needed no bytecode
 * verification the way the Media3/AVFoundation work did.
 */
object PhotoDepthBlurRenderer {
  fun apply(source: Bitmap, blur: PhotoDepthBlur): Bitmap {
    if (blur.mode == null) return source
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source

    // Depth blur is intentionally computed on a bounded working image. Blur contains
    // no high-frequency detail, so processing a 1600–4000px preview only multiplies
    // memory/CPU cost without a visible quality benefit and can OOM when a mode is tapped.
    val longestSide = maxOf(width, height)
    if (longestSide > MAX_WORKING_DIMENSION) {
      val scale = MAX_WORKING_DIMENSION.toFloat() / longestSide
      val working = Bitmap.createScaledBitmap(
        source,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
      )
      return try {
        val blurred = applyAtWorkingSize(working, blur)
        try {
          Bitmap.createScaledBitmap(blurred, width, height, true)
        } finally {
          if (blurred !== working && !blurred.isRecycled) blurred.recycle()
        }
      } finally {
        if (working !== source && !working.isRecycled) working.recycle()
      }
    }
    return applyAtWorkingSize(source, blur)
  }

  private fun applyAtWorkingSize(source: Bitmap, blur: PhotoDepthBlur): Bitmap {
    val width = source.width
    val height = source.height

    val blurRadiusPx = (blur.intensity.coerceIn(0f, 100f) / 100f * 24f).roundToInt().coerceIn(1, 24)
    val blurred = PhotoAdjustmentRenderer.blur(source, blurRadiusPx)

    val maskShader = when (blur.mode) {
      null -> return source
      PhotoDepthBlur.Mode.RADIAL -> {
        val shortSide = min(width, height).toFloat()
        val innerRadius = (blur.radius.coerceIn(0f, 1f) * shortSide).coerceAtLeast(1f)
        val outerRadius = (innerRadius + blur.feather.coerceIn(0.01f, 1f) * shortSide).coerceAtLeast(innerRadius + 1f)
        val innerFraction = (innerRadius / outerRadius).coerceIn(0f, 0.999f)
        RadialGradient(
          blur.centerX.coerceIn(0f, 1f) * width,
          blur.centerY.coerceIn(0f, 1f) * height,
          outerRadius,
          intArrayOf(OPAQUE, OPAQUE, TRANSPARENT),
          floatArrayOf(0f, innerFraction, 1f),
          Shader.TileMode.CLAMP
        )
      }
      PhotoDepthBlur.Mode.LINEAR -> {
        val bandCenterPx = blur.bandCenter.coerceIn(0f, 1f) * height
        val halfBandPx = (blur.bandWidth.coerceIn(0.01f, 1f) * height)
        val featherPx = (blur.feather.coerceIn(0.01f, 1f) * height)
        val top = (bandCenterPx - halfBandPx - featherPx).coerceIn(0f, height.toFloat())
        val topSharp = (bandCenterPx - halfBandPx).coerceIn(0f, height.toFloat())
        val bottomSharp = (bandCenterPx + halfBandPx).coerceIn(0f, height.toFloat())
        val bottom = (bandCenterPx + halfBandPx + featherPx).coerceIn(0f, height.toFloat())
        val span = (bottom - top).coerceAtLeast(1f)
        // Some Android graphics backends reject duplicate gradient stops. Wide
        // bands can clamp both edges to 0/1, so enforce strictly ordered stops.
        val firstSharp = ((topSharp - top) / span).coerceIn(0.001f, 0.998f)
        val lastSharp = ((bottomSharp - top) / span).coerceIn(firstSharp + 0.001f, 0.999f)
        LinearGradient(
          0f, top, 0f, bottom,
          intArrayOf(TRANSPARENT, OPAQUE, OPAQUE, TRANSPARENT),
          floatArrayOf(0f, firstSharp, lastSharp, 1f),
          Shader.TileMode.CLAMP
        )
      }
    }

    val sharpMasked = source.copy(Bitmap.Config.ARGB_8888, true)
    Canvas(sharpMasked).drawRect(
      0f, 0f, width.toFloat(), height.toFloat(),
      Paint().apply {
        shader = maskShader
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        isAntiAlias = true
      }
    )

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    canvas.drawBitmap(blurred, 0f, 0f, null)
    canvas.drawBitmap(sharpMasked, 0f, 0f, null)
    if (blurred !== source && !blurred.isRecycled) blurred.recycle()
    if (!sharpMasked.isRecycled) sharpMasked.recycle()
    return result
  }

  private const val OPAQUE = -0x1 // 0xFFFFFFFF, fully opaque white — only alpha matters under DST_IN
  private const val TRANSPARENT = 0x00FFFFFF
  private const val MAX_WORKING_DIMENSION = 720
}
