import UIKit

/// Shared building blocks for the editor's bottom panels (Crop / Adjust / Filters / Text / Stickers).
/// Mirrors `EditorPanel.kt` — keep the two in step so the platforms don't drift apart again.
///
/// Before this existed, Adjust and Filters shared one bar of plain text buttons. Every panel now
/// composes the same three pieces — `editorPanelHeader`, one slider, one strip — so heights,
/// padding, radii, typography and selected states stay consistent.
enum EditorPanelMetrics {
  static let headerHeight: CGFloat = 40
  static let sliderHeight: CGFloat = 48
  static let stripHeight: CGFloat = 76
  static let stripItemWidth: CGFloat = 68
  static let iconSize: CGFloat = 22
  static let thumbnailWidth: CGFloat = 64
  static let thumbnailHeight: CGFloat = 72
  static var totalHeight: CGFloat { headerHeight + sliderHeight + stripHeight }
}

/// Compact panel header: `Title            [Reset]  Done`.
/// "Done" is an action, not a tool — it gets a 44pt touch target while staying visually small.
func editorPanelHeader(
  title: String,
  accentColor: UIColor,
  secondaryLabel: String? = nil,
  onSecondary: (() -> Void)? = nil,
  onDone: @escaping () -> Void
) -> UIView {
  let titleLabel = UILabel()
  titleLabel.text = title
  titleLabel.font = .systemFont(ofSize: 14, weight: .bold)
  titleLabel.textColor = DesignTokens.textPrimary

  let row = UIStackView(arrangedSubviews: [titleLabel])
  row.axis = .horizontal
  row.alignment = .center
  row.spacing = DesignTokens.spaceSm
  row.isLayoutMarginsRelativeArrangement = true
  row.layoutMargins = UIEdgeInsets(top: 0, left: DesignTokens.spaceLg, bottom: 0, right: DesignTokens.spaceSm)

  let spacer = UIView()
  spacer.setContentHuggingPriority(.defaultLow, for: .horizontal)
  row.addArrangedSubview(spacer)

  if let secondaryLabel, let onSecondary {
    row.addArrangedSubview(editorHeaderAction(label: secondaryLabel, color: DesignTokens.textSecondary, action: onSecondary))
  }
  row.addArrangedSubview(editorHeaderAction(label: "Done", color: accentColor, action: onDone))
  return row
}

/// Small text action sized for touch (44pt) without looking like a button.
private func editorHeaderAction(label: String, color: UIColor, action: @escaping () -> Void) -> UIButton {
  let control = UIButton(type: .system)
  var config = UIButton.Configuration.plain()
  config.title = label
  config.baseForegroundColor = color
  config.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: DesignTokens.spaceMd, bottom: 0, trailing: DesignTokens.spaceMd)
  config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
    var value = incoming; value.font = .systemFont(ofSize: 13, weight: .bold); return value
  }
  control.configuration = config
  control.widthAnchor.constraint(greaterThanOrEqualToConstant: DesignTokens.touchTargetMin).isActive = true
  control.heightAnchor.constraint(equalToConstant: DesignTokens.touchTargetMin).isActive = true
  control.addAction(UIAction { _ in action() }, for: .touchUpInside)
  control.applyPressScale()
  return control
}

/// Horizontally scrollable strip; the container every panel's list sits in.
func editorToolStrip(_ content: UIStackView) -> UIScrollView {
  let scroll = UIScrollView()
  scroll.showsHorizontalScrollIndicator = false
  content.axis = .horizontal
  content.alignment = .center
  scroll.addSubview(content)
  content.translatesAutoresizingMaskIntoConstraints = false
  NSLayoutConstraint.activate([
    content.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: DesignTokens.spaceMd),
    content.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -DesignTokens.spaceMd),
    content.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
    content.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
    content.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
  ])
  return scroll
}

/// An icon + single-line label entry in a strip, with an optional "value changed" dot.
final class EditorStripItem {
  let root: UIView
  private let iconHolder: UIView
  private let icon: UIImageView
  private let label: UILabel
  private let dot: UIView

  init(root: UIView, iconHolder: UIView, icon: UIImageView, label: UILabel, dot: UIView) {
    self.root = root
    self.iconHolder = iconHolder
    self.icon = icon
    self.label = label
    self.dot = dot
  }

  /// Selected state is a purple icon + label over a faint purple disc — never a filled purple tile,
  /// which at this size reads as a giant button rather than a selection.
  func setSelected(_ selected: Bool, accentColor: UIColor) {
    icon.tintColor = selected ? accentColor : DesignTokens.textSecondary
    label.textColor = selected ? accentColor : DesignTokens.textSecondary
    iconHolder.backgroundColor = selected ? accentColor.withAlphaComponent(0.18) : .clear
  }

