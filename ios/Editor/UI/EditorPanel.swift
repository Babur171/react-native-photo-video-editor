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
  static let stripItemWidth: CGFloat = 58
  static let iconSize: CGFloat = 20
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
  let container = UIStackView()
  container.axis = .horizontal
  container.alignment = .center
  container.distribution = .equalSpacing
  container.isLayoutMarginsRelativeArrangement = true
  container.layoutMargins = UIEdgeInsets(
    top: 0,
    left: DesignTokens.spaceMd,
    bottom: 0,
    right: DesignTokens.spaceMd
  )

  let titleLabel = UILabel()
  titleLabel.text = title
  titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
  titleLabel.textColor = DesignTokens.textPrimary
  container.addArrangedSubview(titleLabel)

  let actions = UIStackView()
  actions.axis = .horizontal
  actions.spacing = DesignTokens.spaceMd
  actions.alignment = .center

  if let secondaryLabel, let onSecondary {
    let reset = UIButton(type: .system)
    reset.setTitle(secondaryLabel, for: .normal)
    reset.setTitleColor(DesignTokens.textSecondary, for: .normal)
    reset.titleLabel?.font = .systemFont(ofSize: 13, weight: .regular)
    reset.addAction(UIAction { _ in onSecondary() }, for: .touchUpInside)
    reset.applyPressScale()
    actions.addArrangedSubview(reset)
  }

  let done = editorPanelDoneButton(accentColor: accentColor, action: onDone)
  actions.addArrangedSubview(done)

  container.addArrangedSubview(actions)
  return container
}

private func editorPanelDoneButton(accentColor: UIColor, action: @escaping () -> Void) -> UIButton {
  let control = UIButton(type: .system)
  var config = UIButton.Configuration.plain()
  config.title = "Done"
  config.baseForegroundColor = accentColor
  config.contentInsets = NSDirectionalEdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12)
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
  scroll.alwaysBounceHorizontal = true
  scroll.delaysContentTouches = true
  scroll.canCancelContentTouches = true
  content.axis = .horizontal
  content.alignment = .fill
  scroll.addSubview(content)
  content.translatesAutoresizingMaskIntoConstraints = false
  NSLayoutConstraint.activate([
    content.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: DesignTokens.spaceMd),
    scroll.contentLayoutGuide.trailingAnchor.constraint(equalTo: content.trailingAnchor, constant: DesignTokens.spaceMd),
    content.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
    scroll.contentLayoutGuide.bottomAnchor.constraint(equalTo: content.bottomAnchor),
    content.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
  ])
  return scroll
}

/// Interactive icon + label button in an adjustment strip, with native press feedback.
final class EditorStripItemControl: UIButton {
  let iconHolder = UIView()
  let icon = UIImageView()
  let label = UILabel()
  let dot = UIView()
  private var action: (() -> Void)?

  init(systemName: String, labelText: String, action: @escaping () -> Void) {
    self.action = action
    super.init(frame: .zero)
    setupViews(systemName: systemName, labelText: labelText)
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  private func setupViews(systemName: String, labelText: String) {
    isAccessibilityElement = true
    accessibilityLabel = labelText
    accessibilityTraits = .button

    icon.image = UIImage(systemName: systemName, withConfiguration: UIImage.SymbolConfiguration(pointSize: 16, weight: .regular))
    icon.tintColor = DesignTokens.textSecondary
    icon.contentMode = .scaleAspectFit
    icon.isUserInteractionEnabled = false

    iconHolder.layer.cornerRadius = 15
    iconHolder.isUserInteractionEnabled = false
    iconHolder.addSubview(icon)
    icon.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      iconHolder.widthAnchor.constraint(equalToConstant: 30),
      iconHolder.heightAnchor.constraint(equalToConstant: 30),
      icon.centerXAnchor.constraint(equalTo: iconHolder.centerXAnchor),
      icon.centerYAnchor.constraint(equalTo: iconHolder.centerYAnchor),
      icon.widthAnchor.constraint(equalToConstant: EditorPanelMetrics.iconSize),
      icon.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.iconSize),
    ])

    label.text = labelText
    label.font = .systemFont(ofSize: 11, weight: .medium)
    label.textColor = DesignTokens.textSecondary
    label.textAlignment = .center
    label.numberOfLines = 1
    label.adjustsFontSizeToFitWidth = true
    label.minimumScaleFactor = 0.85
    label.isUserInteractionEnabled = false

    dot.layer.cornerRadius = 2
    dot.isHidden = true
    dot.isUserInteractionEnabled = false
    dot.widthAnchor.constraint(equalToConstant: 4).isActive = true
    dot.heightAnchor.constraint(equalToConstant: 4).isActive = true

    let column = UIStackView(arrangedSubviews: [iconHolder, label, dot])
    column.axis = .vertical
    column.alignment = .center
    column.spacing = DesignTokens.spaceXs
    column.isUserInteractionEnabled = false

    addSubview(column)
    column.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      widthAnchor.constraint(equalToConstant: EditorPanelMetrics.stripItemWidth),
      column.centerXAnchor.constraint(equalTo: centerXAnchor),
      column.centerYAnchor.constraint(equalTo: centerYAnchor),
      column.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor),
      column.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor),
    ])

    addAction(UIAction { [weak self] _ in self?.action?() }, for: .touchUpInside)
    applyPressScale()
  }

  override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    return bounds.insetBy(dx: -8, dy: -8).contains(point)
  }

  func setSelected(_ selected: Bool, accentColor: UIColor) {
    icon.tintColor = selected ? accentColor : DesignTokens.textSecondary
    label.textColor = selected ? accentColor : DesignTokens.textSecondary
    iconHolder.backgroundColor = selected ? accentColor.withAlphaComponent(0.18) : .clear
    accessibilityTraits = selected ? [.button, .selected] : .button
  }

  func setModified(_ modified: Bool, accentColor: UIColor) {
    dot.isHidden = !modified
    dot.backgroundColor = accentColor
  }
}

