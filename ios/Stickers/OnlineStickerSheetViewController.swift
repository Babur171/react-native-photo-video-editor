import UIKit

/// Bottom sheet: search + category filter + a 5-column grid of bundled stickers. Tapping one
/// writes the PNG to a temp file and hands `(URL, UIImage)` back to
/// `onStickerPicked` — the caller feeds that straight into the existing
/// `handlePickedImage(_:image:purpose:)` insertion path (photo sticker chip / video sticker
/// action sheet), so this file has zero knowledge of `PhotoLayer`/session internals.
///
/// Built entirely in code (no storyboard/xib), consistent with the rest of this codebase.
final class OnlineStickerSheetViewController: UIViewController {
  struct RuntimeSticker { let id: String; let uri: String }
  /// Fired once after the bundled image is written to a temp file.
  /// The sheet dismisses itself first, then invokes this closure.
  var onStickerPicked: ((URL, UIImage) -> Void)?
  var onDismiss: (() -> Void)?
  private var didNotifyDismiss = false

  private var localStickerNames: [String] = []
  private var displayedLocalStickerNames: [String] = []
  private let runtimeStickers: [RuntimeSticker]
  private var displayedRuntimeStickers: [RuntimeSticker] = []
  private var selectedGroup: String?  // nil == "All"
  private var searchQuery: String = ""
  private var searchDebounceTimer: Timer?

  private let searchBar = UISearchBar()
  private let categoryScroll = UIScrollView()
  private let categoryStack = UIStackView()
  private var categoryButtons: [String: UIButton] = [:]  // key "" == All
  private let collectionView: UICollectionView
  private let emptyLabel = UILabel()
  private let stateContainer = UIView()

  private static let cellReuseID = "BundledStickerCell"
  private static let columns = 5
  private static let cellSpacing: CGFloat = 6

  init(runtimeStickers: [RuntimeSticker] = []) {
    self.runtimeStickers = runtimeStickers
    let layout = UICollectionViewFlowLayout()
    layout.minimumInteritemSpacing = Self.cellSpacing
    layout.minimumLineSpacing = Self.cellSpacing
    layout.sectionInset = UIEdgeInsets(top: Self.cellSpacing, left: Self.cellSpacing, bottom: Self.cellSpacing, right: Self.cellSpacing)
    collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
    super.init(nibName: nil, bundle: nil)
  }

