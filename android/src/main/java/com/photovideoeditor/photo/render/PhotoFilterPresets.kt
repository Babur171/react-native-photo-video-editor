package com.photovideoeditor.photo.render

import android.graphics.ColorMatrix

/**
 * Original, neutrally named preset filters (no third-party looks or names copied).
 *
 * Every preset is expressed as the same four knobs the renderer already supports, so a preset can
 * never be a button that does nothing: set [saturation], scale RGB by [scale], then add the
 * per-channel offsets. `PhotoFilterPresets.swift` mirrors this table field-for-field — add presets to
 * both or neither, or the two platforms will render different looks for the same id.
 */
data class PhotoFilterRecipe(
  val saturation: Float,
  val scale: Float,
  val redOffset: Float,
  val greenOffset: Float,
  val blueOffset: Float
)

object PhotoFilterPresets {
  /** Display order in the carousel. "Original" (no preset) is prepended by the UI, not listed here. */
  val PRESET_IDS = listOf(
    "vivid", "vividWarm", "vividCool",
    "warmth", "cool",
    "natural", "soft", "fade",
    "mono", "noir", "silvertone",
    "vintage", "sepia", "retro",
    "dramatic", "dramaticWarm", "dramaticCool"
  )

  private val recipes = mapOf(
    "vivid" to PhotoFilterRecipe(1.45f, 1.05f, 0f, 0f, 0f),
    "vividWarm" to PhotoFilterRecipe(1.45f, 1.05f, 12f, 4f, -10f),
    "vividCool" to PhotoFilterRecipe(1.45f, 1.05f, -8f, 0f, 14f),
    "warmth" to PhotoFilterRecipe(1.05f, 1f, 18f, 6f, -12f),
    "cool" to PhotoFilterRecipe(1.05f, 1f, -12f, 0f, 18f),
    "natural" to PhotoFilterRecipe(1.15f, 1.02f, 2f, 2f, 0f),
    "soft" to PhotoFilterRecipe(0.9f, 0.96f, 14f, 12f, 12f),
    "fade" to PhotoFilterRecipe(1f, 0.9f, 25f, 25f, 25f),
    "mono" to PhotoFilterRecipe(0f, 1f, 0f, 0f, 0f),
    "noir" to PhotoFilterRecipe(0f, 1.3f, -40f, -40f, -40f),
    "silvertone" to PhotoFilterRecipe(0f, 1.1f, -6f, -2f, 10f),
    "vintage" to PhotoFilterRecipe(0.6f, 0.95f, 24f, 12f, -6f),
    "sepia" to PhotoFilterRecipe(0f, 1f, 38f, 20f, -14f),
    "retro" to PhotoFilterRecipe(0.75f, 0.92f, 20f, 6f, 14f),
    "dramatic" to PhotoFilterRecipe(1.2f, 1.25f, -28f, -28f, -28f),
    "dramaticWarm" to PhotoFilterRecipe(1.2f, 1.25f, -14f, -26f, -38f),
    "dramaticCool" to PhotoFilterRecipe(1.2f, 1.25f, -38f, -28f, -12f),
    // Retained so photos saved with an earlier preset id still render; not offered in the carousel.
    "chrome" to PhotoFilterRecipe(1.4f, 1.1f, 0f, 0f, 0f)
  )

  fun labelFor(id: String): String = when (id) {
    "vivid" -> "Vivid"
    "vividWarm" -> "Vivid Warm"
    "vividCool" -> "Vivid Cool"
    "warmth" -> "Warm"
    "cool" -> "Cool"
    "natural" -> "Natural"
    "soft" -> "Soft"
    "fade" -> "Fade"
    "mono" -> "Mono"
    "noir" -> "Noir"
    "silvertone" -> "Silvertone"
    "vintage" -> "Vintage"
    "sepia" -> "Sepia"
    "retro" -> "Retro"
    "dramatic" -> "Dramatic"
    "dramaticWarm" -> "Dramatic Warm"
    "dramaticCool" -> "Dramatic Cool"
    "chrome" -> "Chrome"
    else -> id
  }

  fun recipeFor(id: String): PhotoFilterRecipe? = recipes[id]

  fun matrixFor(id: String): ColorMatrix? {
    val recipe = recipes[id] ?: return null
    return ColorMatrix().apply {
      setSaturation(recipe.saturation)
      postConcat(
        ColorMatrix(
          floatArrayOf(
            recipe.scale, 0f, 0f, 0f, recipe.redOffset,
            0f, recipe.scale, 0f, 0f, recipe.greenOffset,
            0f, 0f, recipe.scale, 0f, recipe.blueOffset,
            0f, 0f, 0f, 1f, 0f
          )
        )
      )
    }
  }
}
