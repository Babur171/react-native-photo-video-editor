import UIKit

/// Draws a draggable/resizable crop rectangle over `imageBounds` (the
/// on-screen rect of the preview image, in this view's own coordinate space)
/// and reports the crop edges back normalized to that rect via `onCropChanged`.
final class CropOverlayView: UIView {
  var onCropChanged: ((_ left: CGFloat, _ top: CGFloat, _ right: CGFloat, _ bottom: CGFloat) -> Void)?

  private var imageBounds: CGRect = .zero
  private var cropRect: CGRect = .zero
  private var aspectRatio: CGFloat?

  private let dimLayer = CAShapeLayer()
  private let borderLayer = CAShapeLayer()
  private let handlesLayer = CAShapeLayer()
  private let handleRadius: CGFloat = 32
  private let minSize: CGFloat = 60

  private enum Handle { case move, top, right, bottom, left, topLeft, topRight, bottomLeft, bottomRight }
  private var activeHandle: Handle?
  private var lastPoint: CGPoint = .zero

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    dimLayer.fillRule = .evenOdd
    dimLayer.fillColor = UIColor.black.withAlphaComponent(0.6).cgColor
    layer.addSublayer(dimLayer)
    borderLayer.strokeColor = UIColor.white.cgColor
    borderLayer.fillColor = UIColor.clear.cgColor
    borderLayer.lineWidth = 2
    layer.addSublayer(borderLayer)
    handlesLayer.fillColor = UIColor.white.cgColor
    layer.addSublayer(handlesLayer)
  }

  required init?(coder: NSCoder) { nil }

  /// Sets the on-screen image rect this overlay crops against. Resets the crop to full-frame when `resetCrop` is true.
  func setImageBounds(_ bounds: CGRect, resetCrop: Bool) {
    imageBounds = bounds
    if resetCrop || cropRect.width <= 0 || cropRect.height <= 0 {
      cropRect = bounds
      reportCrop()
    }
    setNeedsLayout()
  }

  /// Restores a normalized crop rectangle, used when reopening or cancelling crop mode.
  func setCrop(left: CGFloat, top: CGFloat, right: CGFloat, bottom: CGFloat) {
    guard imageBounds.width > 0, imageBounds.height > 0 else { return }
    cropRect = CGRect(
      x: imageBounds.minX + left * imageBounds.width,
      y: imageBounds.minY + top * imageBounds.height,
      width: (right - left) * imageBounds.width,
      height: (bottom - top) * imageBounds.height
    )
    clampToImageBounds(); setNeedsLayout()
  }

  func setAspectRatio(_ ratio: CGFloat?) {
    aspectRatio = ratio
    if let ratio, imageBounds.width > 0, imageBounds.height > 0 {
      let center = CGPoint(x: cropRect.midX, y: cropRect.midY)
      var width = cropRect.width
      var height = width / ratio
      if height > imageBounds.height {
        height = cropRect.height
        width = height * ratio
      }
      cropRect = CGRect(x: center.x - width / 2, y: center.y - height / 2, width: width, height: height)
      clampToImageBounds()
      setNeedsLayout()
    }
    reportCrop()
  }

  override func layoutSublayers(of layer: CALayer) {
    super.layoutSublayers(of: layer)
    guard imageBounds.width > 0, imageBounds.height > 0 else { return }
    let outerPath = UIBezierPath(rect: imageBounds)
    outerPath.append(UIBezierPath(rect: cropRect).reversing())
    dimLayer.path = outerPath.cgPath

    let borderPath = UIBezierPath(rect: cropRect)
    let thirdWidth = cropRect.width / 3
    let thirdHeight = cropRect.height / 3
    for index in 1...2 {
      let x = cropRect.minX + thirdWidth * CGFloat(index)
      borderPath.move(to: CGPoint(x: x, y: cropRect.minY))
      borderPath.addLine(to: CGPoint(x: x, y: cropRect.maxY))
      let y = cropRect.minY + thirdHeight * CGFloat(index)
      borderPath.move(to: CGPoint(x: cropRect.minX, y: y))
      borderPath.addLine(to: CGPoint(x: cropRect.maxX, y: y))
    }
    borderLayer.path = borderPath.cgPath

    let handlesPath = UIBezierPath()
    let points = [
      CGPoint(x: cropRect.minX, y: cropRect.minY), CGPoint(x: cropRect.midX, y: cropRect.minY),
      CGPoint(x: cropRect.maxX, y: cropRect.minY), CGPoint(x: cropRect.maxX, y: cropRect.midY),
      CGPoint(x: cropRect.maxX, y: cropRect.maxY), CGPoint(x: cropRect.midX, y: cropRect.maxY),
      CGPoint(x: cropRect.minX, y: cropRect.maxY), CGPoint(x: cropRect.minX, y: cropRect.midY),
    ]
    points.forEach { point in handlesPath.append(UIBezierPath(ovalIn: CGRect(x: point.x - 4, y: point.y - 4, width: 8, height: 8))) }
    handlesLayer.path = handlesPath.cgPath
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let point = touches.first?.location(in: self) else { return }
    activeHandle = hitTest(point)
    lastPoint = point
  }

  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard let handle = activeHandle, let point = touches.first?.location(in: self) else { return }
    let dx = point.x - lastPoint.x
    let dy = point.y - lastPoint.y
    lastPoint = point
    applyDrag(handle, dx: dx, dy: dy)
    setNeedsLayout()
    reportCrop()
  }

  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) { activeHandle = nil }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { activeHandle = nil }

  private func hitTest(_ point: CGPoint) -> Handle? {
    func near(_ target: CGPoint) -> Bool {
      abs(point.x - target.x) < handleRadius && abs(point.y - target.y) < handleRadius
    }
    if near(CGPoint(x: cropRect.minX, y: cropRect.minY)) { return .topLeft }
    if near(CGPoint(x: cropRect.maxX, y: cropRect.minY)) { return .topRight }
    if near(CGPoint(x: cropRect.minX, y: cropRect.maxY)) { return .bottomLeft }
    if near(CGPoint(x: cropRect.maxX, y: cropRect.maxY)) { return .bottomRight }
    if near(CGPoint(x: cropRect.midX, y: cropRect.minY)) { return .top }
    if near(CGPoint(x: cropRect.maxX, y: cropRect.midY)) { return .right }
    if near(CGPoint(x: cropRect.midX, y: cropRect.maxY)) { return .bottom }
    if near(CGPoint(x: cropRect.minX, y: cropRect.midY)) { return .left }
    if cropRect.contains(point) { return .move }
    return nil
  }

  private func applyDrag(_ handle: Handle, dx: CGFloat, dy: CGFloat) {
    var rect = cropRect
    switch handle {
    case .move:
      rect = rect.offsetBy(dx: dx, dy: dy)
    case .topLeft:
      rect = CGRect(x: rect.minX + dx, y: rect.minY + dy, width: rect.width - dx, height: rect.height - dy)
    case .topRight:
      rect = CGRect(x: rect.minX, y: rect.minY + dy, width: rect.width + dx, height: rect.height - dy)
    case .bottomLeft:
      rect = CGRect(x: rect.minX + dx, y: rect.minY, width: rect.width - dx, height: rect.height + dy)
    case .bottomRight:
      rect = CGRect(x: rect.minX, y: rect.minY, width: rect.width + dx, height: rect.height + dy)
    case .top: rect = CGRect(x: rect.minX, y: rect.minY + dy, width: rect.width, height: rect.height - dy)
    case .right: rect.size.width += dx
    case .bottom: rect.size.height += dy
    case .left: rect = CGRect(x: rect.minX + dx, y: rect.minY, width: rect.width - dx, height: rect.height)
    }
    guard rect.width >= minSize, rect.height >= minSize else { return }
    if let ratio = aspectRatio {
      switch handle {
      case .topLeft, .bottomLeft, .bottomRight:
        rect.size.height = rect.width / ratio
      case .topRight:
        let newHeight = rect.width / ratio
        rect.origin.y = cropRect.maxY - newHeight
        rect.size.height = newHeight
      case .move:
        break
      case .left, .right:
        let centerY = cropRect.midY; rect.size.height = rect.width / ratio; rect.origin.y = centerY - rect.height / 2
      case .top, .bottom:
        let centerX = cropRect.midX; rect.size.width = rect.height * ratio; rect.origin.x = centerX - rect.width / 2
      }
    }
    cropRect = rect
    clampToImageBounds()
  }

  private func clampToImageBounds() {
    if cropRect.minX < imageBounds.minX { cropRect.origin.x = imageBounds.minX }
    if cropRect.minY < imageBounds.minY { cropRect.origin.y = imageBounds.minY }
    if cropRect.maxX > imageBounds.maxX { cropRect.origin.x = imageBounds.maxX - cropRect.width }
    if cropRect.maxY > imageBounds.maxY { cropRect.origin.y = imageBounds.maxY - cropRect.height }
  }

  private func reportCrop() {
    guard imageBounds.width > 0, imageBounds.height > 0 else { return }
    let left = (cropRect.minX - imageBounds.minX) / imageBounds.width
    let top = (cropRect.minY - imageBounds.minY) / imageBounds.height
    let right = (cropRect.maxX - imageBounds.minX) / imageBounds.width
    let bottom = (cropRect.maxY - imageBounds.minY) / imageBounds.height
    onCropChanged?(left, top, right, bottom)
  }
}
