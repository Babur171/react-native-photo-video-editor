import Foundation

enum EditorRequestValidator {
  static func errorCode(for request: EditorRequest) -> String? {
    if request.uri.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return "E_INVALID_URI" }
    if request.mediaType != "photo" && request.mediaType != "video" { return "E_UNSUPPORTED_MEDIA_TYPE" }
    return nil
  }
}
