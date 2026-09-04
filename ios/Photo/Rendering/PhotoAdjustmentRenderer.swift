import UIKit

/// Applies the non-destructive `PhotoAdjustments` stack to a `UIImage`. The
/// live preview and the exporter both call this, in this exact order, so what
/// the user sees always matches what gets exported — and the per-channel
/// formulas mirror `PhotoAdjustmentRenderer.kt` so Android/iOS output stays
/// visually close:
///
/// color adjustments (brightness/contrast/saturation/exposure/temperature)
/// -> blur -> mirror -> preset filter blend.
enum PhotoAdjustmentRenderer {
  static func apply(_ source: UIImage, adjustments: PhotoAdjustments) -> UIImage {
    guard !adjustments.isIdentity, var buffer = PixelBuffer(image: source) else { return source }

    let needsColorPass = adjustments.brightness != 0 || adjustments.contrast != 0 || adjustments.saturation != 0 ||
      adjustments.exposure != 0 || adjustments.temperature != 0
    if needsColorPass {
      applyColorAdjustments(&buffer, adjustments)
    }
    if adjustments.blurRadius >= 1 {
      applyBoxBlur(&buffer, radius: Int(adjustments.blurRadius.rounded()))
    }

    var image = buffer.makeImage() ?? source
    if adjustments.mirror {
      image = applyMirror(image)
    }
    if let preset = adjustments.filterPreset {
      image = applyPreset(image, preset: preset, strength: adjustments.filterStrength / 100)
    }
    return image
  }

  private static func applyColorAdjustments(_ buffer: inout PixelBuffer, _ adjustments: PhotoAdjustments) {
    let exposureScale = pow(2, adjustments.exposure / 100)
    let brightnessOffset = adjustments.brightness / 100 * 80
    let contrastScale = (adjustments.contrast + 100) / 100
    let contrastTranslate = 128 * (1 - contrastScale)
    let saturationFactor = (adjustments.saturation + 100) / 100
    let tempShift = adjustments.temperature / 100 * 40

    for y in 0..<buffer.height {
      for x in 0..<buffer.width {
        let pixel = buffer[x, y]
        var r = Double(pixel.r) * exposureScale + brightnessOffset
        var g = Double(pixel.g) * exposureScale + brightnessOffset
        var b = Double(pixel.b) * exposureScale + brightnessOffset
        r = r * contrastScale + contrastTranslate
        g = g * contrastScale + contrastTranslate
        b = b * contrastScale + contrastTranslate
        let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        r = luminance + (r - luminance) * saturationFactor
        g = luminance + (g - luminance) * saturationFactor
        b = luminance + (b - luminance) * saturationFactor
        r += tempShift
        b -= tempShift
        buffer[x, y] = (clampByte(r), clampByte(g), clampByte(b), pixel.a)
      }
    }
  }

  /// Approximate Gaussian blur via three passes of separable box blur.
  private static func applyBoxBlur(_ buffer: inout PixelBuffer, radius: Int) {
    guard radius > 0 else { return }
    for _ in 0..<3 {
      boxBlurPass(&buffer, radius: radius, horizontal: true)
      boxBlurPass(&buffer, radius: radius, horizontal: false)
    }
  }

  private static func boxBlurPass(_ buffer: inout PixelBuffer, radius: Int, horizontal: Bool) {
    let source = buffer
    let outerLimit = horizontal ? buffer.height : buffer.width
    let innerLimit = horizontal ? buffer.width : buffer.height
    for outer in 0..<outerLimit {
      for inner in 0..<innerLimit {
        var r = 0
        var g = 0
        var b = 0
        var a = 0
        var count = 0
        for offset in -radius...radius {
          let sample = min(max(inner + offset, 0), innerLimit - 1)
          let (x, y) = horizontal ? (sample, outer) : (outer, sample)
          let pixel = source[x, y]
          r += Int(pixel.r); g += Int(pixel.g); b += Int(pixel.b); a += Int(pixel.a)
          count += 1
        }
        let (ox, oy) = horizontal ? (inner, outer) : (outer, inner)
        buffer[ox, oy] = (UInt8(r / count), UInt8(g / count), UInt8(b / count), UInt8(a / count))
      }
    }
  }

  /// Mirrors the left half of the image onto the right half.
  private static func applyMirror(_ image: UIImage) -> UIImage {
    guard image.size.width >= 2, let cgImage = image.cgImage else { return image }
    let halfWidth = CGFloat(Int(image.size.width) / 2)
    let leftRect = CGRect(x: 0, y: 0, width: halfWidth, height: image.size.height)
    guard let leftCG = cgImage.cropping(to: leftRect) else { return image }
    let leftImage = UIImage(cgImage: leftCG)
    let renderer = PhotoEditSession.pixelRenderer(size: image.size)
    return renderer.image { context in
      leftImage.draw(in: leftRect)
      context.cgContext.saveGState()
      context.cgContext.translateBy(x: image.size.width, y: 0)
      context.cgContext.scaleBy(x: -1, y: 1)
      leftImage.draw(in: leftRect)
      context.cgContext.restoreGState()
    }
  }

  private static func applyPreset(_ image: UIImage, preset: String, strength: Double) -> UIImage {
    guard let recipe = PhotoFilterPresets.recipe(for: preset), var buffer = PixelBuffer(image: image) else { return image }
    for y in 0..<buffer.height {
      for x in 0..<buffer.width {
        let pixel = buffer[x, y]
        let luminance = 0.2126 * Double(pixel.r) + 0.7152 * Double(pixel.g) + 0.0722 * Double(pixel.b)
        var r = luminance + (Double(pixel.r) - luminance) * recipe.saturation
        var g = luminance + (Double(pixel.g) - luminance) * recipe.saturation
        var b = luminance + (Double(pixel.b) - luminance) * recipe.saturation
        r = r * recipe.scale + recipe.redOffset
        g = g * recipe.scale + recipe.greenOffset
        b = b * recipe.scale + recipe.blueOffset
        buffer[x, y] = (clampByte(r), clampByte(g), clampByte(b), pixel.a)
      }
    }
    guard let filtered = buffer.makeImage() else { return image }
    guard strength < 1 else { return filtered }

    let renderer = PhotoEditSession.pixelRenderer(size: image.size)
    return renderer.image { _ in
      image.draw(in: CGRect(origin: .zero, size: image.size))
      filtered.draw(in: CGRect(origin: .zero, size: image.size), blendMode: .normal, alpha: CGFloat(strength))
    }
  }

  private static func clampByte(_ value: Double) -> UInt8 {
    UInt8(min(max(value.rounded(), 0), 255))
  }
}
