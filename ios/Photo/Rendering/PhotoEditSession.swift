import ImageIO
import UIKit

/// Owns an in-memory, downsampled working copy of the source photo for fast,
/// memory-safe live preview. The full-resolution source is decoded again,
/// once, only at export time by `PhotoExporter`.
final class PhotoEditSession {
  private(set) var state = PhotoTransformState()
  private(set) var adjustments = PhotoAdjustments()
  let layerStack = PhotoLayerStack()
  let baseImage: UIImage?

  private var cachedBaseImage: UIImage?
  private var cachedState: PhotoTransformState?
  private var cachedAdjustments: PhotoAdjustments?
  private var cachedPreviewImage: UIImage?
  private weak var cachedPreviewBaseImage: UIImage?
  private var cachedPreviewLayerRevision: UInt64?
  /// Resolved images for both sticker-uri and overlay-uri layers — the lookup logic is identical
  /// for either layer type, so one cache/resolver serves both.
  private var imageLayerCache: [String: UIImage?] = [:]

  init(sourceUri: String, maxPreviewDimension: CGFloat = 1600) {
    baseImage = PhotoEditSession.decodeUpright(sourceUri: sourceUri, maxDimension: maxPreviewDimension)
  }

  func update(_ transform: (inout PhotoTransformState) -> Void) {
    transform(&state)
  }

  func updateAdjustments(_ transform: (inout PhotoAdjustments) -> Void) {
    transform(&adjustments)
  }

  /// Rotate/flip/straighten + adjustments only, cached so dragging a layer doesn't re-run the full pipeline every frame.
  private func computeBase() -> UIImage? {
    guard let base = baseImage else { return nil }
    if let cached = cachedBaseImage, cachedState == state, cachedAdjustments == adjustments {
      return cached
    }
    let transformed: UIImage
    if state.rotationDegrees == 0 && state.straightenDegrees == 0 {
      transformed = base
    } else {
      transformed = PhotoEditSession.applyTransform(base, state: state)
    }
    let adjusted = PhotoAdjustmentRenderer.apply(transformed, adjustments: adjustments)
    cachedBaseImage = adjusted
    cachedState = state
    cachedAdjustments = adjustments
    return adjusted
  }

  /// Renders rotate/flip/straighten + adjustments + layers — the crop overlay draws the crop live on top of this.
  func renderPreview() -> UIImage? {
    guard let base = computeBase() else { return nil }
    if cachedPreviewBaseImage === base,
       cachedPreviewLayerRevision == layerStack.revision,
       let cachedPreviewImage {
      return cachedPreviewImage
    }
    let rendered = PhotoLayerRenderer.render(base, layers: layerStack.layers) { [weak self] uri in self?.resolveImageLayer(uri) }
    cachedPreviewBaseImage = base
    cachedPreviewLayerRevision = layerStack.revision
    cachedPreviewImage = rendered
    return rendered
  }

  func release() {
    cachedBaseImage = nil
    cachedState = nil
    cachedAdjustments = nil
    cachedPreviewImage = nil
    cachedPreviewBaseImage = nil
    cachedPreviewLayerRevision = nil
    imageLayerCache.removeAll()
  }

  private func resolveImageLayer(_ uri: String) -> UIImage? {
    if let cached = imageLayerCache[uri] { return cached }
    let path = SourceResolver.resolvePath(sourceUri: uri, tempPrefix: "pve_layer_image")
    let image = path.flatMap { UIImage(contentsOfFile: $0) }
    imageLayerCache[uri] = image
    return image
  }

  /// A renderer whose output pixel size exactly matches its point size, so
  /// width/height math stays consistent with `CGImage.width`/`height` elsewhere.
  static func pixelRenderer(size: CGSize) -> UIGraphicsImageRenderer {
    let format = UIGraphicsImageRendererFormat()
    format.scale = 1
    return UIGraphicsImageRenderer(size: size, format: format)
  }

  static func applyTransform(_ image: UIImage, state: PhotoTransformState) -> UIImage {
    guard state.rotationDegrees != 0 || state.straightenDegrees != 0 else { return image }
    let radians = state.totalRotationRadians
    let rotatedBounds = CGRect(origin: .zero, size: image.size).applying(CGAffineTransform(rotationAngle: radians))
    let canvasSize = CGSize(width: abs(rotatedBounds.width), height: abs(rotatedBounds.height))
    let renderer = pixelRenderer(size: canvasSize)
    return renderer.image { context in
      let ctx = context.cgContext
      ctx.translateBy(x: canvasSize.width / 2, y: canvasSize.height / 2)
      ctx.rotate(by: radians)
      image.draw(in: CGRect(x: -image.size.width / 2, y: -image.size.height / 2, width: image.size.width, height: image.size.height))
    }
  }

  private static func decodeUpright(sourceUri: String, maxDimension: CGFloat) -> UIImage? {
    guard let path = SourceResolver.resolvePath(sourceUri: sourceUri, tempPrefix: "pve_preview") else { return nil }
    let url = URL(fileURLWithPath: path)
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
    let options: [CFString: Any] = [
      kCGImageSourceCreateThumbnailFromImageAlways: true,
      kCGImageSourceThumbnailMaxPixelSize: maxDimension,
      kCGImageSourceCreateThumbnailWithTransform: true,
    ]
    guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
    return UIImage(cgImage: cgImage)
  }
}
