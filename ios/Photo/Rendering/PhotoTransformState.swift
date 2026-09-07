import CoreGraphics

/// Non-destructive photo edit state. The crop rectangle is normalized (0...1)
/// against the *transformed* (rotated/straightened) image, matching
/// what the user sees in the crop overlay — the original source file is never
/// modified, only read again at export time.
struct PhotoTransformState: Equatable {
  var cropLeft: CGFloat = 0
  var cropTop: CGFloat = 0
  var cropRight: CGFloat = 1
  var cropBottom: CGFloat = 1
  var rotationDegrees: Int = 0
  var straightenDegrees: CGFloat = 0
  var aspectRatio: CGFloat?
  var aspectPreset = "Free"
  // Zoom/pan are viewport metadata; normalized crop coordinates already encode them for export.
  var zoom: CGFloat = 1
  var panX: CGFloat = 0
  var panY: CGFloat = 0

  var isIdentity: Bool {
    cropLeft == 0 && cropTop == 0 && cropRight == 1 && cropBottom == 1 &&
      rotationDegrees == 0 && straightenDegrees == 0
  }

  /// Rotating changes which pixels a normalized crop rect refers to, so the crop resets to full-frame.
  mutating func rotateRight() {
    rotationDegrees = (rotationDegrees + 90) % 360
    cropLeft = 0
    cropTop = 0
    cropRight = 1
    cropBottom = 1
  }

  mutating func setStraighten(_ degrees: CGFloat) {
    straightenDegrees = max(-45, min(45, degrees))
  }

  mutating func setCrop(left: CGFloat, top: CGFloat, right: CGFloat, bottom: CGFloat) {
    cropLeft = max(0, min(1, left))
    cropTop = max(0, min(1, top))
    cropRight = max(0, min(1, right))
    cropBottom = max(0, min(1, bottom))
  }

  mutating func reset() {
    self = PhotoTransformState()
  }

  /// Combined rotate + straighten radians, applied around the image's own center by the caller.
  var totalRotationRadians: CGFloat {
    (CGFloat(rotationDegrees) + straightenDegrees) * .pi / 180
  }
}
