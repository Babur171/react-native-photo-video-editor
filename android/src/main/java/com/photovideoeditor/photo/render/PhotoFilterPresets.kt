package com.photovideoeditor.photo.render

import android.graphics.ColorMatrix

/** A small set of original, neutrally named preset filters (no third-party looks or names copied). */
object PhotoFilterPresets {
  val PRESET_IDS = listOf("mono", "noir", "fade", "chrome", "warmth")

  fun labelFor(id: String): String = when (id) {
    "mono" -> "Mono"
    "noir" -> "Noir"
    "fade" -> "Fade"
    "chrome" -> "Chrome"
    "warmth" -> "Warmth"
    else -> id
  }

  fun matrixFor(id: String): ColorMatrix? = when (id) {
    "mono" -> ColorMatrix().apply { setSaturation(0f) }
    "noir" -> ColorMatrix().apply {
      setSaturation(0f)
      postConcat(
        ColorMatrix(
          floatArrayOf(
            1.3f, 0f, 0f, 0f, -40f,
            0f, 1.3f, 0f, 0f, -40f,
            0f, 0f, 1.3f, 0f, -40f,
            0f, 0f, 0f, 1f, 0f
          )
        )
      )
    }
    "fade" -> ColorMatrix(
      floatArrayOf(
        0.9f, 0f, 0f, 0f, 25f,
        0f, 0.9f, 0f, 0f, 25f,
        0f, 0f, 0.9f, 0f, 25f,
        0f, 0f, 0f, 1f, 0f
      )
    )
    "chrome" -> ColorMatrix().apply {
      setSaturation(1.4f)
      postConcat(
        ColorMatrix(
          floatArrayOf(
            1.1f, 0f, 0f, 0f, 0f,
            0f, 1.1f, 0f, 0f, 0f,
            0f, 0f, 1.1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
          )
        )
      )
    }
    "warmth" -> ColorMatrix(
      floatArrayOf(
        1f, 0f, 0f, 0f, 18f,
        0f, 1f, 0f, 0f, 6f,
        0f, 0f, 1f, 0f, -12f,
        0f, 0f, 0f, 1f, 0f
      )
    )
    else -> null
  }
}
