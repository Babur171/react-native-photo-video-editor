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
}
