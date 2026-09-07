import UIKit

/// Shared "Studio Violet" UI primitives (DESIGN.md), reused across the photo/video editor shell
/// instead of hand-rolling styling per screen. Mirrors `EditorComponents.kt` on Android.

/// A `UITapGestureRecognizer` that takes a closure instead of a target/action pair.
final class ClosureTapGestureRecognizer: UITapGestureRecognizer {
  private let action: () -> Void

  init(action: @escaping () -> Void) {
    self.action = action
    super.init(target: nil, action: nil)
    addTarget(self, action: #selector(handle))
  }

  @objc private func handle() { action() }
}

/// Tactile press feedback (scale 1.0 -> 0.97) matching DESIGN.md's "Tactile Instrument Feel".
/// Only observes touch-down/up via a zero-duration long-press recognizer with
/// `cancelsTouchesInView = false`, so it never interferes with an existing tap recognizer
/// (e.g. the tool rail's `handleToolNodeTap`) already attached to the same view.
private final class EditorPressFeedback: NSObject {
  private let pressedScale: CGFloat
  private weak var target: UIView?

  init(target: UIView, pressedScale: CGFloat) {
    self.target = target
    self.pressedScale = pressedScale
    super.init()
    let recognizer = UILongPressGestureRecognizer(target: self, action: #selector(handle(_:)))
    recognizer.minimumPressDuration = 0
    recognizer.cancelsTouchesInView = false
    target.addGestureRecognizer(recognizer)
  }

  @objc private func handle(_ recognizer: UILongPressGestureRecognizer) {
    guard let target else { return }
    switch recognizer.state {
    case .began:
      UIView.animate(withDuration: 0.08) { target.transform = CGAffineTransform(scaleX: self.pressedScale, y: self.pressedScale) }
    case .ended, .cancelled, .failed:
      UIView.animate(withDuration: 0.12) { target.transform = .identity }
    default:
      break
    }
  }
}

private var pressFeedbackAssociationKey: UInt8 = 0

extension UIView {
  /// Attaches (once) the shared press-scale feedback to this view.
  func applyPressScale(_ scale: CGFloat = 0.97) {
    guard objc_getAssociatedObject(self, &pressFeedbackAssociationKey) == nil else { return }
    let feedback = EditorPressFeedback(target: self, pressedScale: scale)
    objc_setAssociatedObject(self, &pressFeedbackAssociationKey, feedback, .OBJC_ASSOCIATION_RETAIN)
  }
}

enum EditorComponents {
  /// Elevated rounded container (DESIGN.md "Elevated Panels & Tiles"), used for cards/tiles/sheets.
  static func card(
    radius: CGFloat = DesignTokens.radiusLg,
    backgroundColor: UIColor = DesignTokens.elevatedPanel,
    borderColor: UIColor? = DesignTokens.interactiveNeutral
  ) -> UIView {
    let view = UIView()
    view.backgroundColor = backgroundColor
    view.layer.cornerRadius = radius
    if let borderColor {
      view.layer.borderColor = borderColor.cgColor
      view.layer.borderWidth = 1
    }
    return view
  }

  /// Pill chip for quick actions / filter tags (DESIGN.md "Chips & Filter Presets").
  static func chip(text: String, selected: Bool, onTap: @escaping () -> Void) -> UIView {
    let label = UILabel()
    label.text = text
    label.font = .systemFont(ofSize: 12, weight: .semibold)
    label.textColor = selected ? DesignTokens.textPrimary : DesignTokens.textSecondary
    label.textAlignment = .center

    let container = UIView()
    container.backgroundColor = selected ? DesignTokens.accentViolet : DesignTokens.elevatedPanel
    container.layer.cornerRadius = 16
    if !selected {
      container.layer.borderColor = DesignTokens.interactiveNeutral.cgColor
      container.layer.borderWidth = 1
    }
    container.addSubview(label)
    label.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: DesignTokens.spaceLg),
      label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -DesignTokens.spaceLg),
      label.topAnchor.constraint(equalTo: container.topAnchor, constant: DesignTokens.spaceXs),
      label.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -DesignTokens.spaceXs),
      container.heightAnchor.constraint(greaterThanOrEqualToConstant: 32),
    ])
    container.isUserInteractionEnabled = true
    container.addGestureRecognizer(ClosureTapGestureRecognizer(action: onTap))
    container.applyPressScale()
    return container
  }

  /// Primary Export CTA (DESIGN.md "Primary Export Button"): solid violet fill, 44pt min height,
  /// 12px radius, subtle glow shadow, 0.97 press scale.
  static func primaryButton(
    text: String,
    systemImage: String? = nil,
    fillColor: UIColor = DesignTokens.accentViolet,
    textColor: UIColor = DesignTokens.textPrimary,
    onTap: @escaping () -> Void
  ) -> UIButton {
    let button = UIButton(type: .system)
    button.setTitle(text, for: .normal)
    button.setTitleColor(textColor, for: .normal)
    if let systemImage { button.setImage(UIImage(systemName: systemImage), for: .normal) }
    button.tintColor = textColor
    button.backgroundColor = fillColor
    button.layer.cornerRadius = DesignTokens.radiusLg
    button.titleLabel?.font = .systemFont(ofSize: 13, weight: .bold)
    button.contentEdgeInsets = UIEdgeInsets(top: 0, left: DesignTokens.spaceLg, bottom: 0, right: DesignTokens.spaceLg)
    if #available(iOS 15.0, *) {
      button.configuration = nil // avoid UIButtonConfiguration overriding our manual styling on iOS 15+
    }
    button.heightAnchor.constraint(equalToConstant: DesignTokens.touchTargetMin).isActive = true
    button.layer.shadowColor = fillColor.cgColor
    button.layer.shadowOpacity = 0.35
    button.layer.shadowRadius = 12
    button.layer.shadowOffset = CGSize(width: 0, height: 4)
    button.addAction(UIAction { _ in onTap() }, for: .touchUpInside)
    button.applyPressScale()
    return button
  }
}
