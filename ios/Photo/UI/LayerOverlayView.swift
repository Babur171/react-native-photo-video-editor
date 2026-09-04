import UIKit

/// Reusable native move/scale/rotate surface shared by every overlay type.
final class LayerOverlayView: UIView {
  private enum Gesture { case none, drag, handle, multi }
  private var imageBounds: CGRect = .zero
  var minScale: CGFloat = 0.2
  var maxScale: CGFloat = 8
  var layers: [PhotoLayer] = [] { didSet { setNeedsDisplay() } }
  var selectedLayerID: String? { didSet { setNeedsDisplay() } }
  var onLayerTapped: ((String?) -> Void)?
  var onLayerDoubleTapped: ((String) -> Void)?
  var onLayerTransformChanged: ((_ id: String, _ x: CGFloat, _ y: CGFloat, _ scale: CGFloat, _ rotationDegrees: CGFloat) -> Void)?
  var onLayerTransformEnded: ((String) -> Void)?

  private var gesture: Gesture = .none
  private var gestureID: String?
  private var startLayer: PhotoLayer?
  private var primaryTouch: UITouch?
  private var secondaryTouch: UITouch?
  private var startPoint: CGPoint = .zero
  private var startDistance: CGFloat = 1
  private var startAngle: CGFloat = 0
  private var startMidpoint: CGPoint = .zero
  private let handleRadius: CGFloat = 11
  private let handleTouchRadius: CGFloat = 30

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isOpaque = false
    isMultipleTouchEnabled = true
  }
  required init?(coder: NSCoder) { nil }

  func setImageBounds(_ bounds: CGRect) { imageBounds = bounds; setNeedsDisplay() }

  override func draw(_ rect: CGRect) {
    guard let layer = selectedLayer(), let context = UIGraphicsGetCurrentContext() else { return }
    let center = center(of: layer); let half = selectionHalfExtents(layer)
    context.saveGState()
    context.translateBy(x: center.x, y: center.y)
    context.rotate(by: layer.rotationDegrees * .pi / 180)
    context.setStrokeColor(UIColor.white.cgColor)
    context.setLineWidth(2)
    context.setLineDash(phase: 0, lengths: [8, 5])
    context.stroke(CGRect(x: -half.width, y: -half.height, width: half.width * 2, height: half.height * 2))
    context.setLineDash(phase: 0, lengths: [])
    context.setFillColor(UIColor.white.cgColor)
    context.fillEllipse(in: CGRect(x: half.width - handleRadius, y: half.height - handleRadius, width: handleRadius * 2, height: handleRadius * 2))
    context.setStrokeColor(UIColor(red: 87/255, green: 55/255, blue: 245/255, alpha: 1).cgColor)
    context.setLineWidth(2)
    let glyph = handleRadius * 0.52
    context.move(to: CGPoint(x: half.width - glyph, y: half.height + glyph)); context.addLine(to: CGPoint(x: half.width + glyph, y: half.height - glyph)); context.strokePath()
    context.restoreGState()
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard imageBounds.width > 0 else { return }
    for touch in touches {
      if primaryTouch == nil { primaryTouch = touch }
      else if secondaryTouch == nil { secondaryTouch = touch }
    }
    guard let primaryTouch else { return }
    let point = primaryTouch.location(in: self)
    if let secondaryTouch, let current = currentGestureLayer() {
      startLayer = current
      let metrics = multiMetrics(primaryTouch, secondaryTouch)
      startDistance = max(1, metrics.distance); startAngle = metrics.angle; startMidpoint = metrics.midpoint
      gesture = .multi
      return
    }
    let selected = selectedLayer()
    let handleHit = selected.flatMap { !$0.locked && isHandleHit($0, point) ? $0 : nil }
    let hit = handleHit ?? hitTest(point)
    onLayerTapped?(hit?.id)
    guard let hit, !hit.locked else { resetGesture(); return }
    gestureID = hit.id; startLayer = hit; startPoint = point
    if handleHit != nil {
      gesture = .handle
      let center = center(of: hit)
      startDistance = max(1, distance(center, point)); startAngle = angle(center, point)
    } else { gesture = .drag }
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let initial = startLayer, let primaryTouch else { return }
    let point = primaryTouch.location(in: self)
    switch gesture {
    case .drag:
      let x = clamp(initial.x + (point.x - startPoint.x) / imageBounds.width, 0, 1)
      let y = clamp(initial.y + (point.y - startPoint.y) / imageBounds.height, 0, 1)
      emit(initial, x: x, y: y, scale: initial.scale, rotation: initial.rotationDegrees)
    case .handle:
      let center = center(of: initial)
      let scale = clamp(initial.scale * distance(center, point) / startDistance, minScale, maxScale)
      let rotation = normalizeDegrees(initial.rotationDegrees + angleDelta(from: startAngle, to: angle(center, point)))
      emit(initial, x: initial.x, y: initial.y, scale: scale, rotation: rotation)
    case .multi:
      guard let secondaryTouch else { return }
      let metrics = multiMetrics(primaryTouch, secondaryTouch)
      let scale = clamp(initial.scale * metrics.distance / startDistance, minScale, maxScale)
      let rotation = normalizeDegrees(initial.rotationDegrees + angleDelta(from: startAngle, to: metrics.angle))
      let x = clamp(initial.x + (metrics.midpoint.x - startMidpoint.x) / imageBounds.width, 0, 1)
      let y = clamp(initial.y + (metrics.midpoint.y - startMidpoint.y) / imageBounds.height, 0, 1)
      emit(initial, x: x, y: y, scale: scale, rotation: rotation)
    case .none: break
    }
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let touch = touches.first, touch.tapCount >= 2, let id = gestureID { onLayerDoubleTapped?(id) }
    endTouches(touches)
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { endTouches(touches) }

  private func endTouches(_ touches: Set<UITouch>) {
    let endedPrimary = primaryTouch.map { touches.contains($0) } ?? false
    let endedSecondary = secondaryTouch.map { touches.contains($0) } ?? false
    if endedPrimary || endedSecondary { finishGesture() }
  }
  private func emit(_ layer: PhotoLayer, x: CGFloat, y: CGFloat, scale: CGFloat, rotation: CGFloat) {
    onLayerTransformChanged?(layer.id, x, y, scale, rotation)
  }
  private func finishGesture() {
    if let id = gestureID { onLayerTransformEnded?(id) }
    resetGesture()
  }
  private func resetGesture() {
    gesture = .none; gestureID = nil; startLayer = nil; primaryTouch = nil; secondaryTouch = nil
  }
  private func selectedLayer() -> PhotoLayer? { layers.first { $0.id == selectedLayerID && $0.visible } }
  private func currentGestureLayer() -> PhotoLayer? { layers.first { $0.id == gestureID && $0.visible && !$0.locked } }
  private func center(of layer: PhotoLayer) -> CGPoint { CGPoint(x: imageBounds.minX + layer.x * imageBounds.width, y: imageBounds.minY + layer.y * imageBounds.height) }
  private func selectionHalfExtents(_ layer: PhotoLayer) -> CGSize {
    let shortSide = min(imageBounds.width, imageBounds.height)
    let base: CGSize
    switch layer.type {
    case .text:
      let pointSize = layer.fontSize / 48 * shortSide * 0.06
      let font = layer.fontFamily.flatMap { UIFont(name: $0, size: pointSize) } ?? UIFont.systemFont(ofSize: pointSize)
      let lines = layer.text.isEmpty ? [" "] : layer.text.components(separatedBy: "\n")
      let width = lines.map { ($0 as NSString).size(withAttributes: [.font: font]).width }.max() ?? pointSize
      base = CGSize(width: width / 2 + 8, height: font.lineHeight * CGFloat(lines.count) / 2 + 6)
    case .sticker:
      // Mirrors `PhotoLayerRenderer.drawSticker`'s `shortSide * 0.18` box (half-extent = 0.09).
      let half = shortSide * 0.09
      base = CGSize(width: half, height: half)
    case .overlay:
      // Mirrors `PhotoLayerRenderer.drawOverlay`'s box: full canvas width, height from aspect ratio.
      let aspectRatio = layer.overlayAspectRatio.flatMap { $0 > 0 ? $0 : nil } ?? 1
      base = CGSize(width: imageBounds.width / 2, height: imageBounds.width / (2 * aspectRatio))
    case .drawing:
      let maxX = layer.drawPoints.map { abs($0.0) }.max() ?? 0.08
      let maxY = layer.drawPoints.map { abs($0.1) }.max() ?? 0.08
      base = CGSize(width: maxX * imageBounds.width + handleRadius, height: maxY * imageBounds.height + handleRadius)
    }
    return CGSize(width: max(handleRadius, base.width * layer.scale), height: max(handleRadius, base.height * layer.scale))
  }
  private func handlePoint(_ layer: PhotoLayer) -> CGPoint {
    let center = center(of: layer); let half = selectionHalfExtents(layer); let radians = layer.rotationDegrees * .pi / 180
    return CGPoint(x: center.x + half.width * cos(radians) - half.height * sin(radians), y: center.y + half.width * sin(radians) + half.height * cos(radians))
  }
  private func isHandleHit(_ layer: PhotoLayer, _ point: CGPoint) -> Bool { distance(handlePoint(layer), point) <= handleTouchRadius }
  private func hitTest(_ point: CGPoint) -> PhotoLayer? {
    for layer in layers.reversed() where layer.visible {
      let center = center(of: layer); let radians = -layer.rotationDegrees * .pi / 180
      let dx = point.x - center.x; let dy = point.y - center.y
      let localX = dx * cos(radians) - dy * sin(radians); let localY = dx * sin(radians) + dy * cos(radians)
      let half = selectionHalfExtents(layer)
      if abs(localX) <= half.width && abs(localY) <= half.height { return layer }
    }
    return nil
  }
  private func multiMetrics(_ first: UITouch, _ second: UITouch) -> (midpoint: CGPoint, distance: CGFloat, angle: CGFloat) {
    let a = first.location(in: self); let b = second.location(in: self)
    return (CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2), distance(a, b), angle(a, b))
  }
  private func distance(_ a: CGPoint, _ b: CGPoint) -> CGFloat { hypot(b.x - a.x, b.y - a.y) }
  private func angle(_ a: CGPoint, _ b: CGPoint) -> CGFloat { atan2(b.y - a.y, b.x - a.x) * 180 / .pi }
  private func angleDelta(from: CGFloat, to: CGFloat) -> CGFloat { (to - from + 540).truncatingRemainder(dividingBy: 360) - 180 }
  private func normalizeDegrees(_ value: CGFloat) -> CGFloat { (value.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) }
  private func clamp(_ value: CGFloat, _ minimum: CGFloat, _ maximum: CGFloat) -> CGFloat { min(max(value, minimum), maximum) }
}