  required init?(coder: NSCoder) { nil }

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = DesignTokens.elevatedPanel
    buildUI()
    loadLocalStickers()
  }

  override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)
    guard (isBeingDismissed || presentingViewController == nil), !didNotifyDismiss else { return }
    didNotifyDismiss = true
    onDismiss?()
  }

  // MARK: - UI construction

  private func buildUI() {
    searchBar.placeholder = "Search stickers"
    searchBar.delegate = self
    searchBar.searchBarStyle = .minimal
    searchBar.backgroundColor = .clear

    categoryScroll.showsHorizontalScrollIndicator = false
    categoryStack.axis = .horizontal
    categoryStack.spacing = 6
    rebuildCategoryChips()
    categoryScroll.addSubview(categoryStack)
    categoryStack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      categoryStack.leadingAnchor.constraint(equalTo: categoryScroll.contentLayoutGuide.leadingAnchor, constant: 12),
      categoryStack.trailingAnchor.constraint(equalTo: categoryScroll.contentLayoutGuide.trailingAnchor, constant: -12),
      categoryStack.topAnchor.constraint(equalTo: categoryScroll.contentLayoutGuide.topAnchor),
      categoryStack.bottomAnchor.constraint(equalTo: categoryScroll.contentLayoutGuide.bottomAnchor),
      categoryStack.heightAnchor.constraint(equalTo: categoryScroll.frameLayoutGuide.heightAnchor),
    ])

    collectionView.backgroundColor = .clear
    collectionView.dataSource = self
    collectionView.delegate = self
    collectionView.register(BundledStickerCell.self, forCellWithReuseIdentifier: Self.cellReuseID)

    emptyLabel.text = "No stickers found."
    emptyLabel.textColor = DesignTokens.onSurfaceVariant
    emptyLabel.textAlignment = .center
    emptyLabel.font = .systemFont(ofSize: 14)
    emptyLabel.isHidden = true

    let stateStack = UIStackView(arrangedSubviews: [emptyLabel])
    stateStack.axis = .vertical
    stateStack.alignment = .center
    stateStack.spacing = 12
    stateContainer.addSubview(stateStack)
    stateStack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      stateStack.centerXAnchor.constraint(equalTo: stateContainer.centerXAnchor),
      stateStack.centerYAnchor.constraint(equalTo: stateContainer.centerYAnchor),
      stateStack.leadingAnchor.constraint(greaterThanOrEqualTo: stateContainer.leadingAnchor, constant: 24),
      stateStack.trailingAnchor.constraint(lessThanOrEqualTo: stateContainer.trailingAnchor, constant: -24),
    ])

    // stateContainer (spinner/empty/error) overlays the collection view's area rather than
    // sharing stack space, since it's shown/hidden independently of the grid's own layout.
    let root = UIStackView(arrangedSubviews: [searchBar, categoryScroll, collectionView])
    root.axis = .vertical
    root.translatesAutoresizingMaskIntoConstraints = false

    view.addSubview(root)
    view.addSubview(stateContainer)
    root.translatesAutoresizingMaskIntoConstraints = false
    stateContainer.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      root.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
      root.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
      root.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 8),
      root.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -8),
      categoryScroll.heightAnchor.constraint(equalToConstant: 40),
      stateContainer.leadingAnchor.constraint(equalTo: collectionView.leadingAnchor),
      stateContainer.trailingAnchor.constraint(equalTo: collectionView.trailingAnchor),
      stateContainer.topAnchor.constraint(equalTo: collectionView.topAnchor),
      stateContainer.bottomAnchor.constraint(equalTo: collectionView.bottomAnchor),
    ])
  }

  private func rebuildCategoryChips() {
    categoryStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
    categoryButtons.removeAll()

    let localGroups = Set(localStickerNames.compactMap { $0.split(separator: "_").first.map { "local:" + $0.lowercased() } }).sorted()
    let runtimeGroups = runtimeStickers.isEmpty ? [] : [("runtime", "My stickers")]
    let all = [("", "All")] + runtimeGroups + localGroups.map { ($0, Self.displayName(for: $0.replacingOccurrences(of: "local:", with: ""))) }
    all.forEach { key, title in
      let chip = makeChip(title: title) { [weak self] in self?.selectCategory(key) }
      categoryButtons[key] = chip
      categoryStack.addArrangedSubview(chip)
    }
    updateCategorySelectionUI()
  }

  private static func displayName(for group: String) -> String {
    switch group {
    case "smileys-emotion": return "Smileys"
    case "people-body": return "People"
    case "animals-nature": return "Animals"
    case "food-drink": return "Food"
    case "travel-places": return "Travel"
    case "activities": return "Activities"
    case "objects": return "Objects"
    case "symbols": return "Symbols"
    case "flags": return "Flags"
    default: return group.capitalized
    }
  }

  private func makeChip(title: String, handler: @escaping () -> Void) -> UIButton {
    let chip = UIButton(type: .system)
    chip.setTitle(title, for: .normal)
    chip.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
    chip.backgroundColor = DesignTokens.surfaceContainerHigh
    chip.layer.cornerRadius = DesignTokens.radiusLg
    chip.contentEdgeInsets = UIEdgeInsets(top: 0, left: DesignTokens.spaceMd, bottom: 0, right: DesignTokens.spaceMd)
    chip.heightAnchor.constraint(greaterThanOrEqualToConstant: 32).isActive = true
    chip.addAction(UIAction { _ in handler() }, for: .touchUpInside)
    return chip
  }

  private func updateCategorySelectionUI() {
    let activeKey = selectedGroup ?? ""
    categoryButtons.forEach { key, chip in
      let selected = key == activeKey
      chip.setTitleColor(selected ? DesignTokens.onPrimaryContainer : DesignTokens.onSurfaceVariant, for: .normal)
      chip.backgroundColor = selected ? DesignTokens.primaryContainer : DesignTokens.surfaceContainerHigh
    }
  }

  private func loadLocalStickers() {
    let hostBundle = Bundle(for: OnlineStickerSheetViewController.self)
    let bundleURL = Bundle.main.url(forResource: "PhotoVideoEditorStickers", withExtension: "bundle")
      ?? hostBundle.url(forResource: "PhotoVideoEditorStickers", withExtension: "bundle")
    guard let bundleURL, let bundle = Bundle(url: bundleURL) else { return }
    localStickerNames = (bundle.urls(forResourcesWithExtension: "png", subdirectory: nil) ?? []).map(\.lastPathComponent).sorted()
    displayedLocalStickerNames = localStickerNames
    rebuildCategoryChips()
    applyFilter()
  }

  private func localStickerURL(named name: String) -> URL? {
    let hostBundle = Bundle(for: OnlineStickerSheetViewController.self)
    let bundleURL = Bundle.main.url(forResource: "PhotoVideoEditorStickers", withExtension: "bundle")
      ?? hostBundle.url(forResource: "PhotoVideoEditorStickers", withExtension: "bundle")
    return bundleURL.flatMap(Bundle.init(url:))?.url(forResource: (name as NSString).deletingPathExtension, withExtension: "png")
  }

  // MARK: - Filtering

  private func selectCategory(_ group: String) {
    selectedGroup = group.isEmpty ? nil : group
    updateCategorySelectionUI()
    applyFilter()
  }

  /// Search takes precedence over category when non-empty; otherwise filter by selected
  /// category, with "All" (nil) meaning no filter.
  private func applyFilter() {
    let normalizedQuery = searchQuery.lowercased()
    displayedLocalStickerNames = localStickerNames.filter { name in
      let matchesQuery = normalizedQuery.isEmpty || name.lowercased().contains(normalizedQuery)
      let matchesGroup = selectedGroup == nil || (selectedGroup?.hasPrefix("local:") == true && name.lowercased().hasPrefix(selectedGroup!.replacingOccurrences(of: "local:", with: "")))
      return matchesQuery && matchesGroup
    }
    displayedRuntimeStickers = runtimeStickers.filter { sticker in
      (selectedGroup == nil || selectedGroup == "runtime") && (normalizedQuery.isEmpty || sticker.id.lowercased().contains(normalizedQuery))
    }
    collectionView.reloadData()
    let isEmpty = displayedLocalStickerNames.isEmpty && displayedRuntimeStickers.isEmpty
    stateContainer.isHidden = !isEmpty
    emptyLabel.isHidden = !isEmpty
  }

  private func writeTempImage(_ image: UIImage) -> URL? {
    guard let data = image.pngData() else { return nil }
    let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
    do {
      try data.write(to: url)
      return url
    } catch {
      return nil
    }
  }

  func loadRuntimeSticker(_ sticker: RuntimeSticker, completion: @escaping (URL?, UIImage?) -> Void) {
    if !sticker.uri.lowercased().hasPrefix("https://") {
      guard let path = SourceResolver.resolvePath(sourceUri: sticker.uri, tempPrefix: "pve_runtime_sticker") else {
        completion(nil, nil)
        return
      }
      let localURL = URL(fileURLWithPath: path)
      completion(localURL, UIImage(contentsOfFile: path))
      return
    }
    guard let url = URL(string: sticker.uri) else { completion(nil, nil); return }
    URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
      guard let self, let data, let image = UIImage(data: data), let localURL = self.writeTempImage(image) else {
        DispatchQueue.main.async { completion(nil, nil) }
        return
      }
      DispatchQueue.main.async { completion(localURL, image) }
    }.resume()
  }

}

