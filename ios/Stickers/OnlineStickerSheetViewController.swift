import UIKit

/// Bottom sheet: search + category filter + a 4-column grid of OpenMoji stickers. Tapping one
/// downloads the full-res PNG, writes it to a temp file, and hands `(URL, UIImage)` back to
/// `onStickerPicked` — the caller feeds that straight into the existing
/// `handlePickedImage(_:image:purpose:)` insertion path (photo sticker chip / video sticker
/// action sheet), so this file has zero knowledge of `PhotoLayer`/session internals.
///
/// Built entirely in code (no storyboard/xib), consistent with the rest of this codebase.
final class OnlineStickerSheetViewController: UIViewController {
  /// Fired once, after the full-res download completes and the image is written to a temp file.
  /// The sheet dismisses itself first, then invokes this closure.
  var onStickerPicked: ((URL, UIImage) -> Void)?

  private let catalog = OpenMojiCatalog.shared
  private let loader = RemoteImageLoader.shared

  private enum LoadState {
    case loading, loaded, error(String)
  }

  private var loadState: LoadState = .loading
  private var allEntries: [OpenMojiEntry] = []
  private var displayedEntries: [OpenMojiEntry] = []
  private var selectedGroup: String?  // nil == "All"
  private var searchQuery: String = ""
  private var searchDebounceTimer: Timer?
  private var downloadInFlightHexcode: String?

  private let searchBar = UISearchBar()
  private let categoryScroll = UIScrollView()
  private let categoryStack = UIStackView()
  private var categoryButtons: [String: UIButton] = [:]  // key "" == All
  private let collectionView: UICollectionView
  private let spinner = UIActivityIndicatorView(style: .large)
  private let emptyLabel = UILabel()
  private let errorLabel = UILabel()
  private let retryButton = UIButton(type: .system)
  private let attributionLabel = UILabel()
  private let stateContainer = UIView()

  private static let cellReuseID = "OpenMojiCell"
  private static let columns = 4
  private static let cellSpacing: CGFloat = 8

