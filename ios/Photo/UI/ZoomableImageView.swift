import UIKit

/// UIImageView supporting pinch-to-zoom and single-finger pan for the photo
/// preview, reporting the current on-screen image rect via `onBoundsChanged`
/// so the crop overlay can stay aligned to it.
final class ZoomableImageView: UIImageView {
  var onBoundsChanged: ((CGRect) -> Void)?
  var panZoomEnabled = true {
    didSet {
      pinchRecognizer.isEnabled = panZoomEnabled
      panRecognizer.isEnabled = panZoomEnabled
    }
  }

  private let pinchRecognizer = UIPinchGestureRecognizer()
  private let panRecognizer = UIPanGestureRecognizer()
  private var minScale: CGFloat = 1
  private var maxScale: CGFloat = 4

  override init(frame: CGRect) {
    super.init(frame: frame)
    contentMode = .scaleAspectFit
    isUserInteractionEnabled = true
    pinchRecognizer.addTarget(self, action: #selector(handlePinch(_:)))
    panRecognizer.addTarget(self, action: #selector(handlePan(_:)))
    addGestureRecognizer(pinchRecognizer)
    addGestureRecognizer(panRecognizer)
  }

  required init?(coder: NSCoder) { nil }

  /// Fits the current image to the view bounds and resets zoom/pan.
  func resetToFit() {
    transform = .identity
    minScale = 1
    maxScale = 4
    reportBounds()
  }

  func currentImageBounds() -> CGRect {
    guard let image = image, bounds.width > 0, bounds.height > 0, image.size.width > 0, image.size.height > 0 else { return bounds }
    let scale = min(bounds.width / image.size.width, bounds.height / image.size.height)
    let fittedSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
    let origin = CGPoint(x: (bounds.width - fittedSize.width) / 2, y: (bounds.height - fittedSize.height) / 2)
    return CGRect(origin: origin, size: fittedSize).applying(transform)
  }

  private func reportBounds() {
    onBoundsChanged?(currentImageBounds())
  }

  private func currentScaleFactor() -> CGFloat {
    sqrt(transform.a * transform.a + transform.c * transform.c)
  }

  @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
    guard panZoomEnabled else { return }
    let currentScale = currentScaleFactor()
    let targetScale = max(minScale, min(maxScale, currentScale * gesture.scale))
    let appliedFactor = targetScale / currentScale
    let location = gesture.location(in: self)
    let anchor = CGPoint(x: location.x - bounds.midX, y: location.y - bounds.midY)
    transform = transform
      .translatedBy(x: anchor.x, y: anchor.y)
      .scaledBy(x: appliedFactor, y: appliedFactor)
      .translatedBy(x: -anchor.x, y: -anchor.y)
    gesture.scale = 1
    reportBounds()
  }

  @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
    guard panZoomEnabled else { return }
    let translation = gesture.translation(in: self)
    let scale = currentScaleFactor()
    transform = transform.translatedBy(x: translation.x / scale, y: translation.y / scale)
    gesture.setTranslation(.zero, in: self)
    reportBounds()
  }
}
