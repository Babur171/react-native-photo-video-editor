import Foundation

/// Non-destructive photo adjustment stack (Milestone 3). Every numeric field
/// has a documented range and a zero/no-op default so "reset" is always just
/// `PhotoAdjustments()`. Mirrors `PhotoAdjustments.kt` field-for-field so the
/// two platforms can share the same formulas in their renderers.
struct PhotoAdjustments: Equatable {
  var brightness: Double = 0 // -100..100
  var contrast: Double = 0 // -100..100
  var saturation: Double = 0 // -100..100
  var exposure: Double = 0 // -100..100
  var gamma: Double = 0 // -100..100
  var temperature: Double = 0 // -100..100
  var tint: Double = 0 // -100..100
  var highlights: Double = 0 // -100..100
  var shadows: Double = 0 // -100..100
  var sharpness: Double = 0 // 0..100
  var blurRadius: Double = 0 // 0..25 (px)
  var pixelSize: Double = 0 // 0..40 (block size, 0/1 = off)
  var mirror: Bool = false
  var filterPreset: String?
  var filterStrength: Double = 100 // 0..100, only meaningful when filterPreset is set

  var isIdentity: Bool { self == PhotoAdjustments() }

  func value(_ key: String) -> Double {
    switch key {
    case "brightness": return brightness
    case "contrast": return contrast
    case "saturation": return saturation
    case "exposure": return exposure
    case "gamma": return gamma
    case "temperature": return temperature
    case "tint": return tint
    case "highlights": return highlights
    case "shadows": return shadows
    case "sharpness": return sharpness
    case "blurRadius": return blurRadius
    case "pixelSize": return pixelSize
    case "filterStrength": return filterStrength
    default: return 0
    }
  }

  mutating func setValue(_ key: String, _ newValue: Double) {
    switch key {
    case "brightness": brightness = newValue
    case "contrast": contrast = newValue
    case "saturation": saturation = newValue
    case "exposure": exposure = newValue
    case "gamma": gamma = newValue
    case "temperature": temperature = newValue
    case "tint": tint = newValue
    case "highlights": highlights = newValue
    case "shadows": shadows = newValue
    case "sharpness": sharpness = newValue
    case "blurRadius": blurRadius = newValue
    case "pixelSize": pixelSize = newValue
    case "filterStrength": filterStrength = newValue
    default: break
    }
  }

  static let sliderKeys = [
    "brightness", "contrast", "saturation", "exposure", "gamma",
    "temperature", "tint", "highlights", "shadows", "sharpness", "blurRadius", "pixelSize",
  ]

  static func range(for key: String) -> (Double, Double) {
    switch key {
    case "sharpness": return (0, 100)
    case "blurRadius": return (0, 25)
    case "pixelSize": return (0, 40)
    case "filterStrength": return (0, 100)
    default: return (-100, 100)
    }
  }
}
