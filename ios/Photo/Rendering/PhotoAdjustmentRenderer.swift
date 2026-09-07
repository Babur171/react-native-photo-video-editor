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
    let exposureScale = Float(pow(2.0, adjustments.exposure / 100))
    let brightnessOffset = Float(adjustments.brightness / 100 * 80)
    let contrastScale = Float((adjustments.contrast + 100) / 100)
    let contrastTranslate = Float(128 * (1 - contrastScale))
    let saturationFactor = Float((adjustments.saturation + 100) / 100)
    let tempShift = Float(adjustments.temperature / 100 * 40)

    let combScale = exposureScale * contrastScale
    let combOffset = brightnessOffset * contrastScale + contrastTranslate

    buffer.mutateBytes { ptr, _, width, height in
      let totalPixels = width * height
      for i in 0..<totalPixels {
        let offset = i * 4
        let rVal = Float(ptr[offset])
        let gVal = Float(ptr[offset + 1])
        let bVal = Float(ptr[offset + 2])
        let rBase = rVal * combScale + combOffset
        let gBase = gVal * combScale + combOffset
        let bBase = bVal * combScale + combOffset
        let luminance = 0.2126 * rBase + 0.7152 * gBase + 0.0722 * bBase
        let r = luminance + (rBase - luminance) * saturationFactor + tempShift
        let g = luminance + (gBase - luminance) * saturationFactor
        let b = luminance + (bBase - luminance) * saturationFactor - tempShift
        ptr[offset] = UInt8(min(max(r, 0), 255))
        ptr[offset + 1] = UInt8(min(max(g, 0), 255))
        ptr[offset + 2] = UInt8(min(max(b, 0), 255))
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
    let sat = Float(recipe.saturation)
    let scale = Float(recipe.scale)
    let rOff = Float(recipe.redOffset)
    let gOff = Float(recipe.greenOffset)
    let bOff = Float(recipe.blueOffset)
    buffer.mutateBytes { ptr, _, width, height in
      let totalPixels = width * height
      for i in 0..<totalPixels {
        let offset = i * 4
        let rVal = Float(ptr[offset])
        let gVal = Float(ptr[offset + 1])
        let bVal = Float(ptr[offset + 2])
        let luminance = 0.2126 * rVal + 0.7152 * gVal + 0.0722 * bVal
        let r = (luminance + (rVal - luminance) * sat) * scale + rOff
        let g = (luminance + (gVal - luminance) * sat) * scale + gOff
        let b = (luminance + (bVal - luminance) * sat) * scale + bOff
        ptr[offset] = UInt8(min(max(r, 0), 255))
        ptr[offset + 1] = UInt8(min(max(g, 0), 255))
        ptr[offset + 2] = UInt8(min(max(b, 0), 255))
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
}

