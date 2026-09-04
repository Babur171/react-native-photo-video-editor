package com.photovideoeditor.photo.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint

/**
 * Burns the layer stack onto a bitmap. Used identically by the live preview
 * and the exporter (different bitmap resolutions, same code and normalized
 * coordinates) so what you see matches what gets exported.
 */
object PhotoLayerRenderer {
  private val builtinStickers = mapOf(
    "smile" to "😀",
    "heart" to "❤️",
    "star" to "⭐",
    "fire" to "🔥",
    "thumbsUp" to "👍",
    "sun" to "☀️",
    "clap" to "👏",
    "sparkles" to "✨"
  )

  fun builtinStickerIds(): List<String> = builtinStickers.keys.toList()
  fun glyphFor(stickerId: String): String = builtinStickers[stickerId] ?: "★"

  fun render(base: Bitmap, layers: List<PhotoLayer>, bitmapResolver: (String) -> Bitmap?): Bitmap {
    if (layers.isEmpty()) return base
    val result = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(result)
    layers.filter { it.visible }.forEach { layer ->
      val geometry = OverlayGeometry.map(layer, result.width, result.height)
      canvas.save()
      canvas.translate(geometry.centerX, geometry.centerY)
      canvas.rotate(geometry.rotationDegrees)
      canvas.scale(geometry.scale, geometry.scale)
      val alpha = (geometry.opacity * 255).toInt()
      when (layer.type) {
        LayerType.TEXT -> drawText(canvas, layer, geometry.shortSide, alpha)
        LayerType.STICKER -> drawSticker(canvas, layer, geometry.shortSide, alpha, bitmapResolver)
        LayerType.OVERLAY -> drawOverlay(canvas, layer, geometry.shortSide, alpha, bitmapResolver)
        LayerType.DRAWING -> drawStroke(canvas, layer, result.width.toFloat(), result.height.toFloat(), geometry.shortSide, alpha)
      }
      canvas.restore()
    }
    return result
  }

  private fun drawText(canvas: Canvas, layer: PhotoLayer, shortSide: Float, alpha: Int) {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = layer.textColor
      this.alpha = alpha
      textSize = layer.fontSize / 48f * (shortSide * 0.06f)
      textAlign = Paint.Align.CENTER
      layer.fontFamily?.let { family ->
        try {
          typeface = android.graphics.Typeface.create(family, android.graphics.Typeface.NORMAL)
        } catch (_: Exception) {
          // Fall back to the default typeface if the family cannot be resolved.
        }
      }
    }
    val lines = layer.text.ifBlank { " " }.split("\n")
    val lineHeight = paint.fontSpacing
    val totalHeight = lineHeight * lines.size
    lines.forEachIndexed { index, line ->
      canvas.drawText(line, 0f, -totalHeight / 2f + lineHeight * (index + 1) - paint.descent(), paint)
    }
  }

  private fun drawSticker(canvas: Canvas, layer: PhotoLayer, shortSide: Float, alpha: Int, resolver: (String) -> Bitmap?) {
    val size = shortSide * 0.18f
    val uri = layer.stickerUri
    if (uri != null) {
      resolver(uri)?.let { bitmap ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmap(bitmap, null, RectF(-size / 2, -size / 2, size / 2, size / 2), paint)
        return
      }
    }
    val glyph = layer.stickerId?.let { glyphFor(it) } ?: "★"
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      this.alpha = alpha
      textSize = size
      textAlign = Paint.Align.CENTER
    }
    canvas.drawText(glyph, 0f, size / 2f - paint.descent(), paint)
  }

  private fun drawOverlay(canvas: Canvas, layer: PhotoLayer, shortSide: Float, alpha: Int, resolver: (String) -> Bitmap?) {
    val uri = layer.overlayUri ?: return
    val bitmap = resolver(uri) ?: return
    val aspect = layer.overlayAspectRatio ?: (bitmap.width.toFloat() / bitmap.height.toFloat())
    val baseSize = shortSide * 0.6f
    val width: Float
    val height: Float
    if (aspect >= 1f) {
      width = baseSize
      height = baseSize / aspect
    } else {
      height = baseSize
      width = baseSize * aspect
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.alpha = alpha }
    canvas.drawBitmap(bitmap, null, RectF(-width / 2, -height / 2, width / 2, height / 2), paint)
  }

  private fun drawStroke(canvas: Canvas, layer: PhotoLayer, width: Float, height: Float, shortSide: Float, alpha: Int) {
    if (layer.drawPoints.size < 2) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = layer.drawColor
      this.alpha = alpha
      style = Paint.Style.STROKE
      strokeWidth = layer.drawStrokeWidth * shortSide
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }
    val path = Path()
    layer.drawPoints.forEachIndexed { index, (dx, dy) ->
      val px = dx * width
      val py = dy * height
      if (index == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    canvas.drawPath(path, paint)
  }
}
