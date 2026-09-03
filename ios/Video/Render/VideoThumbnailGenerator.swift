import AVFoundation
import UIKit

/// Extracts a fixed number of evenly spaced thumbnails for the trim strip.
/// Call off the main thread — `copyCGImage` decodes synchronously per frame.
enum VideoThumbnailGenerator {
  static func generate(asset: AVAsset, durationMs: Int64, count: Int = 12) -> [UIImage] {
    guard durationMs > 0 else { return [] }
    let generator = AVAssetImageGenerator(asset: asset)
    generator.appliesPreferredTrackTransform = true
    generator.requestedTimeToleranceBefore = .zero
    generator.requestedTimeToleranceAfter = .zero

    let stepMs = durationMs / Int64(count)
    var images: [UIImage] = []
    for index in 0..<count {
      let timeMs = index * Int(stepMs) + Int(stepMs) / 2
      let time = CMTime(value: Int64(timeMs), timescale: 1000)
      if let cgImage = try? generator.copyCGImage(at: time, actualTime: nil) {
        images.append(UIImage(cgImage: cgImage))
      }
    }
    return images
  }
}
