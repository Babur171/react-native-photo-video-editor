import Foundation

/// What the native editor produced when it finished. `.success` metadata is
/// populated for photo and video exports alike; `durationMs` is video-only.
enum PhotoVideoEditorOutcome {
  case cancelled
  case success(uri: String, mimeType: String?, width: Int?, height: Int?, fileSize: Int?, durationMs: Int64? = nil)
  case failure(code: String, message: String)
}