/// An icon + single-line label entry in a strip, with an optional "value changed" dot.
final class EditorStripItem {
  let control: EditorStripItemControl
  var root: UIView { control }

  init(control: EditorStripItemControl) {
    self.control = control
  }

  func setSelected(_ selected: Bool, accentColor: UIColor) {
    control.setSelected(selected, accentColor: accentColor)
  }

  func setModified(_ modified: Bool, accentColor: UIColor) {
    control.setModified(modified, accentColor: accentColor)
  }
}

func editorStripItem(systemName: String, labelText: String, action: @escaping () -> Void) -> EditorStripItem {
  let control = EditorStripItemControl(systemName: systemName, labelText: labelText, action: action)
  return EditorStripItem(control: control)
}

/// Interactive filter thumbnail button with native touch actions and selection border.
final class EditorFilterThumbnailControl: UIButton {
  let image = UIImageView()
  let frameView = UIView()
  let label = UILabel()
  private var action: (() -> Void)?

  init(labelText: String, action: @escaping () -> Void) {
    self.action = action
    super.init(frame: .zero)
    setupViews(labelText: labelText)
  }

  required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

  private func setupViews(labelText: String) {
    isAccessibilityElement = true
    accessibilityLabel = labelText
    accessibilityTraits = .button

    image.contentMode = .scaleAspectFill
    image.clipsToBounds = true
    image.layer.cornerRadius = DesignTokens.radiusMd
    image.backgroundColor = DesignTokens.surfaceContainerHigh
    image.isUserInteractionEnabled = false

    frameView.layer.cornerRadius = DesignTokens.radiusMd + 2
    frameView.layer.borderWidth = 2
    frameView.layer.borderColor = UIColor.clear.cgColor
    frameView.isUserInteractionEnabled = false

    frameView.addSubview(image)
    image.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      image.widthAnchor.constraint(equalToConstant: EditorPanelMetrics.thumbnailWidth),
      image.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.thumbnailHeight),
      image.leadingAnchor.constraint(equalTo: frameView.leadingAnchor, constant: 2),
      image.trailingAnchor.constraint(equalTo: frameView.trailingAnchor, constant: -2),
      image.topAnchor.constraint(equalTo: frameView.topAnchor, constant: 2),
      image.bottomAnchor.constraint(equalTo: frameView.bottomAnchor, constant: -2),
    ])

    label.text = labelText
    label.font = .systemFont(ofSize: 11, weight: .medium)
    label.textColor = DesignTokens.textSecondary
    label.textAlignment = .center
    label.numberOfLines = 1
    label.adjustsFontSizeToFitWidth = true
    label.minimumScaleFactor = 0.8
    label.isUserInteractionEnabled = false

    let column = UIStackView(arrangedSubviews: [frameView, label])
    column.axis = .vertical
    column.alignment = .center
    column.spacing = DesignTokens.spaceXs
    column.isUserInteractionEnabled = false

    addSubview(column)
    column.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      widthAnchor.constraint(equalToConstant: EditorPanelMetrics.thumbnailWidth + 12),
      column.centerXAnchor.constraint(equalTo: centerXAnchor),
      column.centerYAnchor.constraint(equalTo: centerYAnchor),
      label.widthAnchor.constraint(equalTo: widthAnchor),
    ])

    addAction(UIAction { [weak self] _ in self?.action?() }, for: .touchUpInside)
    applyPressScale()
  }

  override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
    return bounds.insetBy(dx: -8, dy: -8).contains(point)
  }

  func setSelected(_ selected: Bool, accentColor: UIColor) {
    frameView.layer.borderColor = (selected ? accentColor : UIColor.clear).cgColor
    frameView.layer.borderWidth = 2
    label.textColor = selected ? accentColor : DesignTokens.textSecondary
    accessibilityTraits = selected ? [.button, .selected] : .button
  }
}

/// Filter thumbnail: a preview of the current photo, outlined in purple when selected.
final class EditorFilterThumbnail {
  let control: EditorFilterThumbnailControl
  var root: UIView { control }
  var image: UIImageView { control.image }

  init(control: EditorFilterThumbnailControl) {
    self.control = control
  }

  func setSelected(_ selected: Bool, accentColor: UIColor) {
    control.setSelected(selected, accentColor: accentColor)
  }
}

func editorFilterThumbnail(labelText: String, action: @escaping () -> Void) -> EditorFilterThumbnail {
  let control = EditorFilterThumbnailControl(labelText: labelText, action: action)
  return EditorFilterThumbnail(control: control)
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
