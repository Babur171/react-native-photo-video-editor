import Foundation

/// Resolves an editor source uri (file:// or a bare path) to a local
/// filesystem path that ImageIO can read directly. Photos-library identifiers
/// (ph://) and remote https:// sources are not yet supported.
enum SourceResolver {
  static func resolvePath(sourceUri: String, tempPrefix: String) -> String? {
    guard let url = URL(string: sourceUri) else { return nil }
    if url.isFileURL { return url.path }
    if url.scheme == nil { return sourceUri }
    return nil
  }
}
