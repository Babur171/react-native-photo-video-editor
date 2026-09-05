import UIKit

/// Loads filter-carousel previews off the main thread, mirroring `FilterThumbnailLoader.kt`.
///
/// The carousel offers a preset per look, so rendering each one from the full-resolution photo would
/// stall the panel every time it opens. Each preset is instead applied to one small square scaled
/// from the source, and the result is cached by preset id for the life of the editor.
final class FilterThumbnailLoader {
  private let queue = DispatchQueue(label: "filter-thumbnail-loader", qos: .userInitiated)
  private let cache = NSCache<NSString, UIImage>()
  private var scaledSource: UIImage?
  private var scaledSourceSize: CGFloat = 0

  func load(source: UIImage?, presetID: String, size: CGFloat, deliver: @escaping (UIImage?) -> Void) {
    guard let source else {
      deliver(nil)
      return
    }
    if let cached = cache.object(forKey: presetID as NSString) {
      deliver(cached)
      return
    }
    // Scale once and reuse for every preset rather than per thumbnail.
    let square = squareSource(from: source, size: size)
    queue.async { [weak self] in
      let rendered = PhotoAdjustmentRenderer.apply(
        square,
        adjustments: PhotoAdjustments(filterPreset: presetID, filterStrength: 100)
      )
      self?.cache.setObject(rendered, forKey: presetID as NSString)
      DispatchQueue.main.async { deliver(rendered) }
    }
  }

  private func squareSource(from source: UIImage, size: CGFloat) -> UIImage {
    if let scaledSource, scaledSourceSize == size { return scaledSource }
    let scale = UIScreen.main.scale
    let pixelSize = CGSize(width: size * scale, height: size * scale)
    let renderer = PhotoEditSession.pixelRenderer(size: pixelSize)
    // Aspect-fill into a square so each thumbnail frames the photo the same way.
    let fitted = renderer.image { _ in
      let sourceSize = source.size
      let ratio = max(pixelSize.width / max(sourceSize.width, 1), pixelSize.height / max(sourceSize.height, 1))
      let drawSize = CGSize(width: sourceSize.width * ratio, height: sourceSize.height * ratio)
      source.draw(in: CGRect(
        x: (pixelSize.width - drawSize.width) / 2,
        y: (pixelSize.height - drawSize.height) / 2,
        width: drawSize.width,
        height: drawSize.height
      ))
    }
    scaledSource = fitted
    scaledSourceSize = size
    return fitted
  }

  func invalidate() {
    cache.removeAllObjects()
    scaledSource = nil
    scaledSourceSize = 0
  }
}
