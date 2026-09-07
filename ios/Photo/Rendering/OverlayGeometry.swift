import CoreGraphics

/// Pure normalized-coordinate mapping used by preview and export rendering.
enum OverlayGeometry {
  struct Transform {
    let center: CGPoint
    let scale: CGFloat
    let rotationDegrees: CGFloat
    let opacity: CGFloat
    let shortSide: CGFloat
  }

  static func map(_ layer: PhotoLayer, frameSize: CGSize) -> Transform {
    Transform(
      center: CGPoint(x: layer.x * frameSize.width, y: layer.y * frameSize.height),
      scale: layer.scale,
      rotationDegrees: layer.rotationDegrees,
      opacity: min(max(layer.opacity, 0), 1),
      shortSide: min(frameSize.width, frameSize.height)
    )
  }
}
