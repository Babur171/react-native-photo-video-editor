import CoreGraphics
import UIKit

/// Raw RGBA8 pixel access for a `UIImage`, used by adjustment/effect passes
/// that need direct per-pixel math (tone curve, sharpen, box blur).
///
/// Important: a bitmap `CGContext` created via `CGContext(data:...)` has its
/// origin at the bottom-left with Y increasing upward (Quartz-native), which
/// is the OPPOSITE of the top-down row order `CGImage`/`UIImage` assume.
/// Drawing into it without first flipping the CTM produces a vertically
/// flipped image once the raw bytes are wrapped back into a `CGImage`. Both
/// `init` and `makeImage()` apply that flip so round-tripping through this
/// buffer preserves the image's orientation.
struct PixelBuffer {
  private(set) var data: [UInt8]
  let width: Int
  let height: Int
  private static let bytesPerPixel = 4
  private static let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue

  init?(image: UIImage) {
    guard let cgImage = image.cgImage, cgImage.width > 0, cgImage.height > 0 else { return nil }
    width = cgImage.width
    height = cgImage.height
    var buffer = [UInt8](repeating: 0, count: width * height * Self.bytesPerPixel)
    guard let context = CGContext(
      data: &buffer,
      width: width,
      height: height,
      bitsPerComponent: 8,
      bytesPerRow: width * Self.bytesPerPixel,
      space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: Self.bitmapInfo
    ) else { return nil }
    context.translateBy(x: 0, y: CGFloat(height))
    context.scaleBy(x: 1, y: -1)
    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
    data = buffer
  }

  func makeImage() -> UIImage? {
    var buffer = data
    guard let context = CGContext(
      data: &buffer,
      width: width,
      height: height,
      bitsPerComponent: 8,
      bytesPerRow: width * Self.bytesPerPixel,
      space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: Self.bitmapInfo
    ), let cgImage = context.makeImage() else { return nil }
    return UIImage(cgImage: cgImage)
  }

  subscript(x: Int, y: Int) -> (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
    get {
      let offset = (y * width + x) * Self.bytesPerPixel
      return (data[offset], data[offset + 1], data[offset + 2], data[offset + 3])
    }
    set {
      let offset = (y * width + x) * Self.bytesPerPixel
      data[offset] = newValue.r
      data[offset + 1] = newValue.g
      data[offset + 2] = newValue.b
      data[offset + 3] = newValue.a
    }
  }
}
