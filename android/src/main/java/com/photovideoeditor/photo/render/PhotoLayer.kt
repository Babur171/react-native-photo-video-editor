package com.photovideoeditor.photo.render

import android.graphics.Color
import java.util.UUID

enum class LayerType { TEXT, STICKER, OVERLAY }

/**
 * A single non-destructive overlay layer (text, sticker, or uploaded image
 * overlay). One flat model covers every type for simplicity — a documented
 * simplification versus a sealed-class-per-type hierarchy.
 *
 * `x`/`y` are the layer's center, normalized 0..1 against the image.
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
  // Overlay (uploaded image)
  var overlayUri: String? = null,
  var overlayAspectRatio: Float? = null,
  // Timing (video overlays only; ignored for photo layers). endMs <= 0 means "to end of video".
  var startMs: Long = 0L,
  var endMs: Long = 0L
) {
  fun effectiveEndMs(durationMs: Long): Long = if (endMs in 1..durationMs) endMs else durationMs

  /** True if this layer should be visible at [positionMs] against a video of [durationMs]. Always true for photo layers. */
  fun isActiveAt(positionMs: Long, durationMs: Long): Boolean {
    if (!visible || durationMs < 0) return false
    val clamped = positionMs.coerceAtLeast(0L)
    if (clamped < startMs.coerceAtLeast(0L)) return false
    // No explicit end (or one at/past the video's end) means "visible through the real last
    // frame" — don't compare against `durationMs`, which is a separately-computed estimate
    // (summed clip-trim durations) that can be a few ms shy of the true final frame's
    // timestamp due to frame-rate rounding, which would otherwise hide the overlay early.
    if (endMs <= 0 || endMs >= durationMs) return true
    return clamped <= endMs
  }
}
