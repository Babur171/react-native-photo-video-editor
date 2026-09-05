package com.photovideoeditor.photo.render

/** Pure coordinate mapping shared by preview and export renderers. */
object OverlayGeometry {
  /** New sticker width is 30% of media width; persisted layers keep their existing scale. */
  fun initialStickerScale(width: Float, height: Float): Float =
    if (width > 0 && height > 0) 0.3f * width / (0.18f * minOf(width, height)) else 0.3f / 0.18f

  fun stickerSize(shortSide: Float, aspectRatio: Float): Pair<Float, Float> {
    val width = shortSide * 0.18f
    val aspect = aspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    return width to (width / aspect)
  }

  data class Transform(
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val opacity: Float,
    val shortSide: Float,
  )

  /**
   * How to size the bitmap that [PhotoLayerRenderer] composites overlays onto for a
   * [frameWidth]x[frameHeight] output frame, plus the scale a compositor must apply to that bitmap
   * for it to cover the frame exactly.
   *
   * [PhotoLayerRenderer] works in coordinates normalized to the bitmap it is handed, so the invariant
   * every caller must preserve is `width * scaleX == frameWidth` (and likewise for height): a bitmap
   * that ends up covering less than the full frame shrinks every layer and pulls it toward the frame
   * centre, which is precisely how exports came to disagree with the preview.
   *
   * [maxEdgePx] bounds the allocation for high-resolution outputs; the returned scale compensates for
   * whatever downscale that forced, so geometry is identical at every resolution and only sharpness
   * varies.
   */
  data class CanvasPlan(val width: Int, val height: Int, val scaleX: Float, val scaleY: Float)

  fun canvasPlan(frameWidth: Int, frameHeight: Int, maxEdgePx: Float): CanvasPlan {
    val safeWidth = frameWidth.coerceAtLeast(2)
    val safeHeight = frameHeight.coerceAtLeast(2)
    val fit = minOf(1f, maxEdgePx / maxOf(safeWidth, safeHeight))
    val width = (safeWidth * fit).toInt().coerceAtLeast(2)
    val height = (safeHeight * fit).toInt().coerceAtLeast(2)
    // Divide by the rounded size actually allocated, not `fit`, so rounding cannot reintroduce drift.
    return CanvasPlan(width, height, safeWidth.toFloat() / width, safeHeight.toFloat() / height)
  }

  fun map(layer: PhotoLayer, frameWidth: Int, frameHeight: Int): Transform = Transform(
    centerX = layer.x * frameWidth,
    centerY = layer.y * frameHeight,
    scale = layer.scale,
    rotationDegrees = layer.rotationDegrees,
    opacity = layer.opacity.coerceIn(0f, 1f),
    shortSide = minOf(frameWidth, frameHeight).toFloat(),
  )
}