  init() {
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
    view.backgroundColor = DesignTokens.surfaceContainerLowest
    buildUI()
    loadCatalog()
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
    collectionView.register(OpenMojiCell.self, forCellWithReuseIdentifier: Self.cellReuseID)

    spinner.hidesWhenStopped = true

    emptyLabel.text = "No stickers found."
    emptyLabel.textColor = DesignTokens.onSurfaceVariant
    emptyLabel.textAlignment = .center
    emptyLabel.font = .systemFont(ofSize: 14)
    emptyLabel.isHidden = true

    errorLabel.text = "Couldn't load stickers — check your connection."
    errorLabel.textColor = DesignTokens.onSurfaceVariant
    errorLabel.textAlignment = .center
    errorLabel.font = .systemFont(ofSize: 14)
    errorLabel.numberOfLines = 0
    errorLabel.isHidden = true

    retryButton.setTitle("Retry", for: .normal)
    retryButton.setTitleColor(DesignTokens.primaryContainer, for: .normal)
    retryButton.isHidden = true
    retryButton.addAction(UIAction { [weak self] _ in self?.loadCatalog(forceRefresh: true) }, for: .touchUpInside)

    let stateStack = UIStackView(arrangedSubviews: [spinner, emptyLabel, errorLabel, retryButton])
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

    attributionLabel.text = "Stickers by OpenMoji (CC BY-SA 4.0)"
    attributionLabel.font = .systemFont(ofSize: 11)
    attributionLabel.textColor = DesignTokens.outline
    attributionLabel.textAlignment = .center

    // stateContainer (spinner/empty/error) overlays the collection view's area rather than
    // sharing stack space, since it's shown/hidden independently of the grid's own layout.
    let root = UIStackView(arrangedSubviews: [searchBar, categoryScroll, collectionView, attributionLabel])
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
      attributionLabel.heightAnchor.constraint(equalToConstant: 20),

      stateContainer.leadingAnchor.constraint(equalTo: collectionView.leadingAnchor),
      stateContainer.trailingAnchor.constraint(equalTo: collectionView.trailingAnchor),
      stateContainer.topAnchor.constraint(equalTo: collectionView.topAnchor),
      stateContainer.bottomAnchor.constraint(equalTo: collectionView.bottomAnchor),
    ])
  }

  private func rebuildCategoryChips() {
    categoryStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
    categoryButtons.removeAll()

    let all = [("", "All")] + catalog.groups().map { ($0, Self.displayName(for: $0)) }
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

  // MARK: - Loading

  private func loadCatalog(forceRefresh: Bool = false) {
    setLoadState(.loading)
    catalog.load { [weak self] entries, error in
      guard let self else { return }
      if let entries {
        self.allEntries = entries
        self.rebuildCategoryChips()
        self.applyFilter()
        self.setLoadState(.loaded)
      } else {
        self.setLoadState(.error((error as NSError?)?.localizedDescription ?? "Couldn't load stickers — check your connection."))
      }
    }
  }

  private func setLoadState(_ state: LoadState) {
    loadState = state
    switch state {
    case .loading:
      stateContainer.isHidden = false
      spinner.startAnimating()
      emptyLabel.isHidden = true
      errorLabel.isHidden = true
      retryButton.isHidden = true
    case .loaded:
      spinner.stopAnimating()
      let isEmpty = displayedEntries.isEmpty
      stateContainer.isHidden = !isEmpty
      emptyLabel.isHidden = !isEmpty
      errorLabel.isHidden = true
      retryButton.isHidden = true
    case .error(let message):
      spinner.stopAnimating()
      stateContainer.isHidden = false
      emptyLabel.isHidden = true
      errorLabel.isHidden = false
      errorLabel.text = message
      retryButton.isHidden = false
    }
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
    if !searchQuery.isEmpty {
      displayedEntries = catalog.search(searchQuery)
    } else if let selectedGroup {
      displayedEntries = catalog.byGroup(selectedGroup)
    } else {
      displayedEntries = allEntries
    }
    collectionView.reloadData()
    if case .loaded = loadState { setLoadState(.loaded) }
  }

  // MARK: - Sticker selection / download

  private func stickerTapped(_ entry: OpenMojiEntry, at indexPath: IndexPath) {
    guard downloadInFlightHexcode == nil else { return }
    downloadInFlightHexcode = entry.hexcode
    if let cell = collectionView.cellForItem(at: indexPath) as? OpenMojiCell {
      cell.setDownloading(true)
    }
    let url = catalog.fullURL(entry.hexcode)
    loader.downloadFull(url: url) { [weak self] fileURL, error in
      guard let self else { return }
      self.downloadInFlightHexcode = nil
      if let cell = self.collectionView.cellForItem(at: indexPath) as? OpenMojiCell {
        cell.setDownloading(false)
      }
      guard let fileURL, let data = try? Data(contentsOf: fileURL), let image = UIImage(data: data) else {
        self.showInlineDownloadError()
        return
      }
      // Write to a fresh temp file (consistent with the "Upload" flow's `writeTempImage`) so the
      // caller always receives a `file://` URL it can treat like any other picked image, decoupled
      // from our own disk cache's lifetime.
      guard let tempURL = self.writeTempImage(image) else {
        self.showInlineDownloadError()
        return
      }
      self.dismiss(animated: true) { [weak self] in
        self?.onStickerPicked?(tempURL, image)
      }
    }
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

  private func showInlineDownloadError() {
    let alert = UIAlertController(title: "Download failed", message: "Couldn't download that sticker. Please try again.", preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: "OK", style: .default))
    present(alert, animated: true)
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
    displayedEntries.count
  }

  func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
    let cell = collectionView.dequeueReusableCell(withReuseIdentifier: Self.cellReuseID, for: indexPath) as! OpenMojiCell
    let entry = displayedEntries[indexPath.item]
    cell.configure(url: catalog.thumbnailURL(entry.hexcode), loader: loader)
    return cell
  }

  func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
    collectionView.deselectItem(at: indexPath, animated: true)
    guard indexPath.item < displayedEntries.count else { return }
    stickerTapped(displayedEntries[indexPath.item], at: indexPath)
  }

  func collectionView(_ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout, sizeForItemAt indexPath: IndexPath) -> CGSize {
    let totalSpacing = Self.cellSpacing * CGFloat(Self.columns + 1)
    let width = (collectionView.bounds.width - totalSpacing) / CGFloat(Self.columns)
    return CGSize(width: max(width, 1), height: max(width, 1))
  }
}

/// One grid cell: a thumbnail image view plus a small overlay spinner shown while its full-res
/// download is in flight.
private final class OpenMojiCell: UICollectionViewCell {
  private let imageView = UIImageView()
  private let spinner = UIActivityIndicatorView(style: .medium)

  override init(frame: CGRect) {
    super.init(frame: frame)
    contentView.backgroundColor = DesignTokens.surfaceContainerHigh
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

    spinner.hidesWhenStopped = true
    contentView.addSubview(spinner)
    spinner.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      spinner.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
      spinner.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
    ])
  }

  required init?(coder: NSCoder) { nil }

  func configure(url: URL, loader: RemoteImageLoader) {
    loader.loadThumbnail(url: url, into: imageView)
  }

  func setDownloading(_ downloading: Bool) {
    if downloading { spinner.startAnimating() } else { spinner.stopAnimating() }
    imageView.alpha = downloading ? 0.4 : 1.0
  }

  override func prepareForReuse() {
    super.prepareForReuse()
    imageView.image = nil
    spinner.stopAnimating()
    imageView.alpha = 1.0
  }
}
