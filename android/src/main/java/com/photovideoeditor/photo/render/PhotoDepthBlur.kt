package com.photovideoeditor.photo.render

/**
 * Non-destructive tilt-shift-style depth blur (Blur & Depth tool). This is a
 * synthetic focus mask, not true depth-sensor/ML depth estimation — no on-device
 * model is available to verify without a device, so "Portrait" (auto-subject)
 * mode from the mockup is intentionally not implemented; only the two
 * mask-based modes (Radial, Linear) that are pure, verifiable graphics math.
 */
data class PhotoDepthBlur(
  val mode: Mode? = null,
  /** Center of the in-focus region, 0..1 relative to image width/height. Radial mode only. */
  val centerX: Float = 0.5f,
  val centerY: Float = 0.5f,
  /** Radius of the in-focus circle, 0..1 relative to min(width, height). Radial mode only. */
  val radius: Float = 0.3f,
  /** Center of the in-focus horizontal band, 0..1 relative to image height. Linear mode only. */
  val bandCenter: Float = 0.5f,
  /** Half-width of the in-focus band, 0..1 relative to image height. Linear mode only. */
  val bandWidth: Float = 0.15f,
  /** Width of the soft transition between sharp and blurred, 0..1 relative to the same axis as radius/bandWidth. */
  val feather: Float = 0.2f,
  /** Blur strength, 0..100, mapped to a box-blur pixel radius at render time. */
  val intensity: Float = 60f
) {
  enum class Mode { RADIAL, LINEAR }

  fun cycledMode(): PhotoDepthBlur = copy(
    mode = when (mode) {
      null -> Mode.RADIAL
      Mode.RADIAL -> Mode.LINEAR
      Mode.LINEAR -> null
    }
  )
}
