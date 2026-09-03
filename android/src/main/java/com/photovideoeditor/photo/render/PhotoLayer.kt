package com.photovideoeditor.photo.render

import android.graphics.Color
import java.util.UUID

enum class LayerType { TEXT, STICKER, SHAPE, DRAWING }
enum class ShapeKind { RECTANGLE, OVAL, LINE }

/**
 * A single non-destructive overlay layer (text, sticker, shape, or freehand
 * drawing). One flat model covers every type for simplicity — a documented
 * simplification versus a sealed-class-per-type hierarchy.
 *
 * `x`/`y` are the layer's center, normalized 0..1 against the image. For
 * drawing layers, `drawPoints` are offsets from that center, also normalized
 * against image width/height, so the whole stroke moves/scales/rotates as a
 * unit with the layer's transform.
 */
data class PhotoLayer(
  val id: String = UUID.randomUUID().toString(),
  val type: LayerType,
  var x: Float = 0.5f,
  var y: Float = 0.5f,
  var scale: Float = 1f,
  var rotationDegrees: Float = 0f,
  var opacity: Float = 1f,
  var locked: Boolean = false,
  var visible: Boolean = true,
  // Text
  var text: String = "",
  var textColor: Int = Color.WHITE,
  var fontSize: Float = 48f,
  var fontFamily: String? = null,
  // Sticker
  var stickerId: String? = null,
  var stickerUri: String? = null,
  // Shape
  var shapeKind: ShapeKind = ShapeKind.RECTANGLE,
  var shapeColor: Int = Color.WHITE,
  var shapeFilled: Boolean = true,
  // Drawing
  var drawColor: Int = Color.RED,
  var drawStrokeWidth: Float = 0.012f,
  var drawPoints: List<Pair<Float, Float>> = emptyList(),
  // Timing (video overlays only; ignored for photo layers). endMs <= 0 means "to end of video".
  var startMs: Long = 0L,
  var endMs: Long = 0L
) {
  /** True if this layer should be visible at [positionMs] against a video of [durationMs]. Always true for photo layers. */
  fun isActiveAt(positionMs: Long, durationMs: Long): Boolean {
    val effectiveEnd = if (endMs in 1..durationMs) endMs else durationMs
    return positionMs in startMs..effectiveEnd
  }
}
