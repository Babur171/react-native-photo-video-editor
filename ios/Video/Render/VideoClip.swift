import Foundation

/// One segment in the multi-clip timeline (Milestone 7). Mirrors `VideoClip.kt`.
/// Every clip in this milestone shares the same source file (created via
/// Split/Duplicate of one input) — importing separate additional source files
/// from a system picker is a follow-up, not attempted here.
struct VideoClip: Equatable {
  var id: String = UUID().uuidString
  let sourceUri: String
  let originalDurationMs: Int64
  var trimStartMs: Int64 = 0
  var trimEndMs: Int64 = 0

  func effectiveTrimEndMs() -> Int64 {
    (trimEndMs > 0 && trimEndMs <= originalDurationMs) ? trimEndMs : originalDurationMs
  }

  func trimmedDurationMs() -> Int64 {
    max(0, effectiveTrimEndMs() - trimStartMs)
  }
}
