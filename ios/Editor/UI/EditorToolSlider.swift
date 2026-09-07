import UIKit

/// Native slider with a compact property title/value above its track.
final class EditorToolSlider: UISlider {
  var propertyKey = "" { didSet { accessibilityLabel = propertyKey; setNeedsDisplay() } }
  override var intrinsicContentSize: CGSize { CGSize(width: UIView.noIntrinsicMetric, height: 64) }
  override func trackRect(forBounds bounds: CGRect) -> CGRect {
    var rect = super.trackRect(forBounds: bounds)
    rect.origin.y = bounds.height * 0.65
    rect.origin.x = bounds.origin.x + 16
    rect.size.width = max(0, bounds.width - 32)
    return rect
  }
  override func draw(_ rect: CGRect) {
    super.draw(rect)
    let title = propertyKey == "fontSize" ? "Size" : (propertyKey == "rotation" ? "Rotate" : propertyKey.capitalized)
    let suffix = propertyKey == "rotation" ? "°" : (propertyKey == "fontSize" ? "" : "%")
    let valueText = "\(Int(value.rounded()))\(suffix)"
    let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.monospacedDigitSystemFont(ofSize: 12, weight: .medium), .foregroundColor: DesignTokens.textSecondary]
    (title as NSString).draw(at: CGPoint(x: 16, y: 8), withAttributes: attributes)
    let size = (valueText as NSString).size(withAttributes: attributes)
    (valueText as NSString).draw(at: CGPoint(x: bounds.width - size.width - 16, y: 8), withAttributes: attributes)
  }
}