  /// Tiny dot marking an adjustment that is no longer at its default value.
  func setModified(_ modified: Bool, accentColor: UIColor) {
    dot.isHidden = !modified
    dot.backgroundColor = accentColor
  }
}

func editorStripItem(systemName: String, labelText: String, action: @escaping () -> Void) -> EditorStripItem {
  let icon = UIImageView(image: UIImage(systemName: systemName, withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .regular)))
  icon.tintColor = DesignTokens.textSecondary
  icon.contentMode = .scaleAspectFit

  let iconHolder = UIView()
  iconHolder.layer.cornerRadius = 16
  iconHolder.addSubview(icon)
  icon.translatesAutoresizingMaskIntoConstraints = false
  NSLayoutConstraint.activate([
    iconHolder.widthAnchor.constraint(equalToConstant: 32),
    iconHolder.heightAnchor.constraint(equalToConstant: 32),
    icon.centerXAnchor.constraint(equalTo: iconHolder.centerXAnchor),
    icon.centerYAnchor.constraint(equalTo: iconHolder.centerYAnchor),
    icon.widthAnchor.constraint(equalToConstant: EditorPanelMetrics.iconSize),
    icon.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.iconSize),
  ])

  let label = UILabel()
  label.text = labelText
  label.font = .systemFont(ofSize: 11, weight: .medium)
  label.textColor = DesignTokens.textSecondary
  label.textAlignment = .center
  // Labels like "Brightness" and "Saturation" must never wrap to a second line.
  label.numberOfLines = 1
  label.adjustsFontSizeToFitWidth = true
  label.minimumScaleFactor = 0.85

  let dot = UIView()
  dot.layer.cornerRadius = 2
  dot.isHidden = true
  dot.widthAnchor.constraint(equalToConstant: 4).isActive = true
  dot.heightAnchor.constraint(equalToConstant: 4).isActive = true

  let column = UIStackView(arrangedSubviews: [iconHolder, label, dot])
  column.axis = .vertical
  column.alignment = .center
  column.spacing = DesignTokens.spaceXs

  let root = UIView()
  root.addSubview(column)
  column.translatesAutoresizingMaskIntoConstraints = false
  NSLayoutConstraint.activate([
    root.widthAnchor.constraint(equalToConstant: EditorPanelMetrics.stripItemWidth),
    column.centerXAnchor.constraint(equalTo: root.centerXAnchor),
    column.centerYAnchor.constraint(equalTo: root.centerYAnchor),
    column.leadingAnchor.constraint(greaterThanOrEqualTo: root.leadingAnchor),
    column.trailingAnchor.constraint(lessThanOrEqualTo: root.trailingAnchor),
  ])
  root.isAccessibilityElement = true
  root.accessibilityLabel = labelText
  root.accessibilityTraits = .button
  root.addGestureRecognizer(ClosureTapGestureRecognizer(action: action))
  root.applyPressScale()
  return EditorStripItem(root: root, iconHolder: iconHolder, icon: icon, label: label, dot: dot)
}

/// Filter thumbnail: a preview of the current photo, outlined in purple when selected.
/// A thin outline rather than a filled container, so the photo stays readable.
final class EditorFilterThumbnail {
  let root: UIView
  let image: UIImageView
  private let frame: UIView
  private let label: UILabel

  init(root: UIView, image: UIImageView, frame: UIView, label: UILabel) {
    self.root = root
    self.image = image
    self.frame = frame
    self.label = label
  }

  func setSelected(_ selected: Bool, accentColor: UIColor) {
    frame.layer.borderColor = (selected ? accentColor : UIColor.clear).cgColor
    frame.layer.borderWidth = 2
    label.textColor = selected ? accentColor : DesignTokens.textSecondary
  }
}

