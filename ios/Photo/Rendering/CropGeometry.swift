import CoreGraphics

/// Resolution-independent constraints; mirrors Android CropGeometry.
enum CropGeometry {
  enum Handle { case move, top, right, bottom, left, topLeft, topRight, bottomLeft, bottomRight }
  static func fillScale(width: CGFloat, height: CGFloat, degrees: CGFloat) -> CGFloat {
    let radians = degrees * .pi / 180
    let c = abs(cos(radians)), s = abs(sin(radians))
    return max(c + height / width * s, c + width / height * s)
  }
  static func fit(_ bounds: CGRect, ratio: CGFloat?) -> CGRect {
    guard let ratio, ratio > 0 else { return bounds }
    let w = min(bounds.width, bounds.height * ratio), h = min(bounds.width / ratio, bounds.height)
    return CGRect(x: bounds.midX - w / 2, y: bounds.midY - h / 2, width: w, height: h)
  }
  static func resize(_ r: CGRect, bounds: CGRect, handle: Handle, dx: CGFloat, dy: CGFloat, ratio: CGFloat?, minimum: CGFloat) -> CGRect {
    if handle == .move {
      return r.offsetBy(dx: max(bounds.minX-r.minX, min(bounds.maxX-r.maxX, dx)), dy: max(bounds.minY-r.minY, min(bounds.maxY-r.maxY, dy)))
    }
    let left = [Handle.left, .topLeft, .bottomLeft].contains(handle)
    let right = [Handle.right, .topRight, .bottomRight].contains(handle)
    let top = [Handle.top, .topLeft, .topRight].contains(handle)
    let bottom = [Handle.bottom, .bottomLeft, .bottomRight].contains(handle)
    var l = r.minX + (left ? dx : 0), t = r.minY + (top ? dy : 0)
    var rr = r.maxX + (right ? dx : 0), b = r.maxY + (bottom ? dy : 0)
    if let ratio, ratio > 0 {
      if (left || right) && (top || bottom) {
        let dw = left ? -dx : dx, dh = top ? -dy : dy
        let w = r.width + (abs(dw) > abs(dh * ratio) ? dw : dh * ratio)
        if left { l = r.maxX - w } else { rr = r.minX + w }
        if top { t = r.maxY - w / ratio } else { b = r.minY + w / ratio }
      } else if left || right {
        let h = (rr-l)/ratio; t = r.midY-h/2; b = t+h
      } else {
        let w = (b-t)*ratio; l = r.midX-w/2; rr = l+w
      }
    }
    var fraction: CGFloat = 1
    func limit(_ before: CGFloat, _ after: CGFloat, _ minimum: CGFloat) {
      if after < minimum && after < before { fraction = min(fraction, max(0, min(1, (before-minimum)/(before-after)))) }
    }
    limit(r.minX,l,bounds.minX); limit(r.minY,t,bounds.minY)
    limit(-r.maxX,-rr,-bounds.maxX); limit(-r.maxY,-b,-bounds.maxY)
    limit(r.width,rr-l,min(minimum,r.width)); limit(r.height,b-t,min(minimum,r.height))
    let x = r.minX+(l-r.minX)*fraction, y = r.minY+(t-r.minY)*fraction
    return CGRect(x: x, y: y, width: r.maxX+(rr-r.maxX)*fraction-x, height: r.maxY+(b-r.maxY)*fraction-y)
  }
}
