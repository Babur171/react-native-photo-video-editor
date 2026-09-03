import UIKit

/// Dual-handle trim range selector drawn over a horizontal thumbnail strip.
/// `startFraction`/`endFraction` are fractions (0...1) of the video's total
/// duration; a minimum gap between them is enforced so a trim can never
/// collapse to (or past) zero length. Mirrors `TrimRangeView.kt`.
final class TrimRangeView: UIView {
  var thumbnails: [UIImage] = [] { didSet { setNeedsDisplay() } }
  private(set) var startFraction: CGFloat = 0
  private(set) var endFraction: CGFloat = 1
  var minDurationFraction: CGFloat = 0.02
  var onRangeChanged: ((_ start: CGFloat, _ end: CGFloat) -> Void)?

  private let handleWidth: CGFloat = 28
  private var activeHandle: Handle = .none

  private enum Handle { case none, start, end }

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
  }

  required init?(coder: NSCoder) { nil }

  func setRange(start: CGFloat, end: CGFloat) {
    startFraction = min(max(start, 0), 1)
    endFraction = min(max(end, startFraction), 1)
    setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    guard let ctx = UIGraphicsGetCurrentContext() else { return }
    let w = bounds.width
    let h = bounds.height
    guard w > 0, h > 0 else { return }

    if thumbnails.isEmpty {
      ctx.setFillColor(UIColor.darkGray.cgColor)
      ctx.fill(bounds)
    } else {
      let thumbWidth = w / CGFloat(thumbnails.count)
      for (index, image) in thumbnails.enumerated() {
        image.draw(in: CGRect(x: CGFloat(index) * thumbWidth, y: 0, width: thumbWidth, height: h))
      }
    }

    let startX = startFraction * w
    let endX = endFraction * w
    ctx.setFillColor(UIColor.black.withAlphaComponent(0.63).cgColor)
    ctx.fill(CGRect(x: 0, y: 0, width: startX, height: h))
    ctx.fill(CGRect(x: endX, y: 0, width: w - endX, height: h))

    ctx.setStrokeColor(UIColor.white.cgColor)
    ctx.setLineWidth(3)
    ctx.stroke(CGRect(x: startX, y: 0, width: endX - startX, height: h))

    ctx.setFillColor(UIColor.white.cgColor)
    ctx.fill(CGRect(x: startX - handleWidth / 2, y: 0, width: handleWidth, height: h))
    ctx.fill(CGRect(x: endX - handleWidth / 2, y: 0, width: handleWidth, height: h))
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard bounds.width > 0, let point = touches.first?.location(in: self) else { return }
    let startX = startFraction * bounds.width
    let endX = endFraction * bounds.width
    if abs(point.x - startX) < handleWidth {
      activeHandle = .start
    } else if abs(point.x - endX) < handleWidth {
      activeHandle = .end
    } else {
      activeHandle = .none
    }
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard activeHandle != .none, bounds.width > 0, let point = touches.first?.location(in: self) else { return }
    let fraction = min(max(point.x / bounds.width, 0), 1)
    switch activeHandle {
    case .start: startFraction = min(fraction, endFraction - minDurationFraction)
    case .end: endFraction = max(fraction, startFraction + minDurationFraction)
    case .none: return
    }
    setNeedsDisplay()
    onRangeChanged?(startFraction, endFraction)
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { activeHandle = .none }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { activeHandle = .none }
}
