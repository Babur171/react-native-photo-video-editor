import Foundation

/// Original, neutrally named preset filters (no third-party looks or names copied).
///
/// Every preset is expressed as the same four knobs the renderer already supports, so a preset can
/// never be a button that does nothing: 1) set saturation, 2) scale RGB, 3) add the per-channel
/// offset. `PhotoFilterPresets.kt` mirrors this table field-for-field — add presets to both or
/// neither, or the two platforms will render different looks for the same id.
struct PhotoFilterRecipe {
  let scale: Double
  let redOffset: Double
  let greenOffset: Double
  let blueOffset: Double
  let saturation: Double
}

enum PhotoFilterPresets {
  /// Display order in the carousel. "Original" (no preset) is prepended by the UI, not listed here.
  static let presetIDs = [
    "vivid", "vividWarm", "vividCool",
    "warmth", "cool",
    "natural", "soft", "fade",
    "mono", "noir", "silvertone",
    "vintage", "sepia", "retro",
    "dramatic", "dramaticWarm", "dramaticCool",
  ]

  private static let recipes: [String: PhotoFilterRecipe] = [
    "vivid": PhotoFilterRecipe(scale: 1.05, redOffset: 0, greenOffset: 0, blueOffset: 0, saturation: 1.45),
    "vividWarm": PhotoFilterRecipe(scale: 1.05, redOffset: 12, greenOffset: 4, blueOffset: -10, saturation: 1.45),
    "vividCool": PhotoFilterRecipe(scale: 1.05, redOffset: -8, greenOffset: 0, blueOffset: 14, saturation: 1.45),
    "warmth": PhotoFilterRecipe(scale: 1, redOffset: 18, greenOffset: 6, blueOffset: -12, saturation: 1.05),
    "cool": PhotoFilterRecipe(scale: 1, redOffset: -12, greenOffset: 0, blueOffset: 18, saturation: 1.05),
    "natural": PhotoFilterRecipe(scale: 1.02, redOffset: 2, greenOffset: 2, blueOffset: 0, saturation: 1.15),
    "soft": PhotoFilterRecipe(scale: 0.96, redOffset: 14, greenOffset: 12, blueOffset: 12, saturation: 0.9),
    "fade": PhotoFilterRecipe(scale: 0.9, redOffset: 25, greenOffset: 25, blueOffset: 25, saturation: 1),
    "mono": PhotoFilterRecipe(scale: 1, redOffset: 0, greenOffset: 0, blueOffset: 0, saturation: 0),
    "noir": PhotoFilterRecipe(scale: 1.3, redOffset: -40, greenOffset: -40, blueOffset: -40, saturation: 0),
    "silvertone": PhotoFilterRecipe(scale: 1.1, redOffset: -6, greenOffset: -2, blueOffset: 10, saturation: 0),
    "vintage": PhotoFilterRecipe(scale: 0.95, redOffset: 24, greenOffset: 12, blueOffset: -6, saturation: 0.6),
    "sepia": PhotoFilterRecipe(scale: 1, redOffset: 38, greenOffset: 20, blueOffset: -14, saturation: 0),
    "retro": PhotoFilterRecipe(scale: 0.92, redOffset: 20, greenOffset: 6, blueOffset: 14, saturation: 0.75),
    "dramatic": PhotoFilterRecipe(scale: 1.25, redOffset: -28, greenOffset: -28, blueOffset: -28, saturation: 1.2),
    "dramaticWarm": PhotoFilterRecipe(scale: 1.25, redOffset: -14, greenOffset: -26, blueOffset: -38, saturation: 1.2),
    "dramaticCool": PhotoFilterRecipe(scale: 1.25, redOffset: -38, greenOffset: -28, blueOffset: -12, saturation: 1.2),
    // Retained so photos saved with an earlier preset id still render; not offered in the carousel.
    "chrome": PhotoFilterRecipe(scale: 1.1, redOffset: 0, greenOffset: 0, blueOffset: 0, saturation: 1.4),
  ]

  static func label(for id: String) -> String {
    switch id {
    case "vivid": return "Vivid"
    case "vividWarm": return "Vivid Warm"
    case "vividCool": return "Vivid Cool"
    case "warmth": return "Warm"
    case "cool": return "Cool"
    case "natural": return "Natural"
    case "soft": return "Soft"
    case "fade": return "Fade"
    case "mono": return "Mono"
    case "noir": return "Noir"
    case "silvertone": return "Silvertone"
    case "vintage": return "Vintage"
    case "sepia": return "Sepia"
    case "retro": return "Retro"
    case "dramatic": return "Dramatic"
    case "dramaticWarm": return "Dramatic Warm"
    case "dramaticCool": return "Dramatic Cool"
    case "chrome": return "Chrome"
    default: return id
    }
  }

  static func recipe(for id: String) -> PhotoFilterRecipe? { recipes[id] }
}
