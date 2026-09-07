import UIKit

/// Default values for the "Studio Violet" design system used by the native editor
/// shell, matching the Stitch-exported mockups under
/// stitch_react_native_photo_editor_sdk/studio_violet_creative_sdk/DESIGN.md.
/// Mirrors `DesignTokens.kt`. These are FALLBACKS only — every color a consumer
/// can already override via `EditorOptions.theme` still wins; this object just
/// changes what the editor looks like when no override is given.
enum DesignTokens {
  // Surface hierarchy
  static let surfaceDim = UIColor(red: 0x13 / 255, green: 0x13 / 255, blue: 0x16 / 255, alpha: 1)
  static let surface = surfaceDim
  static let surfaceContainerLowest = UIColor(red: 0x0E / 255, green: 0x0E / 255, blue: 0x11 / 255, alpha: 1)
  static let surfaceContainerLow = UIColor(red: 0x1B / 255, green: 0x1B / 255, blue: 0x1E / 255, alpha: 1)
  static let surfaceContainer = UIColor(red: 0x1F / 255, green: 0x1F / 255, blue: 0x22 / 255, alpha: 1)
  static let surfaceContainerHigh = UIColor(red: 0x2A / 255, green: 0x2A / 255, blue: 0x2D / 255, alpha: 1)
  static let surfaceContainerHighest = UIColor(red: 0x35 / 255, green: 0x34 / 255, blue: 0x38 / 255, alpha: 1)
  static let surfaceVariant = surfaceContainerHighest
  static let surfaceBright = UIColor(red: 0x39 / 255, green: 0x39 / 255, blue: 0x3C / 255, alpha: 1)

  // Content
  static let onSurface = UIColor(red: 0xE4 / 255, green: 0xE1 / 255, blue: 0xE6 / 255, alpha: 1)
  static let onSurfaceVariant = UIColor(red: 0xCC / 255, green: 0xC3 / 255, blue: 0xD8 / 255, alpha: 1)
  static let outline = UIColor(red: 0x95 / 255, green: 0x8D / 255, blue: 0xA1 / 255, alpha: 1)
  static let outlineVariant = UIColor(red: 0x4A / 255, green: 0x44 / 255, blue: 0x55 / 255, alpha: 1)

  // Accents
  static let primary = UIColor(red: 0xD2 / 255, green: 0xBB / 255, blue: 0xFF / 255, alpha: 1)
  static let onPrimary = UIColor(red: 0x3F / 255, green: 0x00 / 255, blue: 0x8E / 255, alpha: 1)
  static let primaryContainer = UIColor(red: 0x7C / 255, green: 0x3A / 255, blue: 0xED / 255, alpha: 1)
  static let onPrimaryContainer = UIColor(red: 0xED / 255, green: 0xE0 / 255, blue: 0xFF / 255, alpha: 1)
  static let secondary = UIColor(red: 0xC0 / 255, green: 0xC1 / 255, blue: 0xFF / 255, alpha: 1)
  static let tertiary = UIColor(red: 0xD0 / 255, green: 0xBC / 255, blue: 0xFF / 255, alpha: 1)

  // Literal Stitch accent hexes (DESIGN.md "Primary Accents & Active States"), used by the
  // new EditorUI components where the exact mockup hue matters more than the M3 mapping.
  static let accentViolet = UIColor(red: 0x7C / 255, green: 0x3A / 255, blue: 0xED / 255, alpha: 1)
  static let accentIndigo = UIColor(red: 0x63 / 255, green: 0x66 / 255, blue: 0xF1 / 255, alpha: 1)
  static let accentHighlightViolet = UIColor(red: 0x8B / 255, green: 0x5C / 255, blue: 0xF6 / 255, alpha: 1)

  // Literal Stitch surface hexes (DESIGN.md "Canvas & Surface Hierarchy").
  static let canvasViewport = UIColor(red: 0x09 / 255, green: 0x09 / 255, blue: 0x0B / 255, alpha: 1)
  static let editorSurface = UIColor(red: 0x12 / 255, green: 0x12 / 255, blue: 0x14 / 255, alpha: 1)
  static let elevatedPanel = UIColor(red: 0x18 / 255, green: 0x18 / 255, blue: 0x1B / 255, alpha: 1)
  static let interactiveNeutral = UIColor(red: 0x27 / 255, green: 0x27 / 255, blue: 0x2A / 255, alpha: 1)
  static let mutedBorderColor = UIColor(red: 0x3F / 255, green: 0x3F / 255, blue: 0x46 / 255, alpha: 1)

  // Literal Stitch text hexes (DESIGN.md "Typography & Content Grayscale").
  static let textPrimary = UIColor.white
  static let textSecondary = UIColor(red: 0xA1 / 255, green: 0xA1 / 255, blue: 0xAA / 255, alpha: 1)
  static let textSubtle = UIColor(red: 0x71 / 255, green: 0x71 / 255, blue: 0x7A / 255, alpha: 1)

  // Spacing (points), matching DESIGN.md's `space-*` scale.
  static let spaceXs: CGFloat = 4
  static let spaceSm: CGFloat = 8
  static let spaceMd: CGFloat = 12
  static let spaceLg: CGFloat = 16
  static let spaceXl: CGFloat = 20
  static let space2xl: CGFloat = 24
  static let space3xl: CGFloat = 32
  static let touchTargetMin: CGFloat = 44

  // Corner radii (points)
  static let radiusSm: CGFloat = 4
  static let radiusMd: CGFloat = 8
  static let radiusLg: CGFloat = 12
  static let radiusXl: CGFloat = 16
  static let radiusSheet: CGFloat = 24

  // Elevation tiers (DESIGN.md "Elevation & Depth"): background color + opacity to composite
  // persistent chrome / floating trays / popovers over the canvas.
  enum ElevationTier1 { static let color = editorSurface; static let opacity: CGFloat = 0.92 }
  enum ElevationTier2 { static let color = elevatedPanel; static let opacity: CGFloat = 0.88 }
  enum ElevationTier3 { static let color = interactiveNeutral; static let opacity: CGFloat = 1.0 }
}
