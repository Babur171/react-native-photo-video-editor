import UIKit

/// Shared compact photo/video crop panel. Draft edits remain in the session, never in this view.
final class CropPanel: UIStackView {
  private let slider = CropRulerSlider()
  private let degrees = UILabel()
  private var presets: [String:UIButton] = [:]
  private let accent: UIColor
  private let foreground: UIColor

  init(accent: UIColor, surface: UIColor, foreground: UIColor, originalRatio: @escaping () -> CGFloat,
       onReset: @escaping () -> Void, onDone: @escaping () -> Void, onCancel: @escaping () -> Void,
       onStraighten: @escaping (CGFloat) -> Void, onRotate: @escaping () -> Void,
       onRatio: @escaping (String, CGFloat?) -> Void) {
    self.accent = accent; self.foreground = foreground
    super.init(frame:.zero)
    axis = .vertical; backgroundColor = surface
    layer.cornerRadius = 20; layer.maskedCorners = [.layerMinXMinYCorner,.layerMaxXMinYCorner]
    isLayoutMarginsRelativeArrangement = true; layoutMargins = UIEdgeInsets(top:8,left:12,bottom:8,right:12)
    func icon(_ symbol: String, label: String, action: @escaping () -> Void) -> UIButton {
      let button = UIButton(type:.system)
      button.setImage(UIImage(systemName:symbol),for:.normal); button.tintColor = foreground; button.accessibilityLabel = label
      button.widthAnchor.constraint(equalToConstant:44).isActive = true
      button.addAction(UIAction { _ in action() },for:.touchUpInside); button.applyPressScale()
      return button
    }
    let reset = UIButton(type:.system)
    var resetStyle = UIButton.Configuration.plain(); resetStyle.title = "Reset"; resetStyle.image = UIImage(systemName:"arrow.counterclockwise")
    resetStyle.imagePadding = 6; resetStyle.baseForegroundColor = foreground
    reset.configuration = resetStyle; reset.addAction(UIAction { _ in onReset() },for:.touchUpInside)
    reset.widthAnchor.constraint(equalToConstant:88).isActive = true
    let title = UILabel(); title.text = "Crop"; title.font = .systemFont(ofSize:17,weight:.semibold); title.textColor = foreground; title.textAlignment = .center
    let done = icon("checkmark",label:"Apply crop",action:onDone); done.tintColor = accent
    let header = UIStackView(arrangedSubviews:[reset,title,icon("xmark",label:"Cancel crop",action:onCancel),done]); header.axis = .horizontal
    header.heightAnchor.constraint(equalToConstant:48).isActive = true; addArrangedSubview(header)
    let label = UILabel(); label.text = "Straighten"; label.font = .systemFont(ofSize:13); label.textColor = foreground
    degrees.font = .monospacedDigitSystemFont(ofSize:13,weight:.medium); degrees.textColor = accent; degrees.textAlignment = .center
    degrees.widthAnchor.constraint(equalToConstant:56).isActive = true
    let labels = UIStackView(arrangedSubviews:[label,degrees,icon("arrow.counterclockwise",label:"Reset straighten") { onStraighten(0) },icon("rotate.right",label:"Rotate 90 degrees",action:onRotate)])
    labels.axis = .horizontal; labels.heightAnchor.constraint(equalToConstant:44).isActive = true; addArrangedSubview(labels)
    slider.minimumValue = -45; slider.maximumValue = 45; slider.minimumTrackTintColor = accent; slider.thumbTintColor = accent
    slider.accessibilityLabel = "Straighten"
    slider.addAction(UIAction { [weak slider] _ in if let slider { onStraighten(CGFloat((slider.value*10).rounded()/10)) } },for:.valueChanged)
    slider.heightAnchor.constraint(equalToConstant:48).isActive = true; addArrangedSubview(slider)
    let rail = UIStackView(); rail.axis = .horizontal
    let ratios: [(String,CGFloat?)] = [("Free",nil),("Original",nil),("1:1",1),("9:16",9/16),("16:9",16/9),("10:16",10/16),("16:10",16/10),("4:5",4/5),("5:4",5/4),("3:4",3/4),("4:3",4/3)]
    for (name,ratio) in ratios {
      let control = UIButton(type:.system)
      var config = UIButton.Configuration.plain(); config.title = name; config.imagePlacement = .top; config.imagePadding = 6
      config.baseForegroundColor = foreground
      config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { value in var output = value; output.font = .systemFont(ofSize:11); return output }
      let aspect = name == "Original" ? max(0.01,originalRatio()) : ratio ?? 1.25
      config.image = UIGraphicsImageRenderer(size:CGSize(width:30,height:30)).image { _ in
        let w = min(24,24*aspect), h = w/aspect
        let path = UIBezierPath(roundedRect:CGRect(x:(30-w)/2,y:(30-h)/2,width:w,height:h),cornerRadius:2)
        UIColor.white.setStroke(); path.lineWidth = 1.5; path.stroke()
      }.withRenderingMode(.alwaysTemplate)
      control.configuration = config; control.layer.cornerRadius = 12
      control.accessibilityLabel = "\(name) crop ratio"
      control.widthAnchor.constraint(equalToConstant:64).isActive = true
      control.addAction(UIAction { _ in onRatio(name,name == "Original" ? originalRatio() : ratio) },for:.touchUpInside)
      control.applyPressScale(); rail.addArrangedSubview(control); presets[name] = control
    }
    let scroll = UIScrollView(); scroll.showsHorizontalScrollIndicator = false; scroll.addSubview(rail)
    rail.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      rail.leadingAnchor.constraint(equalTo:scroll.contentLayoutGuide.leadingAnchor), rail.trailingAnchor.constraint(equalTo:scroll.contentLayoutGuide.trailingAnchor),
      rail.topAnchor.constraint(equalTo:scroll.contentLayoutGuide.topAnchor),rail.bottomAnchor.constraint(equalTo:scroll.contentLayoutGuide.bottomAnchor),
      rail.heightAnchor.constraint(equalTo:scroll.frameLayoutGuide.heightAnchor), scroll.heightAnchor.constraint(equalToConstant:72)
    ])
    addArrangedSubview(scroll)
  }
  required init(coder:NSCoder) { fatalError("init(coder:) has not been implemented") }
  func sync(_ state: PhotoTransformState) {
    slider.value = Float(state.straightenDegrees); degrees.text = String(format:"%.1f°",Double(state.straightenDegrees))
    for (name,control) in presets {
      let selected = name == state.aspectPreset
      control.configuration?.baseForegroundColor = selected ? accent : foreground
      control.backgroundColor = selected ? accent.withAlphaComponent(0.13) : .clear
      control.accessibilityTraits = selected ? [.button,.selected] : .button
    }
  }
}

private final class CropRulerSlider: UISlider {
  override func draw(_ rect:CGRect) {
    if let context = UIGraphicsGetCurrentContext() {
      context.setLineWidth(0.5)
      for i in 0...90 {
        let x = 12+(bounds.width-24)*CGFloat(i)/90
        context.setStrokeColor(UIColor.white.withAlphaComponent(i % 5 == 0 ? 0.5 : 0.2).cgColor)
        context.move(to:CGPoint(x:x,y:2)); context.addLine(to:CGPoint(x:x,y:i % 5 == 0 ? 12 : 7)); context.strokePath()
      }
    }
    super.draw(rect)
  }
}
