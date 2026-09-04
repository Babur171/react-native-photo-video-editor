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
  private let stickerAssets: [[String: Any]]
  var completion: ((PhotoVideoEditorOutcome) -> Void)?

  private var photoSession: PhotoEditSession?
  private var cropMode = false
  private var filtersMode = false
  private var stickersMode = false
  private var pendingPickPurpose: ImagePickPurpose?
  private var activeAdjustmentKey = "brightness"
  private var selectedLayerID: String?
  private var activeLayerPropertyKey = "scale"
  private var dragStartSnapshot: [PhotoLayer]?
  private var photoImageView: ZoomableImageView?
  private var cropOverlay: CropOverlayView?
  private var layerOverlay: LayerOverlayView?
  private var straightenSlider: UISlider?
  private var cropSubBar: UIView?
  private var adjustmentSlider: UISlider?
  private var filtersSubBar: UIView?
  private var stickersSubBar: UIView?
  private var layerToolBar: UIView?
  private var layerPropertySlider: UISlider?
  private var layerColorSwatchRow: UIView?
  private var mainToolBar: UIView?
  private var undoButton: UIButton?
  private var redoButton: UIButton?
  private var progressOverlay: UIView?
  private var photoPreviewRenderPending = false
  private var comparingOriginal = false
  private var selectedPhotoExportFormat: String?

  private var videoSession: VideoEditSession?
  private var videoExporter: VideoExporter?
  private var videoAspectMode = false
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
  private var activeVideoLayerPropertyKey = "scale"
  private var videoDragStartSnapshot: [PhotoLayer]?
  private var videoLayerPropertyButtons: [String: UIButton] = [:]
  private var seekingPosition = false
  private var selectedClipIndex = 0
  private var clipStripBar: UIStackView?
  private var clipStripScroll: UIScrollView?
  private var timelineThumbnailRepository: TimelineThumbnailRepository?
  private var timelineThumbnailRequest: TimelineThumbnailRepository.Request?
  private var timelineThumbnailFrames = [UIImage?](repeating: nil, count: 12)

  private var cropAspectButtons: [String: UIButton] = [:]
  private var filterAdjustmentButtons: [String: UIButton] = [:]
  private var filterPresetButtons: [String: UIButton] = [:]
  private var layerPropertyButtons: [String: UIButton] = [:]
  private var layerIconButtons: [String: UIButton] = [:]
  private var videoToolButtons: [String: UIButton] = [:]
  private var videoAspectButtons: [String: UIButton] = [:]

  init(
    uri: String,
    mediaType: String,
    features: [String: Any],
    theme: [String: Any] = [:],
    exportOptions: [String: Any] = [:],
    stickerAssets: [[String: Any]] = []
  ) {
    self.uri = uri
    self.mediaType = mediaType
    self.features = features
    self.theme = theme
    self.exportOptions = exportOptions
    self.stickerAssets = stickerAssets
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

    let imageView = ZoomableImageView()
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

    let layers = LayerOverlayView()
    layerOverlay = layers
    preview.addSubview(layers)
    layers.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      layers.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      layers.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      layers.topAnchor.constraint(equalTo: preview.topAnchor),
      layers.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])

    let metadataBadge = UILabel()
    metadataBadge.text = "  ●  ORIGINAL   •   100%  "
    metadataBadge.font = .systemFont(ofSize: 10, weight: .semibold)
    metadataBadge.textColor = DesignTokens.onSurfaceVariant
    metadataBadge.backgroundColor = DesignTokens.surfaceContainerHigh.withAlphaComponent(0.92)
    metadataBadge.layer.cornerRadius = DesignTokens.radiusLg
    metadataBadge.clipsToBounds = true
    preview.addSubview(metadataBadge)
    metadataBadge.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      metadataBadge.centerXAnchor.constraint(equalTo: preview.centerXAnchor),
      metadataBadge.topAnchor.constraint(equalTo: preview.topAnchor, constant: DesignTokens.spaceSm),
      metadataBadge.heightAnchor.constraint(equalToConstant: 28),
    ])

    imageView.onBoundsChanged = { [weak self] bounds in
      guard let self else { return }
      if self.cropMode { overlay.setImageBounds(bounds, resetCrop: false) }
      layers.setImageBounds(bounds)
    }
    overlay.onCropChanged = { left, top, right, bottom in
      session.update { $0.setCrop(left: left, top: top, right: right, bottom: bottom) }
    }
    layers.onLayerTapped = { [weak self] id in self?.selectLayer(id) }
    layers.onLayerTransformChanged = { [weak self] id, x, y, scale, rotation in
      guard let self, let session = self.photoSession else { return }
      if self.dragStartSnapshot == nil { self.dragStartSnapshot = session.layerStack.layers }
      session.layerStack.updateLive { list in list.map { $0.id == id ? {
        var layer = $0; layer.x = x; layer.y = y; layer.scale = scale; layer.rotationDegrees = rotation; return layer
      }() : $0 } }
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

    let adjustmentSlider = UISlider()
    adjustmentSlider.isHidden = true
    adjustmentSlider.addTarget(self, action: #selector(adjustmentChanged(_:)), for: .valueChanged)
    self.adjustmentSlider = adjustmentSlider

    let filtersBar = makeFiltersSubBar(session: session, imageView: imageView, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    filtersBar.isHidden = true
    filtersSubBar = filtersBar

    let stickersBar = makeStickersSubBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    stickersBar.isHidden = true
    stickersSubBar = stickersBar

    let layerSlider = UISlider()
    layerSlider.isHidden = true
    layerSlider.addTarget(self, action: #selector(layerPropertyChanged(_:)), for: .valueChanged)
    layerPropertySlider = layerSlider

    let layerBar = makeLayerToolBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    layerBar.isHidden = true
    layerToolBar = layerBar

    let colorSwatchRow = makeTextColorSwatchRow(session: session)
    colorSwatchRow.isHidden = true
    layerColorSwatchRow = colorSwatchRow

    let toolbar = makePhotoToolBar(textColor: textColor, toolbarColor: toolbarColor)
    mainToolBar = toolbar

    let root = UIStackView(arrangedSubviews: [header, preview, straighten, cropBar, adjustmentSlider, filtersBar, stickersBar, layerSlider, colorSwatchRow, layerBar, toolbar])
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
      cropBar.heightAnchor.constraint(equalToConstant: 100), // rotate row (44pt) + aspect chip row (56pt)
      filtersBar.heightAnchor.constraint(equalToConstant: 56),
      stickersBar.heightAnchor.constraint(equalToConstant: 56),
      layerBar.heightAnchor.constraint(equalToConstant: 56),
      colorSwatchRow.heightAnchor.constraint(equalToConstant: 56),
    ])

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

  /// Icon-tile + label node in the main tool rail (Studio Violet "Tool Node"). Mirrors Android's `ToolNode`.
  private struct ToolNode {
    let root: UIView
    let tile: UIView
    let icon: UIImageView
    let label: UILabel
  }

  private var photoToolNodes: [String: ToolNode] = [:]
  private weak var photoToolScroll: UIScrollView?
  /// Backs `createToolNode`'s tap handling: each node's root view registers its action here, keyed by
  /// its own identity, and a single shared `UITapGestureRecognizer` target (`self`) looks it up.
  private var toolNodeActions: [ObjectIdentifier: () -> Void] = [:]

  @objc private func handleToolNodeTap(_ recognizer: UITapGestureRecognizer) {
    guard let view = recognizer.view else { return }
    toolNodeActions[ObjectIdentifier(view)]?()
  }

  private func createToolNode(systemName: String, labelText: String, onTap: @escaping () -> Void) -> ToolNode {
    let icon = UIImageView(image: UIImage(systemName: systemName))
    icon.tintColor = DesignTokens.outline
    icon.contentMode = .scaleAspectFit
    let tile = UIView()
    tile.backgroundColor = DesignTokens.surfaceContainerLow
    tile.layer.cornerRadius = DesignTokens.radiusLg
    tile.addSubview(icon)
    icon.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      icon.centerXAnchor.constraint(equalTo: tile.centerXAnchor),
      icon.centerYAnchor.constraint(equalTo: tile.centerYAnchor),
      icon.widthAnchor.constraint(equalToConstant: 22),
      icon.heightAnchor.constraint(equalToConstant: 22),
    ])
    let label = UILabel()
    label.text = labelText
    label.font = .systemFont(ofSize: 11)
    label.textColor = DesignTokens.outline
    label.textAlignment = .center
    let root = UIStackView(arrangedSubviews: [tile, label])
    root.axis = .vertical
    root.alignment = .center
    root.spacing = 4
    root.isUserInteractionEnabled = true
    toolNodeActions[ObjectIdentifier(root)] = onTap
    root.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(handleToolNodeTap(_:))))
    root.applyPressScale()
    NSLayoutConstraint.activate([
      tile.widthAnchor.constraint(equalToConstant: DesignTokens.touchTargetMin),
      tile.heightAnchor.constraint(equalToConstant: DesignTokens.touchTargetMin),
    ])
    return ToolNode(root: root, tile: tile, icon: icon, label: label)
  }

  private func setToolNodeSelected(_ node: ToolNode, selected: Bool, textColor: UIColor) {
    node.tile.backgroundColor = selected ? DesignTokens.surfaceContainer : DesignTokens.surfaceContainerLow
    node.icon.tintColor = selected ? textColor : DesignTokens.outline
    node.label.textColor = selected ? textColor : DesignTokens.outline
  }

  /// Full Studio Violet tool rail (see main_photo_editor_default_state mockup). Rotate lives inside
  /// the Crop sub-bar, not as a top-level tool.
  private func makePhotoToolBar(textColor: UIColor, toolbarColor: UIColor) -> UIView {
    let tools: [(String, String, String)] = [
      ("crop", "Crop", "crop"),
      ("adjust", "Adjust", "slider.horizontal.3"),
      ("filters", "Filters", "camera.filters"),
      ("text", "Text", "textformat"),
      ("stickers", "Stickers", "face.smiling"),
      ("overlay", "Overlay", "photo.on.rectangle.angled"),
      ("retouch", "Retouch", "wand.and.rays"),
      ("layers", "Layers", "square.3.layers.3d"),
      ("resize", "Resize", "aspectratio"),
    ]
    let scroll = UIScrollView(); scroll.backgroundColor = toolbarColor
    photoToolScroll = scroll
    let stack = UIStackView(); stack.axis = .horizontal; stack.spacing = 12
    tools.filter { features[$0.0] as? Bool != false }.forEach { key, label, symbol in
      let node = createToolNode(systemName: symbol, labelText: label) { [weak self] in self?.onPhotoToolTapped(key) }
      photoToolNodes[key] = node
      stack.addArrangedSubview(node.root)
    }
    scroll.addSubview(stack)
    stack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 12),
      stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -12),
      stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      stack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    return scroll
  }

  private func makeCropSubBar(session: PhotoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    let presets: [(String, CGFloat?)] = [("Free", nil), ("1:1", 1), ("4:5", 4 / 5), ("3:4", 3 / 4), ("9:16", 9 / 16), ("16:9", 16 / 9)]
    let outer = UIStackView(); outer.axis = .vertical
    outer.backgroundColor = toolbarColor

    // Rotate row — moved here from the main tool rail to match the crop_transform_editor mockup.
    let transformRow = UIStackView(arrangedSubviews: [
      iconButton(systemName: "rotate.left", tint: textColor) { [weak self] in
        guard let self, let session = self.photoSession, let imageView = self.photoImageView else { return }
        session.update { $0.rotateRight(); $0.rotateRight(); $0.rotateRight() }
        self.schedulePhotoPreviewRender()
        DispatchQueue.main.async {
          imageView.resetToFit()
          if self.cropMode { self.cropOverlay?.setImageBounds(imageView.currentImageBounds(), resetCrop: true) }
        }
      },
      iconButton(systemName: "rotate.right", tint: textColor) { [weak self] in
        guard let self, let session = self.photoSession, let imageView = self.photoImageView else { return }
        session.update { $0.rotateRight() }
        self.schedulePhotoPreviewRender()
        DispatchQueue.main.async {
          imageView.resetToFit()
          if self.cropMode { self.cropOverlay?.setImageBounds(imageView.currentImageBounds(), resetCrop: true) }
        }
      },
    ])
    transformRow.axis = .horizontal
    transformRow.alignment = .center
    transformRow.distribution = .equalCentering
    transformRow.heightAnchor.constraint(equalToConstant: DesignTokens.touchTargetMin).isActive = true
    outer.addArrangedSubview(transformRow)

    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    let presetStack = UIStackView(); presetStack.axis = .horizontal; presetStack.spacing = 4
    presets.forEach { label, ratio in
      let chip = button(label, color: textColor) { [weak self] in
        self?.cropOverlay?.setAspectRatio(ratio)
        session.update { $0.aspectRatio = ratio }
        if let self { self.refreshSelection(self.cropAspectButtons, activeKey: label, textColor: textColor, primaryColor: primaryColor) }
      }
      cropAspectButtons[label] = chip
      presetStack.addArrangedSubview(chip)
    }
    presetStack.addArrangedSubview(button("Reset", color: textColor) { [weak self] in self?.resetPhotoEdits() })
    scroll.addSubview(presetStack)
    presetStack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      presetStack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 8),
      presetStack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -8),
      presetStack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      presetStack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      presetStack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    bar.addArrangedSubview(scroll)
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in self?.setCropMode(false) })
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    bar.heightAnchor.constraint(equalToConstant: 56).isActive = true
    outer.addArrangedSubview(bar)
    return outer
  }

  private func onPhotoToolTapped(_ key: String) {
    guard let session = photoSession, photoImageView != nil else { return }
    switch key {
    case "crop":
      setCropMode(true)
    case "adjust", "filters", "retouch":
      setFiltersMode(true, session: session)
    case "text":
      showTextInputDialog(session: session)
    case "stickers":
      setStickersMode(true)
    case "overlay":
      presentImagePicker(purpose: .photoOverlay)
    case "resize":
      setCropMode(true)
    case "layers":
      if let top = session.layerStack.layers.last { selectLayer(top.id) }
      else { showToast("Add content to start a layer stack.") }
    default:
      showToast("\(key) is coming in a later milestone.")
    }
  }

  // MARK: - Layers: text, selection, and transform/duplicate/reorder/lock/hide/delete
  // with undo/redo (Milestone 4)

  private func showTextInputDialog(session: PhotoEditSession) {
    let alert = UIAlertController(title: "Add text", message: nil, preferredStyle: .alert)
    alert.addTextField { $0.placeholder = "Text" }
    alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
    alert.addAction(UIAlertAction(title: "Add", style: .default) { [weak self, weak alert] _ in
      guard let text = alert?.textFields?.first?.text, !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }
      var layer = PhotoLayer(type: .text)
      layer.text = text
      session.layerStack.commit { $0 + [layer] }
      self?.selectLayer(layer.id)
    })
    present(alert, animated: true)
  }

  private func setStickersMode(_ enabled: Bool) {
    stickersMode = enabled
    stickersSubBar?.isHidden = !enabled
    mainToolBar?.isHidden = enabled
    refreshPhotoToolSelection()
  }

  private func makeStickersSubBar(session: PhotoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    let chipStack = UIStackView(); chipStack.axis = .horizontal; chipStack.spacing = 4
    PhotoLayerRenderer.builtinStickerIDs().forEach { id in
      chipStack.addArrangedSubview(button(PhotoLayerRenderer.glyph(for: id)) { [weak self] in
        var layer = PhotoLayer(type: .sticker)
        layer.stickerId = id
        session.layerStack.commit { $0 + [layer] }
        self?.selectLayer(layer.id)
        self?.setStickersMode(false)
      })
    }
    consumerStickerAssets().forEach { asset in
      chipStack.addArrangedSubview(button(asset.id, color: textColor) { [weak self] in
        var layer = PhotoLayer(type: .sticker)
        layer.stickerUri = asset.uri
        session.layerStack.commit { $0 + [layer] }
        self?.selectLayer(layer.id)
        self?.setStickersMode(false)
      })
    }
    chipStack.addArrangedSubview(button("Upload", color: textColor) { [weak self] in
      self?.presentImagePicker(purpose: .photoSticker)
    })
    if features["onlineStickers"] as? Bool != false {
      chipStack.addArrangedSubview(button("Browse", color: textColor) { [weak self] in
        self?.presentOnlineStickerSheet(purpose: .photoSticker)
      })
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
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in self?.setStickersMode(false) })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    return bar
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

  /// Presents the OpenMoji "Browse online" bottom sheet, reusing `handlePickedImage` as the
  /// insertion point once the user picks a sticker — same shared path the Upload flow uses.
  private func presentOnlineStickerSheet(purpose: ImagePickPurpose) {
    let sheet = OnlineStickerSheetViewController()
    sheet.onStickerPicked = { [weak self] fileURL, image in
      self?.handlePickedImage(fileURL, image: image, purpose: purpose)
    }
    sheet.sheetPresentationController?.detents = [.medium(), .large()]
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
      var layer = PhotoLayer(type: .sticker)
      layer.stickerUri = uri
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
      var layer = PhotoLayer(type: .sticker)
      layer.stickerUri = uri
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
      case "adjust", "filters": selected = filtersMode
      case "stickers": selected = stickersMode
      default: selected = false
      }
      setToolNodeSelected(node, selected: selected, textColor: textColor)
      if selected { scrollToolNodeIntoView(node) }
    }
  }

  /// Keeps the active tool-rail node fully visible, never partially clipped at the scroll edge.
  private func scrollToolNodeIntoView(_ node: ToolNode) {
    guard let scroll = photoToolScroll else { return }
    DispatchQueue.main.async {
      let frame = node.root.convert(node.root.bounds, to: scroll)
      scroll.scrollRectToVisible(frame.insetBy(dx: -DesignTokens.spaceSm, dy: 0), animated: true)
    }
  }

  private func selectLayer(_ id: String?) {
    selectedLayerID = id
    layerOverlay?.selectedLayerID = id
    if let session = photoSession {
      layerOverlay?.layers = session.layerStack.layers
      schedulePhotoPreviewRender()
    }
    setLayerToolBarVisible(id != nil)
  }

  private func setLayerToolBarVisible(_ visible: Bool) {
    layerToolBar?.isHidden = !visible
    mainToolBar?.isHidden = visible
    if visible {
      // TEXT layers show only Opacity/Colors/Duplicate/Delete, and STICKER layers show only
      // Opacity/Duplicate/Delete (no Colors) — Scale/Rotate and the lock/visibility/reorder
      // icons don't apply to either and are hidden per the reduced action list.
      let isText = currentSelectedLayer()?.type == .text
      let isSticker = currentSelectedLayer()?.type == .sticker
      let reducedControls = isText || isSticker
      layerPropertyButtons["scale"]?.isHidden = reducedControls
      layerPropertyButtons["rotation"]?.isHidden = reducedControls
      layerPropertyButtons["color"]?.isHidden = !isText
      ["lock", "visibility", "front", "back"].forEach { key in
        layerIconButtons[key]?.isHidden = reducedControls
      }
      if reducedControls, ["scale", "rotation"].contains(activeLayerPropertyKey) {
        activeLayerPropertyKey = "opacity"
      } else if !isText, activeLayerPropertyKey == "color" {
        activeLayerPropertyKey = reducedControls ? "opacity" : "scale"
      }
      layerPropertySlider?.isHidden = activeLayerPropertyKey == "color"
      layerColorSwatchRow?.isHidden = activeLayerPropertyKey != "color"
      if activeLayerPropertyKey != "color" { syncLayerPropertySlider() }
      let textColor = color("textColor") ?? DesignTokens.onSurface
      let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
      refreshSelection(layerPropertyButtons, activeKey: activeLayerPropertyKey, textColor: textColor, primaryColor: primaryColor)
    } else {
      layerPropertySlider?.isHidden = true
      layerColorSwatchRow?.isHidden = true
    }
  }

  private func currentSelectedLayer() -> PhotoLayer? {
    guard let session = photoSession else { return nil }
    return session.layerStack.layers.first { $0.id == selectedLayerID }
  }

  private func syncLayerPropertySlider() {
    guard let slider = layerPropertySlider, let layer = currentSelectedLayer() else { return }
    let (min, max, value): (CGFloat, CGFloat, CGFloat)
    switch activeLayerPropertyKey {
    case "rotation": (min, max, value) = (-180, 180, layer.rotationDegrees)
    case "opacity": (min, max, value) = (0, 100, layer.opacity * 100)
    default: (min, max, value) = (20, 800, layer.scale * 100)
    }
    slider.minimumValue = Float(min)
    slider.maximumValue = Float(max)
    slider.value = Float(value)
  }

  @objc private func layerPropertyChanged(_ slider: UISlider) {
    guard let session = photoSession, let id = selectedLayerID else { return }
    let value = CGFloat(slider.value)
    let key = activeLayerPropertyKey
    session.layerStack.updateLive { list in
      list.map { layer in
        guard layer.id == id else { return layer }
        var updated = layer
        switch key {
        case "rotation": updated.rotationDegrees = value
        case "opacity": updated.opacity = value / 100
        default: updated.scale = value / 100
        }
        return updated
      }
    }
    layerOverlay?.layers = session.layerStack.layers
    schedulePhotoPreviewRender()
  }

  private func makeLayerToolBar(session: PhotoEditSession, textColor: UIColor, primaryColor: UIColor, toolbarColor: UIColor) -> UIView {
    // "color" only makes sense for TEXT layers; its chip is hidden/shown per-selection in `setLayerToolBarVisible`.
    let properties: [(String, String)] = [("scale", "Scale"), ("rotation", "Rotate"), ("opacity", "Opacity"), ("color", "Colors")]
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    let chipStack = UIStackView(); chipStack.axis = .horizontal; chipStack.spacing = 4
    properties.forEach { key, label in
      let chip = button(label, color: textColor) { [weak self] in
        self?.activeLayerPropertyKey = key
        self?.layerPropertySlider?.isHidden = key == "color"
        self?.layerColorSwatchRow?.isHidden = key != "color"
        if key != "color" { self?.syncLayerPropertySlider() }
        if let self { self.refreshSelection(self.layerPropertyButtons, activeKey: self.activeLayerPropertyKey, textColor: textColor, primaryColor: primaryColor) }
      }
      layerPropertyButtons[key] = chip
      chipStack.addArrangedSubview(chip)
    }
    chipStack.addArrangedSubview(button("Duplicate", color: textColor) { [weak self] in
      guard let self, let original = self.currentSelectedLayer() else { return }
      var copy = PhotoLayer(id: UUID().uuidString, type: original.type)
      copy.x = min(max(original.x + 0.04, 0), 1)
      copy.y = min(max(original.y + 0.04, 0), 1)
      copy.scale = original.scale
      copy.rotationDegrees = original.rotationDegrees
      copy.opacity = original.opacity
      copy.text = original.text
      copy.textColor = original.textColor
      copy.fontSize = original.fontSize
      copy.fontFamily = original.fontFamily
      copy.stickerId = original.stickerId
      copy.stickerUri = original.stickerUri
      copy.overlayUri = original.overlayUri
      copy.overlayAspectRatio = original.overlayAspectRatio
      session.layerStack.commit { $0 + [copy] }
      self.selectLayer(copy.id)
    })
    let lockButton = button("Lock", color: textColor) { [weak self] in
      guard let self, let id = self.selectedLayerID else { return }
      session.layerStack.commit { list in list.map { $0.id == id ? { var l = $0; l.locked.toggle(); return l }() : $0 } }
      self.onLayerStackChanged()
    }
    layerIconButtons["lock"] = lockButton
    chipStack.addArrangedSubview(lockButton)
    let visibilityButton = button("Hide", color: textColor) { [weak self] in
      guard let self, let id = self.selectedLayerID else { return }
      session.layerStack.commit { list in list.map { $0.id == id ? { var l = $0; l.visible.toggle(); return l }() : $0 } }
      self.onLayerStackChanged()
    }
    layerIconButtons["visibility"] = visibilityButton
    chipStack.addArrangedSubview(visibilityButton)
    let frontButton = button("Front", color: textColor) { [weak self] in
      guard let self, let id = self.selectedLayerID else { return }
      session.layerStack.commit { list in list.filter { $0.id != id } + list.filter { $0.id == id } }
      self.onLayerStackChanged()
    }
    layerIconButtons["front"] = frontButton
    chipStack.addArrangedSubview(frontButton)
    let backButton = button("Back", color: textColor) { [weak self] in
      guard let self, let id = self.selectedLayerID else { return }
      session.layerStack.commit { list in list.filter { $0.id == id } + list.filter { $0.id != id } }
      self.onLayerStackChanged()
    }
    layerIconButtons["back"] = backButton
    chipStack.addArrangedSubview(backButton)
    chipStack.addArrangedSubview(button("Delete", color: textColor) { [weak self] in
      guard let self, let id = self.selectedLayerID else { return }
      session.layerStack.commit { list in list.filter { $0.id != id } }
      self.selectLayer(nil)
      self.onLayerStackChanged()
    })
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
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in self?.selectLayer(nil) })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    return bar
  }

  /// Circular color swatch, matching the Android drawing_brush_studio mockup's palette dots.
  private func createColorSwatch(color: UIColor, onTap: @escaping () -> Void) -> UIView {
    let outer = UIButton(type: .custom)
    outer.backgroundColor = DesignTokens.surfaceContainerHigh
    outer.layer.cornerRadius = 16
    outer.widthAnchor.constraint(equalToConstant: 32).isActive = true
    outer.heightAnchor.constraint(equalToConstant: 32).isActive = true
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
    let toolbarColor = color("toolbarColor") ?? DesignTokens.surfaceContainerLow
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    let colors: [UIColor] = [
      .white, .black, .red, UIColor(red: 255 / 255, green: 214 / 255, blue: 51 / 255, alpha: 1),
      UIColor(red: 76 / 255, green: 217 / 255, blue: 100 / 255, alpha: 1), UIColor(red: 0, green: 145 / 255, blue: 255 / 255, alpha: 1),
      DesignTokens.primary, DesignTokens.tertiary,
    ]
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    let swatchStack = UIStackView(); swatchStack.axis = .horizontal; swatchStack.spacing = 8
    colors.forEach { swatchColor in
      swatchStack.addArrangedSubview(createColorSwatch(color: swatchColor) { [weak self] in
        guard let self, let id = self.selectedLayerID else { return }
        session.layerStack.commit { list in list.map { $0.id == id ? { var l = $0; l.textColor = swatchColor; return l }() : $0 } }
        self.onLayerStackChanged()
      })
    }
    scroll.addSubview(swatchStack)
    swatchStack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      swatchStack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 8),
      swatchStack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -8),
      swatchStack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      swatchStack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      swatchStack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    bar.addArrangedSubview(scroll)
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in self?.selectLayer(nil) })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    return bar
  }

  /// Refreshes the preview + selection + undo/redo enabled state after any layer-stack change.
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

  private func setFiltersMode(_ enabled: Bool, session: PhotoEditSession) {
    filtersMode = enabled
    filtersSubBar?.isHidden = !enabled
    adjustmentSlider?.isHidden = !enabled
    mainToolBar?.isHidden = enabled
    layerOverlay?.isHidden = enabled
    if enabled { syncAdjustmentSlider(session: session) }
    refreshPhotoToolSelection()
    refreshFiltersSelection(session: session)
  }

  private func refreshFiltersSelection(session: PhotoEditSession) {
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    refreshSelection(filterAdjustmentButtons, activeKey: activeAdjustmentKey, textColor: textColor, primaryColor: primaryColor)
    refreshSelection(filterPresetButtons, activeKey: session.adjustments.filterPreset, textColor: textColor, primaryColor: primaryColor)
  }

  private func syncAdjustmentSlider(session: PhotoEditSession) {
    guard let slider = adjustmentSlider else { return }
    let (min, max) = PhotoAdjustments.range(for: activeAdjustmentKey)
    slider.minimumValue = Float(min)
    slider.maximumValue = Float(max)
    slider.value = Float(session.adjustments.value(activeAdjustmentKey))
  }

  @objc private func adjustmentChanged(_ slider: UISlider) {
    guard let session = photoSession, let imageView = photoImageView else { return }
    let key = activeAdjustmentKey
    session.updateAdjustments { $0.setValue(key, Double(slider.value)) }
    schedulePhotoPreviewRender()
  }

  private func makeFiltersSubBar(
    session: PhotoEditSession,
    imageView: ZoomableImageView,
    textColor: UIColor,
    primaryColor: UIColor,
    toolbarColor: UIColor
  ) -> UIView {
    let adjustmentChips: [(String, String)] = [
      ("brightness", "Brightness"), ("contrast", "Contrast"), ("saturation", "Saturation"),
      ("exposure", "Exposure"), ("temperature", "Temp"), ("blurRadius", "Blur"),
    ]
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    let chipStack = UIStackView(); chipStack.axis = .horizontal; chipStack.spacing = 4
    adjustmentChips.forEach { key, label in
      let chip = button(label, color: textColor) { [weak self] in
        self?.activeAdjustmentKey = key
        self?.syncAdjustmentSlider(session: session)
        if let self { self.refreshFiltersSelection(session: session) }
      }
      filterAdjustmentButtons[key] = chip
      chipStack.addArrangedSubview(chip)
    }
    chipStack.addArrangedSubview(button("Mirror", color: textColor) { [weak self] in
      session.updateAdjustments { $0.mirror.toggle() }
      self?.schedulePhotoPreviewRender()
    })
    PhotoFilterPresets.presetIDs.forEach { id in
      let chip = button(PhotoFilterPresets.label(for: id), color: textColor) { [weak self] in
        session.updateAdjustments { $0.filterPreset = $0.filterPreset == id ? nil : id }
        self?.activeAdjustmentKey = "filterStrength"
        self?.syncAdjustmentSlider(session: session)
        self?.schedulePhotoPreviewRender()
        if let self { self.refreshFiltersSelection(session: session) }
      }
      filterPresetButtons[id] = chip
      chipStack.addArrangedSubview(chip)
    }
    chipStack.addArrangedSubview(button("Reset", color: textColor) { [weak self] in
      session.updateAdjustments { $0 = PhotoAdjustments() }
      self?.syncAdjustmentSlider(session: session)
      self?.schedulePhotoPreviewRender()
      if let self { self.refreshFiltersSelection(session: session) }
    })
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
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in self?.setFiltersMode(false, session: session) })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    return bar
  }

  private func setCropMode(_ enabled: Bool) {
    guard let imageView = photoImageView, let overlay = cropOverlay, let session = photoSession else { return }
    cropMode = enabled
    imageView.panZoomEnabled = !enabled
    overlay.isHidden = !enabled
    straightenSlider?.isHidden = !enabled
    cropSubBar?.isHidden = !enabled
    mainToolBar?.isHidden = enabled
    layerOverlay?.isHidden = enabled
    if enabled {
      imageView.resetToFit()
      overlay.setImageBounds(imageView.currentImageBounds(), resetCrop: session.state.isIdentity)
    }
    refreshPhotoToolSelection()
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    refreshSelection(cropAspectButtons, activeKey: aspectRatioLabel(session.state.aspectRatio), textColor: textColor, primaryColor: primaryColor)
  }

  /// Maps an aspect ratio back to its preset label (matching the presets defined in `makeCropSubBar`/`makeVideoAspectSubBar`) so the correct chip can be highlighted.
  private func aspectRatioLabel(_ ratio: CGFloat?) -> String {
    guard let ratio else { return "Free" }
    let knownRatios: [(String, CGFloat)] = [("1:1", 1), ("4:5", 4 / 5), ("3:4", 3 / 4), ("9:16", 9 / 16), ("16:9", 16 / 9)]
    return knownRatios.first(where: { $0.1 == ratio })?.0 ?? "Free"
  }

  private func resetPhotoEdits() {
    guard let imageView = photoImageView, let overlay = cropOverlay, let session = photoSession else { return }
    session.update { $0.reset() }
    overlay.setAspectRatio(nil)
    straightenSlider?.value = 0
    schedulePhotoPreviewRender()
    imageView.resetToFit()
    overlay.setImageBounds(imageView.currentImageBounds(), resetCrop: true)
    let textColor = color("textColor") ?? DesignTokens.onSurface
    let primaryColor = color("primaryColor") ?? DesignTokens.primaryContainer
    refreshSelection(cropAspectButtons, activeKey: "Free", textColor: textColor, primaryColor: primaryColor)
  }

  @objc private func straightenChanged(_ slider: UISlider) {
    guard let session = photoSession, let imageView = photoImageView else { return }
    session.update { $0.setStraighten(CGFloat(slider.value)) }
    schedulePhotoPreviewRender()
  }

  private func exportPhoto() {
    guard let session = photoSession else {
      completion?(.failure(code: "E_INTERNAL", message: "Nothing to export."))
      return
    }
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
    let preview = UIView(); preview.backgroundColor = backgroundColor

    let playerController = AVPlayerViewController()
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

    let videoLayers = LayerOverlayView()
    videoLayerOverlay = videoLayers
    preview.addSubview(videoLayers)
    videoLayers.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      videoLayers.leadingAnchor.constraint(equalTo: preview.leadingAnchor),
      videoLayers.trailingAnchor.constraint(equalTo: preview.trailingAnchor),
      videoLayers.topAnchor.constraint(equalTo: preview.topAnchor),
      videoLayers.bottomAnchor.constraint(equalTo: preview.bottomAnchor),
    ])
    videoLayers.onLayerTapped = { [weak self] id in self?.selectVideoLayer(id, session: session) }
    videoLayers.onLayerTransformChanged = { [weak self] id, x, y, scale, rotation in
      guard let self else { return }
      if self.videoDragStartSnapshot == nil { self.videoDragStartSnapshot = session.layerStack.layers }
      session.layerStack.updateLive { list in list.map { $0.id == id ? {
        var layer = $0; layer.x = x; layer.y = y; layer.scale = scale; layer.rotationDegrees = rotation; return layer
      }() : $0 } }
      videoLayers.layers = session.layerStack.layers
      self.refreshVideoOverlayPreview(session: session)
    }
    videoLayers.onLayerTransformEnded = { [weak self] _ in
      guard let self else { return }
      if let snapshot = self.videoDragStartSnapshot { session.layerStack.commitSnapshot(snapshot) }
      self.videoDragStartSnapshot = nil
      self.onVideoLayerStackChanged()
    }

    let playPause = button("Play", color: textColor) { [weak self] in
      guard let self, let session = self.videoSession else { return }
      // `.rate = x` (rather than `.play()`) so playback always resumes at the currently selected speed.
      if session.player.timeControlStatus == .playing { session.player.pause() } else { session.player.rate = session.state.speed }
    }
    playPauseButton = playPause
    let time = UILabel()
    time.text = "0:00 / 0:00"
    time.textColor = textColor
    time.font = .systemFont(ofSize: 12)
    timeLabel = time
    let transport = UIStackView(arrangedSubviews: [playPause, time])
    transport.axis = .horizontal
    transport.alignment = .center
    transport.spacing = 8
    transport.backgroundColor = toolbarColor
    transport.isLayoutMarginsRelativeArrangement = true
    transport.layoutMargins = UIEdgeInsets(top: 0, left: 8, bottom: 0, right: 8)

    let position = UISlider()
    position.minimumValue = 0
    position.maximumValue = 1000
    position.addTarget(self, action: #selector(positionSliderChanged(_:)), for: .valueChanged)
    position.addTarget(self, action: #selector(positionSliderTouchDown(_:)), for: .touchDown)
    position.addTarget(self, action: #selector(positionSliderTouchUp(_:)), for: [.touchUpInside, .touchUpOutside])
    positionSlider = position

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

    let aspectBar = makeVideoAspectSubBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    aspectBar.isHidden = true
    videoAspectSubBar = aspectBar

    let layerSlider = UISlider()
    layerSlider.isHidden = true
    layerSlider.addTarget(self, action: #selector(videoLayerPropertyChanged(_:)), for: .valueChanged)
    videoLayerPropertySlider = layerSlider

    let layerBar = makeVideoLayerToolBar(session: session, textColor: textColor, primaryColor: primaryColor, toolbarColor: toolbarColor)
    layerBar.isHidden = true
    videoLayerToolBar = layerBar

    let toolbar = makeVideoToolBar(session: session, playerController: playerController, textColor: textColor, toolbarColor: toolbarColor)
    videoToolBar = toolbar

    let root = UIStackView(arrangedSubviews: [header, preview, transport, position, trim, stripScroll, aspectBar, layerSlider, layerBar, toolbar])
    root.axis = .vertical
    root.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(root)
    NSLayoutConstraint.activate([
      root.leadingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.leadingAnchor),
      root.trailingAnchor.constraint(equalTo: view.safeAreaLayoutGuide.trailingAnchor),
      root.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
      root.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),
      header.heightAnchor.constraint(equalToConstant: 60),
      transport.heightAnchor.constraint(equalToConstant: 40),
      trim.heightAnchor.constraint(equalToConstant: 48),
      stripScroll.heightAnchor.constraint(equalToConstant: 40),
      aspectBar.heightAnchor.constraint(equalToConstant: 56),
      layerBar.heightAnchor.constraint(equalToConstant: 56),
      toolbar.heightAnchor.constraint(equalToConstant: 72),
    ])

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
    positionPollTimer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
      guard let self, let session = self.videoSession else { return }
      if !self.seekingPosition, session.durationMs > 0 {
        let currentMs = Int64(CMTimeGetSeconds(session.player.currentTime()) * 1000)
        self.positionSlider?.value = Float(min(max(Double(currentMs) / Double(session.durationMs), 0), 1)) * 1000
        self.updateTimeLabel()
      }
      self.playPauseButton?.setTitle(session.player.timeControlStatus == .playing ? "Pause" : "Play", for: .normal)
      self.refreshVideoOverlayPreview(session: session)
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
  private func computeVideoLetterboxBounds(session: VideoEditSession, in view: UIView) -> CGRect {
    guard let clip = session.clips.first, let track = session.asset(for: clip).tracks(withMediaType: .video).first else { return view.bounds }
    let naturalSize = track.naturalSize.applying(track.preferredTransform)
    let videoWidth = abs(naturalSize.width)
    let videoHeight = abs(naturalSize.height)
    let viewWidth = view.bounds.width
    let viewHeight = view.bounds.height
    guard videoWidth > 0, videoHeight > 0, viewWidth > 0, viewHeight > 0 else { return view.bounds }
    let scale = min(viewWidth / videoWidth, viewHeight / videoHeight)
    let fittedWidth = videoWidth * scale
    let fittedHeight = videoHeight * scale
    let x = (viewWidth - fittedWidth) / 2
    let y = (viewHeight - fittedHeight) / 2
    return CGRect(x: x, y: y, width: fittedWidth, height: fittedHeight)
  }

  /// Mirrors `onLayerStackChanged` for the video overlay stack (Milestone 6).
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
    selectedVideoLayerID = id
    videoLayerOverlay?.selectedLayerID = id
    setVideoLayerToolBarVisible(id != nil, session: session)
  }

  private func setVideoLayerToolBarVisible(_ visible: Bool, session: VideoEditSession) {
    videoLayerToolBar?.isHidden = !visible
    videoLayerPropertySlider?.isHidden = !visible
    videoToolBar?.isHidden = visible
    if visible { syncVideoLayerPropertySlider(session: session) }
  }

  private func currentSelectedVideoLayer(session: VideoEditSession) -> PhotoLayer? {
    session.layerStack.layers.first { $0.id == selectedVideoLayerID }
  }

  private func syncVideoLayerPropertySlider(session: VideoEditSession) {
    guard let slider = videoLayerPropertySlider, let layer = currentSelectedVideoLayer(session: session) else { return }
    let (min, max, value): (CGFloat, CGFloat, CGFloat)
    switch activeVideoLayerPropertyKey {
    case "rotation": (min, max, value) = (-180, 180, layer.rotationDegrees)
    case "opacity": (min, max, value) = (0, 100, layer.opacity * 100)
    case "start": (min, max, value) = (0, CGFloat(session.durationMs), CGFloat(layer.startMs))
    case "end":
      let effectiveEnd = (layer.endMs > 0 && layer.endMs <= session.durationMs) ? layer.endMs : session.durationMs
      (min, max, value) = (0, CGFloat(session.durationMs), CGFloat(effectiveEnd))
    default: (min, max, value) = (20, 800, layer.scale * 100)
    }
    slider.minimumValue = Float(min)
    slider.maximumValue = Float(max)
    slider.value = Float(value)
  }

  @objc private func videoLayerPropertyChanged(_ slider: UISlider) {
    guard let session = videoSession, let id = selectedVideoLayerID else { return }
    let value = CGFloat(slider.value)
    let key = activeVideoLayerPropertyKey
    session.layerStack.updateLive { list in
      list.map { layer in
        guard layer.id == id else { return layer }
        var updated = layer
        switch key {
        case "rotation": updated.rotationDegrees = value
        case "opacity": updated.opacity = value / 100
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
    let properties: [(String, String)] = [("scale", "Scale"), ("rotation", "Rotate"), ("opacity", "Opacity"), ("start", "Start"), ("end", "End")]
    let bar = UIStackView(); bar.axis = .horizontal; bar.alignment = .center
    let scroll = UIScrollView()
    let chipStack = UIStackView(); chipStack.axis = .horizontal; chipStack.spacing = 4
    properties.forEach { key, label in
      let chip = button(label, color: textColor) { [weak self] in
        self?.activeVideoLayerPropertyKey = key
        if let self { self.syncVideoLayerPropertySlider(session: session) }
        if let self { self.refreshSelection(self.videoLayerPropertyButtons, activeKey: self.activeVideoLayerPropertyKey, textColor: textColor, primaryColor: primaryColor) }
      }
      videoLayerPropertyButtons[key] = chip
      chipStack.addArrangedSubview(chip)
    }
    chipStack.addArrangedSubview(button("Delete", color: textColor) { [weak self] in
      guard let self, let id = self.selectedVideoLayerID else { return }
      session.layerStack.commit { $0.filter { $0.id != id } }
      self.selectVideoLayer(nil, session: session)
      self.onVideoLayerStackChanged()
    })
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
    bar.addArrangedSubview(button("Done", color: primaryColor) { [weak self] in self?.selectVideoLayer(nil, session: session) })
    bar.backgroundColor = toolbarColor
    bar.isLayoutMarginsRelativeArrangement = true
    bar.layoutMargins = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
    return bar
  }

  private func showVideoTextInputDialog(session: VideoEditSession) {
    let alert = UIAlertController(title: "Add text", message: nil, preferredStyle: .alert)
    alert.addTextField { $0.placeholder = "Text" }
    alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
    alert.addAction(UIAlertAction(title: "Add", style: .default) { [weak self, weak alert] _ in
      guard let text = alert?.textFields?.first?.text, !text.trimmingCharacters(in: .whitespaces).isEmpty else { return }
      var layer = PhotoLayer(type: .text)
      layer.text = text
      session.layerStack.commit { $0 + [layer] }
      self?.onVideoLayerStackChanged()
      self?.selectVideoLayer(layer.id, session: session)
    })
    present(alert, animated: true)
  }

  @objc private func positionSliderTouchDown(_ slider: UISlider) { seekingPosition = true }

  @objc private func positionSliderTouchUp(_ slider: UISlider) {
    seekingPosition = false
    guard let session = videoSession, session.durationMs > 0 else { return }
    let targetMs = Double(slider.value / 1000) * Double(session.durationMs)
    session.player.seek(to: CMTime(value: Int64(targetMs), timescale: 1000))
  }

  @objc private func positionSliderChanged(_ slider: UISlider) {
    guard let session = videoSession, session.durationMs > 0 else { return }
    let targetMs = Double(slider.value / 1000) * Double(session.durationMs)
    session.player.seek(to: CMTime(value: Int64(targetMs), timescale: 1000))
  }

  private func makeVideoToolBar(session: VideoEditSession, playerController: AVPlayerViewController, textColor: UIColor, toolbarColor: UIColor) -> UIView {
    let tools: [(String, String)] = [
      ("cover", "Cover"), ("speed", "Speed 1x"), ("crop", "Crop"), ("rotate", "Rotate"),
      ("filters", "Filters"), ("text", "Text"), ("stickers", "Stickers"), ("overlay", "Overlay"),
    ]
    let scroll = UIScrollView(); scroll.backgroundColor = toolbarColor
    let stack = UIStackView(); stack.axis = .horizontal; stack.spacing = 8
    tools.filter { features[$0.0] as? Bool != false }.forEach { key, label in
      let toolButton = button(label, color: textColor) { [weak self, weak playerController] in
        self?.onVideoToolTapped(key, session: session, playerView: playerController?.view)
      }
      videoToolButtons[key] = toolButton
      stack.addArrangedSubview(toolButton)
    }
    scroll.addSubview(stack)
    stack.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      stack.leadingAnchor.constraint(equalTo: scroll.contentLayoutGuide.leadingAnchor, constant: 12),
      stack.trailingAnchor.constraint(equalTo: scroll.contentLayoutGuide.trailingAnchor, constant: -12),
      stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor),
      stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor),
      stack.heightAnchor.constraint(equalTo: scroll.frameLayoutGuide.heightAnchor),
    ])
    return scroll
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
    case "cover":
      let atMs = Int64(CMTimeGetSeconds(session.player.currentTime()) * 1000)
      session.update { $0.coverFrameMs = atMs }
      showToast("Cover frame set at \(formatMs(atMs))")
    case "rotate":
      session.update { $0.rotateRight() }
      applyVideoPreviewTransform(playerView, state: session.state)
    case "crop":
      setVideoAspectMode(true, session: session)
    case "speed":
      session.update { $0.cycleSpeed() }
      if session.player.timeControlStatus == .playing { session.player.rate = session.state.speed }
      videoToolButtons["speed"]?.setTitle(formatSpeedLabel(session.state.speed), for: .normal)
    case "text":
      showVideoTextInputDialog(session: session)
    case "stickers":
      showVideoStickerPicker(session: session)
    case "overlay":
      presentImagePicker(purpose: .videoOverlay)
    default:
      showToast("\(key) is coming in a later milestone.")
    }
  }

  private func showVideoStickerPicker(session: VideoEditSession) {
    let alert = UIAlertController(title: "Add sticker", message: nil, preferredStyle: .actionSheet)
    PhotoLayerRenderer.builtinStickerIDs().forEach { id in
      alert.addAction(UIAlertAction(title: PhotoLayerRenderer.glyph(for: id), style: .default) { [weak self] _ in
        var layer = PhotoLayer(type: .sticker)
        layer.stickerId = id
        session.layerStack.commit { $0 + [layer] }
        self?.onVideoLayerStackChanged()
      })
    }
    consumerStickerAssets().forEach { asset in
      alert.addAction(UIAlertAction(title: asset.id, style: .default) { [weak self] _ in
        var layer = PhotoLayer(type: .sticker)
        layer.stickerUri = asset.uri
        session.layerStack.commit { $0 + [layer] }
        self?.onVideoLayerStackChanged()
      })
    }
    alert.addAction(UIAlertAction(title: "Upload", style: .default) { [weak self] _ in
      self?.presentImagePicker(purpose: .videoSticker)
    })
    if features["onlineStickers"] as? Bool != false {
      alert.addAction(UIAlertAction(title: "Browse online", style: .default) { [weak self] _ in
        self?.presentOnlineStickerSheet(purpose: .videoSticker)
      })
    }
    alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
    if let popover = alert.popoverPresentationController {
      popover.sourceView = view
      popover.sourceRect = CGRect(x: view.bounds.midX, y: view.bounds.maxY - 1, width: 1, height: 1)
    }
    present(alert, animated: true)
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
    title.font = .systemFont(ofSize: 16, weight: .bold)
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
      undoButton = undo
      redoButton = redo
      header.addArrangedSubview(undo)
      header.addArrangedSubview(redo)
      let compare = iconButton(systemName: "rectangle.split.2x1", tint: textColor, handler: nil)
      compare.accessibilityLabel = "Hold to compare with original"
      compare.addTarget(self, action: #selector(compareTouchDown), for: .touchDown)
      compare.addTarget(self, action: #selector(compareTouchUp), for: [.touchUpInside, .touchUpOutside, .touchCancel])
      header.addArrangedSubview(compare)
    } else if mediaType == "video" {
      let undo = iconButton(systemName: "arrow.uturn.backward", tint: textColor) { [weak self] in
        self?.videoSession?.layerStack.undo()
        self?.onVideoLayerStackChanged()
      }
      let redo = iconButton(systemName: "arrow.uturn.forward", tint: textColor) { [weak self] in
        self?.videoSession?.layerStack.redo()
        self?.onVideoLayerStackChanged()
      }
      undoButton = undo
      redoButton = redo
      header.addArrangedSubview(undo)
      header.addArrangedSubview(redo)
    }

    let onPrimaryColor = color("onPrimaryColor") ?? DesignTokens.onPrimaryContainer
    let export = EditorComponents.primaryButton(
      text: "Export",
      systemImage: "square.and.arrow.up",
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

  /// Coalesces slider and drag callbacks so bitmap composition runs at most once per display frame.
  private func schedulePhotoPreviewRender() {
    guard !photoPreviewRenderPending else { return }
    photoPreviewRenderPending = true
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      self.photoPreviewRenderPending = false
      guard self.viewIfLoaded?.window != nil,
            let session = self.photoSession,
            let imageView = self.photoImageView else { return }
      autoreleasepool { imageView.image = session.renderPreview() }
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
