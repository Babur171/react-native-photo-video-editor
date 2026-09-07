import UIKit

/// Handle gestures resize the box; interior gestures pan/pinch the media, keeping the box fixed.
final class CropOverlayView: UIView {
  var onCropChanged: ((CGFloat, CGFloat, CGFloat, CGFloat) -> Void)?
  var onMediaBoundsChanged: ((CGRect) -> Void)?
  var onViewportChanged: ((CGFloat, CGFloat, CGFloat) -> Void)?
  private var imageBounds = CGRect.zero, fittedBounds = CGRect.zero, cropRect = CGRect.zero
  private var aspectRatio: CGFloat?
  private var activeHandle: CropGeometry.Handle?
  private var primary: UITouch?, secondary: UITouch?
  private var lastPoint = CGPoint.zero
  private var lastDistance: CGFloat = 0

  override init(frame: CGRect) {
    super.init(frame: frame); backgroundColor = .clear; isOpaque = false; isMultipleTouchEnabled = true
  }
  required init?(coder: NSCoder) { nil }

  func restore(bounds: CGRect, state: PhotoTransformState) {
    guard bounds.width > 0, bounds.height > 0 else { return }
    fittedBounds = bounds
    let zoom = max(1, min(8, state.zoom))
    let cx = bounds.midX + state.panX*bounds.width, cy = bounds.midY + state.panY*bounds.height
    imageBounds = CGRect(x: cx-bounds.width*zoom/2, y: cy-bounds.height*zoom/2, width: bounds.width*zoom, height: bounds.height*zoom)
    aspectRatio = state.aspectRatio
    setCrop(left: state.cropLeft, top: state.cropTop, right: state.cropRight, bottom: state.cropBottom)
    onMediaBoundsChanged?(imageBounds)
  }
  func setImageBounds(_ bounds: CGRect, resetCrop: Bool) {
    let old = normalized()
    imageBounds = bounds; fittedBounds = bounds
    if resetCrop || cropRect.isEmpty { cropRect = bounds }
    else { setCrop(left: old.minX, top: old.minY, right: old.maxX, bottom: old.maxY) }
    setNeedsDisplay()
  }
  func setCrop(left: CGFloat, top: CGFloat, right: CGFloat, bottom: CGFloat) {
    cropRect = CGRect(x: imageBounds.minX+left*imageBounds.width, y: imageBounds.minY+top*imageBounds.height,
      width: (right-left)*imageBounds.width, height: (bottom-top)*imageBounds.height)
    setNeedsDisplay()
  }
  func setAspectRatio(_ ratio: CGFloat?) {
    aspectRatio = ratio
    cropRect = CropGeometry.fit(imageBounds.intersection(bounds), ratio: ratio)
    reportCrop(); setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    guard !cropRect.isEmpty, let context = UIGraphicsGetCurrentContext() else { return }
    let shade = UIBezierPath(rect: bounds); shade.append(UIBezierPath(rect: cropRect)); shade.usesEvenOddFillRule = true
    UIColor.black.withAlphaComponent(0.65).setFill(); shade.fill()
    context.setStrokeColor(UIColor.white.cgColor); context.setLineWidth(1); context.stroke(cropRect)
    context.setStrokeColor(UIColor.white.withAlphaComponent(0.33).cgColor); context.setLineWidth(0.5)
    for i in 1...2 {
      let x = cropRect.minX+cropRect.width*CGFloat(i)/3, y = cropRect.minY+cropRect.height*CGFloat(i)/3
      context.move(to: CGPoint(x:x,y:cropRect.minY)); context.addLine(to:CGPoint(x:x,y:cropRect.maxY))
      context.move(to: CGPoint(x:cropRect.minX,y:y)); context.addLine(to:CGPoint(x:cropRect.maxX,y:y))
    }
    context.strokePath(); context.setStrokeColor(UIColor.white.cgColor); context.setLineWidth(3); context.setLineCap(.round)
    for x in [cropRect.minX,cropRect.maxX] {
      for y in [cropRect.minY,cropRect.maxY] {
        context.move(to: CGPoint(x:x+(x == cropRect.minX ? 14 : -14),y:y)); context.addLine(to:CGPoint(x:x,y:y))
        context.addLine(to: CGPoint(x:x,y:y+(y == cropRect.minY ? 14 : -14)))
      }
    }
    for y in [cropRect.minY,cropRect.maxY] { context.move(to:CGPoint(x:cropRect.midX-8,y:y)); context.addLine(to:CGPoint(x:cropRect.midX+8,y:y)) }
    for x in [cropRect.minX,cropRect.maxX] { context.move(to:CGPoint(x:x,y:cropRect.midY-8)); context.addLine(to:CGPoint(x:x,y:cropRect.midY+8)) }
    context.strokePath()
  }

