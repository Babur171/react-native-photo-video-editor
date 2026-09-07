import CoreGraphics
import UIKit

/// Raw RGBA8 pixel access for a `UIImage`, optimized for high-throughput
/// contiguous memory access.
struct PixelBuffer {
  var data: [UInt8]
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
    context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
    data = buffer
  }

  mutating func mutateBytes(_ body: (UnsafeMutablePointer<UInt8>, Int, Int, Int) -> Void) {
    let count = data.count
    let w = width
    let h = height
    data.withUnsafeMutableBufferPointer { buffer in
      if let base = buffer.baseAddress {
        body(base, count, w, h)
      }
    }
  }

  func makeImage() -> UIImage? {
    guard let provider = CGDataProvider(data: Data(data) as CFData) else { return nil }
    guard let cgImage = CGImage(
      width: width,
      height: height,
      bitsPerComponent: 8,
      bitsPerPixel: 32,
      bytesPerRow: width * Self.bytesPerPixel,
      space: CGColorSpaceCreateDeviceRGB(),
      bitmapInfo: CGBitmapInfo(rawValue: Self.bitmapInfo),
      provider: provider,
      decode: nil,
      shouldInterpolate: false,
      intent: .defaultIntent
    ) else { return nil }
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

