import AVFoundation
import AVKit
import PhotosUI
import UIKit

/// Distinguishes which flow a picked image is for, since photo/video and sticker/overlay all share
/// one `PHPickerViewControllerDelegate` callback.
private enum ImagePickPurpose {
  case photoSticker, photoOverlay, videoSticker, videoOverlay
}

final class PhotoVideoEditorViewController: UIViewController {
  private let uri: String
  private let mediaType: String
  private let features: [String: Any]
  private let theme: [String: Any]
  private let exportOptions: [String: Any]
  private let initialStickerIds: [String]
  private let initialStickerLayerID = UUID().uuidString
  private var initialStickerImages: [String: (URL, UIImage)] = [:]
  private var initialStickerLoading = false
  private let stickerAssets: [[String: Any]]
  var completion: ((PhotoVideoEditorOutcome) -> Void)?

  private var photoSession: PhotoEditSession?
  private var cropMode = false
  private var cropEntryState: PhotoTransformState?
  private var photoCropPanel: CropPanel?
  private var filtersMode = false
  private var drawMode = false
  private var stickersMode = false
  private var pendingPickPurpose: ImagePickPurpose?
  private var activeAdjustmentKey = "brightness"
  private var selectedLayerID: String?
  private var activeLayerPropertyKey = ""
  private var dragStartSnapshot: [PhotoLayer]?
  private var photoImageView: ZoomableImageView?
  private var cropOverlay: CropOverlayView?
  private var layerOverlay: LayerOverlayView?
  private var drawOverlay: DrawOverlayView?
  private var straightenSlider: UISlider?
  private var cropSubBar: UIView?
  /// Drives whichever adjustment is selected in the Adjust panel.
  private var adjustmentSliderControl: EditorValueSlider?
  /// Filter strength, owned by the Filters panel (a view has one superview, so not shared).
  private var filterIntensityControl: EditorValueSlider?
  private var adjustSubBar: UIView?
  private var filtersSubBar: UIView?
  private var adjustMode = false
  /// Adjustment key (plus "mirror") -> its entry in the Adjust panel's strip.
  private var adjustmentStripItems: [String: EditorStripItem] = [:]
  /// Preset id ("" = Original) -> its thumbnail in the Filters carousel.
  private var filterThumbnails: [String: EditorFilterThumbnail] = [:]
  private let filterThumbnailLoader = FilterThumbnailLoader()
  private var stickersSubBar: UIView?
  private var drawSubBar: UIView?
  private var layerToolBar: UIView?
  private var layerPropertySlider: UISlider?
  private var layerColorSwatchRow: UIView?
  private var mainToolBar: UIView?
  private var undoButton: UIButton?
  private var redoButton: UIButton?
  private var progressOverlay: UIView?
  private var photoPreviewRenderPending = false
  private var isRenderingPhotoPreview = false
  private let photoRenderQueue = DispatchQueue(label: "pve.photo.render", qos: .userInteractive)
  private var drawColorSwatches: [(UIColor, UIButton)] = []
  private var comparingOriginal = false
  private var selectedPhotoExportFormat: String?

  private var videoSession: VideoEditSession?
  private var videoExporter: VideoExporter?
  private var videoAspectMode = false
  private var videoCropMode = false
  private var videoCropEntryState: PhotoTransformState?
  private var videoCropPanel: CropPanel?
  private var videoCropOverlay: CropOverlayView?
  private weak var cropPlayerView: UIView?
  private weak var videoPreviewContainer: UIView?
  private var trimRangeView: TrimRangeView?
  private var positionSlider: UISlider?
  private var playPauseButton: UIButton?
  private var timeLabel: UILabel?
  private var videoAspectSubBar: UIView?
  private var videoToolBar: UIView?
  private var videoExportProgressOverlay: UIView?
  private var positionPollTimer: Timer?
  private var videoOverlayImageView: UIImageView?
  private var videoOverlayRenderKey: String?
  private var videoNaturalSize: CGSize?
  private var videoLayerOverlay: LayerOverlayView?
  private var videoLayerToolBar: UIView?
  private var videoLayerPropertySlider: UISlider?
  private var selectedVideoLayerID: String?
  private var activeVideoLayerPropertyKey = ""
  private var videoDragStartSnapshot: [PhotoLayer]?
  private var videoColorRow: UIView?
  private var textSwatches: [(UIColor, UIView)] = []
  private var videoTextSwatches: [(UIColor, UIView)] = []
  private var sliderSnapshot: [PhotoLayer]?
  private var videoLayerPropertyButtons: [String: UIButton] = [:]
  private var seekingPosition = false
  private weak var videoControlsOverlay: UIView?
  private var videoControlsVisible = true
  private var hideVideoControlsTimer: Timer?
  private var selectedClipIndex = 0
  private var clipStripBar: UIStackView?
  private var clipStripScroll: UIScrollView?
  private var timelineThumbnailRepository: TimelineThumbnailRepository?
  private var timelineThumbnailRequest: TimelineThumbnailRepository.Request?
  private var timelineThumbnailFrames = [UIImage?](repeating: nil, count: 12)

  private var cropAspectButtons: [String: UIButton] = [:]
  private var layerPropertyButtons: [String: UIButton] = [:]
  private var videoToolButtons: [String: UIButton] = [:]
  private var videoAspectButtons: [String: UIButton] = [:]

  init(
    uri: String,
    mediaType: String,
    features: [String: Any],
    theme: [String: Any] = [:],
    exportOptions: [String: Any] = [:],
    stickerAssets: [[String: Any]] = [],
    initialStickerIds: [String] = []
  ) {
    self.uri = uri
    self.mediaType = mediaType
    self.features = features
    self.theme = theme
    self.exportOptions = exportOptions
    self.stickerAssets = stickerAssets
    self.initialStickerIds = initialStickerIds
    super.init(nibName: nil, bundle: nil)
    modalPresentationStyle = .fullScreen
  }
  required init?(coder: NSCoder) { nil }

  /// Parses a "#RRGGBB" or "#AARRGGBB" hex string; returns nil for anything else so callers can fall back to a default.
  private func color(_ key: String) -> UIColor? {
    guard var hex = theme[key] as? String, hex.hasPrefix("#") else { return nil }
    hex.removeFirst()
    guard hex.count == 6 || hex.count == 8, let value = UInt64(hex, radix: 16) else { return nil }
    let hasAlpha = hex.count == 8
    let alpha = hasAlpha ? CGFloat((value >> 24) & 0xFF) / 255 : 1
    let red = CGFloat((value >> 16) & 0xFF) / 255
    let green = CGFloat((value >> 8) & 0xFF) / 255
    let blue = CGFloat(value & 0xFF) / 255
    return UIColor(red: red, green: green, blue: blue, alpha: alpha)
  }

  override var preferredStatusBarStyle: UIStatusBarStyle {
    (theme["statusBarStyle"] as? String) == "dark" ? .darkContent : .lightContent
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    if mediaType == "photo" {
      buildPhotoEditor()
    } else {
      buildVideoEditor()
    }
    insertInitialSticker()
  }

  private func currentInitialSticker() -> PhotoLayer? {
    (photoSession?.layerStack.layers ?? videoSession?.layerStack.layers ?? []).first { $0.id == initialStickerLayerID }
  }

  private func switchInitialSticker() {
    guard !initialStickerLoading, initialStickerIds.count > 1 else { return }
    let currentID = currentInitialSticker()?.stickerId
    let currentIndex = currentID.flatMap { initialStickerIds.firstIndex(of: $0) } ?? -1
    insertInitialSticker(index: (currentIndex + 1) % initialStickerIds.count)
  }

  private func insertInitialSticker(index: Int = 0) {
    guard !initialStickerLoading, initialStickerIds.indices.contains(index) else { return }
    let id = initialStickerIds[index]
    guard let asset = consumerStickerAssets().first(where: { $0.id == id }) else { return }
    initialStickerLoading = true
    view.isUserInteractionEnabled = false
    let apply: (URL?, UIImage?) -> Void = { [weak self] url, image in
      guard let self else { return }
      self.initialStickerLoading = false
      self.view.isUserInteractionEnabled = true
      guard let url, let image else {
        if self.initialStickerImages.isEmpty {
          self.completion?(.failure(code: "E_SOURCE_UNREADABLE", message: "The initial sticker could not be loaded."))
        } else {
          let alert = UIAlertController(title: "Couldn't load sticker", message: "Please try again.", preferredStyle: .alert)
          alert.addAction(UIAlertAction(title: "OK", style: .default))
          self.present(alert, animated: true)
        }
        return
      }
      self.initialStickerImages[id] = (url, image)
      self.view.layoutIfNeeded()
      var layer = self.currentInitialSticker() ?? self.newSticker()
      layer.id = self.initialStickerLayerID
      layer.stickerId = id
      layer.stickerUri = url.absoluteString
      layer.overlayAspectRatio = max(image.size.width, 1) / max(image.size.height, 1)
      let replace: ([PhotoLayer]) -> [PhotoLayer] = { layers in
        if layers.contains(where: { $0.id == self.initialStickerLayerID }) {
          return layers.map { $0.id == self.initialStickerLayerID ? layer : $0 }
        }
        return layers + [layer]
      }
      if let session = self.photoSession {
        session.layerStack.commit(replace)
        self.onLayerStackChanged()
        self.selectLayer(layer.id)
      } else if let session = self.videoSession {
        session.layerStack.commit(replace)
        self.onVideoLayerStackChanged()
        self.selectVideoLayer(layer.id, session: session)
      }
    }
    if let cached = initialStickerImages[id] {
      apply(cached.0, cached.1)
    } else {
      let loader = OnlineStickerSheetViewController()
      loader.loadRuntimeSticker(.init(id: id, uri: asset.uri)) { [loader] url, image in
        _ = loader // Keep the loader alive until its download finishes.
        apply(url, image)
      }
    }
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    if mediaType == "photo" {
      photoImageView?.resetToFit()
      schedulePhotoPreviewRender()
    }
  }

  // MARK: - Photo editor (crop, rotate, straighten, export — Milestone 2)

  private func buildPhotoEditor() {
    let backgroundColor = color("backgroundColor") ?? DesignTokens.surfaceContainerLowest
    let toolbarColor = color("toolbarColor") ?? DesignTokens.surfaceContainerLow
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    view.backgroundColor = backgroundColor
    setNeedsStatusBarAppearanceUpdate()

    let session = PhotoEditSession(sourceUri: uri)
    photoSession = session

    let header = makeHeader(textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    let preview = UIView(); preview.backgroundColor = .black
    preview.layer.cornerRadius = DesignTokens.radiusLg
    preview.clipsToBounds = true

    guard let baseImage = session.baseImage else {
      completion?(.failure(code: "E_SOURCE_UNREADABLE", message: "The selected photo could not be decoded."))
      return
    }

    let imageView = ZoomableImageView(frame: .zero)
    imageView.image = session.renderPreview() ?? baseImage
    photoImageView = imageView
    preview.addSubview(imageView)
    imageView.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      imageView.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      imageView.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      imageView.topAnchor.constraint(equalTo: preview.topAnchor),
      imageView.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])