  override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
    for touch in touches { if primary == nil { primary = touch } else if secondary == nil { secondary = touch } }
    guard let primary else { return }
    if secondary != nil { activeHandle = nil } else { activeHandle = hitHandle(primary.location(in:self)) }
    let metrics = touchMetrics(); lastPoint = metrics.0; lastDistance = metrics.1
  }
  override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
    guard primary != nil, imageBounds.width > 0 else { return }
    let (point,distance) = touchMetrics(), dx = point.x-lastPoint.x, dy = point.y-lastPoint.y
    if let activeHandle {
      cropRect = CropGeometry.resize(cropRect, bounds: imageBounds.intersection(bounds), handle: activeHandle, dx: dx, dy: dy, ratio: aspectRatio, minimum: 48)
    } else {
      moveMedia(dx:dx,dy:dy,factor:lastDistance > 0 && distance > 0 ? distance/lastDistance : 1,focus:point)
    }
    lastPoint = point; lastDistance = distance; reportCrop(); setNeedsDisplay()
  }
  override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
    if let secondary, touches.contains(secondary) { self.secondary = nil }
    if let primary, touches.contains(primary) { self.primary = secondary; secondary = nil }
    let metrics = touchMetrics(); lastPoint = metrics.0; lastDistance = metrics.1
    if primary == nil { activeHandle = nil }
  }
  override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) { primary = nil; secondary = nil; activeHandle = nil }

  private func moveMedia(dx:CGFloat,dy:CGFloat,factor:CGFloat,focus:CGPoint) {
    let minimum = max(1,max(cropRect.width/fittedBounds.width,cropRect.height/fittedBounds.height))
    let zoom = imageBounds.width/fittedBounds.width
    let scale = max(minimum,min(max(8,minimum),zoom*factor))/zoom
    let w = imageBounds.width*scale, h = imageBounds.height*scale
    let x = max(cropRect.maxX-w,min(cropRect.minX,focus.x+(imageBounds.minX-focus.x)*scale+dx))
    let y = max(cropRect.maxY-h,min(cropRect.minY,focus.y+(imageBounds.minY-focus.y)*scale+dy))
    imageBounds = CGRect(x:x,y:y,width:w,height:h)
    onMediaBoundsChanged?(imageBounds)
    onViewportChanged?(w/fittedBounds.width,(imageBounds.midX-fittedBounds.midX)/fittedBounds.width,(imageBounds.midY-fittedBounds.midY)/fittedBounds.height)
  }
  private func touchMetrics() -> (CGPoint,CGFloat) {
    guard let a = primary?.location(in:self) else { return (.zero,0) }
    guard let b = secondary?.location(in:self) else { return (a,0) }
    return (CGPoint(x:(a.x+b.x)/2,y:(a.y+b.y)/2),hypot(a.x-b.x,a.y-b.y))
  }
  private func hitHandle(_ p:CGPoint) -> CropGeometry.Handle? {
    let points: [(CGPoint,CropGeometry.Handle)] = [
      (CGPoint(x:cropRect.minX,y:cropRect.minY),.topLeft),(CGPoint(x:cropRect.maxX,y:cropRect.minY),.topRight),
      (CGPoint(x:cropRect.minX,y:cropRect.maxY),.bottomLeft),(CGPoint(x:cropRect.maxX,y:cropRect.maxY),.bottomRight),
      (CGPoint(x:cropRect.midX,y:cropRect.minY),.top),(CGPoint(x:cropRect.maxX,y:cropRect.midY),.right),
      (CGPoint(x:cropRect.midX,y:cropRect.maxY),.bottom),(CGPoint(x:cropRect.minX,y:cropRect.midY),.left)]
    guard let nearest = points.min(by: { hypot(p.x-$0.0.x,p.y-$0.0.y) < hypot(p.x-$1.0.x,p.y-$1.0.y) }), hypot(p.x-nearest.0.x,p.y-nearest.0.y) <= 24 else { return nil }
    return nearest.1
  }
  private func normalized() -> CGRect {
    guard imageBounds.width > 0,imageBounds.height > 0 else { return CGRect(x:0,y:0,width:1,height:1) }
    return CGRect(x:(cropRect.minX-imageBounds.minX)/imageBounds.width,y:(cropRect.minY-imageBounds.minY)/imageBounds.height,width:cropRect.width/imageBounds.width,height:cropRect.height/imageBounds.height)
  }
  private func reportCrop() { let r = normalized(); onCropChanged?(max(0,r.minX),max(0,r.minY),min(1,r.maxX),min(1,r.maxY)) }
}
