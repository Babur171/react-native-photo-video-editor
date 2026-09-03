package com.photovideoeditor.ui

import android.graphics.Color

/**
 * Default values for the "Studio Violet" design system used by the native editor
 * shell, matching the Stitch-exported mockups under
 * stitch_react_native_photo_editor_sdk/studio_violet_creative_sdk/DESIGN.md.
 * These are FALLBACKS only — every color a consumer can already override via
 * `EditorOptions.theme` (see `PhotoVideoEditorActivity.themeColor`) still wins;
 * this object just changes what the editor looks like when no override is given.
 */
object DesignTokens {
  // Surface hierarchy
  const val surfaceDim = 0xFF131316.toInt()
  const val surface = 0xFF131316.toInt()
  const val surfaceContainerLowest = 0xFF0E0E11.toInt()
  const val surfaceContainerLow = 0xFF1B1B1E.toInt()
  const val surfaceContainer = 0xFF1F1F22.toInt()
  const val surfaceContainerHigh = 0xFF2A2A2D.toInt()
  const val surfaceContainerHighest = 0xFF353438.toInt()
  const val surfaceVariant = 0xFF353438.toInt()
  const val surfaceBright = 0xFF39393C.toInt()

  // Content
  const val onSurface = 0xFFE4E1E6.toInt()
  const val onSurfaceVariant = 0xFFCCC3D8.toInt()
  const val outline = 0xFF958DA1.toInt()
  const val outlineVariant = 0xFF4A4455.toInt()

  // Accents
  const val primary = 0xFFD2BBFF.toInt()
  const val onPrimary = 0xFF3F008E.toInt()
  const val primaryContainer = 0xFF7C3AED.toInt()
  const val onPrimaryContainer = 0xFFEDE0FF.toInt()
  const val secondary = 0xFFC0C1FF.toInt()
  const val tertiary = 0xFFD0BCFF.toInt()
  const val error = 0xFFFFB4AB.toInt()
  const val errorContainer = 0xFF93000A.toInt()

  // Backwards-compatible aliases matching the flatter names `themeColor()` fallbacks used before this pass.
  const val toolbarColor = surfaceContainerLow
  const val backgroundColor = surfaceContainerLowest
  const val textColor = onSurface
  const val primaryColor = primaryContainer

  /** [color] at [alphaPercent]% opacity (0..100), matching the mockups' translucent surface tiers. */
  fun withAlphaPercent(color: Int, alphaPercent: Int): Int {
    val alpha = ((alphaPercent.coerceIn(0, 100)) * 255 / 100)
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
  }

  // Spacing (dp), matching DESIGN.md's `space-*` scale.
  const val spaceXs = 4
  const val spaceSm = 8
  const val spaceMd = 12
  const val spaceLg = 16
  const val spaceXl = 20
  const val space2xl = 24
  const val space3xl = 32
  const val touchTargetMin = 44

  // Corner radii (dp)
  const val radiusSm = 4
  const val radiusMd = 8
  const val radiusLg = 12
  const val radiusXl = 16
}
