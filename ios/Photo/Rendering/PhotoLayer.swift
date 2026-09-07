import UIKit

enum LayerType {
  case text, sticker, overlay, drawing
}

/// A single non-destructive overlay layer (text, sticker, or a user-uploaded
/// overlay image). One flat model covers every type for simplicity — a
/// documented simplification versus a per-type hierarchy. Mirrors
/// `PhotoLayer.kt`.
///
/// `x`/`y` are the layer's center, normalized 0..1 against the image.
struct PhotoLayer {
  var id: String
  var type: LayerType
  var x: CGFloat = 0.5
  var y: CGFloat = 0.5
  var scale: CGFloat = 1
  var rotationDegrees: CGFloat = 0
  var opacity: CGFloat = 1
  var locked = false
  var visible = true
  // Text
  var text: String = ""
  var textColor: UIColor = .white
  var fontSize: CGFloat = 48
  var fontFamily: String?
  // Sticker
  var stickerId: String?
  var stickerUri: String?
  // Overlay (user-uploaded image, placed as a fully generic movable/resizable/rotatable layer)
  var overlayUri: String?
  var overlayAspectRatio: CGFloat?
  // Drawing (points are normalized offsets from x/y)
  var drawColor: UIColor = .red
  var drawStrokeWidth: CGFloat = 0.012
  var drawPoints: [(CGFloat, CGFloat)] = []
  // Timing (video overlays only; ignored for photo layers). endMs <= 0 means "to end of video".
  var startMs: Int64 = 0
  var endMs: Int64 = 0

  init(id: String = UUID().uuidString, type: LayerType) {
    self.id = id
    self.type = type
  }

  func duplicated() -> PhotoLayer {
    var copy = self
    copy.id = UUID().uuidString
    copy.x = min(max(x + 0.04, 0), 1)
    copy.y = min(max(y + 0.04, 0), 1)
    return copy
  }

  /// True if this layer should be visible at `positionMs` against a video of `durationMs`. Always true for photo layers.
  func effectiveEndMs(durationMs: Int64) -> Int64 {
    (endMs > 0 && endMs <= durationMs) ? endMs : durationMs
  }

  func isActive(atMs positionMs: Int64, durationMs: Int64) -> Bool {
    guard visible, durationMs >= 0 else { return false }
    let clamped = max(positionMs, 0)
    guard clamped >= max(startMs, 0) else { return false }
    // No explicit end (or one at/past the video's end) means "visible through the real last
    // frame" — don't compare against `durationMs`, which is a separately-computed estimate
    // that can be a few ms shy of the true final frame's timestamp due to frame-rate rounding,
    // which would otherwise hide the overlay early.
    if endMs <= 0 || endMs >= durationMs { return true }
    return clamped <= endMs
  }
}
