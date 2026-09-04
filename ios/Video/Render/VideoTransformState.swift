import CoreGraphics

/// Non-destructive video edit state that applies globally across the whole
/// multi-clip timeline (Milestone 7) — trim now lives per clip on `VideoClip`
/// instead of here, mirroring `VideoTransformState.kt`. `coverFrameMs` is a
/// position on the composed timeline (which, on iOS, is exactly
/// `session.player.currentTime()` since the preview player plays a single
/// `AVMutableComposition` concatenating every clip — see `VideoEditSession`).
struct VideoTransformState: Equatable {
  var rotationDegrees: Int = 0
  var aspectRatio: CGFloat?
  var coverFrameMs: Int64 = 0
  /// Preview-only for now: cycled via the Speed tool. Export does not yet honor this — see docs/video-editor.md.
  var speed: Float = 1

  mutating func rotateRight() { rotationDegrees = (rotationDegrees + 90) % 360 }

  private static let speedSteps: [Float] = [0.5, 1, 1.5, 2]
  mutating func cycleSpeed() {
    let currentIndex = Self.speedSteps.firstIndex(of: speed) ?? 1
    speed = Self.speedSteps[(currentIndex + 1) % Self.speedSteps.count]
  }
}
