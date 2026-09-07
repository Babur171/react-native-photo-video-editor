import Foundation

/// Resolves an editor source uri (file:// or a bare path) to a local
/// filesystem path that ImageIO can read directly. Photos-library identifiers
/// (ph://) and remote https:// sources are not yet supported.
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
  }
}
