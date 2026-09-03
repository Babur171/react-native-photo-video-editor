import UIKit

/// Burns the layer stack onto a `UIImage`. Used identically by the live
/// preview and the exporter (different image resolutions, same code and
/// normalized coordinates) so what you see matches what gets exported.
/// Mirrors `PhotoLayerRenderer.kt`.
enum PhotoLayerRenderer {
  private static let builtinStickers: [String: String] = [
    "smile": "😀", "heart": "❤️", "star": "⭐", "fire": "🔥",
    "thumbsUp": "👍", "sun": "☀️", "clap": "👏", "sparkles": "✨",
  ]

  static func builtinStickerIDs() -> [String] { Array(builtinStickers.keys).sorted() }
  static func glyph(for stickerID: String) -> String { builtinStickers[stickerID] ?? "★" }

  static func render(_ base: UIImage, layers: [PhotoLayer], stickerResolver: (String) -> UIImage?) -> UIImage {
    guard !layers.isEmpty else { return base }
    let shortSide = min(base.size.width, base.size.height)
    let renderer = PhotoEditSession.pixelRenderer(size: base.size)
    return renderer.image { context in
      base.draw(in: CGRect(origin: .zero, size: base.size))
      for layer in layers where layer.visible {
        let ctx = context.cgContext
        ctx.saveGState()
        ctx.translateBy(x: layer.x * base.size.width, y: layer.y * base.size.height)
        ctx.rotate(by: layer.rotationDegrees * .pi / 180)
        ctx.scaleBy(x: layer.scale, y: layer.scale)
        switch layer.type {
        case .text: drawText(layer, shortSide: shortSide)
        case .sticker: drawSticker(layer, shortSide: shortSide, resolver: stickerResolver)
        case .shape: drawShape(ctx, layer, shortSide: shortSide)
        case .drawing: drawStroke(ctx, layer, width: base.size.width, height: base.size.height, shortSide: shortSide)
        }
        ctx.restoreGState()
      }
    }
  }

  private static func drawText(_ layer: PhotoLayer, shortSide: CGFloat) {
    let fontSize = layer.fontSize / 48 * (shortSide * 0.06)
    let font: UIFont = layer.fontFamily.flatMap { UIFont(name: $0, size: fontSize) } ?? .systemFont(ofSize: fontSize)
    let paragraph = NSMutableParagraphStyle()
    paragraph.alignment = .center
    let attributes: [NSAttributedString.Key: Any] = [
      .font: font,
      .foregroundColor: layer.textColor.withAlphaComponent(layer.opacity),
      .paragraphStyle: paragraph,
    ]
    let text = layer.text.isEmpty ? " " : layer.text
    let attributed = NSAttributedString(string: text, attributes: attributes)
    let size = attributed.size()
    attributed.draw(at: CGPoint(x: -size.width / 2, y: -size.height / 2))
  }

  private static func drawSticker(_ layer: PhotoLayer, shortSide: CGFloat, resolver: (String) -> UIImage?) {
    let size = shortSide * 0.18
    let rect = CGRect(x: -size / 2, y: -size / 2, width: size, height: size)
    if let uri = layer.stickerUri, let image = resolver(uri) {
      image.draw(in: rect, blendMode: .normal, alpha: layer.opacity)
      return
    }
    let glyph = layer.stickerId.map { PhotoLayerRenderer.glyph(for: $0) } ?? "★"
    let attributes: [NSAttributedString.Key: Any] = [
      .font: UIFont.systemFont(ofSize: size),
    ]
    let attributed = NSAttributedString(string: glyph, attributes: attributes)
    let textSize = attributed.size()
    attributed.draw(at: CGPoint(x: -textSize.width / 2, y: -size / 2), withAlpha: layer.opacity)
  }

  private static func drawShape(_ ctx: CGContext, _ layer: PhotoLayer, shortSide: CGFloat) {
    let size = shortSide * 0.24
    let rect = CGRect(x: -size / 2, y: -size / 2, width: size, height: size)
    ctx.setAlpha(layer.opacity)
    let color = layer.shapeColor.cgColor
    if layer.shapeFilled {
      ctx.setFillColor(color)
    } else {
      ctx.setStrokeColor(color)
      ctx.setLineWidth(shortSide * 0.01)
    }
    switch layer.shapeKind {
    case .rectangle:
      layer.shapeFilled ? ctx.fill(rect) : ctx.stroke(rect)
    case .oval:
      layer.shapeFilled ? ctx.fillEllipse(in: rect) : ctx.strokeEllipse(in: rect)
    case .line:
      ctx.setStrokeColor(color)
      ctx.setLineWidth(shortSide * 0.01)
      ctx.move(to: CGPoint(x: -size / 2, y: 0))
      ctx.addLine(to: CGPoint(x: size / 2, y: 0))
      ctx.strokePath()
    }
  }

  private static func drawStroke(_ ctx: CGContext, _ layer: PhotoLayer, width: CGFloat, height: CGFloat, shortSide: CGFloat) {
    guard layer.drawPoints.count >= 2 else { return }
    ctx.setAlpha(layer.opacity)
    ctx.setStrokeColor(layer.drawColor.cgColor)
    ctx.setLineWidth(layer.drawStrokeWidth * shortSide)
    ctx.setLineCap(.round)
    ctx.setLineJoin(.round)
    let points = layer.drawPoints.map { CGPoint(x: $0.0 * width, y: $0.1 * height) }
    ctx.move(to: points[0])
    for point in points.dropFirst() { ctx.addLine(to: point) }
    ctx.strokePath()
  }
}

private extension NSAttributedString {
  /// `draw(at:)` ignores `withAlpha` unless applied via the graphics context; this composites the whole glyph at `alpha`.
  func draw(at point: CGPoint, withAlpha alpha: CGFloat) {
    guard let ctx = UIGraphicsGetCurrentContext() else { draw(at: point); return }
    ctx.saveGState()
    ctx.setAlpha(alpha)
    draw(at: point)
    ctx.restoreGState()
  }
}
