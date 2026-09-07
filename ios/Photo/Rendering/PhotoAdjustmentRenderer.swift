import Accelerate
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

    buffer.mutateBytes { ptr, _, width, height in
      let totalPixels = width * height
      for i in 0..<totalPixels {
        let offset = i * 4
        let rVal = Double(ptr[offset])
        let gVal = Double(ptr[offset + 1])
        let bVal = Double(ptr[offset + 2])
        var r = rVal * exposureScale + brightnessOffset
        var g = gVal * exposureScale + brightnessOffset
        var b = bVal * exposureScale + brightnessOffset
        r = r * contrastScale + contrastTranslate
        g = g * contrastScale + contrastTranslate
        b = b * contrastScale + contrastTranslate
        let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        r = luminance + (r - luminance) * saturationFactor
        g = luminance + (g - luminance) * saturationFactor
        b = luminance + (b - luminance) * saturationFactor
        r += tempShift
        b -= tempShift
        ptr[offset] = clampByte(r)
        ptr[offset + 1] = clampByte(g)
        ptr[offset + 2] = clampByte(b)
      }
    }
  }

  /// Hardware-accelerated box blur using Apple Accelerate vImage.
  private static func applyBoxBlur(_ buffer: inout PixelBuffer, radius: Int) {
    guard radius > 0 else { return }
    let kernel = UInt32(max(3, (radius * 2 + 1) | 1))
    buffer.mutateBytes { ptr, count, width, height in
      var src = vImage_Buffer(data: ptr, height: vImagePixelCount(height), width: vImagePixelCount(width), rowBytes: width * 4)
      var temp = [UInt8](repeating: 0, count: count)
      temp.withUnsafeMutableBufferPointer { tempBuf in
        var dest = vImage_Buffer(data: tempBuf.baseAddress, height: vImagePixelCount(height), width: vImagePixelCount(width), rowBytes: width * 4)
        for _ in 0..<3 {
          vImageBoxConvolve_ARGB8888(&src, &dest, nil, 0, 0, kernel, kernel, nil, vImage_Flags(kvImageEdgeExtend))
          memcpy(src.data, dest.data, count)
        }
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
    buffer.mutateBytes { ptr, _, width, height in
      let totalPixels = width * height
      for i in 0..<totalPixels {
        let offset = i * 4
        let rVal = Double(ptr[offset])
        let gVal = Double(ptr[offset + 1])
        let bVal = Double(ptr[offset + 2])
        let luminance = 0.2126 * rVal + 0.7152 * gVal + 0.0722 * bVal
        var r = luminance + (rVal - luminance) * recipe.saturation
        var g = luminance + (gVal - luminance) * recipe.saturation
        var b = luminance + (bVal - luminance) * recipe.saturation
        r = r * recipe.scale + recipe.redOffset
        g = g * recipe.scale + recipe.greenOffset
        b = b * recipe.scale + recipe.blueOffset
        ptr[offset] = clampByte(r)
        ptr[offset + 1] = clampByte(g)
        ptr[offset + 2] = clampByte(b)
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

