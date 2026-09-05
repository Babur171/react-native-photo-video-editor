import UIKit

/// Keyboard-anchored text entry shared by photo and video. It only commits on Add/Done.
final class TextEditorSheet: UIViewController, UITextViewDelegate {
  private let input = UITextView()
  private let placeholder = UILabel()
  private let initialText: String
  private let editing: Bool
  private let accent: UIColor
  private let onSave: (String) -> Void
  private var inputHeight: NSLayoutConstraint?

  init(text: String, editing: Bool, accent: UIColor, onSave: @escaping (String) -> Void) {
    initialText = text; self.editing = editing; self.accent = accent; self.onSave = onSave
    super.init(nibName: nil, bundle: nil)
    modalPresentationStyle = .overFullScreen
    modalTransitionStyle = .crossDissolve
  }
  required init?(coder: NSCoder) { nil }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = UIColor.black.withAlphaComponent(0.7)
    let panel = UIStackView(); panel.axis = .vertical; panel.spacing = 16
    panel.backgroundColor = DesignTokens.surfaceContainerHigh
    panel.layer.cornerRadius = 16
    panel.isLayoutMarginsRelativeArrangement = true
    panel.layoutMargins = UIEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
    let title = UILabel(); title.text = editing ? "Edit Text" : "Add Text"
    title.textColor = .white; title.font = .systemFont(ofSize: 20, weight: .semibold)
    panel.addArrangedSubview(title)
    input.text = initialText; input.textColor = .white; input.tintColor = accent
    input.font = .systemFont(ofSize: 22)
    input.backgroundColor = DesignTokens.surfaceContainerLow
    input.layer.cornerRadius = 12
    input.textContainerInset = UIEdgeInsets(top: 12, left: 8, bottom: 12, right: 8)
    input.delegate = self
    input.accessibilityLabel = "Text"
    placeholder.text = "Type something..."; placeholder.textColor = DesignTokens.outline
    placeholder.font = input.font; placeholder.isUserInteractionEnabled = false
    input.addSubview(placeholder); placeholder.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      placeholder.leadingAnchor.constraint(equalTo: input.leadingAnchor, constant: 13),
      placeholder.topAnchor.constraint(equalTo: input.topAnchor, constant: 12),
    ])
    panel.addArrangedSubview(input)
    inputHeight = input.heightAnchor.constraint(equalToConstant: 100); inputHeight?.priority = .defaultHigh; inputHeight?.isActive = true
    let actions = UIStackView(); actions.axis = .horizontal; actions.spacing = 12
    actions.addArrangedSubview(UIView())
    let cancel = UIButton(type: .system); cancel.setTitle("Cancel", for: .normal); cancel.tintColor = .white
    cancel.addAction(UIAction { [weak self] _ in self?.close() }, for: .touchUpInside)
    let save = UIButton(type: .system)
    save.setTitle(editing ? "Done" : "Add", for: .normal); save.tintColor = .white
    save.backgroundColor = accent; save.layer.cornerRadius = 12
    save.addAction(UIAction { [weak self] _ in
      guard let self, !self.input.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
      self.onSave(self.input.text); self.close()
    }, for: .touchUpInside)
    for button in [cancel, save] {
      button.widthAnchor.constraint(equalToConstant: 80).isActive = true
      button.heightAnchor.constraint(equalToConstant: 48).isActive = true
      actions.addArrangedSubview(button)
    }
    panel.addArrangedSubview(actions)
    view.addSubview(panel); panel.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      panel.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor, constant: 12),
      panel.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor, constant: -12),
      panel.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -12),
      panel.topAnchor.constraint(greaterThanOrEqualTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
    ])
    textViewDidChange(input)
  }

  override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); input.becomeFirstResponder() }
  func textViewDidChange(_ textView: UITextView) {
    placeholder.isHidden = !textView.text.isEmpty
    let width = max(view.bounds.width - 64, 100)
    let height = textView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude)).height
    inputHeight?.constant = min(max(height, 80), 180)
    textView.isScrollEnabled = height > 180
  }
  private func close() { input.resignFirstResponder(); dismiss(animated: true) }
}