// MARK: - UISearchBarDelegate (debounced)

extension OnlineStickerSheetViewController: UISearchBarDelegate {
  func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
    searchDebounceTimer?.invalidate()
    searchDebounceTimer = Timer.scheduledTimer(withTimeInterval: 0.3, repeats: false) { [weak self] _ in
      guard let self else { return }
      self.searchQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
      self.applyFilter()
    }
  }

  func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
    searchBar.resignFirstResponder()
  }

  func searchBarCancelButtonClicked(_ searchBar: UISearchBar) {
    searchBar.text = ""
    searchBar.resignFirstResponder()
    searchDebounceTimer?.invalidate()
    searchQuery = ""
    applyFilter()
  }
}

// MARK: - UICollectionView

extension OnlineStickerSheetViewController: UICollectionViewDataSource, UICollectionViewDelegate, UICollectionViewDelegateFlowLayout {
  func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
    displayedRuntimeStickers.count + displayedLocalStickerNames.count
  }

  func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
    let cell = collectionView.dequeueReusableCell(withReuseIdentifier: Self.cellReuseID, for: indexPath) as! BundledStickerCell
    if indexPath.item < displayedRuntimeStickers.count {
      let sticker = displayedRuntimeStickers[indexPath.item]
      cell.configure(image: nil)
      loadRuntimeSticker(sticker) { [weak collectionView] _, image in
        guard collectionView?.indexPath(for: cell) == indexPath else { return }
        cell.configure(image: image)
      }
    } else {
      let name = displayedLocalStickerNames[indexPath.item - displayedRuntimeStickers.count]
      let image = localStickerURL(named: name).flatMap { UIImage(contentsOfFile: $0.path) }
      cell.configure(image: image)
    }
    return cell
  }

  func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
    collectionView.deselectItem(at: indexPath, animated: true)
    if indexPath.item < displayedRuntimeStickers.count {
      loadRuntimeSticker(displayedRuntimeStickers[indexPath.item]) { [weak self] url, image in
        guard let self, let url, let image else { return }
        self.dismiss(animated: true) { [weak self] in self?.onStickerPicked?(url, image) }
      }
    } else {
      let name = displayedLocalStickerNames[indexPath.item - displayedRuntimeStickers.count]
      guard let url = localStickerURL(named: name), let image = UIImage(contentsOfFile: url.path), let tempURL = writeTempImage(image) else { return }
      dismiss(animated: true) { [weak self] in self?.onStickerPicked?(tempURL, image) }
    }
  }

  func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
    let totalSpacing = Self.cellSpacing * CGFloat(Self.columns + 1)
    let width = (collectionView.bounds.width - totalSpacing) / CGFloat(Self.columns)
    return CGSize(width: max(width, 1), height: max(width, 1))
  }
}

/// One grid cell for a bundled sticker image.
private final class BundledStickerCell: UICollectionViewCell {
  private let imageView = UIImageView()

  override init(frame: CGRect) {
    super.init(frame: frame)
    contentView.backgroundColor = DesignTokens.surfaceContainerLow
    contentView.layer.cornerRadius = DesignTokens.radiusMd
    contentView.clipsToBounds = true

    imageView.contentMode = .scaleAspectFit
    contentView.addSubview(imageView)
    imageView.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      imageView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 4),
      imageView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -4),
      imageView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
      imageView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
    ])

  }

  required init?(coder: NSCoder) { nil }

  func configure(image: UIImage?) { imageView.image = image }

  override func prepareForReuse() {
    super.prepareForReuse()
    imageView.image = nil
  }
}
