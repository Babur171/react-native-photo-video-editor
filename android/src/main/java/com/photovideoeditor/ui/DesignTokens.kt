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

  // Literal Stitch accent hexes (DESIGN.md "Primary Accents & Active States"), used by the
  // new EditorUI components below where the exact mockup hue matters more than the M3 mapping.
  const val accentViolet = 0xFF7C3AED.toInt()
  const val accentIndigo = 0xFF6366F1.toInt()
  const val accentHighlightViolet = 0xFF8B5CF6.toInt()

  // Literal Stitch surface hexes (DESIGN.md "Canvas & Surface Hierarchy").
  const val canvasViewport = 0xFF09090B.toInt()
  const val editorSurface = 0xFF121214.toInt()
  const val elevatedPanel = 0xFF18181B.toInt()
  const val interactiveNeutral = 0xFF27272A.toInt()
  const val mutedBorderColor = 0xFF3F3F46.toInt()

  // Literal Stitch text hexes (DESIGN.md "Typography & Content Grayscale").
  const val textPrimary = 0xFFFFFFFF.toInt()
  const val textSecondary = 0xFFA1A1AA.toInt()
  const val textSubtle = 0xFF71717A.toInt()

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
  const val radiusSheet = 24

  // Elevation tiers (DESIGN.md "Elevation & Depth"): background color + opacity percent
  // to composite persistent chrome / floating trays / popovers over the canvas.
  object ElevationTier1 { const val color = editorSurface; const val opacityPercent = 92 }
  object ElevationTier2 { const val color = elevatedPanel; const val opacityPercent = 88 }
  object ElevationTier3 { const val color = interactiveNeutral; const val opacityPercent = 100 }
}
