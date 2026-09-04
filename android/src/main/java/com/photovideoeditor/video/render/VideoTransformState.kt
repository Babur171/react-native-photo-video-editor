package com.photovideoeditor.video.render

/**
 * Non-destructive video edit state that applies globally across the whole
 * multi-clip timeline (Milestone 7) — trim now lives per clip on [VideoClip]
 * instead of here. `coverFrameMs` is a position in the composed timeline
 * (sum of preceding clips' trimmed durations + offset into the current one),
 * not a position within any single clip's own source file.
 */
data class VideoTransformState(
  val rotationDegrees: Int = 0,
  val aspectRatio: Float? = null,
  val coverFrameMs: Long = 0L,
  /** Preview-only for now: cycled via the Speed tool. Export does not yet honor this — see docs/video-editor.md. */
  val speed: Float = 1f,
  /** -100..100. Export-only for now — no live preview. See docs/video-editor.md. */
  val brightness: Float = 0f,
  /** -100..100. Export-only for now — no live preview. */
  val contrast: Float = 0f,
  /** -100..100. Export-only for now — no live preview. */
  val saturation: Float = 0f
) {
  fun rotatedRight(): VideoTransformState = copy(rotationDegrees = (rotationDegrees + 90) % 360)
  fun withAspectRatio(ratio: Float?): VideoTransformState = copy(aspectRatio = ratio)
  fun withCoverFrame(atMs: Long): VideoTransformState = copy(coverFrameMs = atMs)

  fun filterValue(key: String): Float = when (key) {
    "brightness" -> brightness
    "contrast" -> contrast
    "saturation" -> saturation
    else -> 0f
  }

  fun withFilter(key: String, value: Float): VideoTransformState = when (key) {
    "brightness" -> copy(brightness = value)
    "contrast" -> copy(contrast = value)
    "saturation" -> copy(saturation = value)
    else -> this
  }

  fun withFiltersReset(): VideoTransformState = copy(brightness = 0f, contrast = 0f, saturation = 0f)

  private val speedSteps = listOf(0.5f, 1f, 1.5f, 2f)
  fun cycledSpeed(): VideoTransformState {
    val currentIndex = speedSteps.indexOf(speed).takeIf { it >= 0 } ?: 1
    return copy(speed = speedSteps[(currentIndex + 1) % speedSteps.size])
  }
}
