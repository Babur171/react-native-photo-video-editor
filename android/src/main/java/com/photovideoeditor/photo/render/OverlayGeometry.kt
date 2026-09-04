package com.photovideoeditor.photo.render

/** Pure coordinate mapping shared by preview and export renderers. */
object OverlayGeometry {
  data class Transform(
    val centerX: Float,
    val centerY: Float,
    val scale: Float,
    val rotationDegrees: Float,
    val opacity: Float,
    val shortSide: Float,
  )

  fun map(layer: PhotoLayer, frameWidth: Int, frameHeight: Int): Transform = Transform(
    centerX = layer.x * frameWidth,
    centerY = layer.y * frameHeight,
    scale = layer.scale,
    rotationDegrees = layer.rotationDegrees,
    opacity = layer.opacity.coerceIn(0f, 1f),
    shortSide = minOf(frameWidth, frameHeight).toFloat(),
  )
}
