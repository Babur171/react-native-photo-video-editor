import UIKit

/// Draws a selection outline around the selected layer and handles tap-to-select
/// and single-finger drag-to-move. Hit testing uses an axis-aligned box around
/// each layer's center (it does not account for the layer's own rotation) —
/// a documented simplification. Mirrors `LayerOverlayView.kt`.
final class LayerOverlayView: UIView {
  private var imageBounds: CGRect = .zero
  var layers: [PhotoLayer] = [] { didSet { setNeedsDisplay() } }
  var selectedLayerID: String? { didSet { setNeedsDisplay() } }

  /// Fires on touch-down with the tapped layer's id, or nil when the tap hit nothing.
  var onLayerTapped: ((String?) -> Void)?
  /// Fires on every touch-move while dragging, with the new normalized (0...1) center.
  var onLayerDragged: ((_ id: String, _ x: CGFloat, _ y: CGFloat) -> Void)?
  /// Fires once when a drag gesture ends, so the caller can commit one undo entry.
  var onLayerDragEnded: ((String) -> Void)?

  private var draggingID: String?
  private var lastPoint: CGPoint = .zero

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
  }

  required init?(coder: NSCoder) { nil }

  func setImageBounds(_ bounds: CGRect) {
    imageBounds = bounds
    setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    guard imageBounds.width > 0, let selected = layers.first(where: { $0.id == selectedLayerID }),
          let ctx = UIGraphicsGetCurrentContext() else { return }
    let center = CGPoint(x: imageBounds.minX + selected.x * imageBounds.width, y: imageBounds.minY + selected.y * imageBounds.height)
    let halfSize = min(imageBounds.width, imageBounds.height) * 0.16 * selected.scale
    ctx.saveGState()
    ctx.translateBy(x: center.x, y: center.y)
    ctx.rotate(by: selected.rotationDegrees * .pi / 180)
    ctx.setStrokeColor(UIColor.white.cgColor)
    ctx.setLineWidth(2)
    ctx.setLineDash(phase: 0, lengths: [8, 6])
    ctx.stroke(CGRect(x: -halfSize, y: -halfSize, width: halfSize * 2, height: halfSize * 2))
    ctx.restoreGState()
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard imageBounds.width > 0, let point = touches.first?.location(in: self) else { return }
    let hit = hitTest(point)
    draggingID = (hit?.locked == false) ? hit?.id : nil
    lastPoint = point
    onLayerTapped?(hit?.id)
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let id = draggingID, let point = touches.first?.location(in: self),
          let layer = layers.first(where: { $0.id == id }), imageBounds.width > 0 else { return }
    let dx = (point.x - lastPoint.x) / imageBounds.width
    let dy = (point.y - lastPoint.y) / imageBounds.height
    lastPoint = point
    let newX = min(max(layer.x + dx, 0), 1)
    let newY = min(max(layer.y + dy, 0), 1)
    onLayerDragged?(id, newX, newY)
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let id = draggingID { onLayerDragEnded?(id) }
    draggingID = nil
  }

  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let id = draggingID { onLayerDragEnded?(id) }
    draggingID = nil
  }

  private func hitTest(_ point: CGPoint) -> PhotoLayer? {
    let halfSizeBase = min(imageBounds.width, imageBounds.height) * 0.16
    for layer in layers.reversed() where layer.visible {
      let center = CGPoint(x: imageBounds.minX + layer.x * imageBounds.width, y: imageBounds.minY + layer.y * imageBounds.height)
      let halfSize = halfSizeBase * layer.scale
      let box = CGRect(x: center.x - halfSize, y: center.y - halfSize, width: halfSize * 2, height: halfSize * 2)
      if box.contains(point) { return layer }
    }
    return nil
  }
}
