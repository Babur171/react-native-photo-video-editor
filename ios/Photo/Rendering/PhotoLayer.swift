import UIKit

enum LayerType {
  case text, sticker, shape, drawing
}

enum ShapeKind {
  case rectangle, oval, line
}

/// A single non-destructive overlay layer (text, sticker, shape, or freehand
/// drawing). One flat model covers every type for simplicity — a documented
/// simplification versus a per-type hierarchy. Mirrors `PhotoLayer.kt`.
///
/// `x`/`y` are the layer's center, normalized 0..1 against the image. For
/// drawing layers, `drawPoints` are offsets from that center, also normalized
/// against image width/height, so the whole stroke moves/scales/rotates as a
/// unit with the layer's transform.
struct PhotoLayer {
  let id: String
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
  // Shape
  var shapeKind: ShapeKind = .rectangle
  var shapeColor: UIColor = .white
  var shapeFilled: Bool = true
  // Drawing
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

  /// True if this layer should be visible at `positionMs` against a video of `durationMs`. Always true for photo layers.
  func isActive(atMs positionMs: Int64, durationMs: Int64) -> Bool {
    let effectiveEnd = (endMs > 0 && endMs <= durationMs) ? endMs : durationMs
    return positionMs >= startMs && positionMs <= effectiveEnd
  }
}
