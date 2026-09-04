import UIKit

struct DrawStroke {
  var points: [CGPoint]
  let color: UIColor
  let widthPx: CGFloat
}

/** Captures freehand strokes over the visible image while Draw mode is active. */
final class DrawOverlayView: UIView {
  private var imageBounds: CGRect = .zero
  var strokeColor: UIColor = .red
  var strokeWidthPx: CGFloat = 12
  private var strokes: [DrawStroke] = []
  private var currentIndex: Int?

  override init(frame: CGRect) { super.init(frame: frame); backgroundColor = .clear; isOpaque = false }
  required init?(coder: NSCoder) { nil }
  func setImageBounds(_ bounds: CGRect) { imageBounds = bounds }
  func clearStrokes() { strokes.removeAll(); currentIndex = nil; setNeedsDisplay() }
  func undoLastStroke() { if !strokes.isEmpty { strokes.removeLast() }; setNeedsDisplay() }
  var hasStrokes: Bool { !strokes.isEmpty }
  func normalizedStrokes() -> [(points: [(CGFloat, CGFloat)], stroke: DrawStroke)] {
    guard imageBounds.width > 0, imageBounds.height > 0 else { return [] }
    return strokes.map { stroke in
      (stroke.points.map { (($0.x - imageBounds.minX) / imageBounds.width, ($0.y - imageBounds.minY) / imageBounds.height) }, stroke)
    }
  }

  override func draw(_ rect: CGRect) {
    guard let ctx = UIGraphicsGetCurrentContext() else { return }
    ctx.setLineCap(.round); ctx.setLineJoin(.round)
    for stroke in strokes where stroke.points.count >= 2 {
      ctx.setStrokeColor(stroke.color.cgColor); ctx.setLineWidth(stroke.widthPx)
      ctx.move(to: stroke.points[0]); stroke.points.dropFirst().forEach { ctx.addLine(to: $0) }; ctx.strokePath()
    }
  }
  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let point = touches.first?.location(in: self), imageBounds.contains(point) else { return }
    strokes.append(DrawStroke(points: [point], color: strokeColor, widthPx: strokeWidthPx)); currentIndex = strokes.count - 1; setNeedsDisplay()
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let index = currentIndex, let point = touches.first?.location(in: self) else { return }
    strokes[index].points.append(point); setNeedsDisplay()
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { currentIndex = nil }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { currentIndex = nil }
}