    let overlay = CropOverlayView()
    overlay.isHidden = true
    cropOverlay = overlay
    preview.addSubview(overlay)
    overlay.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      overlay.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      overlay.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      overlay.topAnchor.constraint(equalTo: preview.topAnchor),
      overlay.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])

    let layers = LayerOverlayView(frame: .zero)
    layers.swappableLayerID = initialStickerLayerID
    layers.stickerSwapEnabled = initialStickerIds.count > 1
    layers.onStickerSwap = { [weak self] in self?.switchInitialSticker() }
    layerOverlay = layers
    preview.addSubview(layers)
    layers.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      layers.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      layers.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      layers.topAnchor.constraint(equalTo: preview.topAnchor),
      layers.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])

    let drawing = DrawOverlayView()
    drawing.isHidden = true
    drawOverlay = drawing
    preview.addSubview(drawing)
    drawing.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      drawing.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      drawing.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      drawing.topAnchor.constraint(equalTo: preview.topAnchor),
      drawing.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])

    imageView.onBoundsChanged = { [weak self] bounds in
      guard let self else { return }
      if self.cropMode { overlay.restore(bounds:bounds,state:session.state) }
      layers.setImageBounds(bounds)
      drawing.setImageBounds(bounds)
    }
    overlay.onCropChanged = { left, top, right, bottom in
      session.update { $0.setCrop(left: left, top: top, right: right, bottom: bottom) }
    }
    overlay.onMediaBoundsChanged = { [weak imageView] bounds in imageView?.setRenderedBounds(bounds) }
    overlay.onViewportChanged = { zoom,x,y in session.update { $0.zoom = zoom; $0.panX = x; $0.panY = y } }

    layers.onLayerDelete = { [weak self] id in
      session.layerStack.commit { $0.filter { $0.id != id } }
      self?.selectLayer(nil); self?.onLayerStackChanged()
    }
    layers.onLayerTapped = { [weak self] id in self?.selectLayer(id) }
    layers.onLayerDoubleTapped = { [weak self] id in
      guard let self, let layer = session.layerStack.layers.first(where: { $0.id == id && $0.type == .text }) else { return }
      self.showTextInputDialog(session: session, editingLayer: layer)
    }
    layers.onLayerTransformChanged = { [weak self] id, x, y, scale, rotation in
      guard let self, let session = self.photoSession else { return }
      if self.dragStartSnapshot == nil { self.dragStartSnapshot = session.layerStack.layers }
      session.layerStack.updateLive { list in
        list.map { current in
          guard current.id == id else { return current }
          var layer = current
          layer.x = x; layer.y = y; layer.scale = scale; layer.rotationDegrees = rotation
          return layer
        }
      }
      layers.layers = session.layerStack.layers
      self.schedulePhotoPreviewRender()
    }
    layers.onLayerTransformEnded = { [weak self] _ in
      guard let self, let session = self.photoSession else { return }
      if let snapshot = self.dragStartSnapshot { session.layerStack.commitSnapshot(snapshot) }
      self.dragStartSnapshot = nil
      self.onLayerStackChanged()
    }

    let straighten = UISlider()
    straighten.minimumValue = -45
    straighten.maximumValue = 45
    straighten.value = 0
    straighten.isHidden = true
    straighten.addTarget(self, action: #selector(straightenChanged(_:)), for: .valueChanged)
    straightenSlider = straighten

    let cropBar = makeCropSubBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    cropBar.isHidden = true
    cropSubBar = cropBar

    let adjustmentControl = EditorValueSlider(accentColor: primaryColor)
    adjustmentControl.slider.addTarget(self, action: #selector(adjustmentChanged(_:)), for: .valueChanged)
    adjustmentSliderControl = adjustmentControl

    let intensityControl = EditorValueSlider(accentColor: primaryColor)
    intensityControl.titleText = "Intensity"
    intensityControl.slider.addTarget(self, action: #selector(filterIntensityChanged(_:)), for: .valueChanged)
    filterIntensityControl = intensityControl

    let adjustBar = makeAdjustSubBar(session: session, primaryColor: primaryColor)
    adjustBar.isHidden = true
    adjustSubBar = adjustBar

    let filtersBar = makeFiltersSubBar(session: session, primaryColor: primaryColor)
    filtersBar.isHidden = true
    filtersSubBar = filtersBar

    let stickersBar = makeStickersSubBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    stickersBar.isHidden = true
    stickersSubBar = stickersBar

    let drawBar = makeDrawSubBar(textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    drawBar.isHidden = true
    drawSubBar = drawBar

    let layerSlider = EditorToolSlider()
    layerSlider.isHidden = true
    layerSlider.addTarget(self, action: #selector(layerPropertyChanged(_:)), for: .valueChanged)
    configureLayerSlider(layerSlider, video: false)
    layerPropertySlider = layerSlider

    let layerBar = makeLayerToolBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    layerBar.isHidden = true
    layerToolBar = layerBar

    let colorSwatchRow = makeTextColorSwatchRow(session: session)
    colorSwatchRow.isHidden = true
    layerColorSwatchRow = colorSwatchRow

    let toolbar = makePhotoToolBar(textColor: textColor, toolbarColor: toolbarColor)
    mainToolBar = toolbar

    let root = UIStackView(arrangedSubviews: [header, preview, cropBar, adjustBar, filtersBar, stickersBar, drawBar, layerSlider, colorSwatchRow, layerBar, toolbar])
    root.axis = .vertical
    root.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(root)
    NSLayoutConstraint.activate([
      root.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
      root.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
      root.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
      root.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
      header.heightAnchor.constraint(equalToConstant: 60),
      toolbar.heightAnchor.constraint(equalToConstant: 72),
      cropBar.heightAnchor.constraint(equalToConstant: 232), // rotate row (44pt) + aspect chip row (56pt)
      adjustBar.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.totalHeight),
      filtersBar.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.totalHeight + 24),
      stickersBar.heightAnchor.constraint(equalToConstant: 56),
      drawBar.heightAnchor.constraint(equalToConstant: 56),
      layerSlider.heightAnchor.constraint(equalToConstant: 64),
      layerBar.heightAnchor.constraint(equalToConstant: 68),
      colorSwatchRow.heightAnchor.constraint(equalToConstant: 56),
    ])

    root.arrangedSubviews.forEach { child in
      child.constraints.filter { $0.firstAttribute == .height && $0.secondItem == nil }.forEach { $0.priority = UILayoutPriority(999) }
    }
    let progress = UIView()
    progress.backgroundColor = UIColor.black.withAlphaComponent(0.6)
    progress.isHidden = true
    let spinner = UIActivityIndicatorView(style: .large)
    spinner.color = .white
    spinner.startAnimating()
    progress.addSubview(spinner)
    spinner.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([spinner.centerXAnchor.constraint(equalTo: progress.centerXAnchor), spinner.centerYAnchor.constraint(equalTo: progress.centerYAnchor)])
    progressOverlay = progress
    view.addSubview(progress)
    progress.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      progress.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      progress.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      progress.topAnchor.constraint(equalTo: view.topAnchor),
      progress.bottomAnchor.constraint(equalTo: view.bottomAnchor),
    ])

    view.layoutIfNeeded()
    imageView.resetToFit()
  }

  /// Interactive tool dock button with tactile feedback, balanced icon/label spacing,
  /// and seamless scroll coordination without conflicting gesture recognizers.
  private final class ToolButton: UIControl {
    let tile = UIView()
    let icon: UIImageView
    let label = UILabel()
    private var onTapAction: (() -> Void)?

    init(systemName: String, labelText: String, onTap: (() -> Void)? = nil) {
      self.onTapAction = onTap
      self.icon = UIImageView(image: UIImage(systemName: systemName))
      super.init(frame: .zero)
      setupViews(labelText: labelText)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func setupViews(labelText: String) {
      isAccessibilityElement = true
      accessibilityLabel = labelText
      accessibilityTraits = .button

      tile.isUserInteractionEnabled = false
      tile.backgroundColor = .clear
      tile.layer.cornerRadius = 15
      tile.clipsToBounds = true

      icon.isUserInteractionEnabled = false
      icon.tintColor = DesignTokens.textSecondary
      icon.contentMode = .scaleAspectFit

      tile.addSubview(icon)
      icon.translatesAutoresizingMaskIntoConstraints = false
      NSLayoutConstraint.activate([
        icon.centerXAnchor.constraint(equalTo: tile.centerXAnchor),
        icon.centerYAnchor.constraint(equalTo: tile.centerYAnchor),
        icon.widthAnchor.constraint(equalToConstant: 18),
        icon.heightAnchor.constraint(equalToConstant: 18),
      ])

      label.isUserInteractionEnabled = false
      label.text = labelText
      label.font = .systemFont(ofSize: 11, weight: .medium)
      label.textColor = DesignTokens.textSecondary
      label.textAlignment = .center
      label.numberOfLines = 1
      label.adjustsFontSizeToFitWidth = true
      label.minimumScaleFactor = 0.85

      let contentStack = UIStackView(arrangedSubviews: [tile, label])
      contentStack.axis = .vertical
      contentStack.alignment = .center
      contentStack.spacing = 3
      contentStack.isUserInteractionEnabled = false

      addSubview(contentStack)
      contentStack.translatesAutoresizingMaskIntoConstraints = false
      NSLayoutConstraint.activate([
        tile.widthAnchor.constraint(equalToConstant: 30),
        tile.heightAnchor.constraint(equalToConstant: 30),
        contentStack.centerXAnchor.constraint(equalTo: centerXAnchor),
        contentStack.centerYAnchor.constraint(equalTo: centerYAnchor),
        label.leadingAnchor.constraint(greaterThanOrEqualTo: leadingAnchor, constant: 2),
        label.trailingAnchor.constraint(lessThanOrEqualTo: trailingAnchor, constant: -2),
      ])

      if onTapAction != nil {
        addTarget(self, action: #selector(handleTap), for: .touchUpInside)
      }
    }

    @objc private func handleTap() {
      onTapAction?()
    }

    override var isHighlighted: Bool {
      didSet {
        UIView.animate(
          withDuration: isHighlighted ? 0.08 : 0.14,
          delay: 0,
          options: [.allowUserInteraction, .beginFromCurrentState],
          animations: { self.transform = self.isHighlighted ? CGAffineTransform(scaleX: 0.94, y: 0.94) : .identity }
        )
      }
    }

    func setSelectedState(_ selected: Bool, accent: UIColor) {
      accessibilityTraits = selected ? [.button, .selected] : .button
      tile.backgroundColor = selected ? accent.withAlphaComponent(0.18) : .clear
      icon.tintColor = selected ? accent : DesignTokens.textSecondary
      label.textColor = selected ? accent : DesignTokens.textSecondary
    }
  }

  /// Icon-tile + label node in the main tool rail (Studio Violet "Tool Node").
  private struct ToolNode {
    let button: ToolButton
    var root: UIView { button }
    var tile: UIView { button.tile }
    var icon: UIImageView { button.icon }
    var label: UILabel { button.label }
  }

  private var photoToolNodes: [String: ToolNode] = [:]
  private weak var photoToolScroll: UIScrollView?

  private func createToolNode(systemName: String, labelText: String, onTap: (() -> Void)? = nil) -> ToolNode {
    let button = ToolButton(systemName: systemName, labelText: labelText, onTap: onTap)
    return ToolNode(button: button)
  }

  /// Selection is a purple icon + label over a faint purple disc — not a filled tile, which at dock
  /// size reads as a large button and fights the photo for attention.
  private func setToolNodeSelected(_ node: ToolNode, selected: Bool, textColor: UIColor) {
    let accent = color("primaryColor") ?? DesignTokens.primaryContainer
    node.button.setSelectedState(selected, accent: accent)
  }

  /// Full Studio Violet tool rail (see main_photo_editor_default_state mockup). Rotate lives inside
  /// the Crop sub-bar, not as a top-level tool.
  private func makePhotoToolBar(textColor: UIColor, toolbarColor: UIColor) -> UIView {
    let tools: [(String, String, String)] = [
      ("crop", "Crop", "crop"),
      ("rotate", "Rotate", "rotate.right"),
      ("adjust", "Adjust", "slider.horizontal.3"),
      ("filters", "Filters", "camera.filters"),
      ("text", "Text", "textformat"),
      ("stickers", "Stickers", "face.smiling"),
      ("draw", "Draw", "pencil.tip"),
      ("overlay", "Overlay", "plus.rectangle.on.rectangle"),
      ("resize", "Resize", "aspectratio"),
    ]
    let scroll = UIScrollView()
    scroll.backgroundColor = toolbarColor
    scroll.showsHorizontalScrollIndicator = false
    scroll.alwaysBounceHorizontal = true
    scroll.delaysContentTouches = true
    scroll.canCancelContentTouches = true
    photoToolScroll = scroll
    let stack = UIStackView()
    stack.axis = .horizontal
    stack.spacing = 2
    let itemWidth: CGFloat = 58
    tools.filter { key, _, _ in
      if key == "overlay" {
        return (features["overlay"] as? Bool ?? features["overlays"] as? Bool) != false
      }
      return features[key] as? Bool != false
    }.forEach { key, label, symbol in
      let node = createToolNode(systemName: symbol, labelText: label) { [weak self] in self?.onPhotoToolTapped(key) }
      node.button.widthAnchor.constraint(equalToConstant: itemWidth).isActive = true
      photoToolNodes[key] = node
      stack.addArrangedSubview(node.button)
    }
    // "Original" compare button uses native touch-down / touch-up events without gesture recognizers.
    let compare = createToolNode(systemName: "rectangle.split.2x1", labelText: "Original")
    compare.button.accessibilityLabel = "Hold to compare with original"
    compare.button.widthAnchor.constraint(equalToConstant: itemWidth).isActive = true
    compare.button.addTarget(self, action: #selector(compareTouchDown), for: .touchDown)
    compare.button.addTarget(self, action: #selector(compareTouchUp), for: [.touchUpInside, .touchUpOutside, .touchCancel])
    photoToolNodes["compare"] = compare
    stack.addArrangedSubview(compare.button)

    scroll.addSubview(stack)
    stack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 12),
      scroll.contentLayoutGuide.trailingAnchor.constraint(equalTo: stack.trailingAnchor, constant: 12),
      stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      scroll.contentLayoutGuide.bottomAnchor.constraint(equalTo: stack.bottomAnchor),
      stack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    return scroll
  }

  private func makeCropSubBar(session: PhotoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    let panel = CropPanel(accent:primaryColor,surface:toolbarColor,foreground:textColor,
      originalRatio: { session.baseImage.map { $0.size.width/$0.size.height } ?? 1 },
      onReset: { [weak self] in self?.resetPhotoEdits() },
      onDone: { [weak self] in self?.setCropMode(false) },
      onCancel: { [weak self] in self?.cancelCropMode() },
      onStraighten: { [weak self] value in
        session.update { $0.setStraighten(value) }; self?.photoCropPanel?.sync(session.state); self?.schedulePhotoPreviewRender()
      },
      onRotate: { [weak self] in
        session.update { $0.rotateRight(); $0.zoom = 1; $0.panX = 0; $0.panY = 0; $0.aspectRatio = nil; $0.aspectPreset = "Free" }
        self?.photoCropPanel?.sync(session.state); self?.schedulePhotoPreviewRender()
      },
      onRatio: { [weak self] label,ratio in
        session.update { $0.aspectRatio = ratio; $0.aspectPreset = label }
        self?.cropOverlay?.setAspectRatio(ratio); self?.photoCropPanel?.sync(session.state)
      })
    photoCropPanel = panel; panel.sync(session.state)
    return panel
  }

  private func onPhotoToolTapped(_ key: String) {
    guard let session = photoSession, photoImageView != nil else { return }
    switch key {
    case "crop":
      setCropMode(true)
    case "rotate":
      session.update { $0.rotateRight(); $0.zoom = 1; $0.panX = 0; $0.panY = 0 }
      photoCropPanel?.sync(session.state)
      schedulePhotoPreviewRender()
    case "adjust":
      setAdjustMode(true, session: session)
    case "filters":
      setFiltersMode(true, session: session)
    case "text":
      showTextInputDialog(session: session)
    case "stickers":
      stickersMode = true
      refreshPhotoToolSelection()
      presentOnlineStickerSheet(purpose: .photoSticker)
    case "draw":
      setDrawMode(true, session: session)
    case "overlay", "overlays":
      presentImagePicker(purpose: .photoOverlay)
    case "resize":
      setCropMode(true)
    default:
      showToast("\(key) is coming in a later milestone.")
    }
  }

  // MARK: - Layers: text, selection, and transform/duplicate/reorder/lock/hide/delete
  // with undo/redo (Milestone 4)

  private func showTextInputDialog(session: PhotoEditSession, editingLayer: PhotoLayer? = nil) {
    let sheet = TextEditorSheet(text: editingLayer?.text ?? "", editing: editingLayer != nil, accent: color("primaryColor") ?? DesignTokens.primaryContainer) { [weak self] text in
      var layer = editingLayer ?? PhotoLayer(type: .text)
      layer.text = text
      session.layerStack.commit { list in editingLayer == nil ? list + [layer] : list.map { $0.id == layer.id ? layer : $0 } }
      self?.selectLayer(layer.id)
      self?.onLayerStackChanged()
    }
    present(sheet, animated: true)
  }

  private func newSticker() -> PhotoLayer {
    var layer = PhotoLayer(type: .sticker)
    let bounds: CGRect
    if let imageView = photoImageView { bounds = imageView.currentImageBounds() }
    else if let session = videoSession, let overlay = videoOverlayImageView { bounds = computeVideoLetterboxBounds(session: session, in: overlay) }
    else { bounds = CGRect(x: 0, y: 0, width: 1, height: 1) }
    layer.scale = 0.3 * bounds.width / max(0.18 * min(bounds.width, bounds.height), 0.001)
    return layer
  }

  private func setStickersMode(_ enabled: Bool) {
    stickersMode = enabled
    stickersSubBar?.isHidden = !enabled
    mainToolBar?.isHidden = enabled || selectedLayerID != nil
    refreshPhotoToolSelection()
  }

  private func makeStickersSubBar(session: PhotoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    scroll.showsHorizontalScrollIndicator = false
    scroll.alwaysBounceHorizontal = true
    scroll.delaysContentTouches = true
    scroll.canCancelContentTouches = true
    let chipStack = UIStackView(); chipStack.axis = .horizontal; chipStack.spacing = 4
    PhotoLayerRenderer.builtinStickerIDs().forEach { id in
      chipStack.addArrangedSubview(button(PhotoLayerRenderer.glyph(for: id)) { [weak self] in
        guard let self else { return }
        var layer = self.newSticker()
        layer.stickerId = id
        session.layerStack.commit { $0 + [layer] }
        self.selectLayer(layer.id)
        self.setStickersMode(false)
      })
    }
    consumerStickerAssets().forEach { asset in
      chipStack.addArrangedSubview(button(asset.id, color: textColor) { [weak self] in
        guard let self else { return }
        var layer = self.newSticker()
        layer.stickerUri = asset.uri
        if let image = self.resolveVideoImageLayer(asset.uri) { layer.overlayAspectRatio = image.size.width / max(image.size.height, 1) }
        session.layerStack.commit { $0 + [layer] }
        self.selectLayer(layer.id)
        self.setStickersMode(false)
      })
    }
    chipStack.addArrangedSubview(button("Upload", color: textColor) { [weak self] in
      self?.presentImagePicker(purpose: .photoSticker)
    })
    scroll.addSubview(chipStack)
    chipStack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      chipStack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 8),
      scroll.contentLayoutGuide.trailingAnchor.constraint(equalTo: chipStack.trailingAnchor, constant: 8),
      chipStack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      scroll.contentLayoutGuide.bottomAnchor.constraint(equalTo: chipStack.bottomAnchor),
      chipStack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    bar.addArrangedSubview(scroll)
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in self?.setStickersMode(false) })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    return bar
  }

  private func setDrawMode(_ enabled: Bool, session: PhotoEditSession) {
    drawMode = enabled
    drawOverlay?.isHidden = !enabled
    drawSubBar?.isHidden = !enabled
    mainToolBar?.isHidden = enabled
    layerOverlay?.isHidden = enabled
    photoImageView?.panZoomEnabled = !enabled
    if enabled {
      drawOverlay?.strokeColor = .systemRed
      drawOverlay?.strokeWidthPx = 4
      updateDrawSwatches(selected: .systemRed)
      if let bounds = photoImageView?.currentImageBounds() { drawOverlay?.setImageBounds(bounds) }
    } else {
      commitDrawStrokes(session: session)
      drawOverlay?.clearStrokes()
    }
    refreshPhotoToolSelection()
  }

  private func commitDrawStrokes(session: PhotoEditSession) {
    guard let drawOverlay, drawOverlay.hasStrokes, let bounds = photoImageView?.currentImageBounds(), bounds.width > 0 else { return }
    let shortSide = min(bounds.width, bounds.height)
    let newLayers: [PhotoLayer] = drawOverlay.normalizedStrokes().compactMap { points, stroke in
      guard points.count >= 2 else { return nil }
      let centerX = points.reduce(0) { $0 + $1.0 } / CGFloat(points.count)
      let centerY = points.reduce(0) { $0 + $1.1 } / CGFloat(points.count)
      var layer = PhotoLayer(type: .drawing)
      layer.x = centerX; layer.y = centerY
      layer.drawColor = stroke.color
      layer.drawStrokeWidth = stroke.widthPx / shortSide
      layer.drawPoints = points.map { ($0.0 - centerX, $0.1 - centerY) }
      return layer
    }
    if !newLayers.isEmpty { session.layerStack.commit { $0 + newLayers } }
    onLayerStackChanged()
  }

  private func makeDrawSubBar(textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    drawColorSwatches.removeAll()
    let colors: [(String, UIColor)] = [
      ("Red", .systemRed),
      ("Yellow", .systemYellow),
      ("Green", .systemGreen),
      ("Blue", .systemBlue),
      ("Purple", DesignTokens.primary),
      ("Pink", .systemPink),
      ("White", .white),
      ("Black", .black),
    ]
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    scroll.showsHorizontalScrollIndicator = false
    scroll.alwaysBounceHorizontal = true
    scroll.delaysContentTouches = true
    scroll.canCancelContentTouches = true
    let controls = UIStackView(); controls.axis = .horizontal; controls.spacing = 6
    controls.alignment = .center

    colors.forEach { name, color in
      let swatch = createDrawColorSwatch(color: color) { [weak self] in
        self?.drawOverlay?.strokeColor = color
        self?.updateDrawSwatches(selected: color)
      }
      swatch.accessibilityLabel = name
      drawColorSwatches.append((color, swatch))
      controls.addArrangedSubview(swatch)
    }

    let divider = UIView()
    divider.backgroundColor = DesignTokens.surfaceContainerHigh
    divider.widthAnchor.constraint(equalToConstant: 1).isActive = true
    divider.heightAnchor.constraint(equalToConstant: 24).isActive = true
    controls.addArrangedSubview(divider)

    controls.addArrangedSubview(button("Thin", color: textColor) { [weak self] in self?.drawOverlay?.strokeWidthPx = 2 })
    controls.addArrangedSubview(button("Thick", color: textColor) { [weak self] in self?.drawOverlay?.strokeWidthPx = 10 })
    controls.addArrangedSubview(button("Undo", color: textColor) { [weak self] in self?.drawOverlay?.undoLastStroke() })
    controls.addArrangedSubview(button("Clear", color: textColor) { [weak self] in self?.drawOverlay?.clearStrokes() })

    scroll.addSubview(controls); controls.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      controls.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 8),
      scroll.contentLayoutGuide.trailingAnchor.constraint(equalTo: controls.trailingAnchor, constant: 8),
      controls.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      scroll.contentLayoutGuide.bottomAnchor.constraint(equalTo: controls.bottomAnchor),
      controls.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    bar.addArrangedSubview(scroll)
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in
      guard let self, let session = self.photoSession else { return }
      self.setDrawMode(false, session: session)
    })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    updateDrawSwatches(selected: .systemRed)
    return bar
  }

  private func createDrawColorSwatch(color: UIColor, onTap: @escaping () -> Void) -> UIButton {
    let outer = UIButton(type: .custom)
    outer.backgroundColor = DesignTokens.surfaceContainerHigh
    outer.layer.cornerRadius = 16
    outer.widthAnchor.constraint(equalToConstant: 32).isActive = true
    outer.heightAnchor.constraint(equalToConstant: 32).isActive = true
    let inner = UIView()
    inner.backgroundColor = color
    inner.layer.cornerRadius = 10
    inner.isUserInteractionEnabled = false
    inner.translatesAutoresizingMaskIntoConstraints = false
    outer.addSubview(inner)
    NSLayoutConstraint.activate([
      inner.centerXAnchor.constraint(equalTo: outer.centerXAnchor),
      inner.centerYAnchor.constraint(equalTo: outer.centerYAnchor),
      inner.widthAnchor.constraint(equalToConstant: 20),
      inner.heightAnchor.constraint(equalToConstant: 20),
    ])
    outer.addAction(UIAction { _ in onTap() }, for: .touchUpInside)
    outer.applyPressScale()
    return outer
  }

  private func updateDrawSwatches(selected: UIColor) {
    let primary = color("primaryColor") ?? DesignTokens.primaryContainer
    drawColorSwatches.forEach { color, swatch in
      let isSel = color == selected
      swatch.layer.borderWidth = isSel ? 2 : 0
      swatch.layer.borderColor = primary.cgColor
      swatch.accessibilityTraits = isSel ? [.button, .selected] : .button
    }
  }

  private func consumerStickerAssets() -> [(id: String, uri: String)] {
    stickerAssets.compactMap { asset in
      guard let uri = asset["uri"] as? String, !uri.isEmpty else { return nil }
      return (asset["id"] as? String ?? uri, uri)
    }
  }

  // MARK: - Native image picker (Upload chip / Overlay tool)

  private func presentImagePicker(purpose: ImagePickPurpose) {
    pendingPickPurpose = purpose
    var configuration = PHPickerConfiguration()
    configuration.filter = .images
    configuration.selectionLimit = 1
    let picker = PHPickerViewController(configuration: configuration)
    picker.delegate = self
    present(picker, animated: true)
  }

  /// Presents the bundled sticker sheet, reusing `handlePickedImage` as the insertion point.
  private func presentOnlineStickerSheet(purpose: ImagePickPurpose) {
    let runtimeStickers = consumerStickerAssets().map { OnlineStickerSheetViewController.RuntimeSticker(id: $0.id, uri: $0.uri) }
    let sheet = OnlineStickerSheetViewController(runtimeStickers: runtimeStickers)
    sheet.isModalInPresentation = false
    sheet.onDismiss = { [weak self] in
      if case .photoSticker = purpose {
        self?.stickersMode = false
        self?.refreshPhotoToolSelection()
      }
    }
    sheet.onStickerPicked = { [weak self] fileURL, image in
      self?.handlePickedImage(fileURL, image: image, purpose: purpose)
    }
    sheet.sheetPresentationController?.detents = [.medium(), .large()]
    sheet.sheetPresentationController?.selectedDetentIdentifier = .large
    sheet.sheetPresentationController?.prefersGrabberVisible = true
    sheet.sheetPresentationController?.preferredCornerRadius = DesignTokens.radiusSheet
    present(sheet, animated: true)
  }

  private func writeTempImage(_ image: UIImage) -> URL? {
    guard let data = image.pngData() ?? image.jpegData(compressionQuality: 0.92) else { return nil }
    let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".png")
    do {
      try data.write(to: url)
      return url
    } catch {
      return nil
    }
  }

  /// Inserts a picked image as either a sticker layer or a generic movable/resizable overlay layer,
  /// on the matching session (photo or video), based on which flow requested the picker.
  private func handlePickedImage(_ fileURL: URL, image: UIImage, purpose: ImagePickPurpose) {
    let uri = fileURL.absoluteString
    let aspectRatio = max(image.size.width, 1) / max(image.size.height, 1)
    switch purpose {
    case .photoSticker:
      guard let session = photoSession else { return }
      var layer = newSticker()
      layer.stickerUri = uri
      layer.overlayAspectRatio = aspectRatio
      session.layerStack.commit { $0 + [layer] }
      selectLayer(layer.id)
      setStickersMode(false)
    case .photoOverlay:
      guard let session = photoSession else { return }
      var layer = PhotoLayer(type: .overlay)
      layer.overlayUri = uri
      layer.overlayAspectRatio = aspectRatio
      layer.scale = 0.7
      session.layerStack.commit { $0 + [layer] }
      selectLayer(layer.id)
    case .videoSticker:
      guard let session = videoSession else { return }
      var layer = newSticker()
      layer.stickerUri = uri
      layer.overlayAspectRatio = aspectRatio
      session.layerStack.commit { $0 + [layer] }
      onVideoLayerStackChanged()
      selectVideoLayer(layer.id, session: session)
    case .videoOverlay:
      guard let session = videoSession else { return }
      var layer = PhotoLayer(type: .overlay)
      layer.overlayUri = uri
      layer.overlayAspectRatio = aspectRatio
      layer.scale = 0.7
      session.layerStack.commit { $0 + [layer] }
      onVideoLayerStackChanged()
      selectVideoLayer(layer.id, session: session)
    }
  }

  private func refreshPhotoToolSelection() {
    let textColor = color("textColor") ?? DesignTokens.onSurface
    photoToolNodes.forEach { key, node in
      let selected: Bool
      switch key {
      case "crop": selected = cropMode
      case "adjust": selected = adjustMode
      case "filters": selected = filtersMode
      case "stickers": selected = stickersMode
      case "draw": selected = drawMode
      default: selected = false
      }
      setToolNodeSelected(node, selected: selected, textColor: textColor)
      if selected { scrollToolNodeIntoView(node) }
    }
  }

  /// Keeps the active tool-rail node fully visible, never partially clipped at the scroll edge.
  private func scrollToolNodeIntoView(_ node: ToolNode) {
    guard let scroll = photoToolScroll, !cropMode, !scroll.isHidden else { return }
    DispatchQueue.main.async {
      let frame = node.root.convert(node.root.bounds, to: scroll)
      scroll.scrollRectToVisible(frame.insetBy(dx: -DesignTokens.spaceSm, dy: 0), animated: true)
    }
  }

  private func selectLayer(_ id: String?) {
    if selectedLayerID != id {
      let isSticker = photoSession?.layerStack.layers.first(where: { $0.id == id })?.type == .sticker
      activeLayerPropertyKey = isSticker ? "opacity" : ""
    }
    selectedLayerID = id
    layerOverlay?.selectedLayerID = id
    if let session = photoSession {
      layerOverlay?.layers = session.layerStack.layers
      schedulePhotoPreviewRender()
    }
    if id != nil {
      if drawMode, let session = photoSession { setDrawMode(false, session: session) }
      if cropMode { cancelCropMode() }
      filtersMode = false; stickersMode = false; adjustMode = false
      filtersSubBar?.isHidden = true; stickersSubBar?.isHidden = true; adjustSubBar?.isHidden = true
    }
    setLayerToolBarVisible(id != nil)
  }

  private func setLayerToolBarVisible(_ visible: Bool) {
    layerToolBar?.isHidden = !visible
    mainToolBar?.isHidden = visible
    let isText = currentSelectedLayer()?.type == .text
    let isSticker = currentSelectedLayer()?.type == .sticker
    let simple = isText || isSticker
    ["scale", "lock", "visibility", "front", "back"].forEach { layerPropertyButtons[$0]?.isHidden = simple }
    ["edit", "color", "fontSize"].forEach { layerPropertyButtons[$0]?.isHidden = !isText }
    if isSticker && activeLayerPropertyKey.isEmpty { activeLayerPropertyKey = "opacity" }
    if !isText && ["color", "fontSize"].contains(activeLayerPropertyKey) { activeLayerPropertyKey = "" }
    layerPropertySlider?.isHidden = !visible || !["scale", "rotation", "opacity", "fontSize"].contains(activeLayerPropertyKey)
    layerColorSwatchRow?.isHidden = !visible || activeLayerPropertyKey != "color"
    if visible { syncLayerPropertySlider() }
    refreshContextSelection(layerPropertyButtons, active: activeLayerPropertyKey)
    updateSwatches(textSwatches, selected: currentSelectedLayer()?.textColor)
  }

  private func currentSelectedLayer() -> PhotoLayer? {
    guard let session = photoSession else { return nil }
    return session.layerStack.layers.first { $0.id == selectedLayerID }
  }

  private func configureLayerSlider(_ slider: UISlider, video: Bool) {
    slider.minimumTrackTintColor = color("primaryColor") ?? DesignTokens.primaryContainer
    slider.addAction(UIAction { [weak self] _ in
      guard let self else { return }
      self.sliderSnapshot = video ? self.videoSession?.layerStack.layers : self.photoSession?.layerStack.layers
    }, for: .touchDown)
    slider.addAction(UIAction { [weak self] _ in
      guard let self, let snapshot = self.sliderSnapshot else { return }
      if video { self.videoSession?.layerStack.commitSnapshot(snapshot); self.onVideoLayerStackChanged() }
      else { self.photoSession?.layerStack.commitSnapshot(snapshot); self.onLayerStackChanged() }
      self.sliderSnapshot = nil
    }, for: [.touchUpInside, .touchUpOutside, .touchCancel])
  }

  private func syncLayerPropertySlider() {
    guard let slider = layerPropertySlider, let layer = currentSelectedLayer() else { return }
    let (min, max, value): (CGFloat, CGFloat, CGFloat)
    switch activeLayerPropertyKey {
    case "rotation": (min, max, value) = (-180, 180, (layer.rotationDegrees.truncatingRemainder(dividingBy: 360) + 540).truncatingRemainder(dividingBy: 360) - 180)
    case "opacity": (min, max, value) = (0, 100, layer.opacity * 100)
    case "fontSize": (min, max, value) = (12, 160, layer.fontSize)
    default: (min, max, value) = (20, 800, layer.scale * 100)
    }
    (slider as? EditorToolSlider)?.propertyKey = slider === layerPropertySlider ? activeLayerPropertyKey : activeVideoLayerPropertyKey
    slider.minimumValue = Float(min)
    slider.maximumValue = Float(max)
    slider.value = Float(value)
  }

  @objc private func layerPropertyChanged(_ slider: UISlider) {
    guard let session = photoSession, let id = selectedLayerID else { return }
    slider.setNeedsDisplay()
    let value = CGFloat(slider.value)
    let key = activeLayerPropertyKey
    session.layerStack.updateLive { list in
      list.map { layer in
        guard layer.id == id else { return layer }
        var updated = layer
        switch key {
        case "rotation": updated.rotationDegrees = value
        case "opacity": updated.opacity = value / 100
        case "fontSize": updated.fontSize = value
        default: updated.scale = value / 100
        }
        return updated
      }
    }
    layerOverlay?.layers = session.layerStack.layers
    schedulePhotoPreviewRender()
  }

  private func makeLayerToolBar(session: PhotoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    makeContextToolbar(video: false, toolbarColor: toolbarColor) { [weak self] key in
      guard let self, let layer = self.currentSelectedLayer() else { return }
      switch key {
      case "edit": self.showTextInputDialog(session: session, editingLayer: layer)
      case "done": self.selectLayer(nil)
      case "delete":
        session.layerStack.commit { $0.filter { $0.id != layer.id } }
        self.selectLayer(nil); self.onLayerStackChanged()
      case "duplicate":
        let copy = layer.duplicated()
        session.layerStack.commit { $0 + [copy] }; self.selectLayer(copy.id); self.onLayerStackChanged()
      case "lock", "visibility", "front", "back":
        session.layerStack.commit { list in
          if key == "front" { return list.filter { $0.id != layer.id } + [layer] }
          if key == "back" { return [layer] + list.filter { $0.id != layer.id } }
          return list.map { item in
            guard item.id == layer.id else { return item }
            var updated = item
            if key == "lock" { updated.locked.toggle() } else { updated.visible.toggle() }
            return updated
          }
        }
        self.onLayerStackChanged()
      default:
        self.activeLayerPropertyKey = self.activeLayerPropertyKey == key ? "" : key
        self.setLayerToolBarVisible(true)
      }
    }
  }

  private func toolButton(_ label: String, symbol: String, action: @escaping () -> Void) -> UIButton {
    let control = UIButton(type: .system)
    var config = UIButton.Configuration.plain()
    config.title = label
    config.image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .regular))
    config.imagePlacement = .top
    config.imagePadding = 4
    config.baseForegroundColor = DesignTokens.outline
    config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
      var value = incoming; value.font = .systemFont(ofSize: 10, weight: .medium); return value
    }
    config.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 2, bottom: 6, trailing: 2)
    control.configuration = config
    control.accessibilityLabel = label
    let width = control.widthAnchor.constraint(equalToConstant: 54); width.priority = .defaultHigh; width.isActive = true
    control.heightAnchor.constraint(equalToConstant: 68).isActive = true
    control.addAction(UIAction { _ in action() }, for: .touchUpInside)
    control.applyPressScale()
    return control
  }

  private func refreshContextSelection(_ buttons: [String: UIButton], active: String) {
    buttons.forEach { key, control in
      let selected = key == active
      control.configuration?.baseForegroundColor = key == "delete" ? .systemRed : (selected ? (color("primaryColor") ?? DesignTokens.primaryContainer) : DesignTokens.outline)
      control.backgroundColor = selected ? DesignTokens.surfaceContainerHigh : .clear
      control.layer.cornerRadius = 12
      control.accessibilityTraits = selected ? [.button, .selected] : .button
    }
  }

  private func makeContextToolbar(video: Bool, toolbarColor: UIColor, action: @escaping (String) -> Void) -> UIView {
    var actions = [("edit", "Edit", "pencil"), ("color", "Color", "paintpalette"), ("fontSize", "Size", "textformat.size"), ("rotation", "Rotate", "arrow.clockwise"), ("opacity", "Opacity", "circle.lefthalf.filled"), ("scale", "Scale", "arrow.up.left.and.arrow.down.right"), ("duplicate", "Duplicate", "square.on.square"), ("delete", "Delete", "trash")]
    if !video { actions += [("lock", "Lock", "lock"), ("visibility", "Hide", "eye.slash"), ("front", "Front", "square.3.layers.3d.top.filled"), ("back", "Back", "square.3.layers.3d.bottom.filled")] }
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    scroll.showsHorizontalScrollIndicator = false
    scroll.alwaysBounceHorizontal = true
    scroll.delaysContentTouches = true
    scroll.canCancelContentTouches = true
    let stack = UIStackView(); stack.axis = .horizontal; stack.spacing = 2
    actions.forEach { key, label, symbol in
      let control = toolButton(label, symbol: symbol) { action(key) }
      if video { videoLayerPropertyButtons[key] = control } else { layerPropertyButtons[key] = control }
      stack.addArrangedSubview(control)
    }
    scroll.addSubview(stack); stack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 6),
      scroll.contentLayoutGuide.trailingAnchor.constraint(equalTo: stack.trailingAnchor, constant: 6),
      stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      scroll.contentLayoutGuide.bottomAnchor.constraint(equalTo: stack.bottomAnchor),
      stack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
      scroll.heightAnchor.constraint(equalToConstant: 68),
    ])
    bar.addArrangedSubview(scroll)
    let done = toolButton("Done", symbol: "checkmark") { action("done") }
    done.configuration?.baseForegroundColor = color("primaryColor") ?? DesignTokens.primaryContainer
    bar.addArrangedSubview(done); bar.backgroundColor = toolbarColor
    return bar
  }

  private func createColorSwatch(color: UIColor, onTap: @escaping () -> Void) -> UIView {
    let outer = UIButton(type: .custom)
    outer.backgroundColor = DesignTokens.surfaceContainerHigh
    outer.layer.cornerRadius = 22
    outer.widthAnchor.constraint(equalToConstant: 44).isActive = true
    outer.heightAnchor.constraint(equalToConstant: 44).isActive = true
    let inner = UIView()
    inner.backgroundColor = color
    inner.layer.cornerRadius = 12
    inner.isUserInteractionEnabled = false
    inner.translatesAutoresizingMaskIntoConstraints = false
    outer.addSubview(inner)
    NSLayoutConstraint.activate([
      inner.centerXAnchor.constraint(equalTo: outer.centerXAnchor),
      inner.centerYAnchor.constraint(equalTo: outer.centerYAnchor),
      inner.widthAnchor.constraint(equalToConstant: 24),
      inner.heightAnchor.constraint(equalToConstant: 24),
    ])
    outer.addAction(UIAction { _ in onTap() }, for: .touchUpInside)
    return outer
  }

  /// Text color swatch row, shown when a TEXT layer's "Colors" property is active.
  private func makeTextColorSwatchRow(session: PhotoEditSession) -> UIView {
    makeTextPalette(video: false) { [weak self] color in
      guard let self, let id = self.selectedLayerID else { return }
      session.layerStack.commit { list in list.map { item in
        var updated = item; if item.id == id { updated.textColor = color }; return updated
      } }
      self.onLayerStackChanged(); self.setLayerToolBarVisible(true)
    }
  }

  private func updateSwatches(_ swatches: [(UIColor, UIView)], selected: UIColor?) {
    swatches.forEach { color, swatch in
      swatch.layer.borderWidth = color == selected ? 2 : 0
      swatch.layer.borderColor = (self.color("primaryColor") ?? DesignTokens.primaryContainer).cgColor
      swatch.accessibilityTraits = color == selected ? [.button, .selected] : .button
    }
  }

  private func makeTextPalette(video: Bool, onColor: @escaping (UIColor) -> Void) -> UIView {
    let scroll = UIScrollView()
    scroll.showsHorizontalScrollIndicator = false
    scroll.alwaysBounceHorizontal = true
    scroll.delaysContentTouches = true
    scroll.canCancelContentTouches = true
    scroll.backgroundColor = color("toolbarColor") ?? DesignTokens.surfaceContainerLow
    let stack = UIStackView(); stack.axis = .horizontal; stack.spacing = 4
    let colors: [(String, UIColor)] = [("White", .white), ("Black", .black), ("Red", .systemRed), ("Orange", .systemOrange), ("Yellow", .systemYellow), ("Green", .systemGreen), ("Blue", .systemBlue), ("Purple", DesignTokens.primary), ("Pink", .systemPink)]
    colors.forEach { name, color in
      let swatch = createColorSwatch(color: color) { onColor(color) }
      swatch.accessibilityLabel = name
      if video { videoTextSwatches.append((color, swatch)) } else { textSwatches.append((color, swatch)) }
      stack.addArrangedSubview(swatch)
    }
    scroll.addSubview(stack); stack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 12),
      scroll.contentLayoutGuide.trailingAnchor.constraint(equalTo: stack.trailingAnchor, constant: 12),
      stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      scroll.contentLayoutGuide.bottomAnchor.constraint(equalTo: stack.bottomAnchor),
      stack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    return scroll
  }

  private func onLayerStackChanged() {
    guard let session = photoSession else { return }
    if let id = selectedLayerID, !session.layerStack.layers.contains(where: { $0.id == id }) {
      selectedLayerID = nil
    }
    layerOverlay?.layers = session.layerStack.layers
    layerOverlay?.selectedLayerID = selectedLayerID
    schedulePhotoPreviewRender()
    undoButton?.isEnabled = session.layerStack.canUndo
    redoButton?.isEnabled = session.layerStack.canRedo
    undoButton?.alpha = session.layerStack.canUndo ? 1 : 0.4
    redoButton?.alpha = session.layerStack.canRedo ? 1 : 0.4
    if selectedLayerID == nil, layerToolBar?.isHidden == false { setLayerToolBarVisible(false) }
  }

  private func setAdjustMode(_ enabled: Bool, session: PhotoEditSession) {
    adjustMode = enabled
    // The slider lives inside the panel now, so showing the panel shows it.
    adjustSubBar?.isHidden = !enabled
    mainToolBar?.isHidden = enabled
    layerOverlay?.isHidden = enabled
    // Entering Adjust selects a control to scrub but never resets the values themselves, so
    // Adjust -> Filters -> Adjust round-trips keep whatever the user already dialled in.
    if enabled {
      activeAdjustmentKey = "brightness"
      syncAdjustmentSlider(session: session)
      if let adjustSubBar { fadeInPanel(adjustSubBar) }
    }
    refreshPhotoToolSelection()
    refreshAdjustSelection(session: session)
  }

  private func setFiltersMode(_ enabled: Bool, session: PhotoEditSession) {
    filtersMode = enabled
    filtersSubBar?.isHidden = !enabled
    mainToolBar?.isHidden = enabled
    layerOverlay?.isHidden = enabled
    if enabled {
      syncAdjustmentSlider(session: session)
      if let filtersSubBar { fadeInPanel(filtersSubBar) }
    }
    refreshPhotoToolSelection()
    refreshFiltersSelection(session: session)
  }

  /// Short cross-fade when a panel opens, per the "subtle transition" spec (no slide/scale drama).
  private func fadeInPanel(_ panel: UIView) {
    panel.alpha = 0
    UIView.animate(withDuration: 0.18) { panel.alpha = 1 }
  }

  private func refreshAdjustSelection(session: PhotoEditSession) {
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    let defaults = PhotoAdjustments()
    adjustmentStripItems.forEach { key, item in
      if key == "mirror" {
        // A toggle, so "selected" means "on" rather than "being scrubbed".
        item.setSelected(session.adjustments.mirror, accentColor: primaryColor)
        item.setModified(false, accentColor: primaryColor)
      } else {
        item.setSelected(key == activeAdjustmentKey, accentColor: primaryColor)
        item.setModified(session.adjustments.value(key) != defaults.value(key), accentColor: primaryColor)
      }
    }
  }

  private func refreshFiltersSelection(session: PhotoEditSession) {
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    let activePreset = session.adjustments.filterPreset ?? ""
    filterThumbnails.forEach { id, thumbnail in
      thumbnail.setSelected(id == activePreset, accentColor: primaryColor)
    }
    // Intensity only means something once a preset is applied.
    filterIntensityControl?.alpha = activePreset.isEmpty ? 0 : 1
    filterIntensityControl?.isUserInteractionEnabled = !activePreset.isEmpty
  }

  /// Points whichever slider is on screen at the currently selected value.
  private func syncAdjustmentSlider(session: PhotoEditSession) {
    let (min, max) = PhotoAdjustments.range(for: activeAdjustmentKey)
    if let control = adjustmentSliderControl {
      control.slider.minimumValue = Float(min)
      control.slider.maximumValue = Float(max)
      control.slider.value = Float(session.adjustments.value(activeAdjustmentKey))
      control.titleText = photoAdjustmentDefinitions.first { $0.0 == activeAdjustmentKey }?.1
      control.valueText = formatAdjustmentValue(activeAdjustmentKey, session.adjustments.value(activeAdjustmentKey))
      // Bipolar adjustments get a centre tick at their neutral value; unipolar ones (blur) do not.
      control.neutralValue = min < 0 ? 0 : nil
    }
    if let intensity = filterIntensityControl {
      let (strengthMin, strengthMax) = PhotoAdjustments.range(for: "filterStrength")
      intensity.slider.minimumValue = Float(strengthMin)
      intensity.slider.maximumValue = Float(strengthMax)
      intensity.slider.value = Float(session.adjustments.filterStrength)
      intensity.valueText = "\(Int(session.adjustments.filterStrength.rounded()))"
    }
  }

  private func formatAdjustmentValue(_ key: String, _ value: Double) -> String {
    let rounded = Int(value.rounded())
    if key == "blurRadius" { return "\(rounded)" }
    return rounded > 0 ? "+\(rounded)" : "\(rounded)"
  }

  @objc private func adjustmentChanged(_ slider: UISlider) {
    guard let session = photoSession else { return }
    let key = activeAdjustmentKey
    session.updateAdjustments { $0.setValue(key, Double(slider.value)) }
    adjustmentSliderControl?.valueText = formatAdjustmentValue(key, Double(slider.value))
    // Coalesced by the preview renderer, so dragging does not queue a render per pixel.
    schedulePhotoPreviewRender()
  }

  @objc private func filterIntensityChanged(_ slider: UISlider) {
    guard let session = photoSession else { return }
    session.updateAdjustments { $0.filterStrength = Double(slider.value) }
    filterIntensityControl?.valueText = "\(Int(slider.value.rounded()))"
    schedulePhotoPreviewRender()
  }

  /// Adjustments the processing engine actually implements, in carousel order. Deliberately not a
  /// wishlist: every entry maps to a real `PhotoAdjustments` field, so there are no controls that
  /// move a slider without changing the photo. (Highlights/shadows/tint/sharpness were removed from
  /// the engine in an earlier pass and would need that processing restored before they could return.)
  private var photoAdjustmentDefinitions: [(String, String, String)] {
    [
      ("exposure", "Exposure", "plusminus.circle"),
      ("brightness", "Brightness", "sun.max"),
      ("contrast", "Contrast", "circle.lefthalf.filled"),
      ("saturation", "Saturation", "drop"),
      ("temperature", "Warmth", "thermometer"),
      ("blurRadius", "Blur", "camera.filters"),
    ]
  }

  /// Adjust panel: `Adjust / Reset / Done` header, one shared slider bound to whichever adjustment
  /// is selected, then a compact scrollable strip. Replaces the row of plain text buttons that
  /// previously mixed adjustments, presets and actions into a single undifferentiated bar.
  private func makeAdjustSubBar(session: PhotoEditSession, primaryColor: UIColor) -> UIView {
    let header = editorPanelHeader(
      title: "Adjust",
      accentColor: primaryColor,
      secondaryLabel: "Reset",
      onSecondary: { [weak self] in
        session.updateAdjustments {
          // Preserves the chosen filter preset — Reset here means "reset adjustments".
          let preset = $0.filterPreset
          let strength = $0.filterStrength
          $0 = PhotoAdjustments()
          $0.filterPreset = preset
          $0.filterStrength = strength
        }
        self?.syncAdjustmentSlider(session: session)
        self?.schedulePhotoPreviewRender()
        self?.refreshAdjustSelection(session: session)
      },
      onDone: { [weak self] in self?.setAdjustMode(false, session: session) }
    )

    let strip = UIStackView()
    strip.spacing = 2
    photoAdjustmentDefinitions.forEach { key, label, symbol in
      let item = editorStripItem(systemName: symbol, labelText: label) { [weak self] in
        self?.activeAdjustmentKey = key
        self?.syncAdjustmentSlider(session: session)
        self?.refreshAdjustSelection(session: session)
      }
      adjustmentStripItems[key] = item
      strip.addArrangedSubview(item.root)
    }
    // Mirror is a toggle, not a slider target, but it lives in the same strip so the panel has one
    // visual language rather than a stray button.
    let mirror = editorStripItem(systemName: "flip.horizontal", labelText: "Mirror") { [weak self] in
      session.updateAdjustments { $0.mirror.toggle() }
      self?.schedulePhotoPreviewRender()
      self?.refreshAdjustSelection(session: session)
    }
    adjustmentStripItems["mirror"] = mirror
    strip.addArrangedSubview(mirror.root)

    let panel = UIStackView(arrangedSubviews: [header, adjustmentSliderControl ?? UIView(), editorToolStrip(strip)])
    panel.axis = .vertical
    panel.backgroundColor = DesignTokens.editorSurface
    NSLayoutConstraint.activate([
      header.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.headerHeight),
      panel.arrangedSubviews[1].heightAnchor.constraint(equalToConstant: EditorPanelMetrics.sliderHeight),
    ])
    return panel
  }

  /// Filters panel: `Filters / Done` header, an intensity slider shown once a preset is applied,
  /// then the preset carousel. Same skeleton as Adjust so the two read as one system.
  private func makeFiltersSubBar(session: PhotoEditSession, primaryColor: UIColor) -> UIView {
    let header = editorPanelHeader(
      title: "Filters",
      accentColor: primaryColor,
      onDone: { [weak self] in self?.setFiltersMode(false, session: session) }
    )

    let strip = UIStackView()
    strip.spacing = DesignTokens.spaceSm
    let original = editorFilterThumbnail(labelText: "Original") { [weak self] in
      session.updateAdjustments { $0.filterPreset = nil; $0.filterStrength = 100 }
      self?.syncAdjustmentSlider(session: session)
      self?.schedulePhotoPreviewRender()
      self?.refreshFiltersSelection(session: session)
    }
    original.image.image = session.baseImage
    filterThumbnails[""] = original
    strip.addArrangedSubview(original.root)

    PhotoFilterPresets.presetIDs.forEach { id in
      let thumbnail = editorFilterThumbnail(labelText: PhotoFilterPresets.label(for: id)) { [weak self] in
        session.updateAdjustments { $0.filterPreset = $0.filterPreset == id ? nil : id }
        self?.syncAdjustmentSlider(session: session)
        self?.schedulePhotoPreviewRender()
        self?.refreshFiltersSelection(session: session)
      }
      // Thumbnails render off a cached downscaled copy of the photo, never the full-resolution
      // image — a preset per look at full size would stall the panel on open.
      filterThumbnailLoader.load(source: session.baseImage, presetID: id, size: EditorPanelMetrics.thumbnailWidth) { image in
        thumbnail.image.image = image
      }
      filterThumbnails[id] = thumbnail
      strip.addArrangedSubview(thumbnail.root)
    }

    let panel = UIStackView(arrangedSubviews: [header, filterIntensityControl ?? UIView(), editorToolStrip(strip)])
    panel.axis = .vertical
    panel.backgroundColor = DesignTokens.editorSurface
    NSLayoutConstraint.activate([
      header.heightAnchor.constraint(equalToConstant: EditorPanelMetrics.headerHeight),
      panel.arrangedSubviews[1].heightAnchor.constraint(equalToConstant: EditorPanelMetrics.sliderHeight),
    ])
    return panel
  }

  private func setCropMode(_ enabled: Bool) {
    guard let session = photoSession else { return }
    if enabled && !cropMode { cropEntryState = session.state }
    cropMode = enabled
    photoImageView?.panZoomEnabled = !enabled
    cropOverlay?.isHidden = !enabled; cropSubBar?.isHidden = !enabled
    mainToolBar?.isHidden = enabled; layerOverlay?.isHidden = enabled
    if !enabled { cropEntryState = nil }
    photoCropPanel?.sync(session.state); schedulePhotoPreviewRender(); refreshPhotoToolSelection()
  }

  private func cancelCropMode() {
    guard let session = photoSession else { return }
    if let saved = cropEntryState { session.update { $0 = saved } }
    setCropMode(false)
  }

  private func aspectRatioLabel(_ ratio: CGFloat?) -> String {
    guard let ratio else { return "Free" }
    let knownRatios: [(String, CGFloat)] = [("1:1", 1), ("4:5", 4 / 5), ("3:4", 3 / 4), ("9:16", 9 / 16), ("16:9", 16 / 9)]
    return knownRatios.first(where: { $0.1 == ratio })?.0 ?? "Free"
  }

  private func resetPhotoEdits() {
    guard let session = photoSession else { return }
    session.update { $0.reset() }
    photoCropPanel?.sync(session.state); schedulePhotoPreviewRender()
  }

  @objc private func straightenChanged(_ slider: UISlider) {
    guard let session = photoSession, photoImageView != nil else { return }
    session.update { $0.setStraighten(CGFloat(slider.value)) }
    schedulePhotoPreviewRender()
  }

  private func exportPhoto() {
    guard let session = photoSession else {
      completion?(.failure(code: "E_INTERNAL", message: "Nothing to export."))
      return
    }
    if drawMode { setDrawMode(false, session: session) }
    progressOverlay?.isHidden = false
    let state = session.state
    let adjustments = session.adjustments
    let layers = session.layerStack.layers
    var options = exportOptions
    if let selectedPhotoExportFormat { options["imageFormat"] = selectedPhotoExportFormat }
    let sourceUri = uri
    DispatchQueue.global(qos: .userInitiated).async { [weak self] in
      do {
        let result = try PhotoExporter.export(sourceUri: sourceUri, transform: state, adjustments: adjustments, layers: layers, exportOptions: options)
        DispatchQueue.main.async {
          self?.completion?(.success(uri: result.uri, mimeType: result.mimeType, width: result.width, height: result.height, fileSize: result.fileSize))
        }
      } catch let error as PhotoExportError {
        DispatchQueue.main.async { self?.completion?(.failure(code: error.code, message: error.message)) }
      } catch {
        DispatchQueue.main.async { self?.completion?(.failure(code: "E_EXPORT_FAILED", message: "Unable to export the photo.")) }
      }
    }
  }

  // MARK: - Video editor shell (Milestone 1 only — trim/crop/etc. are Milestone 5+)

  private func buildVideoEditor() {
    let backgroundColor = color("backgroundColor") ?? DesignTokens.surfaceContainerLowest
    let toolbarColor = color("toolbarColor") ?? DesignTokens.surfaceContainerLow
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    view.backgroundColor = backgroundColor
    setNeedsStatusBarAppearanceUpdate()

    let session = VideoEditSession(sourceUri: uri)
    videoSession = session

    let header = makeHeader(textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    let preview = UIView(); preview.backgroundColor = .black
    preview.clipsToBounds = true; videoPreviewContainer = preview

    let playerController = AVPlayerViewController()
    cropPlayerView = playerController.view
    playerController.videoGravity = .resizeAspect
    playerController.player = session.player
    playerController.showsPlaybackControls = false
    addChild(playerController)
    preview.addSubview(playerController.view)
    playerController.view.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      playerController.view.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      playerController.view.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      playerController.view.topAnchor.constraint(equalTo: preview.topAnchor),
      playerController.view.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])
    playerController.didMove(toParent: self)

    let overlayView = UIImageView()
    overlayView.contentMode = .scaleAspectFit
    videoOverlayImageView = overlayView
    preview.addSubview(overlayView)
    overlayView.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      overlayView.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      overlayView.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      overlayView.topAnchor.constraint(equalTo: preview.topAnchor),
      overlayView.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])

    let cropCanvas = CropOverlayView(); cropCanvas.isHidden = true
    videoCropOverlay = cropCanvas
    preview.addSubview(cropCanvas); cropCanvas.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      cropCanvas.leadingAnchor.constraint(equalTo:preview.leadingAnchor),cropCanvas.trailingAnchor.constraint(equalTo:preview.trailingAnchor),
      cropCanvas.topAnchor.constraint(equalTo:preview.topAnchor),cropCanvas.bottomAnchor.constraint(equalTo:preview.bottomAnchor)
    ])
    cropCanvas.onCropChanged = { l,t,r,b in session.update { $0.crop.setCrop(left:l,top:t,right:r,bottom:b) } }
    cropCanvas.onViewportChanged = { zoom,x,y in session.update { $0.crop.zoom = zoom; $0.crop.panX = x; $0.crop.panY = y } }
    cropCanvas.onMediaBoundsChanged = { [weak self] bounds in
      guard let self,let playerView = self.cropPlayerView else { return }
      let fitted = self.videoMediaBounds(session:session,in:preview,cropped:false)
      let scale = bounds.width/max(fitted.width,0.001)
      playerView.transform = CGAffineTransform(translationX:bounds.midX-fitted.midX,y:bounds.midY-fitted.midY).scaledBy(x:scale,y:scale)
    }
    let videoLayers = LayerOverlayView(frame: .zero)
    videoLayers.swappableLayerID = initialStickerLayerID
    videoLayers.stickerSwapEnabled = initialStickerIds.count > 1
    videoLayers.onStickerSwap = { [weak self] in self?.switchInitialSticker() }
    videoLayerOverlay = videoLayers
    preview.addSubview(videoLayers)
    videoLayers.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      videoLayers.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      videoLayers.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      videoLayers.topAnchor.constraint(equalTo: preview.topAnchor),
      videoLayers.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])
    videoLayers.onLayerDelete = { [weak self] id in
      session.layerStack.commit { $0.filter { $0.id != id } }
      self?.selectVideoLayer(nil, session: session); self?.onVideoLayerStackChanged()
    }
    videoLayers.onLayerDoubleTapped = { [weak self] id in
      if let layer = session.layerStack.layers.first(where: { $0.id == id && $0.type == .text }) { self?.showVideoTextInputDialog(session: session, editingLayer: layer) }
    }
    videoLayers.onLayerTapped = { [weak self] id in self?.selectVideoLayer(id, session: session) }
    videoLayers.onLayerTransformChanged = { [weak self] id, x, y, scale, rotation in
      guard let self else { return }
      if self.videoDragStartSnapshot == nil { self.videoDragStartSnapshot = session.layerStack.layers }
      session.layerStack.updateLive { list in
        list.map { current in
          guard current.id == id else { return current }
          var layer = current
          layer.x = x; layer.y = y; layer.scale = scale; layer.rotationDegrees = rotation
          return layer
        }
      }
      videoLayers.layers = session.layerStack.layers
      self.refreshVideoOverlayPreview(session: session)
    }
    videoLayers.onLayerTransformEnded = { [weak self] _ in
      guard let self else { return }
      if let snapshot = self.videoDragStartSnapshot { session.layerStack.commitSnapshot(snapshot) }
      self.videoDragStartSnapshot = nil
      self.onVideoLayerStackChanged()
    }

    let playPause = UIButton(type: .system)
    playPause.widthAnchor.constraint(equalToConstant: 44).isActive = true
    playPause.heightAnchor.constraint(equalToConstant: 44).isActive = true
    playPause.tintColor = .white
    playPause.backgroundColor = UIColor.black.withAlphaComponent(0.55)
    playPause.layer.cornerRadius = 20
    playPause.setImage(UIImage(systemName: "play.fill"), for: .normal)
    playPause.accessibilityLabel = "Play video"
    playPause.applyPressScale()
    playPause.addAction(UIAction { [weak self] _ in
      guard let self, let session = self.videoSession else { return }
      if session.player.timeControlStatus == .playing {
        session.player.pause()
      } else {
        let current = CMTimeGetSeconds(session.player.currentTime())
        let duration = Double(session.durationMs) / 1000
        if current.isFinite, duration > 0, current >= duration - 0.05 {
          session.player.seek(to: .zero) { finished in
            if finished { session.player.rate = session.state.speed }
          }
        } else {
          session.player.rate = session.state.speed
        }
      }
      self.setVideoControlsVisible(true)
    }, for: .touchUpInside)
    playPauseButton = playPause
    let time = UILabel()
    time.text = "0:00 / 0:00"
    time.textColor = .white
    time.font = .monospacedDigitSystemFont(ofSize: 11, weight: .medium)
    timeLabel = time
    let transport = UIStackView(arrangedSubviews: [playPause, time])
    transport.axis = .horizontal
    transport.alignment = .center
    transport.spacing = 8

    let position = UISlider()
    position.accessibilityLabel = "Playback position"
    position.minimumTrackTintColor = primaryColor
    position.maximumTrackTintColor = UIColor.white.withAlphaComponent(0.35)
    if let thumb = UIImage(systemName: "circle.fill")?.withConfiguration(UIImage.SymbolConfiguration(pointSize: 11)) {
      position.setThumbImage(thumb.withTintColor(.white, renderingMode: .alwaysOriginal), for: .normal)
    }
    position.heightAnchor.constraint(equalToConstant: 20).isActive = true
    position.minimumValue = 0
    position.maximumValue = 1000
    position.addTarget(self, action: #selector(positionSliderChanged(_:)), for: .valueChanged)
    position.addTarget(self, action: #selector(positionSliderTouchDown(_:)), for: .touchDown)
    position.addTarget(self, action: #selector(positionSliderTouchUp(_:)), for: [.touchUpInside, .touchUpOutside])
    positionSlider = position

    let controlsOverlay = UIStackView(arrangedSubviews: [transport, position])
    controlsOverlay.axis = .vertical
    controlsOverlay.spacing = 2
    controlsOverlay.backgroundColor = UIColor.black.withAlphaComponent(0.45)
    controlsOverlay.isLayoutMarginsRelativeArrangement = true
    controlsOverlay.layoutMargins = UIEdgeInsets(top: 6, left: 10, bottom: 6, right: 10)
    videoControlsOverlay = controlsOverlay
    preview.addSubview(controlsOverlay)
    controlsOverlay.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      controlsOverlay.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      controlsOverlay.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      controlsOverlay.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])
    preview.addGestureRecognizer(ClosureTapGestureRecognizer(action: { [weak self] in self?.toggleVideoControls() }))

    let trim = TrimRangeView()
    // Bound to the currently selected clip (Milestone 7): fractions are relative to that clip's
    // own `originalDurationMs`, not the composed multi-clip timeline.
    trim.onRangeChanged = { [weak self] start, end in
      guard let self, let session = self.videoSession, let clip = session.clips[safe: self.selectedClipIndex] else { return }
      let newStart = Int64(start * CGFloat(clip.originalDurationMs))
      let newEnd = Int64(end * CGFloat(clip.originalDurationMs))
      let index = self.selectedClipIndex
      session.updateClips { clips in
        clips.enumerated().map { i, c in i == index ? { var c = c; c.trimStartMs = newStart; c.trimEndMs = newEnd; return c }() : c }
      }
      self.updateTimeLabel()
      self.refreshClipStrip(session: session)
    }
    trimRangeView = trim

    let strip = UIStackView()
    strip.axis = .horizontal
    strip.spacing = 4
    strip.isLayoutMarginsRelativeArrangement = true
    strip.layoutMargins = UIEdgeInsets(top: 4, left: 8, bottom: 4, right: 8)
    clipStripBar = strip
    let stripScroll = UIScrollView()
    stripScroll.addSubview(strip)
    stripScroll.showsHorizontalScrollIndicator = false
    strip.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      strip.leadingAnchor.constraint(equalTo: stripScroll.contentLayoutGuide.leadingAnchor),
      strip.trailingAnchor.constraint(equalTo: stripScroll.contentLayoutGuide.trailingAnchor),
      strip.topAnchor.constraint(equalTo: stripScroll.contentLayoutGuide.topAnchor),
      strip.bottomAnchor.constraint(equalTo: stripScroll.contentLayoutGuide.bottomAnchor),
      strip.heightAnchor.constraint(equalTo: stripScroll.frameLayoutGuide.heightAnchor),
    ])
    clipStripScroll = stripScroll

    session.onClipsReady = { [weak self] in
      guard let self else { return }
      DispatchQueue.main.async {
        self.selectedClipIndex = 0
        self.updateTimeLabel()
        self.bindTrimViewToSelectedClip(session: session)
        self.refreshClipStrip(session: session)
      }
    }

    let cropPanel = CropPanel(accent:primaryColor,surface:toolbarColor,foreground:textColor,
      originalRatio: { [weak self] in let size = self?.videoSourceSize(session:session) ?? CGSize(width:1,height:1); return size.width/size.height },
      onReset: { [weak self] in session.update { $0.crop = PhotoTransformState() }; self?.refreshVideoCrop(session:session) },
      onDone: { [weak self] in self?.setVideoCropMode(false,session:session) },
      onCancel: { [weak self] in if let saved = self?.videoCropEntryState { session.update { $0.crop = saved } }; self?.setVideoCropMode(false,session:session) },
      onStraighten: { [weak self] value in session.update { $0.crop.setStraighten(value) }; self?.refreshVideoCrop(session:session) },
      onRotate: { [weak self] in
        session.update { $0.crop.rotateRight(); $0.crop.zoom = 1; $0.crop.panX = 0; $0.crop.panY = 0; $0.crop.aspectRatio = nil; $0.crop.aspectPreset = "Free" }
        self?.refreshVideoCrop(session:session)
      },
      onRatio: { [weak self] label,ratio in
        session.update { $0.crop.aspectRatio = ratio; $0.crop.aspectPreset = label }
        cropCanvas.setAspectRatio(ratio); self?.videoCropPanel?.sync(session.state.crop)
      })
    cropPanel.isHidden = true; videoCropPanel = cropPanel
    cropPanel.heightAnchor.constraint(equalToConstant:232).isActive = true
    let aspectBar = makeVideoAspectSubBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    aspectBar.isHidden = true
    videoAspectSubBar = aspectBar

    let layerSlider = EditorToolSlider()
    layerSlider.isHidden = true
    layerSlider.addTarget(self, action: #selector(videoLayerPropertyChanged(_:)), for: .valueChanged)
    configureLayerSlider(layerSlider, video: true)
    videoLayerPropertySlider = layerSlider

    let palette = makeTextPalette(video: true) { [weak self] color in
      guard let self, let id = self.selectedVideoLayerID else { return }
      session.layerStack.commit { list in list.map { item in
        var updated = item; if item.id == id { updated.textColor = color }; return updated
      } }
      self.onVideoLayerStackChanged(); self.setVideoLayerToolBarVisible(true, session: session)
    }
    palette.isHidden = true; videoColorRow = palette
    palette.heightAnchor.constraint(equalToConstant: 48).isActive = true
    let layerBar = makeVideoLayerToolBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    layerBar.isHidden = true
    videoLayerToolBar = layerBar

    let toolbar = makeVideoToolBar(session: session, playerController: playerController)
    videoToolBar = toolbar

    let previewWrapper = UIView()
    previewWrapper.addSubview(preview)
    preview.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      preview.leadingAnchor.constraint(equalTo: previewWrapper.leadingAnchor),
      preview.trailingAnchor.constraint(equalTo: previewWrapper.trailingAnchor),
      preview.topAnchor.constraint(equalTo: previewWrapper.topAnchor, constant: DesignTokens.spaceXs),
      preview.bottomAnchor.constraint(equalTo: previewWrapper.bottomAnchor, constant: -DesignTokens.spaceXs),
    ])

    // Transparent holder so the safe-area inset lands on empty space rather than stretching the pill.
    let toolbarWrapper = UIView()
    toolbarWrapper.addSubview(toolbar)
    toolbar.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      toolbar.leadingAnchor.constraint(equalTo: toolbarWrapper.leadingAnchor, constant: DesignTokens.spaceMd),
      toolbar.trailingAnchor.constraint(equalTo: toolbarWrapper.trailingAnchor, constant: -DesignTokens.spaceMd),
      toolbar.topAnchor.constraint(equalTo: toolbarWrapper.topAnchor),
      toolbar.bottomAnchor.constraint(equalTo: toolbarWrapper.bottomAnchor, constant: -DesignTokens.spaceSm),
    ])

    let trimEnabled = features["trim"] as? Bool != false
    trim.isHidden = !trimEnabled
    let root = UIStackView(arrangedSubviews: [header, previewWrapper, trim, cropPanel, aspectBar, layerSlider, palette, layerBar, toolbarWrapper])
    root.axis = .vertical
    root.spacing = DesignTokens.spaceSm
    root.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(root)
    NSLayoutConstraint.activate([
      root.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
      root.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
      root.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
      root.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
      header.heightAnchor.constraint(equalToConstant: 60),
      trim.heightAnchor.constraint(equalToConstant: 48),
      aspectBar.heightAnchor.constraint(equalToConstant: 56),
      layerBar.heightAnchor.constraint(equalToConstant: 68),
      toolbar.heightAnchor.constraint(equalToConstant: 52),
    ])

    root.arrangedSubviews.forEach { child in
      child.constraints.filter { $0.firstAttribute == .height && $0.secondItem == nil }.forEach { $0.priority = UILayoutPriority(999) }
    }
    let progress = UIView()
    progress.backgroundColor = UIColor.black.withAlphaComponent(0.6)
    progress.isHidden = true
    let column = UIStackView()
    column.axis = .vertical
    column.alignment = .center
    column.spacing = 12
    let spinner = UIActivityIndicatorView(style: .large)
    spinner.color = .white
    spinner.startAnimating()
    column.addArrangedSubview(spinner)
    column.addArrangedSubview(button("Cancel export", color: .white) { [weak self] in self?.cancelVideoExport() })
    progress.addSubview(column)
    column.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([column.centerXAnchor.constraint(equalTo: progress.centerXAnchor), column.centerYAnchor.constraint(equalTo: progress.centerYAnchor)])
    videoExportProgressOverlay = progress
    view.addSubview(progress)
    progress.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      progress.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      progress.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      progress.topAnchor.constraint(equalTo: view.topAnchor),
      progress.bottomAnchor.constraint(equalTo: view.bottomAnchor),
    ])

    startPositionPolling()
  }

  private func startPositionPolling() {
    var wasPlaying = false
    positionPollTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
      guard let self, let session = self.videoSession else { return }
      if !self.seekingPosition, session.durationMs > 0 {
        let currentMs = Int64(CMTimeGetSeconds(session.player.currentTime()) * 1000)
        self.positionSlider?.value = Float(min(max(Double(currentMs) / Double(session.durationMs), 0), 1)) * 1000
        self.updateTimeLabel()
      }
      let playing = session.player.timeControlStatus == .playing
      self.playPauseButton?.setImage(UIImage(systemName: playing ? "pause.fill" : "play.fill"), for: .normal)
      self.playPauseButton?.accessibilityLabel = playing ? "Pause video" : "Play video"
      if playing != wasPlaying {
        wasPlaying = playing
        if playing { self.scheduleAutoHideVideoControls() } else { self.setVideoControlsVisible(true) }
      }
      self.refreshVideoOverlayPreview(session: session)
    }
  }

  /// Shows/hides the in-preview playback controls with a subtle fade (spec: auto-hide while playing).
  private func setVideoControlsVisible(_ visible: Bool) {
    guard let overlay = videoControlsOverlay else { return }
    hideVideoControlsTimer?.invalidate()
    videoControlsVisible = visible
    UIView.animate(withDuration: 0.2) {
      overlay.alpha = visible ? 1 : 0
    }
  }

  private func scheduleAutoHideVideoControls() {
    hideVideoControlsTimer?.invalidate()
    guard let session = videoSession, session.player.timeControlStatus == .playing else { return }
    hideVideoControlsTimer = Timer.scheduledTimer(withTimeInterval: 2.5, repeats: false) { [weak self] _ in
      self?.setVideoControlsVisible(false)
    }
  }

  private func toggleVideoControls() {
    if videoControlsVisible {
      setVideoControlsVisible(false)
    } else {
      setVideoControlsVisible(true)
      scheduleAutoHideVideoControls()
    }
  }

  private func updateTimeLabel() {
    guard let session = videoSession else { return }
    let currentMs = Int64(CMTimeGetSeconds(session.player.currentTime()) * 1000)
    timeLabel?.text = "\(formatMs(currentMs)) / \(formatMs(session.durationMs))"
  }

  private func formatMs(_ ms: Int64) -> String {
    let totalSeconds = max(ms / 1000, 0)
    return String(format: "%d:%02d", totalSeconds / 60, totalSeconds % 60)
  }

  /// Renders whichever overlays are active at the current playhead onto a
  /// transparent image sized to the video's own natural size. Reuses
  /// `PhotoLayerRenderer` unchanged — overlays are just `PhotoLayer`s with a time range.
  private func refreshVideoOverlayPreview(session: VideoEditSession) {
    let positionMs = Int64(CMTimeGetSeconds(session.player.currentTime()) * 1000)
    let activeLayers = session.layerStack.layers.filter { $0.isActive(atMs: positionMs, durationMs: session.durationMs) }
    if let overlayView = videoOverlayImageView {
      videoLayerOverlay?.layers = activeLayers
      videoLayerOverlay?.setImageBounds(computeVideoLetterboxBounds(session: session, in: overlayView))
    }
    guard !activeLayers.isEmpty else {
      videoOverlayImageView?.image = nil
      videoOverlayRenderKey = nil
      return
    }
    guard let clip = session.clips.first else {
      videoOverlayImageView?.image = nil
      return
    }
    if videoNaturalSize == nil,
       let track = session.asset(for: clip).tracks(withMediaType: .video).first {
      let naturalSize = track.naturalSize.applying(track.preferredTransform)
      videoNaturalSize = CGSize(width: max(1, abs(naturalSize.width)), height: max(1, abs(naturalSize.height)))
    }
    guard let naturalSize = videoNaturalSize else { return }
    let previewScale = min(1, 1280 / max(naturalSize.width, naturalSize.height))
    let size = CGSize(width: max(2, floor(naturalSize.width * previewScale)), height: max(2, floor(naturalSize.height * previewScale)))
    let renderKey = "\(session.layerStack.revision):\(Int(size.width)):\(Int(size.height)):\(activeLayers.map(\.id).joined(separator: ","))"
    guard renderKey != videoOverlayRenderKey else { return }
    let renderer = PhotoEditSession.pixelRenderer(size: size)
    let transparent = renderer.image { _ in }
    videoOverlayImageView?.image = PhotoLayerRenderer.render(transparent, layers: activeLayers) { [weak self] uri in self?.resolveVideoImageLayer(uri) }
    videoOverlayRenderKey = renderKey
  }

  /// One-shot resolver for sticker-uri and overlay-uri layers used by the video overlay preview.
  private func resolveVideoImageLayer(_ uri: String) -> UIImage? {
    guard let path = SourceResolver.resolvePath(sourceUri: uri, tempPrefix: "pve_video_layer") else { return nil }
    return UIImage(contentsOfFile: path)
  }

  /// Where the video actually renders within `view` under aspect-fit letterboxing (mirrors `ZoomableImageView.currentImageBounds()`'s math).
  private func videoSourceSize(session: VideoEditSession) -> CGSize {
    if session.naturalSize != .zero { return session.naturalSize }
    guard let clip = session.clips.first, let track = session.asset(for: clip).tracks(withMediaType: .video).first else { return CGSize(width: 1, height: 1) }
    let size = track.naturalSize.applying(track.preferredTransform)
    return CGSize(width: max(1, abs(size.width)), height: max(1, abs(size.height)))
  }

  private func videoMediaBounds(session:VideoEditSession,in view:UIView,cropped:Bool) -> CGRect {
    let size = VideoCropGeometry.plan(size:videoSourceSize(session:session),state:session.state.crop,includeCrop:cropped).size
    return AVMakeRect(aspectRatio:size,insideRect:view.bounds)
  }

  private func computeVideoLetterboxBounds(session:VideoEditSession,in view:UIView) -> CGRect {
    videoMediaBounds(session:session,in:view,cropped:!videoCropMode)
  }

  private func refreshVideoCrop(session:VideoEditSession) {
    if let item = session.player.currentItem {
      item.videoComposition = VideoCropGeometry.previewComposition(asset:item.asset,state:session.state.crop,includeCrop:!videoCropMode)
      // Refresh the paused frame after changing a composition.
      session.player.seek(to:session.player.currentTime(),toleranceBefore:.zero,toleranceAfter:.zero)
    }
    cropPlayerView?.transform = .identity
    videoCropPanel?.sync(session.state.crop)
    view.layoutIfNeeded()
    if videoCropMode,let preview = videoPreviewContainer { videoCropOverlay?.restore(bounds:videoMediaBounds(session:session,in:preview,cropped:false),state:session.state.crop) }
    videoNaturalSize = VideoCropGeometry.plan(size:videoSourceSize(session:session),state:session.state.crop,includeCrop:true).size
    videoOverlayRenderKey = nil; refreshVideoOverlayPreview(session:session)
  }

  private func setVideoCropMode(_ enabled:Bool,session:VideoEditSession) {
    if enabled && !videoCropMode { videoCropEntryState = session.state.crop; session.player.pause(); selectVideoLayer(nil,session:session) }
    videoCropMode = enabled
    videoCropPanel?.isHidden = !enabled; videoCropOverlay?.isHidden = !enabled
    videoLayerOverlay?.isHidden = enabled; videoOverlayImageView?.isHidden = enabled; videoToolBar?.isHidden = enabled
    if !enabled { videoCropEntryState = nil }
    refreshVideoCrop(session:session)
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    if videoCropMode,let session = videoSession,let preview = videoPreviewContainer {
      videoCropOverlay?.restore(bounds:videoMediaBounds(session:session,in:preview,cropped:false),state:session.state.crop)
    }
  }

  private func onVideoLayerStackChanged() {
    guard let session = videoSession else { return }
    if let id = selectedVideoLayerID, !session.layerStack.layers.contains(where: { $0.id == id }) {
      selectedVideoLayerID = nil
    }
    videoLayerOverlay?.selectedLayerID = selectedVideoLayerID
    refreshVideoOverlayPreview(session: session)
    undoButton?.isEnabled = session.layerStack.canUndo
    redoButton?.isEnabled = session.layerStack.canRedo
    undoButton?.alpha = session.layerStack.canUndo ? 1 : 0.4
    redoButton?.alpha = session.layerStack.canRedo ? 1 : 0.4
    if selectedVideoLayerID == nil, videoLayerToolBar?.isHidden == false { setVideoLayerToolBarVisible(false, session: session) }
  }

  private func selectVideoLayer(_ id: String?, session: VideoEditSession) {
    if selectedVideoLayerID != id { activeVideoLayerPropertyKey = "" }
    selectedVideoLayerID = id
    videoLayerOverlay?.selectedLayerID = id
    setVideoLayerToolBarVisible(id != nil, session: session)
  }

  private func setVideoLayerToolBarVisible(_ visible: Bool, session: VideoEditSession) {
    if visible { setVideoControlsVisible(true) }
    videoLayerToolBar?.isHidden = !visible
    videoToolBar?.isHidden = visible
    let isText = currentSelectedVideoLayer(session: session)?.type == .text
    videoLayerPropertyButtons["scale"]?.isHidden = isText || currentSelectedVideoLayer(session: session)?.type == .sticker
    ["edit", "color", "fontSize"].forEach { videoLayerPropertyButtons[$0]?.isHidden = !isText }
    if !isText && ["color", "fontSize"].contains(activeVideoLayerPropertyKey) { activeVideoLayerPropertyKey = "" }
    videoLayerPropertySlider?.isHidden = !visible || !["scale", "rotation", "opacity", "fontSize"].contains(activeVideoLayerPropertyKey)
    videoColorRow?.isHidden = !visible || activeVideoLayerPropertyKey != "color"
    if visible { syncVideoLayerPropertySlider(session: session) }
    refreshContextSelection(videoLayerPropertyButtons, active: activeVideoLayerPropertyKey)
    updateSwatches(videoTextSwatches, selected: currentSelectedVideoLayer(session: session)?.textColor)
  }

  private func currentSelectedVideoLayer(session: VideoEditSession) -> PhotoLayer? {
    session.layerStack.layers.first { $0.id == selectedVideoLayerID }
  }

  private func syncVideoLayerPropertySlider(session: VideoEditSession) {
    guard let slider = videoLayerPropertySlider, let layer = currentSelectedVideoLayer(session: session) else { return }
    let (min, max, value): (CGFloat, CGFloat, CGFloat)
    switch activeVideoLayerPropertyKey {
    case "rotation": (min, max, value) = (-180, 180, (layer.rotationDegrees.truncatingRemainder(dividingBy: 360) + 540).truncatingRemainder(dividingBy: 360) - 180)
    case "opacity": (min, max, value) = (0, 100, layer.opacity * 100)
    case "fontSize": (min, max, value) = (12, 160, layer.fontSize)
    case "start": (min, max, value) = (0, CGFloat(session.durationMs), CGFloat(layer.startMs))
    case "end":
      let effectiveEnd = (layer.endMs > 0 && layer.endMs <= session.durationMs) ? layer.endMs : session.durationMs
      (min, max, value) = (0, CGFloat(session.durationMs), CGFloat(effectiveEnd))
    default: (min, max, value) = (20, 800, layer.scale * 100)
    }
    (slider as? EditorToolSlider)?.propertyKey = slider === layerPropertySlider ? activeLayerPropertyKey : activeVideoLayerPropertyKey
    slider.minimumValue = Float(min)
    slider.maximumValue = Float(max)
    slider.value = Float(value)
  }

  @objc private func videoLayerPropertyChanged(_ slider: UISlider) {
    guard let session = videoSession, let id = selectedVideoLayerID else { return }
    slider.setNeedsDisplay()
    let value = CGFloat(slider.value)
    let key = activeVideoLayerPropertyKey
    session.layerStack.updateLive { list in
      list.map { layer in
        guard layer.id == id else { return layer }
        var updated = layer
        switch key {
        case "rotation": updated.rotationDegrees = value
        case "opacity": updated.opacity = value / 100
        case "fontSize": updated.fontSize = value
        case "start": updated.startMs = min(Int64(value), (layer.endMs > 0 ? layer.endMs : session.durationMs))
        case "end": updated.endMs = max(Int64(value), layer.startMs)
        default: updated.scale = value / 100
        }
        return updated
      }
    }
    refreshVideoOverlayPreview(session: session)
  }

  private func makeVideoLayerToolBar(session: VideoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    makeContextToolbar(video: true, toolbarColor: toolbarColor) { [weak self] key in
      guard let self, let layer = self.currentSelectedVideoLayer(session: session) else { return }
      switch key {
      case "edit": self.showVideoTextInputDialog(session: session, editingLayer: layer)
      case "done": self.selectVideoLayer(nil, session: session)
      case "delete":
        session.layerStack.commit { $0.filter { $0.id != layer.id } }; self.selectVideoLayer(nil, session: session); self.onVideoLayerStackChanged()
      case "duplicate":
        let copy = layer.duplicated()
        session.layerStack.commit { $0 + [copy] }; self.selectVideoLayer(copy.id, session: session); self.onVideoLayerStackChanged()
      default:
        self.activeVideoLayerPropertyKey = self.activeVideoLayerPropertyKey == key ? "" : key
        self.setVideoLayerToolBarVisible(true, session: session)
      }
    }
  }

  private func showVideoTextInputDialog(session: VideoEditSession, editingLayer: PhotoLayer? = nil) {
    let sheet = TextEditorSheet(text: editingLayer?.text ?? "", editing: editingLayer != nil, accent: color("primaryColor") ?? DesignTokens.primaryContainer) { [weak self] text in
      var layer = editingLayer ?? PhotoLayer(type: .text); layer.text = text
      session.layerStack.commit { list in editingLayer == nil ? list + [layer] : list.map { $0.id == layer.id ? layer : $0 } }
      self?.onVideoLayerStackChanged(); self?.selectVideoLayer(layer.id, session: session)
    }
    present(sheet, animated: true)
  }

  @objc private func positionSliderTouchDown(_ slider: UISlider) { seekingPosition = true; setVideoControlsVisible(true) }

  @objc private func positionSliderTouchUp(_ slider: UISlider) {
    seekingPosition = false
    scheduleAutoHideVideoControls()
    guard let session = videoSession, session.durationMs > 0 else { return }
    let targetMs = Double(slider.value / 1000) * Double(session.durationMs)
    session.player.seek(to: CMTime(value: Int64(targetMs), timescale: 1000))
  }

  @objc private func positionSliderChanged(_ slider: UISlider) {
    guard let session = videoSession, session.durationMs > 0 else { return }
    let targetMs = Double(slider.value / 1000) * Double(session.durationMs)
    session.player.seek(to: CMTime(value: Int64(targetMs), timescale: 1000))
  }

  /// Compact video dock: two equal-width icon+label actions in one rounded pill. Deliberately not
  /// `toolButton` — that rail stacks a symbol above a label (72pt of chrome), far more than two
  /// actions warrant. `textformat` renders as "Aa", which reads as typography where a lone "T" does not.
  private func makeVideoToolBar(session: VideoEditSession, playerController: AVPlayerViewController) -> UIView {
    let tools: [(String, String, String)] = [
      ("trim", "Trim", "arrow.left.and.right"),
      ("crop", "Crop", "crop"),
      ("rotate", "Rotate", "rotate.right"),
      ("speed", "Speed", "gauge.with.dots.needle.50percent"),
      ("text", "Text", "textformat"),
      ("stickers", "Stickers", "face.smiling"),
      ("overlay", "Overlay", "plus.rectangle.on.rectangle"),
      ("cover", "Cover", "photo"),
    ]
    let scroll = UIScrollView()
    scroll.backgroundColor = DesignTokens.surfaceContainer
    scroll.layer.cornerRadius = 18
    scroll.clipsToBounds = true
    scroll.showsHorizontalScrollIndicator = false

    let stack = UIStackView()
    stack.axis = .horizontal
    stack.spacing = DesignTokens.spaceXs

    let itemWidth = Swift.min(Swift.max(UIScreen.main.bounds.width / 4.5, 76), 96)
    tools.filter { key, _, _ in
      if key == "overlay" {
        return (features["overlay"] as? Bool ?? features["overlays"] as? Bool) != false
      }
      return features[key] as? Bool != false
    }.forEach { key, label, symbol in
      let control = makeVideoDockAction(label: label, symbol: symbol) { [weak self, weak playerController] in
        self?.onVideoToolTapped(key, session: session, playerView: playerController?.view)
      }
      control.widthAnchor.constraint(equalToConstant: itemWidth).isActive = true
      stack.addArrangedSubview(control)
    }
    scroll.addSubview(stack)
    stack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 8),
      stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -8),
      stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      stack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    return scroll
  }

  /// One dock action: symbol + label on a single row, purple pill while pressed.
  private func makeVideoDockAction(label: String, symbol: String, action: @escaping () -> Void) -> UIButton {
    let control = UIButton(type: .system)
    var config = UIButton.Configuration.plain()
    config.title = label
    config.image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 16, weight: .medium))
    config.imagePlacement = .leading
    config.imagePadding = DesignTokens.spaceSm
    config.baseForegroundColor = DesignTokens.textPrimary
    config.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: DesignTokens.spaceSm, bottom: 0, trailing: DesignTokens.spaceSm)
    config.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { incoming in
      var value = incoming; value.font = .systemFont(ofSize: 13, weight: .semibold); return value
    }
    control.configuration = config
    control.accessibilityLabel = label
    let accent = color("primaryColor") ?? DesignTokens.primaryContainer
    control.configurationUpdateHandler = { button in
      button.configuration?.background.backgroundColor = button.isHighlighted ? accent : .clear
      button.configuration?.background.cornerRadius = 18
    }
    control.addAction(UIAction { _ in action() }, for: .touchUpInside)
    control.applyPressScale()
    return control
  }

  private func refreshVideoToolSelection(session: VideoEditSession) {
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    videoToolButtons.forEach { key, button in
      let selected: Bool
      switch key {
      case "crop": selected = videoAspectMode
      default: selected = false
      }
      setButtonSelected(button, selected: selected, textColor: textColor, primaryColor: primaryColor)
    }
  }

  private func onVideoToolTapped(_ key: String, session: VideoEditSession, playerView: UIView?) {
    switch key {
    case "trim":
      guard let trim = trimRangeView else { return }
      trim.isHidden.toggle()
    case "crop":
      setVideoCropMode(true, session: session)
    case "cover":
      let atMs = Int64(CMTimeGetSeconds(session.player.currentTime()) * 1000)
      session.update { $0.coverFrameMs = atMs }
      showToast("Cover frame set at \(formatMs(atMs))")
    case "rotate":
      session.update { $0.rotateRight() }
      applyVideoPreviewTransform(playerView, state: session.state)
    case "aspect", "resize":
      setVideoAspectMode(true, session: session)
    case "speed":
      session.update { $0.cycleSpeed() }
      if session.player.timeControlStatus == .playing { session.player.rate = session.state.speed }
      showToast(formatSpeedLabel(session.state.speed))
    case "text":
      showVideoTextInputDialog(session: session)
    case "stickers":
      showVideoStickerPicker(session: session)
    case "overlay", "overlays":
      presentImagePicker(purpose: .videoOverlay)
    default:
      showToast("\(key) is coming in a later milestone.")
    }
  }

  private func showVideoStickerPicker(session: VideoEditSession) {
    presentOnlineStickerSheet(purpose: .videoSticker)
  }

  private func formatSpeedLabel(_ speed: Float) -> String {
    let trimmed = speed == speed.rounded() ? String(Int(speed)) : String(speed)
    return "Speed \(trimmed)x"
  }

  private func applyVideoPreviewTransform(_ playerView: UIView?, state: VideoTransformState) {
    playerView?.transform = CGAffineTransform(rotationAngle: CGFloat(state.rotationDegrees) * .pi / 180)
  }

  private func setVideoAspectMode(_ enabled: Bool, session: VideoEditSession) {
    videoAspectMode = enabled
    videoAspectSubBar?.isHidden = !enabled
    videoToolBar?.isHidden = enabled
    refreshVideoToolSelection(session: session)
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    refreshSelection(videoAspectButtons, activeKey: videoAspectRatioLabel(session.state.aspectRatio), textColor: textColor, primaryColor: primaryColor)
  }

  private func videoAspectRatioLabel(_ ratio: CGFloat?) -> String {
    guard let ratio else { return "Original" }
    let knownRatios: [(String, CGFloat)] = [("1:1", 1), ("4:5", 4 / 5), ("9:16", 9 / 16), ("16:9", 16 / 9)]
    return knownRatios.first(where: { $0.1 == ratio })?.0 ?? "Original"
  }

  private func makeVideoAspectSubBar(session: VideoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    let presets: [(String, CGFloat?)] = [("Original", nil), ("1:1", 1), ("4:5", 4 / 5), ("9:16", 9 / 16), ("16:9", 16 / 9)]
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    let chipStack = UIStackView(); chipStack.axis = .horizontal; chipStack.spacing = 4
    presets.forEach { label, ratio in
      let chip = button(label, color: textColor) { [weak self] in
        session.update { $0.aspectRatio = ratio }
        self?.showToast("Export aspect: \(label) (Android only for now)")
        if let self { self.refreshSelection(self.videoAspectButtons, activeKey: label, textColor: textColor, primaryColor: primaryColor) }
      }
      videoAspectButtons[label] = chip
      chipStack.addArrangedSubview(chip)
    }
    scroll.addSubview(chipStack)
    chipStack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      chipStack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 8),
      chipStack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -8),
      chipStack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      chipStack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      chipStack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    bar.addArrangedSubview(scroll)
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in
      guard let self, let session = self.videoSession else { return }
      self.setVideoAspectMode(false, session: session)
    })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    return bar
  }

  private func exportVideo() {
    guard let session = videoSession else {
      completion?(.failure(code: "E_INTERNAL", message: "Nothing to export."))
      return
    }
    session.player.pause()
    videoExportProgressOverlay?.isHidden = false
    let exporter = VideoExporter()
    videoExporter = exporter
    exporter.export(
      clips: session.clips,
      assetProvider: { session.asset(for: $0) },
      state: session.state,
      layers: session.layerStack.layers,
      exportOptions: exportOptions,
      onProgress: { _ in /* Progress UI is a spinner for now; see docs/video-editor.md. */ },
      onComplete: { [weak self] result in
        DispatchQueue.main.async {
          self?.completion?(.success(uri: result.uri, mimeType: result.mimeType, width: result.width, height: result.height, fileSize: result.fileSize, durationMs: result.durationMs))
        }
      },
      onError: { [weak self] error in
        DispatchQueue.main.async { self?.completion?(.failure(code: error.code, message: error.message)) }
      }
    )
  }

  private func cancelVideoExport() {
    videoExporter?.cancel()
    videoExporter = nil
    videoExportProgressOverlay?.isHidden = true
  }

  // MARK: - Multi-clip timeline (Milestone 7)
  //
  // Clip strip UI plus split/duplicate/delete/reorder, all operating on
  // `session.clips` via `session.updateClips`. Mirrors the Android
  // implementation's controls, adapted to `UIStackView`/`UIButton`.

  /// Rebinds the trim range view to whichever clip is currently selected.
  private func bindTrimViewToSelectedClip(session: VideoEditSession) {
    guard let trim = trimRangeView, let clip = session.clips[safe: selectedClipIndex] else { return }
    let duration = max(1, clip.originalDurationMs)
    trim.setRange(
      start: CGFloat(clip.trimStartMs) / CGFloat(duration),
      end: CGFloat(clip.effectiveTrimEndMs()) / CGFloat(duration)
    )
    timelineThumbnailRequest?.cancel()
    trim.thumbnails = []
    let clipAsset = session.asset(for: clip)
    let repository = timelineThumbnailRepository ?? TimelineThumbnailRepository()
    timelineThumbnailRepository = repository
    timelineThumbnailFrames = [UIImage?](repeating: nil, count: 12)
    timelineThumbnailRequest = repository.load(
      mediaID: clip.sourceUri,
      asset: clipAsset,
      durationMs: clip.originalDurationMs,
      count: 12,
      size: CGSize(width: 144, height: 96),
      onThumbnail: { [weak self, weak trim] index, image in
        guard let self,
              self.videoSession?.clips[safe: self.selectedClipIndex]?.id == clip.id else { return }
        self.timelineThumbnailFrames[index] = image
        trim?.thumbnails = self.timelineThumbnailFrames.compactMap { $0 }
      },
      onComplete: { [weak self] in
        guard self?.videoSession?.clips[safe: self?.selectedClipIndex ?? -1]?.id == clip.id else { return }
        self?.timelineThumbnailRequest = nil
      }
    )
  }

  /// Rebuilds the clip strip: one chip per clip plus Split/Duplicate/Delete/Move-Left/Move-Right actions.
  private func refreshClipStrip(session: VideoEditSession) {
    guard let strip = clipStripBar else { return }
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    strip.arrangedSubviews.forEach { $0.removeFromSuperview() }
    session.clips.enumerated().forEach { index, clip in
      let chip = button("Clip \(index + 1)\n\(formatMs(clip.trimmedDurationMs()))", color: textColor) { [weak self] in
        guard let self, let session = self.videoSession else { return }
        self.selectedClipIndex = index
        let precedingMs = session.clips.prefix(index).reduce(Int64(0)) { $0 + $1.trimmedDurationMs() }
        session.player.seek(to: CMTime(value: precedingMs, timescale: 1000))
        self.bindTrimViewToSelectedClip(session: session)
        self.refreshClipStrip(session: session)
      }
      chip.titleLabel?.numberOfLines = 2
      chip.titleLabel?.textAlignment = .center
      chip.titleLabel?.font = .systemFont(ofSize: 11)
      setButtonSelected(chip, selected: index == selectedClipIndex, textColor: textColor, primaryColor: primaryColor)
      strip.addArrangedSubview(chip)
    }
    strip.addArrangedSubview(button("Split", color: textColor) { [weak self] in self?.splitSelectedClip() })
    strip.addArrangedSubview(button("Copy", color: textColor) { [weak self] in self?.duplicateSelectedClip() })
    strip.addArrangedSubview(button("Delete", color: textColor) { [weak self] in self?.deleteSelectedClip() })
    strip.addArrangedSubview(button("<", color: textColor) { [weak self] in self?.moveSelectedClip(by: -1) })
    strip.addArrangedSubview(button(">", color: textColor) { [weak self] in self?.moveSelectedClip(by: 1) })
  }

  /// Splits the selected clip at the current playhead into two clips sharing the same source.
  private func splitSelectedClip() {
    guard let session = videoSession, let clip = session.clips[safe: selectedClipIndex] else { return }
    let index = selectedClipIndex
    let precedingMs = session.clips.prefix(index).reduce(Int64(0)) { $0 + $1.trimmedDurationMs() }
    let globalMs = Int64(CMTimeGetSeconds(session.player.currentTime()) * 1000)
    let splitWithinClipMs = min(max(globalMs - precedingMs, 0), clip.trimmedDurationMs())
    let splitSourceMs = clip.trimStartMs + splitWithinClipMs
    guard splitSourceMs > clip.trimStartMs + 50, splitSourceMs < clip.effectiveTrimEndMs() - 50 else {
      showToast("Move the playhead into the clip first to split it there.")
      return
    }
    session.updateClips { clips in
      clips.enumerated().flatMap { i, c -> [VideoClip] in
        guard i == index else { return [c] }
        var first = c
        first.trimEndMs = splitSourceMs
        let second = VideoClip(sourceUri: c.sourceUri, originalDurationMs: c.originalDurationMs, trimStartMs: splitSourceMs, trimEndMs: c.effectiveTrimEndMs())
        return [first, second]
      }
    }
    refreshClipStrip(session: session)
  }

  /// Duplicates the selected clip immediately after itself.
  private func duplicateSelectedClip() {
    guard let session = videoSession else { return }
    let index = selectedClipIndex
    session.updateClips { clips in
      clips.enumerated().flatMap { i, c -> [VideoClip] in
        guard i == index else { return [c] }
        var copy = c
        copy.id = UUID().uuidString
        return [c, copy]
      }
    }
    selectedClipIndex = index + 1
    bindTrimViewToSelectedClip(session: session)
    refreshClipStrip(session: session)
  }

  /// Deletes the selected clip, refusing to leave the timeline empty.
  private func deleteSelectedClip() {
    guard let session = videoSession else { return }
    guard session.clips.count > 1 else {
      showToast("A video needs at least one clip.")
      return
    }
    let index = selectedClipIndex
    session.updateClips { clips in clips.enumerated().filter { $0.offset != index }.map(\.element) }
    selectedClipIndex = min(selectedClipIndex, session.clips.count - 1)
    bindTrimViewToSelectedClip(session: session)
    refreshClipStrip(session: session)
  }

  /// Moves the selected clip one position left (-1) or right (+1) in the timeline.
  private func moveSelectedClip(by direction: Int) {
    guard let session = videoSession else { return }
    let index = selectedClipIndex
    let target = index + direction
    guard target >= 0, target < session.clips.count else { return }
    session.updateClips { clips in
      var mutable = clips
      let moved = mutable.remove(at: index)
      mutable.insert(moved, at: target)
      return mutable
    }
    selectedClipIndex = target
    refreshClipStrip(session: session)
  }

  // MARK: - Shared shell

  /// Studio Violet header: circular close button, centered title, icon Undo/Redo, pill Export CTA.
  /// Mirrors the Android header reskin in `PhotoVideoEditorActivity.createTopBar`.
  private func makeHeader(textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    let header = UIStackView(); header.axis = .horizontal; header.alignment = .center
    header.backgroundColor = toolbarColor
    header.isLayoutMarginsRelativeArrangement = true
    header.layoutMargins = UIEdgeInsets(top: 0, left: DesignTokens.spaceLg, bottom: 0, right: DesignTokens.spaceLg)
    header.spacing = DesignTokens.spaceXs

    let close = iconButton(systemName: "xmark", tint: textColor) { [weak self] in self?.cancelEditor() }
    close.backgroundColor = DesignTokens.surfaceContainerLow
    close.layer.cornerRadius = DesignTokens.touchTargetMin / 2
    close.clipsToBounds = true

    let title = UILabel()
    title.text = mediaType == "photo" ? "Photo Editor" : "Video Editor"
    title.textColor = textColor
    title.font = .systemFont(ofSize: 14, weight: .semibold)
    title.adjustsFontSizeToFitWidth = true
    title.minimumScaleFactor = 0.75
    title.textAlignment = .center

    header.addArrangedSubview(close)
    header.addArrangedSubview(title)

    if mediaType == "photo" {
      let undo = iconButton(systemName: "arrow.uturn.backward", tint: textColor) { [weak self] in
        self?.photoSession?.layerStack.undo()
        self?.onLayerStackChanged()
      }
      let redo = iconButton(systemName: "arrow.uturn.forward", tint: textColor) { [weak self] in
        self?.photoSession?.layerStack.redo()
        self?.onLayerStackChanged()
      }
      undo.isEnabled = false; undo.alpha = 0.4
      redo.isEnabled = false; redo.alpha = 0.4
      undoButton = undo
      redoButton = redo
      header.addArrangedSubview(undo)
      header.addArrangedSubview(redo)
    } else if mediaType == "video" {
      let undo = iconButton(systemName: "arrow.uturn.backward", tint: textColor) { [weak self] in
        self?.videoSession?.layerStack.undo()
        self?.onVideoLayerStackChanged()
      }
      let redo = iconButton(systemName: "arrow.uturn.forward", tint: textColor) { [weak self] in
        self?.videoSession?.layerStack.redo()
        self?.onVideoLayerStackChanged()
      }
      undo.isEnabled = false; undo.alpha = 0.4
      redo.isEnabled = false; redo.alpha = 0.4
      undoButton = undo
      redoButton = redo
      header.addArrangedSubview(undo)
      header.addArrangedSubview(redo)
    }

    let onPrimaryColor = color("onPrimaryColor") ?? DesignTokens.onPrimaryContainer
    let export = EditorComponents.primaryButton(
      text: "Export",
      systemImage: nil,
      fillColor: primaryColor,
      textColor: onPrimaryColor
    ) { [weak self] in
      guard let self else { return }
      if self.mediaType == "photo" { self.showPhotoExportConfiguration() } else { self.doneEditor() }
    }
    header.addArrangedSubview(export)

    title.setContentHuggingPriority(.defaultLow, for: .horizontal)
    return header
  }

  /// 44x44pt icon-only button, matching the Studio Violet "Tool Icon Button" spec.
  private func iconButton(systemName: String, tint: UIColor, handler: (() -> Void)?) -> UIButton {
    let value = UIButton(type: .system)
    value.setImage(UIImage(systemName: systemName), for: .normal)
    value.tintColor = tint
    value.widthAnchor.constraint(equalToConstant: DesignTokens.touchTargetMin).isActive = true
    value.heightAnchor.constraint(equalToConstant: DesignTokens.touchTargetMin).isActive = true
    if let handler { value.addAction(UIAction { _ in handler() }, for: .touchUpInside) }
    value.applyPressScale()
    return value
  }

  private func button(_ title: String, color: UIColor = .white, handler: (() -> Void)?) -> UIButton {
    let value = UIButton(type: .system)
    value.setTitle(title, for: .normal)
    value.setTitleColor(color, for: .normal)
    value.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
    value.backgroundColor = DesignTokens.surfaceContainerHigh
    value.layer.cornerRadius = DesignTokens.radiusLg
    value.contentEdgeInsets = UIEdgeInsets(top: 0, left: DesignTokens.spaceMd, bottom: 0, right: DesignTokens.spaceMd)
    value.widthAnchor.constraint(greaterThanOrEqualToConstant: 64).isActive = true
    value.heightAnchor.constraint(greaterThanOrEqualToConstant: DesignTokens.touchTargetMin).isActive = true
    if let handler { value.addAction(UIAction { _ in handler() }, for: .touchUpInside) }
    return value
  }

  /// Highlights a tool/chip button so the user can tell at a glance which one is active.
  private func setButtonSelected(_ button: UIButton, selected: Bool, textColor: UIColor, primaryColor: UIColor) {
    button.setTitleColor(selected ? DesignTokens.onPrimaryContainer : textColor, for: .normal)
    button.backgroundColor = selected ? primaryColor : DesignTokens.surfaceContainerHigh
  }

  private func refreshSelection<K: Hashable>(_ buttons: [K: UIButton], activeKey: K?, textColor: UIColor, primaryColor: UIColor) {
    buttons.forEach { key, button in setButtonSelected(button, selected: key == activeKey, textColor: textColor, primaryColor: primaryColor) }
  }

  private func showToast(_ message: String) {
    let label = UILabel()
    label.text = "  \(message)  "
    label.textColor = .white
    label.backgroundColor = UIColor.black.withAlphaComponent(0.75)
    label.textAlignment = .center
    label.numberOfLines = 0
    label.layer.cornerRadius = 8
    label.clipsToBounds = true
    label.alpha = 0
    label.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(label)
    NSLayoutConstraint.activate([
      label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      label.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -96),
      label.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
      label.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),
    ])
    UIView.animate(withDuration: 0.2, animations: { label.alpha = 1 }) { _ in
      UIView.animate(withDuration: 0.2, delay: 1.2, options: [], animations: { label.alpha = 0 }) { _ in label.removeFromSuperview() }
    }
  }

  @objc private func compareHeld(_ recognizer: UILongPressGestureRecognizer) {
    switch recognizer.state {
    case .began: compareTouchDown()
    case .ended, .cancelled, .failed: compareTouchUp()
    default: break
    }
  }

  @objc private func compareTouchDown() {
    guard !comparingOriginal, let session = photoSession else { return }
    comparingOriginal = true
    photoImageView?.image = session.baseImage
  }

  @objc private func compareTouchUp() {
    guard comparingOriginal else { return }
    comparingOriginal = false
    schedulePhotoPreviewRender()
  }

  private func showPhotoExportConfiguration() {
    let sheet = UIAlertController(
      title: "Export & Share",
      message: "Choose an output format. Quality and size limits continue to use your export settings.",
      preferredStyle: .actionSheet
    )
    [("JPEG", "jpeg"), ("PNG", "png"), ("WebP", "webp")].forEach { label, format in
      sheet.addAction(UIAlertAction(title: label, style: .default) { [weak self] _ in
        self?.selectedPhotoExportFormat = format
        self?.doneEditor()
      })
    }
    sheet.addAction(UIAlertAction(title: "Cancel", style: .cancel))
    if let popover = sheet.popoverPresentationController {
      popover.sourceView = view
      popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 1, width: 1, height: 1)
    }
    present(sheet, animated: true)
  }

  /// Coalesces slider and drag callbacks and performs rendering on a dedicated background queue
  /// so the main thread remains at 120 FPS without slider hitching or dragging lag.
  private func schedulePhotoPreviewRender() {
    photoRenderQueue.async { [weak self] in
      guard let self else { return }
      if self.isRenderingPhotoPreview {
        self.photoPreviewRenderPending = true
        return
      }
      self.isRenderingPhotoPreview = true
      self.performPhotoPreviewRender()
    }
  }

  private func performPhotoPreviewRender() {
    guard self.isViewLoaded,
          let session = self.photoSession else {
      self.isRenderingPhotoPreview = false
      return
    }
    let isCropping = self.cropMode
    let rendered = autoreleasepool {
      session.renderPreview(cropping: isCropping)
    }
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      if let rendered, let imageView = self.photoImageView {
        imageView.image = rendered
        if self.cropMode {
          self.cropOverlay?.restore(bounds: imageView.currentImageBounds(), state: session.state)
        }
      }
      self.photoRenderQueue.async { [weak self] in
        guard let self else { return }
        if self.photoPreviewRenderPending {
          self.photoPreviewRenderPending = false
          self.performPhotoPreviewRender()
        } else {
          self.isRenderingPhotoPreview = false
        }
      }
    }
  }

  private func cancelEditor() {
    let alert = UIAlertController(
      title: "Discard Unsaved Changes?",
      message: "If you exit now, your edits will be lost.",
      preferredStyle: .alert
    )
    alert.addAction(UIAlertAction(title: "Keep Editing", style: .cancel))
    alert.addAction(UIAlertAction(title: "Discard Changes", style: .destructive) { [weak self] _ in
      self?.videoExporter?.cancel()
      self?.completion?(.cancelled)
    })
    present(alert, animated: true)
  }

  private func doneEditor() {
    if mediaType == "photo" {
      exportPhoto()
    } else {
      exportVideo()
    }
  }

  override func didReceiveMemoryWarning() {
    super.didReceiveMemoryWarning()
    timelineThumbnailRepository?.clearMemory()
  }

  deinit {
    positionPollTimer?.invalidate()
    timelineThumbnailRequest?.cancel()
    timelineThumbnailRepository?.close()
    photoSession?.release()
    videoSession?.release()
  }
}

private extension Array {
  subscript(safe index: Int) -> Element? {
    indices.contains(index) ? self[index] : nil
  }
}

extension PhotoVideoEditorViewController: PHPickerViewControllerDelegate {
  func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
    picker.dismiss(animated: true)
    guard let purpose = pendingPickPurpose, let provider = results.first?.itemProvider else {
      pendingPickPurpose = nil
      return
    }
    pendingPickPurpose = nil
    guard provider.canLoadObject(ofClass: UIImage.self) else { return }
    // `loadObject`'s completion runs off the main thread — hop back before touching UI/session state.
    provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
      guard let self, let image = object as? UIImage, let fileURL = self.writeTempImage(image) else { return }
      DispatchQueue.main.async {
        self.handlePickedImage(fileURL, image: image, purpose: purpose)
      }
    }
  }
}
