import ImageIO
import UIKit

struct PhotoExportResult {
  let uri: String
  let width: Int
  let height: Int
  let fileSize: Int
  let mimeType: String
}

struct PhotoExportError: Error {
  let code: String
  let message: String
}

/// Applies a `PhotoTransformState` to the full-resolution source photo and
/// writes the result to a new file under the app's temporary directory. The
/// source file is never modified. WebP export is not implemented on iOS
/// (no first-party encoder); JPEG and PNG are supported.
enum PhotoExporter {
  private static let defaultMaxDimension: CGFloat = 4096

  static func export(
    sourceUri: String,
    transform: PhotoTransformState,
    adjustments: PhotoAdjustments,
    layers: [PhotoLayer],
    exportOptions: [String: Any]?
  ) throws -> PhotoExportResult {
    guard let path = SourceResolver.resolvePath(sourceUri: sourceUri, tempPrefix: "pve_export") else {
      throw PhotoExportError(code: "E_SOURCE_NOT_FOUND", message: "The selected photo could not be found.")
    }
    let url = URL(fileURLWithPath: path)
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else {
      throw PhotoExportError(code: "E_SOURCE_UNREADABLE", message: "The selected photo could not be decoded.")
    }

    let maxWidth = (exportOptions?["maxWidth"] as? NSNumber)?.doubleValue.map { CGFloat($0) }
    let maxHeight = (exportOptions?["maxHeight"] as? NSNumber)?.doubleValue.map { CGFloat($0) }
    let decodeTarget = max(maxWidth ?? defaultMaxDimension, maxHeight ?? defaultMaxDimension) * 2

    let thumbnailOptions: [CFString: Any] = [
      kCGImageSourceCreateThumbnailFromImageAlways: true,
      kCGImageSourceThumbnailMaxPixelSize: decodeTarget,
      kCGImageSourceCreateThumbnailWithTransform: true,
    ]
    guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbnailOptions as CFDictionary) else {
      throw PhotoExportError(code: "E_OUT_OF_MEMORY", message: "The photo is too large to process on this device.")
    }
    let upright = UIImage(cgImage: cgImage)

    let transformed = PhotoEditSession.applyTransform(upright, state: transform)
    let adjusted = PhotoAdjustmentRenderer.apply(transformed, adjustments: adjustments)
    let layered = PhotoLayerRenderer.render(adjusted, layers: layers) { uri in resolveImageLayer(uri) }
    let cropped = crop(layered, transform: transform)
    let resized = resize(cropped, maxWidth: maxWidth, maxHeight: maxHeight)

    let format = (exportOptions?["imageFormat"] as? String)?.lowercased() ?? "jpeg"
    let quality = qualityFor(exportOptions?["quality"] as? String ?? "high")

    let outputDir = FileManager.default.temporaryDirectory.appendingPathComponent("photovideoeditor", isDirectory: true)
    try? FileManager.default.createDirectory(at: outputDir, withIntermediateDirectories: true)

    let data: Data?
    let mimeType: String
    let ext: String
    switch format {
    case "png":
      data = resized.pngData()
      mimeType = "image/png"
      ext = "png"
    default:
      // WebP encoding is not implemented on iOS; unsupported formats fall back to JPEG.
      data = resized.jpegData(compressionQuality: quality)
      mimeType = "image/jpeg"
      ext = "jpg"
    }
    guard let outputData = data else {
      throw PhotoExportError(code: "E_EXPORT_FAILED", message: "Unable to encode the exported photo.")
    }

    let outputFile = outputDir.appendingPathComponent(UUID().uuidString + "." + ext)
    do {
      try outputData.write(to: outputFile)
    } catch {
      throw PhotoExportError(code: "E_EXPORT_FAILED", message: "Unable to write the exported photo.")
    }

    let attributes = try? FileManager.default.attributesOfItem(atPath: outputFile.path)
    let fileSize = (attributes?[.size] as? Int) ?? outputData.count

    return PhotoExportResult(
      uri: outputFile.absoluteString,
      width: Int(resized.size.width),
      height: Int(resized.size.height),
      fileSize: fileSize,
      mimeType: mimeType
    )
  }

  /// Export-time, one-shot resolver for sticker-uri and overlay-uri layers alike (no cache needed —
  /// export runs once per invocation).
  private static func resolveImageLayer(_ uri: String) -> UIImage? {
    guard let path = SourceResolver.resolvePath(sourceUri: uri, tempPrefix: "pve_layer_export") else { return nil }
    return UIImage(contentsOfFile: path)
  }

  private static func crop(_ image: UIImage, transform: PhotoTransformState) -> UIImage {
    guard transform.cropLeft > 0 || transform.cropTop > 0 || transform.cropRight < 1 || transform.cropBottom < 1 else { return image }
    guard let cgImage = image.cgImage else { return image }
    let width = CGFloat(cgImage.width)
    let height = CGFloat(cgImage.height)
    let fullRect = CGRect(x: 0, y: 0, width: width, height: height)
    let rect = CGRect(
      x: transform.cropLeft * width,
      y: transform.cropTop * height,
      width: (transform.cropRight - transform.cropLeft) * width,
      height: (transform.cropBottom - transform.cropTop) * height
    ).integral.intersection(fullRect)
    guard rect.width > 0, rect.height > 0, let croppedImage = cgImage.cropping(to: rect) else { return image }
    return UIImage(cgImage: croppedImage)
  }

  private static func resize(_ image: UIImage, maxWidth: CGFloat?, maxHeight: CGFloat?) -> UIImage {
    guard maxWidth != nil || maxHeight != nil else { return image }
    let widthLimit = maxWidth ?? image.size.width
    let heightLimit = maxHeight ?? image.size.height
    guard image.size.width > widthLimit || image.size.height > heightLimit else { return image }
    let scale = min(widthLimit / image.size.width, heightLimit / image.size.height)
    let targetSize = CGSize(width: max(1, image.size.width * scale), height: max(1, image.size.height * scale))
    let renderer = PhotoEditSession.pixelRenderer(size: targetSize)
    return renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: targetSize)) }
  }

  private static func qualityFor(_ preset: String) -> CGFloat {
    switch preset {
    case "low": return 0.5
    case "medium": return 0.75
    case "original": return 1.0
    default: return 0.9
    }
  }
}
