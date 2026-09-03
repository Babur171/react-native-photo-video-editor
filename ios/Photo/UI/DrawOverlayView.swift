import UIKit

/// One freehand stroke: its points (view coordinates while drawing) plus the
/// color/width active when it started.
struct DrawStroke {
  var points: [CGPoint]
  let color: UIColor
  let widthPx: CGFloat
}

/// Captures freehand strokes over the image while in draw mode. Each
/// finger-down..up gesture is one stroke, recorded with the color/width
/// active at the moment it started. Mirrors `DrawOverlayView.kt`.
final class DrawOverlayView: UIView {
  private var imageBounds: CGRect = .zero
  var strokeColor: UIColor = .red
  var strokeWidthPx: CGFloat = 12
  private var strokes: [DrawStroke] = []
  private var currentIndex: Int?
  var onStrokesChanged: (([DrawStroke]) -> Void)?

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
  }

  required init?(coder: NSCoder) { nil }

  func setImageBounds(_ bounds: CGRect) {
    imageBounds = bounds
  }

  func clearStrokes() {
    strokes.removeAll()
    currentIndex = nil
    setNeedsDisplay()
    onStrokesChanged?(strokes)
  }

  func undoLastStroke() {
    if !strokes.isEmpty { strokes.removeLast() }
    setNeedsDisplay()
    onStrokesChanged?(strokes)
  }

  var hasStrokes: Bool { !strokes.isEmpty }

  /// Each stroke's points normalized (0...1) against the current image bounds, paired with its stroke metadata.
  func normalizedStrokes() -> [(points: [(CGFloat, CGFloat)], stroke: DrawStroke)] {
    guard imageBounds.width > 0 else { return [] }
    return strokes.map { stroke in
      let normalized = stroke.points.map { point in
        ((point.x - imageBounds.minX) / imageBounds.width, (point.y - imageBounds.minY) / imageBounds.height)
      }
      return (normalized, stroke)
    }
  }

  override func draw(_ rect: CGRect) {
    guard let ctx = UIGraphicsGetCurrentContext() else { return }
    ctx.setLineCap(.round)
    ctx.setLineJoin(.round)
    for stroke in strokes where stroke.points.count >= 2 {
      ctx.setStrokeColor(stroke.color.cgColor)
      ctx.setLineWidth(stroke.widthPx)
      ctx.move(to: stroke.points[0])
      for point in stroke.points.dropFirst() { ctx.addLine(to: point) }
      ctx.strokePath()
    }
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard imageBounds.width > 0, let point = touches.first?.location(in: self) else { return }
    strokes.append(DrawStroke(points: [point], color: strokeColor, widthPx: strokeWidthPx))
    currentIndex = strokes.count - 1
    setNeedsDisplay()
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let index = currentIndex, let point = touches.first?.location(in: self) else { return }
    strokes[index].points.append(point)
    setNeedsDisplay()
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    currentIndex = nil
    onStrokesChanged?(strokes)
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    currentIndex = nil
    onStrokesChanged?(strokes)
  }
}
