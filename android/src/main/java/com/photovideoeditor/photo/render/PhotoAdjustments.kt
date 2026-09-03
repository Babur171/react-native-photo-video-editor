package com.photovideoeditor.photo.render

/**
 * Non-destructive photo adjustment stack (Milestone 3). Every numeric field
 * has a documented range and a zero/no-op default so "reset" is always just
 * `PhotoAdjustments()`.
 */
data class PhotoAdjustments(
  val brightness: Float = 0f, // -100..100
  val contrast: Float = 0f, // -100..100
  val saturation: Float = 0f, // -100..100
  val exposure: Float = 0f, // -100..100
  val gamma: Float = 0f, // -100..100
  val temperature: Float = 0f, // -100..100
  val tint: Float = 0f, // -100..100
  val highlights: Float = 0f, // -100..100
  val shadows: Float = 0f, // -100..100
  val sharpness: Float = 0f, // 0..100
  val blurRadius: Float = 0f, // 0..25 (px)
  val pixelSize: Float = 0f, // 0..40 (block size, 0/1 = off)
  val mirror: Boolean = false,
  val filterPreset: String? = null,
  val filterStrength: Float = 100f // 0..100, only meaningful when filterPreset is set
) {
  val isIdentity: Boolean get() = this == PhotoAdjustments()

  fun value(key: String): Float = when (key) {
    "brightness" -> brightness
    "contrast" -> contrast
    "saturation" -> saturation
    "exposure" -> exposure
    "gamma" -> gamma
    "temperature" -> temperature
    "tint" -> tint
    "highlights" -> highlights
    "shadows" -> shadows
    "sharpness" -> sharpness
    "blurRadius" -> blurRadius
    "pixelSize" -> pixelSize
    "filterStrength" -> filterStrength
    else -> 0f
  }

  fun with(key: String, value: Float): PhotoAdjustments = when (key) {
    "brightness" -> copy(brightness = value)
    "contrast" -> copy(contrast = value)
    "saturation" -> copy(saturation = value)
    "exposure" -> copy(exposure = value)
    "gamma" -> copy(gamma = value)
    "temperature" -> copy(temperature = value)
    "tint" -> copy(tint = value)
    "highlights" -> copy(highlights = value)
    "shadows" -> copy(shadows = value)
    "sharpness" -> copy(sharpness = value)
    "blurRadius" -> copy(blurRadius = value)
    "pixelSize" -> copy(pixelSize = value)
    "filterStrength" -> copy(filterStrength = value)
    else -> this
  }

  companion object {
    /** Adjustment keys that map to a continuous slider (excludes mirror/filterPreset, which are selectors). */
    val SLIDER_KEYS = listOf(
      "brightness", "contrast", "saturation", "exposure", "gamma",
      "temperature", "tint", "highlights", "shadows", "sharpness", "blurRadius", "pixelSize"
    )

    fun rangeFor(key: String): Pair<Float, Float> = when (key) {
      "sharpness" -> 0f to 100f
      "blurRadius" -> 0f to 25f
      "pixelSize" -> 0f to 40f
      "filterStrength" -> 0f to 100f
      else -> -100f to 100f
    }
  }
}
