import AVFoundation
import Foundation
import Photos
import UIKit

/// Resolves an editor source URI (file://, bare path, ph://, or remote/data URI)
/// to a local filesystem path that ImageIO / AVFoundation can read directly.
enum SourceResolver {
  static func resolvePath(sourceUri: String, tempPrefix: String) -> String? {
    if sourceUri.hasPrefix("data:image/png;base64,") {
      guard let separator = sourceUri.firstIndex(of: ","),
            let data = Data(base64Encoded: String(sourceUri[sourceUri.index(after: separator)...])) else { return nil }
      let file = FileManager.default.temporaryDirectory.appendingPathComponent("\(tempPrefix)_\(UUID().uuidString).png")
      do {
        try data.write(to: file)
        return file.path
      } catch { return nil }
    }
    guard let url = URL(string: sourceUri) else { return nil }
    if url.isFileURL { return url.path }
    if url.scheme == nil { return sourceUri }
    return nil
    let trimmed = sourceUri.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty else { return nil }

    // 1. Direct filesystem path
    if trimmed.hasPrefix("/") {
      if FileManager.default.fileExists(atPath: trimmed) { return trimmed }
      if let decoded = trimmed.removingPercentEncoding, FileManager.default.fileExists(atPath: decoded) { return decoded }
      return trimmed
    }

    // 2. file:// URI
    if trimmed.hasPrefix("file://") {
      if let url = URL(string: trimmed), url.isFileURL {
        return url.path
      }
      let stripped = String(trimmed.dropFirst(7))
      if let decoded = stripped.removingPercentEncoding, FileManager.default.fileExists(atPath: decoded) {
        return decoded
      }
      return stripped
    }

    // 3. ph:// Photo Library identifier (from react-native-image-picker or Photos)
    if trimmed.hasPrefix("ph://") {
      let localId = String(trimmed.dropFirst(5))
      return resolvePHAsset(localIdentifier: localId, tempPrefix: tempPrefix)
    }

    // 4. assets-library:// legacy identifier
    if trimmed.hasPrefix("assets-library://") {
      if let url = URL(string: trimmed) {
        let fetchResult = PHAsset.fetchAssets(withALAssetURLs: [url], options: nil)
        if let asset = fetchResult.firstObject {
          return exportPHAsset(asset, tempPrefix: tempPrefix)
        }
      }
    }

    // 5. Remote http:// or https:// URL
    if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
      if let url = URL(string: trimmed) {
        return downloadRemoteURL(url, tempPrefix: tempPrefix)
      }
    }

    // 6. Base64 data: URI
    if trimmed.hasPrefix("data:") {
      return resolveDataURI(trimmed, tempPrefix: tempPrefix)
    }

    // 7. Fallback for un-schemed relative or encoded path
    if let url = URL(string: trimmed), url.isFileURL {
      return url.path
    }
    if let decoded = trimmed.removingPercentEncoding, FileManager.default.fileExists(atPath: decoded) {
      return decoded
    }
    return trimmed
  }

  private static func resolvePHAsset(localIdentifier: String, tempPrefix: String) -> String? {
    let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil)
    guard let asset = fetchResult.firstObject else { return nil }
    return exportPHAsset(asset, tempPrefix: tempPrefix)
  }

  private static func exportPHAsset(_ asset: PHAsset, tempPrefix: String) -> String? {
    let tempDir = FileManager.default.temporaryDirectory
    if asset.mediaType == .video {
      let semaphore = DispatchSemaphore(value: 0)
      var resolvedPath: String?
      let options = PHVideoRequestOptions()
      options.isNetworkAccessAllowed = true
      options.version = .current
      options.deliveryMode = .highQualityFormat
      PHImageManager.default().requestAVAsset(forVideo: asset, options: options) { avAsset, _, _ in
        defer { semaphore.signal() }
        if let urlAsset = avAsset as? AVURLAsset {
          resolvedPath = urlAsset.url.path
        }
      }
      _ = semaphore.wait(timeout: .now() + 5)
      return resolvedPath
    } else {
      let options = PHImageRequestOptions()
      options.isNetworkAccessAllowed = true
      options.isSynchronous = true
      options.deliveryMode = .highQualityFormat
      var resolvedPath: String?
      PHImageManager.default().requestImageDataAndOrientation(for: asset, options: options) { data, _, _, _ in
        if let data {
          let ext = (data.prefix(3) == Data([0xFF, 0xD8, 0xFF])) ? "jpg" : "png"
          let outURL = tempDir.appendingPathComponent("\(tempPrefix)_\(UUID().uuidString).\(ext)")
          try? data.write(to: outURL)
          resolvedPath = outURL.path
        }
      }
      return resolvedPath
    }
  }

  private static func downloadRemoteURL(_ url: URL, tempPrefix: String) -> String? {
    let ext = url.pathExtension.isEmpty ? "tmp" : url.pathExtension
    let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(tempPrefix)_\(UUID().uuidString).\(ext)")
    guard let data = try? Data(contentsOf: url) else { return nil }
    try? data.write(to: outURL)
    return outURL.path
  }

  private static func resolveDataURI(_ dataUri: String, tempPrefix: String) -> String? {
    guard let commaIndex = dataUri.firstIndex(of: ",") else { return nil }
    let base64String = String(dataUri[dataUri.index(after: commaIndex)...])
    guard let data = Data(base64Encoded: base64String, options: .ignoreUnknownCharacters) else { return nil }
    let outURL = FileManager.default.temporaryDirectory.appendingPathComponent("\(tempPrefix)_\(UUID().uuidString).png")
    try? data.write(to: outURL)
    return outURL.path
  }
}

