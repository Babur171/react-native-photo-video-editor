import Foundation

/// A small set of original, neutrally named preset filters (no third-party
/// looks or names copied). Each recipe is applied by the renderer as:
/// 1) set saturation, 2) scale RGB, 3) add the per-channel offset —
/// mirroring the matrices in `PhotoFilterPresets.kt`.
struct PhotoFilterRecipe {
  let scale: Double
  let redOffset: Double
  let greenOffset: Double
  let blueOffset: Double
  let saturation: Double
}

enum PhotoFilterPresets {
  static let presetIDs = ["mono", "noir", "fade", "chrome", "warmth"]

  static func label(for id: String) -> String {
    switch id {
    case "mono": return "Mono"
    case "noir": return "Noir"
    case "fade": return "Fade"
    case "chrome": return "Chrome"
    case "warmth": return "Warmth"
    default: return id
    }
  }

  static func recipe(for id: String) -> PhotoFilterRecipe? {
    switch id {
    case "mono":
      return PhotoFilterRecipe(scale: 1, redOffset: 0, greenOffset: 0, blueOffset: 0, saturation: 0)
    case "noir":
      return PhotoFilterRecipe(scale: 1.3, redOffset: -40, greenOffset: -40, blueOffset: -40, saturation: 0)
    case "fade":
      return PhotoFilterRecipe(scale: 0.9, redOffset: 25, greenOffset: 25, blueOffset: 25, saturation: 1)
    case "chrome":
      return PhotoFilterRecipe(scale: 1.1, redOffset: 0, greenOffset: 0, blueOffset: 0, saturation: 1.4)
    case "warmth":
      return PhotoFilterRecipe(scale: 1, redOffset: 18, greenOffset: 6, blueOffset: -12, saturation: 1)
    default:
      return nil
    }
  }
}