func editorFilterThumbnail(labelText: String, action: @escaping () -> Void) -> EditorFilterThumbnail {
  let image = UIImageView()
  image.contentMode = .scaleAspectFill
  image.clipsToBounds = true
  image.layer.cornerRadius = DesignTokens.radiusMd
  image.backgroundColor = DesignTokens.surfaceContainerHigh

  // The outline lives on a frame *around* the image so the stroke never crops the preview.
  let frame = UIView()
  frame.layer.cornerRadius = DesignTokens.radiusMd + 2
  frame.addSubview(image)
  image.translatesAutoresizingMaskIntoConstraints = false
  NSLayoutConstraint.activate([
    image.widthAnchor.constraint(equalToConstant: EditorPanelMetrics.thumbnailWidth),
    image.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.thumbnailHeight),
    image.leadingAnchor.constraint(equalTo: frame.leadingAnchor, constant: 2),
    image.trailingAnchor.constraint(equalTo: frame.trailingAnchor, constant: -2),
    image.topAnchor.constraint(equalTo: frame.topAnchor, constant: 2),
    image.bottomAnchor.constraint(equalTo: frame.bottomAnchor, constant: -2),
  ])

  let label = UILabel()
  label.text = labelText
  label.font = .systemFont(ofSize: 11, weight: .medium)
  label.textColor = DesignTokens.textSecondary
  label.textAlignment = .center
  label.numberOfLines = 1
  label.adjustsFontSizeToFitWidth = true
  label.minimumScaleFactor = 0.8

  let column = UIStackView(arrangedSubviews: [frame, label])
  column.axis = .vertical
  column.alignment = .center
  column.spacing = DesignTokens.spaceXs

  let root = UIView()
  root.addSubview(column)
  column.translatesAutoresizingMaskIntoConstraints = false
  NSLayoutConstraint.activate([
    root.widthAnchor.constraint(equalToConstant: EditorPanelMetrics.thumbnailWidth + 12),
    column.centerXAnchor.constraint(equalTo: root.centerXAnchor),
    column.centerYAnchor.constraint(equalTo: root.centerYAnchor),
    label.widthAnchor.constraint(equalTo: root.widthAnchor),
  ])
  root.isAccessibilityElement = true
  root.accessibilityLabel = labelText
  root.accessibilityTraits = .button
  root.addGestureRecognizer(ClosureTapGestureRecognizer(action: action))
  root.applyPressScale()
  return EditorFilterThumbnail(root: root, image: image, frame: frame, label: label)
}

/// The editor's one slider: a `Label ......... value` line above a thin track, so a panel never
/// needs a separate caption row and the same instance can be re-pointed at whichever adjustment is
/// selected. Mirrors `EditorToolSlider.kt`.
final class EditorValueSlider: UIView {
  let slider = UISlider()
  private let titleLabel = UILabel()
  private let valueLabel = UILabel()
  private let neutralTick = UIView()

  /// Value at which the underlying adjustment is neutral, for bipolar ranges. When set, a tick is
  /// drawn there so it is obvious where "no change" sits.
  var neutralValue: Float? {
    didSet { updateNeutralTick() }
  }

  var titleText: String? {
    get { titleLabel.text }
    set { titleLabel.text = newValue }
  }

  var valueText: String? {
    get { valueLabel.text }
    set { valueLabel.text = newValue }
  }

  init(accentColor: UIColor) {
    super.init(frame: .zero)
    titleLabel.font = .systemFont(ofSize: 12, weight: .medium)
    titleLabel.textColor = DesignTokens.textSecondary
    valueLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .medium)
    valueLabel.textColor = DesignTokens.textSecondary
    valueLabel.textAlignment = .right

    slider.minimumTrackTintColor = accentColor
    slider.maximumTrackTintColor = UIColor.white.withAlphaComponent(0.28)
    if let thumb = UIImage(systemName: "circle.fill")?.withConfiguration(UIImage.SymbolConfiguration(pointSize: 12)) {
      slider.setThumbImage(thumb.withTintColor(.white, renderingMode: .alwaysOriginal), for: .normal)
    }

    neutralTick.backgroundColor = DesignTokens.textSubtle
    neutralTick.isHidden = true
    // Sits above the track (added last) but must never swallow a drag toward it.
    neutralTick.isUserInteractionEnabled = false

    let caption = UIStackView(arrangedSubviews: [titleLabel, valueLabel])
    caption.axis = .horizontal
    caption.distribution = .fill
    valueLabel.setContentHuggingPriority(.required, for: .horizontal)

    let column = UIStackView(arrangedSubviews: [caption, slider])
    column.axis = .vertical
    column.spacing = 2
    column.isLayoutMarginsRelativeArrangement = true
    column.layoutMargins = UIEdgeInsets(top: 4, left: DesignTokens.spaceLg, bottom: 4, right: DesignTokens.spaceLg)

    addSubview(column)
    addSubview(neutralTick)
    column.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      column.leadingAnchor.constraint(equalTo: leadingAnchor),
      column.trailingAnchor.constraint(equalTo: trailingAnchor),
      column.topAnchor.constraint(equalTo: topAnchor),
      column.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) is not used") }

  override func layoutSubviews() {
    super.layoutSubviews()
    updateNeutralTick()
  }

  private func updateNeutralTick() {
    guard let neutralValue, slider.maximumValue > slider.minimumValue, slider.bounds.width > 0 else {
      neutralTick.isHidden = true
      return
    }
    neutralTick.isHidden = false
    let fraction = CGFloat((neutralValue - slider.minimumValue) / (slider.maximumValue - slider.minimumValue))
    let trackFrame = slider.convert(slider.bounds, to: self)
    let x = trackFrame.minX + trackFrame.width * fraction
    neutralTick.frame = CGRect(x: x - 0.5, y: trackFrame.midY - 5, width: 1, height: 10)
  }
}
