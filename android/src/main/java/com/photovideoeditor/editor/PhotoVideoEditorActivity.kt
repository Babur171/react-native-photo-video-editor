package com.photovideoeditor.editor

import com.photovideoeditor.R
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.photovideoeditor.files.SourceResolver
import com.photovideoeditor.photo.export.PhotoExportException
import com.photovideoeditor.photo.export.PhotoExporter
import com.photovideoeditor.photo.render.OverlayGeometry
import com.photovideoeditor.photo.render.LayerType
import com.photovideoeditor.photo.render.PhotoAdjustments
import com.photovideoeditor.photo.render.PhotoEditSession
import com.photovideoeditor.photo.render.PhotoFilterPresets
import com.photovideoeditor.photo.render.PhotoLayer
import com.photovideoeditor.photo.render.PhotoLayerRenderer
import com.photovideoeditor.photo.render.PhotoTransformState
import com.photovideoeditor.photo.ui.CropOverlayView
import com.photovideoeditor.photo.ui.DrawOverlayView
import com.photovideoeditor.photo.ui.FilterThumbnailLoader
import com.photovideoeditor.video.export.VideoExportException
import com.photovideoeditor.video.export.VideoExporter
import com.photovideoeditor.video.cache.TimelineThumbnailRepository
import com.photovideoeditor.video.render.VideoClip
import com.photovideoeditor.video.render.VideoEditSession
import com.photovideoeditor.video.ui.TrimRangeView
import com.photovideoeditor.photo.ui.LayerOverlayView
import com.photovideoeditor.photo.ui.PhotoPreviewRenderCoordinator
import com.photovideoeditor.photo.ui.ZoomableImageView
import com.photovideoeditor.stickers.OnlineStickerSheet
import com.photovideoeditor.stickers.RuntimeSticker
import com.photovideoeditor.ui.DesignTokens
import com.photovideoeditor.ui.components.CropPanel
import com.photovideoeditor.ui.components.EditorFilterThumbnail
import com.photovideoeditor.ui.components.EditorPanelMetrics
import com.photovideoeditor.ui.components.EditorStripItem
import com.photovideoeditor.ui.components.EditorToolSlider
import com.photovideoeditor.ui.components.applyPressScale
import com.photovideoeditor.ui.components.editorChip
import com.photovideoeditor.ui.components.editorFilterThumbnail
import com.photovideoeditor.ui.components.editorPanelHeader
import com.photovideoeditor.ui.components.editorPrimaryButton
import com.photovideoeditor.ui.components.editorStripItem
import com.photovideoeditor.ui.components.editorToolStrip
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

class PhotoVideoEditorActivity : Activity() {
  private val sourceUri by lazy { intent.getStringExtra(EXTRA_URI).orEmpty() }
  private val mediaType by lazy { intent.getStringExtra(EXTRA_TYPE).orEmpty() }
  private val request by lazy { JSONObject(intent.getStringExtra(EXTRA_REQUEST).orEmpty()) }
  private val theme by lazy { request.optJSONObject("theme") }

  private var photoSession: PhotoEditSession? = null
  private var cropMode = false
  private var cropEntryState: PhotoTransformState? = null
  private var photoCropPanel: CropPanel? = null
  private var adjustMode = false
  private var filtersMode = false
  private var drawMode = false
  private var stickersMode = false
  private lateinit var stickersSubBar: View
  private var activeAdjustmentKey = "brightness"
  private var selectedLayerId: String? = null
  private var activeLayerPropertyKey = ""
  private var dragStartSnapshot: List<PhotoLayer>? = null
  private lateinit var photoImageView: ZoomableImageView
  private lateinit var cropOverlay: CropOverlayView
  private lateinit var layerOverlay: LayerOverlayView
  private lateinit var drawOverlay: DrawOverlayView
  private lateinit var straightenSeekBar: SeekBar
  private lateinit var cropSubBar: View
  /** Drives whichever adjustment is selected in the Adjust panel. */
  private lateinit var adjustmentSeekBar: EditorToolSlider
  /** Filter strength, owned by the Filters panel (a View can only have one parent, so not shared). */
  private lateinit var filterIntensitySeekBar: EditorToolSlider
  private lateinit var filtersSubBar: View
  private lateinit var adjustSubBar: View
  private lateinit var drawSubBar: View
  private lateinit var layerToolBar: View
  private lateinit var layerPropertySeekBar: SeekBar
  private lateinit var layerColorSwatchRow: View
  private lateinit var mainToolBar: View
  private lateinit var undoButton: ImageButton
  private lateinit var redoButton: ImageButton
  private lateinit var exportProgressView: View
  private var photoPreviewRenderer: PhotoPreviewRenderCoordinator? = null
  private val filterThumbnailLoader = FilterThumbnailLoader()
  private var comparingOriginal = false

  private var videoSession: VideoEditSession? = null
  private var videoExporter: VideoExporter? = null
  private var videoAspectMode = false
  private var videoCropMode = false
  private var videoCropEntryState: PhotoTransformState? = null
  private var videoCropPanel: CropPanel? = null
  private lateinit var videoCropOverlay: CropOverlayView
  private lateinit var cropPlayerView: PlayerView
  private lateinit var trimRangeView: TrimRangeView
  private lateinit var positionSeekBar: SeekBar
  private lateinit var playPauseButton: ImageButton
  private lateinit var timeLabel: TextView
  private lateinit var videoAspectSubBar: View
  private lateinit var videoToolBar: View
  private lateinit var videoExportProgressView: View
  private lateinit var videoExportCancelButton: Button
  private lateinit var videoOverlayImageView: ImageView
  private var videoOverlayBitmap: Bitmap? = null
  private var videoOverlayRenderKey: String? = null
  private val positionPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
  private var positionPollRunnable: Runnable? = null
  private var seeking = false
  private lateinit var videoControlsOverlay: View
  private var videoControlsVisible = true
  private val hideVideoControlsRunnable = Runnable { setVideoControlsVisible(false) }

  private val photoToolButtons = mutableMapOf<String, ToolNode>()
  private var photoToolScroll: HorizontalScrollView? = null
  private val cropAspectButtons = mutableMapOf<Float?, Button>()
  /** Adjustment key (plus "mirror") -> its entry in the Adjust panel's strip. */
  private val adjustmentStripItems = mutableMapOf<String, EditorStripItem>()
  /** Preset id ("" = Original) -> its thumbnail in the Filters carousel. */
  private val filterPresetCards = mutableMapOf<String, EditorFilterThumbnail>()
  private var resizeMode = false
  private lateinit var resizeSubBar: View
  private var resizePresetSize: Pair<Int, Int>? = null
  private val resizePresetButtons = mutableMapOf<Pair<Int, Int>?, Button>()
  private val layerPropertyButtons = mutableMapOf<String, ToolNode>()
  private val videoToolButtons = mutableMapOf<String, ToolNode>()
  private val videoAspectButtons = mutableMapOf<Float?, Button>()
  private val videoFilterButtons = mutableMapOf<String, Button>()
  private var activeVideoFilterKey = "brightness"
  private var videoFiltersMode = false
  private lateinit var videoFiltersSubBar: View
  private lateinit var videoFilterSeekBar: SeekBar
  private lateinit var videoLayerOverlay: LayerOverlayView
  private lateinit var videoLayerToolBar: View
  private lateinit var videoLayerPropertySeekBar: SeekBar
  private var selectedVideoLayerId: String? = null
  private var activeVideoLayerPropertyKey = ""
  private var videoDragStartSnapshot: List<PhotoLayer>? = null
  private val videoLayerPropertyButtons = mutableMapOf<String, ToolNode>()
  private lateinit var videoColorRow: View
  private val textSwatches = mutableMapOf<Int, View>()
  private val videoTextSwatches = mutableMapOf<Int, View>()
  private var selectedClipIndex = 0
  private lateinit var clipStripBar: LinearLayout
  private var timelineThumbnailRepository: TimelineThumbnailRepository? = null
  private var timelineThumbnailRequest: TimelineThumbnailRepository.Request? = null
  private val videoImageLayerCache = mutableMapOf<String, Bitmap?>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (sourceUri.isBlank() || mediaType !in setOf("photo", "video")) {
      finishWithError("The selected media could not be opened.", "E_INVALID_URI")
      return
    }
    applyStatusBarStyle()
    setContentView(createEditorView())
  }

  private fun applyStatusBarStyle() {
    val isLight = theme?.optString("statusBarStyle") == "dark"
    @Suppress("DEPRECATION")
    window.decorView.systemUiVisibility = if (isLight) {
      window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    } else {
      window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    }
  }

  /** Parses a "#RRGGBB"/"#AARRGGBB" theme string, falling back when absent or malformed. */
  private fun themeColor(key: String, fallback: Int): Int {
    val value = theme?.optString(key)?.takeIf { it.isNotBlank() } ?: return fallback
    return try {
      Color.parseColor(value)
    } catch (_: IllegalArgumentException) {
      fallback
    }
  }

  private fun createEditorView(): View = if (mediaType == "photo") createPhotoEditorView() else createVideoEditorView()

  // ---------------------------------------------------------------------
  // Photo editor: crop, rotate, flip, straighten, and export (Milestone 2).
  // ---------------------------------------------------------------------

  private fun createPhotoEditorView(): View {
    val backgroundColor = themeColor("backgroundColor", DesignTokens.surfaceContainerLowest)
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(backgroundColor)
    }

    val headerHeight = dp(64)
    val header = createTopBar()
    root.addView(header, LinearLayout.LayoutParams(MATCH, headerHeight))

    val previewContainer = FrameLayout(this).apply {
      background = roundedDrawable(Color.BLACK, DesignTokens.radiusLg)
      clipToOutline = true
    }
    root.addView(previewContainer, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
      setMargins(dp(DesignTokens.spaceLg), dp(DesignTokens.spaceSm), dp(DesignTokens.spaceLg), dp(DesignTokens.spaceSm))
    })

    val session = PhotoEditSession(this, sourceUri)
    photoSession = session

    val outer = FrameLayout(this)
    val progressOverlay = FrameLayout(this).apply {
      visibility = View.GONE
      setBackgroundColor(Color.argb(160, 0, 0, 0))
      addView(
        ProgressBar(this@PhotoVideoEditorActivity),
        FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER)
      )
    }
    exportProgressView = progressOverlay
    outer.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
    outer.addView(progressOverlay, FrameLayout.LayoutParams(MATCH, MATCH))

    if (session.baseBitmap == null) {
      finishWithError("The selected photo could not be decoded.", "E_SOURCE_UNREADABLE")
      return outer
    }

    val imageView = ZoomableImageView(this).apply {
      contentDescription = "Selected photo preview"
      setImageBitmap(session.renderPreview())
    }
    photoImageView = imageView
    photoPreviewRenderer = PhotoPreviewRenderCoordinator(
      render = { session.renderPreview(cropMode || resizeMode) },
      display = { bitmap ->
        if (!comparingOriginal && !isFinishing && !isDestroyed) {
          imageView.setImageBitmap(bitmap)
          imageView.resetToFit()
          if (cropMode && ::cropOverlay.isInitialized) cropOverlay.restore(imageView.currentImageBounds(), session.state)
        }
      },
    )
    previewContainer.addView(imageView, FrameLayout.LayoutParams(MATCH, MATCH))

    val overlay = CropOverlayView(this).apply { visibility = View.GONE }
    cropOverlay = overlay
    previewContainer.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

    val layers = LayerOverlayView(this)
    layerOverlay = layers
    previewContainer.addView(layers, FrameLayout.LayoutParams(MATCH, MATCH))

    val drawing = DrawOverlayView(this).apply { visibility = View.GONE }
    drawOverlay = drawing
    previewContainer.addView(drawing, FrameLayout.LayoutParams(MATCH, MATCH))

    imageView.onBoundsChanged = { bounds ->
      if (cropMode) overlay.restore(bounds, session.state)
      layers.setImageBounds(bounds)
      drawing.setImageBounds(bounds)
    }
    overlay.onCropChanged = { left, top, right, bottom -> session.update { it.withCrop(left, top, right, bottom) } }
    overlay.onMediaBoundsChanged = { bounds -> imageView.setRenderedBounds(bounds) }
    overlay.onViewportChanged = { zoom, panX, panY -> session.update { it.copy(zoom = zoom, panX = panX, panY = panY) } }
    layers.onLayerDelete = { id ->
      session.layerStack.commit { list -> list.filterNot { it.id == id } }
      selectLayer(null); onLayerStackChanged()
    }
    layers.onLayerTapped = { id -> selectLayer(id) }
    layers.onLayerDoubleTapped = { id ->
      session.layerStack.layers.firstOrNull { it.id == id && it.type == LayerType.TEXT }?.let { showTextInputDialog(session, it) }
    }
    layers.onLayerTransformChanged = { id, x, y, scale, rotation ->
      if (dragStartSnapshot == null) dragStartSnapshot = session.layerStack.layers
      session.layerStack.updateLive { list ->
        list.map { if (it.id == id) it.copy(x = x, y = y, scale = scale, rotationDegrees = rotation) else it }
      }
      layerOverlay.layers = session.layerStack.layers
      schedulePhotoPreviewRender()
    }
    layers.onLayerTransformEnded = {
      dragStartSnapshot?.let { session.layerStack.commitSnapshot(it) }
      dragStartSnapshot = null
      onLayerStackChanged()
    }
    imageView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
      override fun onGlobalLayout() {
        imageView.viewTreeObserver.removeOnGlobalLayoutListener(this)
        imageView.resetToFit()
      }
    })

    val straighten = SeekBar(this).apply {
      max = 90
      progress = 45
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
          if (!fromUser) return
          session.update { it.withStraighten((value - 45).toFloat()) }
          schedulePhotoPreviewRender()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      })
    }
    straightenSeekBar = straighten


    val cropBar = createCropSubBar(session, imageView, overlay)
    cropSubBar = cropBar
    cropBar.visibility = View.GONE
    root.addView(cropBar, LinearLayout.LayoutParams(MATCH, dp(CROP_SUB_BAR_HEIGHT_DP)))

    val resizeBar = createResizeSubBar(session, imageView, overlay)
    resizeSubBar = resizeBar
    resizeBar.visibility = View.GONE
    root.addView(resizeBar, LinearLayout.LayoutParams(MATCH, dp(56)))

    val accentColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    adjustmentSeekBar = EditorToolSlider(this).apply {
      applyCompactTrack(accentColor)
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          val (min, _) = PhotoAdjustments.rangeFor(activeAdjustmentKey)
          session.updateAdjustments { it.with(activeAdjustmentKey, min + progress) }
          // Coalesced by the preview renderer, so dragging does not queue a render per pixel.
          schedulePhotoPreviewRender()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) { refreshAdjustSelection(session) }
      })
    }
    filterIntensitySeekBar = EditorToolSlider(this).apply {
      applyCompactTrack(accentColor)
      labelText = "Intensity"
      valueFormatter = { progress -> "$progress" }
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          session.updateAdjustments { it.copy(filterStrength = progress.toFloat()) }
          schedulePhotoPreviewRender()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      })
    }

    val adjustBar = createAdjustSubBar(session, imageView)
    adjustSubBar = adjustBar
    adjustBar.visibility = View.GONE
    root.addView(adjustBar, LinearLayout.LayoutParams(MATCH, dp(ADJUST_SUB_BAR_HEIGHT_DP)))

    val filtersBar = createFiltersSubBar(session, imageView)
    filtersSubBar = filtersBar
    filtersBar.visibility = View.GONE
    root.addView(filtersBar, LinearLayout.LayoutParams(MATCH, dp(FILTERS_SUB_BAR_HEIGHT_DP)))

    val stickersBar = createStickersSubBar(session)
    stickersSubBar = stickersBar
    stickersBar.visibility = View.GONE
    root.addView(stickersBar, LinearLayout.LayoutParams(MATCH, dp(ASSET_GRID_BAR_HEIGHT_DP)))

    val drawBar = createDrawSubBar(session)
    drawSubBar = drawBar
    drawBar.visibility = View.GONE
    root.addView(drawBar, LinearLayout.LayoutParams(MATCH, dp(56)))

    val layerPropertySeek = EditorToolSlider(this).apply {
      progressTintList = android.content.res.ColorStateList.valueOf(themeColor("primaryColor", DesignTokens.primaryContainer))
      thumbTintList = progressTintList
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          onLayerPropertyChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) { dragStartSnapshot = session.layerStack.layers.map { it.copy() } }
        override fun onStopTrackingTouch(seekBar: SeekBar?) { dragStartSnapshot?.let { session.layerStack.commitSnapshot(it) }; dragStartSnapshot = null; onLayerStackChanged() }
      })
    }
    layerPropertySeekBar = layerPropertySeek
    root.addView(layerPropertySeek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(16), dp(4), dp(16), 0) })

    val colorRow = createTextColorSwatchRow(session)
    layerColorSwatchRow = colorRow
    colorRow.visibility = View.GONE
    root.addView(colorRow, LinearLayout.LayoutParams(MATCH, dp(48)))

    val layerBar = createLayerToolBar(session)
    layerToolBar = layerBar
    layerBar.visibility = View.GONE
    root.addView(layerBar, LinearLayout.LayoutParams(MATCH, dp(76)))

    val toolbarHeight = dp(76)
    val toolbar = createPhotoToolBar(session, imageView, overlay)
    mainToolBar = toolbar
    root.addView(toolbar, LinearLayout.LayoutParams(MATCH, toolbarHeight))

    applySystemBarInsets(
      header,
      headerHeight,
      listOf(
        toolbar to toolbarHeight,
        cropBar to dp(CROP_SUB_BAR_HEIGHT_DP),
        adjustBar to dp(ADJUST_SUB_BAR_HEIGHT_DP),
        resizeBar to dp(56),
        filtersBar to dp(FILTERS_SUB_BAR_HEIGHT_DP),
        stickersBar to dp(ASSET_GRID_BAR_HEIGHT_DP),
        drawBar to dp(56),
        layerBar to dp(76)
      )
    )
    return outer
  }

  /** Full Studio Violet tool rail (see main_photo_editor_default_state mockup). Rotate lives inside the Crop sub-bar, not as a top-level tool. */
  private val photoToolDefinitions = listOf(
    Triple("crop", "Crop", R.drawable.ic_crop),
    Triple("adjust", "Adjust", R.drawable.ic_tune),
    Triple("filters", "Filters", R.drawable.ic_photo_filter),
    Triple("text", "Text", R.drawable.ic_title),
    Triple("stickers", "Stickers", R.drawable.ic_sentiment_satisfied),
    Triple("draw", "Draw", R.drawable.ic_draw),
    Triple("resize", "Resize", R.drawable.ic_aspect_ratio),
    Triple("compare", "Original", R.drawable.ic_compare)
  )

  /**
   * Main tool dock. All eight tools stay available; rather than compressing them onto one screen
   * (tiny icons, wrapped labels), items are sized so roughly five fill the width and the rest are a
   * scroll away.
   */
  private fun createPhotoToolBar(session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView): LinearLayout {
    val backgroundColor = themeColor("toolbarColor", DesignTokens.withAlphaPercent(DesignTokens.surfaceContainerLowest, 95))
    val itemWidth = photoToolItemWidth()
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(backgroundColor)

      val features = request.optJSONObject("features")
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        photoToolScroll = this
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          photoToolDefinitions.filter { features?.optBoolean(it.first, true) != false }.forEach { (key, label, iconRes) ->
            val node = createToolNode(iconRes, label) { onPhotoToolTapped(key, session, imageView, overlay) }
            if (key == "compare") {
              node.root.contentDescription = "Hold to compare with original"
              node.root.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                  MotionEvent.ACTION_DOWN -> showOriginalPreview(true)
                  MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showOriginalPreview(false)
                }
                true
              }
            }
            photoToolButtons[key] = node
            addView(node.root, LinearLayout.LayoutParams(itemWidth, WRAP))
          }
        })
      }, LinearLayout.LayoutParams(MATCH, WRAP))
    }
  }

  /**
   * Width that shows about five tools at once, so the dock fills the screen evenly on a small phone
   * and does not leave 40dp gaps on a large one. Clamped so labels stay on one line either way.
   */
  private fun photoToolItemWidth(): Int =
    (resources.displayMetrics.widthPixels / 5).coerceIn(dp(64), dp(88))

  private fun refreshPhotoToolSelection() {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    photoToolButtons.forEach { (key, node) ->
      val selected = when (key) {
        "crop" -> cropMode
        "adjust" -> adjustMode
        "filters" -> filtersMode
        "stickers" -> stickersMode
        "draw" -> drawMode
        "resize" -> resizeMode
        else -> false
      }
      setToolNodeSelected(node, selected, textColor)
      if (selected) scrollToolNodeIntoView(node)
    }
  }

  /** Keeps the active tool-rail node fully visible, never partially clipped at the scroll edge. */
  private fun scrollToolNodeIntoView(node: ToolNode) {
    val scroll = photoToolScroll ?: return
    scroll.post {
      val left = node.root.left
      val right = node.root.right
      when {
        left < scroll.scrollX -> scroll.smoothScrollTo(left, 0)
        right > scroll.scrollX + scroll.width -> scroll.smoothScrollTo(right - scroll.width, 0)
      }
    }
  }

  private fun onPhotoToolTapped(key: String, session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView) {
    if (key != "compare" && comparingOriginal) showOriginalPreview(false)
    when (key) {
      "compare" -> showOriginalPreview(!comparingOriginal)
      "crop" -> setCropMode(true, session, imageView, overlay)
      "adjust" -> setAdjustMode(true, session)
      "filters" -> setFiltersMode(true, session)
      "text" -> showTextInputDialog(session)
      "stickers" -> showStickerBottomSheet(session)
      "draw" -> setDrawMode(true, session)
      "resize" -> setResizeMode(true, session, imageView, overlay)
      else -> Toast.makeText(this, "$key is coming in a later milestone.", Toast.LENGTH_SHORT).show()
    }
  }

  // ---------------------------------------------------------------------
  // Layers: text, stickers, shapes, freehand drawing, selection, and
  // transform/duplicate/reorder/lock/hide/delete with undo/redo (Milestone 4).
  // ---------------------------------------------------------------------

  private fun showTextInputDialog(session: PhotoEditSession, editingLayer: PhotoLayer? = null) {
    showTextEditor(editingLayer) { text ->
      val layer = editingLayer?.copy(text = text) ?: PhotoLayer(type = LayerType.TEXT, text = text)
      session.layerStack.commit { list -> if (editingLayer == null) list + layer else list.map { if (it.id == layer.id) layer else it } }
      selectLayer(layer.id)
      onLayerStackChanged()
    }
  }

  private fun showTextEditor(editingLayer: PhotoLayer?, onSave: (String) -> Unit) {
    val dialog = android.app.Dialog(this)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    val panel = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(16), dp(20), dp(20))
      background = roundedDrawable(DesignTokens.surfaceContainerHigh, 16)
    }
    panel.addView(TextView(this).apply {
      text = if (editingLayer == null) "Add Text" else "Edit Text"
      textSize = 20f
      setTextColor(Color.WHITE)
      setTypeface(typeface, android.graphics.Typeface.BOLD)
    })
    val input = EditText(this).apply {
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
      hint = "Type something..."
      setHintTextColor(DesignTokens.outline)
      setTextColor(Color.WHITE)
      textSize = 22f
      gravity = Gravity.TOP or Gravity.START
      minLines = 2; maxLines = 6
      setPadding(dp(12), dp(12), dp(12), dp(12))
      background = roundedDrawable(DesignTokens.surfaceContainerLow, 12)
      setText(editingLayer?.text.orEmpty())
      setSelection(text.length)
    }
    panel.addView(input, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(16); bottomMargin = dp(12) })
    val actions = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
    actions.addView(actionButton("Cancel") { dialog.dismiss() }, LinearLayout.LayoutParams(dp(88), dp(48)))
    actions.addView(editorPrimaryButton(this, if (editingLayer == null) "Add" else "Done", fillColor = themeColor("primaryColor", DesignTokens.primaryContainer)) {
      val text = input.text.toString()
      if (text.isNotBlank()) { onSave(text); dialog.dismiss() }
    }, LinearLayout.LayoutParams(dp(88), dp(48)))
    panel.addView(actions, LinearLayout.LayoutParams(MATCH, WRAP))
    val scroll = android.widget.ScrollView(this).apply { isFillViewport = true; addView(panel) }
    dialog.setContentView(scroll)
    dialog.window?.apply {
      setBackgroundDrawableResource(android.R.color.transparent)
      addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
      setDimAmount(0.7f)
      setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }
    dialog.setOnShowListener {
      dialog.window?.setLayout(MATCH, WRAP)
      dialog.window?.setGravity(Gravity.BOTTOM)
      input.requestFocus()
      input.post { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
    }
    dialog.show()
  }

  private fun setStickersMode(enabled: Boolean, session: PhotoEditSession) {
    stickersMode = enabled
    stickersSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled || selectedLayerId != null) View.GONE else View.VISIBLE
    refreshPhotoToolSelection()
  }

  private fun newSticker(stickerId: String? = null, stickerUri: String? = null): PhotoLayer {
    val bounds = if (mediaType == "photo") photoImageView.currentImageBounds()
      else videoSession?.let { computeVideoLetterboxBounds(it, videoOverlayImageView) }
    val scale = bounds?.takeIf { it.width() > 0 && it.height() > 0 }?.let {
      OverlayGeometry.initialStickerScale(it.width(), it.height())
    } ?: (0.3f / 0.18f)
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    stickerUri?.let { uri ->
      SourceResolver.resolvePath(this, uri, "pve_sticker_bounds")?.let { BitmapFactory.decodeFile(it, options) }
    }
    val aspect = if (options.outWidth > 0 && options.outHeight > 0) options.outWidth.toFloat() / options.outHeight else 1f
    return PhotoLayer(type = LayerType.STICKER, stickerId = stickerId, stickerUri = stickerUri, scale = scale, overlayAspectRatio = aspect)
  }

  private fun showStickerBottomSheet(session: PhotoEditSession) {
    stickersMode = true
    refreshPhotoToolSelection()
    OnlineStickerSheet(this, runtimeStickerAssets()) { path ->
      val layer = newSticker(stickerUri = Uri.fromFile(File(path)).toString())
      session.layerStack.commit { it + layer }
      selectLayer(layer.id)
    }.apply {
      setOnDismissListener {
        stickersMode = false
        refreshPhotoToolSelection()
      }
    }.show()
  }

  /** One asset card in the Stickers library grid (see stickers_shapes_library mockup). */
  private fun createAssetCard(
    faceView: View,
    captionText: String?,
    faceParams: FrameLayout.LayoutParams = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER),
    onClick: () -> Unit
  ): LinearLayout {
    val cardSize = dp(64)
    val face = FrameLayout(this).apply {
      background = roundedDrawable(DesignTokens.surfaceContainer, DesignTokens.radiusLg)
      addView(faceView, faceParams)
    }
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      isClickable = true
      setPadding(dp(4), 0, dp(4), 0)
      addView(face, LinearLayout.LayoutParams(cardSize, cardSize))
      if (captionText != null) {
        addView(TextView(this@PhotoVideoEditorActivity).apply {
          text = captionText
          textSize = 10f
          maxLines = 1
          gravity = Gravity.CENTER
          setTextColor(DesignTokens.outline)
          setPadding(0, dp(DesignTokens.spaceXs), 0, 0)
        }, LinearLayout.LayoutParams(dp(72), WRAP))
      }
      setOnClickListener { onClick() }
    }
  }

  private fun createStickersSubBar(session: PhotoEditSession): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val consumerAssets = request.optJSONArray("stickerAssets")
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), dp(8), dp(12), dp(8))
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          PhotoLayerRenderer.builtinStickerIds().forEach { id ->
            val emoji = TextView(this@PhotoVideoEditorActivity).apply { text = PhotoLayerRenderer.glyphFor(id); textSize = 26f }
            addView(
              createAssetCard(emoji, null) {
                val layer = newSticker(stickerId = id)
                session.layerStack.commit { it + layer }
                selectLayer(layer.id)
                setStickersMode(false, session)
              },
              LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) }
            )
          }
          if (consumerAssets != null) {
            for (i in 0 until consumerAssets.length()) {
              val asset = consumerAssets.optJSONObject(i) ?: continue
              val uri = asset.optString("uri").takeIf { it.isNotBlank() } ?: continue
              val id = asset.optString("id", "asset$i")
              val icon = ImageView(this@PhotoVideoEditorActivity).apply {
                setImageResource(R.drawable.ic_sentiment_satisfied)
                setColorFilter(DesignTokens.tertiary)
              }
              addView(
                createAssetCard(icon, id) {
                  val layer = newSticker(stickerUri = uri)
                  session.layerStack.commit { it + layer }
                  selectLayer(layer.id)
                  setStickersMode(false, session)
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) }
              )
            }
          }
          val uploadIcon = ImageView(this@PhotoVideoEditorActivity).apply {
            setImageResource(R.drawable.ic_wallpaper)
            setColorFilter(DesignTokens.tertiary)
          }
          addView(
            createAssetCard(uploadIcon, "Upload") {
              launchOverlayPicker(REQUEST_CODE_STICKER_UPLOAD)
            },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) }
          )
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { setStickersMode(false, session) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  private fun setDrawMode(enabled: Boolean, session: PhotoEditSession) {
    drawMode = enabled
    drawOverlay.visibility = if (enabled) View.VISIBLE else View.GONE
    drawSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    photoImageView.panZoomEnabled = !enabled
    if (enabled) {
      drawOverlay.strokeColor = Color.RED
      drawOverlay.strokeWidthPx = dp(4).toFloat()
      drawOverlay.setImageBounds(photoImageView.currentImageBounds())
    } else {
      commitDrawStrokes(session)
      drawOverlay.clearStrokes()
    }
    refreshPhotoToolSelection()
  }

  private fun commitDrawStrokes(session: PhotoEditSession) {
    if (!drawOverlay.hasStrokes) return
    val bounds = photoImageView.currentImageBounds()
    val shortSide = minOf(bounds.width(), bounds.height())
    if (shortSide <= 0f) return
    val newLayers = drawOverlay.normalizedStrokes().mapNotNull { (points, stroke) ->
      if (points.size < 2) return@mapNotNull null
      val centerX = points.sumOf { it.first.toDouble() }.toFloat() / points.size
      val centerY = points.sumOf { it.second.toDouble() }.toFloat() / points.size
      PhotoLayer(
        type = LayerType.DRAWING,
        x = centerX,
        y = centerY,
        drawColor = stroke.color,
        drawStrokeWidth = stroke.widthPx / shortSide,
        drawPoints = points.map { (x, y) -> (x - centerX) to (y - centerY) }
      )
    }
    if (newLayers.isNotEmpty()) session.layerStack.commit { it + newLayers }
    onLayerStackChanged()
  }

  private fun createDrawSubBar(session: PhotoEditSession): LinearLayout {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val colors = listOf(Color.RED, Color.rgb(255, 214, 51), Color.rgb(76, 217, 100), Color.rgb(10, 132, 255), Color.WHITE, Color.BLACK)
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(themeColor("toolbarColor", DesignTokens.surfaceContainerLow))
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
          colors.forEach { color -> addView(createColorSwatch(color) { drawOverlay.strokeColor = color }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(6) }) }
          addView(actionButton("Thin") { drawOverlay.strokeWidthPx = dp(2).toFloat() }.apply { setTextColor(textColor) })
          addView(actionButton("Thick") { drawOverlay.strokeWidthPx = dp(10).toFloat() }.apply { setTextColor(textColor) })
          addView(actionButton("Undo") { drawOverlay.undoLastStroke() }.apply { setTextColor(textColor) })
          addView(actionButton("Clear") { drawOverlay.clearStrokes() }.apply { setTextColor(textColor) })
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(actionButton("Done") { setDrawMode(false, session) }.apply { setTextColor(primaryColor) }, LinearLayout.LayoutParams(dp(72), MATCH))
    }
  }

  /** Launches the SAF image picker for either the sticker-upload or overlay-upload flow, dispatched in [onActivityResult]. */
  private fun launchOverlayPicker(requestCode: Int) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
    try {
      startActivityForResult(intent, requestCode)
    } catch (_: Exception) {
      Toast.makeText(this, "No app available to pick an image.", Toast.LENGTH_SHORT).show()
    }
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (resultCode != Activity.RESULT_OK) return
    val pickedUri = data?.data?.toString() ?: return
    val path = SourceResolver.resolvePath(this, pickedUri, "pve_picked_image") ?: run {
      Toast.makeText(this, "The selected image could not be opened.", Toast.LENGTH_SHORT).show()
      return
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val aspectRatio = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
      bounds.outWidth.toFloat() / bounds.outHeight.toFloat()
    } else 1f
    val resolvedUri = Uri.fromFile(File(path)).toString()
    when (requestCode) {
      REQUEST_CODE_STICKER_UPLOAD -> {
        val session = photoSession ?: return
        val layer = newSticker(stickerUri = resolvedUri)
        session.layerStack.commit { it + layer }
        selectLayer(layer.id)
        setStickersMode(false, session)
      }
      REQUEST_CODE_OVERLAY_UPLOAD -> {
        val session = photoSession ?: return
        val layer = PhotoLayer(
          type = LayerType.OVERLAY,
          overlayUri = resolvedUri,
          overlayAspectRatio = aspectRatio,
          x = 0.5f,
          y = 0.5f,
          scale = 1f
        )
        session.layerStack.commit { it + layer }
        selectLayer(layer.id)
      }
      REQUEST_CODE_VIDEO_STICKER_UPLOAD -> {
        val session = videoSession ?: return
        val layer = newSticker(stickerUri = resolvedUri)
        session.layerStack.commit { it + layer }
        onVideoLayerStackChanged()
        selectVideoLayer(layer.id, session)
      }
      REQUEST_CODE_VIDEO_OVERLAY_UPLOAD -> {
        val session = videoSession ?: return
        val layer = PhotoLayer(
          type = LayerType.OVERLAY,
          overlayUri = resolvedUri,
          overlayAspectRatio = aspectRatio,
          x = 0.5f,
          y = 0.5f,
          scale = 1f
        )
        session.layerStack.commit { it + layer }
        onVideoLayerStackChanged()
        selectVideoLayer(layer.id, session)
      }
    }
  }

  private fun selectLayer(id: String?) {
    val session = photoSession
    if (selectedLayerId != id) activeLayerPropertyKey = ""
    selectedLayerId = id
    layerOverlay.selectedLayerId = id
    if (session != null) {
      layerOverlay.layers = session.layerStack.layers
      schedulePhotoPreviewRender()
    }
    if (id != null) {
      if (drawMode && session != null) setDrawMode(false, session)
      if (cropMode && session != null) cancelCropMode(session, photoImageView, cropOverlay)
      adjustMode = false; filtersMode = false; stickersMode = false; resizeMode = false
      listOf(adjustSubBar, filtersSubBar, stickersSubBar, resizeSubBar, adjustmentSeekBar).forEach { it.visibility = View.GONE }
    }
    setLayerToolBarVisible(id != null)
  }

  private fun setLayerToolBarVisible(visible: Boolean) {
    layerToolBar.visibility = if (visible) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (visible) View.GONE else View.VISIBLE
    val isText = currentSelectedLayer()?.type == LayerType.TEXT
    val simple = isText || currentSelectedLayer()?.type == LayerType.STICKER
    listOf("scale", "lock", "visibility", "front", "back").forEach { layerPropertyButtons[it]?.root?.visibility = if (simple) View.GONE else View.VISIBLE }
    listOf("edit", "color", "fontSize").forEach { layerPropertyButtons[it]?.root?.visibility = if (isText) View.VISIBLE else View.GONE }
    if (!isText && activeLayerPropertyKey in setOf("color", "fontSize")) activeLayerPropertyKey = ""
    layerPropertySeekBar.visibility = if (visible && activeLayerPropertyKey in setOf("scale", "rotation", "opacity", "fontSize")) View.VISIBLE else View.GONE
    layerColorSwatchRow.visibility = if (visible && activeLayerPropertyKey == "color") View.VISIBLE else View.GONE
    if (visible) syncLayerPropertySeekBar()
    textSwatches.forEach { (color, view) -> view.background = roundedDrawable(DesignTokens.surfaceContainerHigh, 24).apply { if (color == currentSelectedLayer()?.textColor) setStroke(dp(2), themeColor("primaryColor", DesignTokens.primaryContainer)) } }
    refreshToolNodeSelection(layerPropertyButtons, activeLayerPropertyKey, Color.WHITE)
  }

  private fun createColorSwatch(color: Int, onClick: () -> Unit): FrameLayout {
    val outer = FrameLayout(this).apply {
      background = circleDrawable(DesignTokens.surfaceContainerHigh)
      isClickable = true
      setOnClickListener { onClick() }
    }
    val inner = View(this).apply { background = circleDrawable(color) }
    outer.addView(inner, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
    return outer
  }

  /** Text color swatch row, shown when a TEXT layer's "Color" property is active (text_editor_typography_studio mockup). */
  private fun createTextColorSwatchRow(session: PhotoEditSession): LinearLayout = createTextPalette(false) { color ->
    val id = selectedLayerId ?: return@createTextPalette
    session.layerStack.commit { list -> list.map { if (it.id == id) it.copy(textColor = color) else it } }
    onLayerStackChanged()
    setLayerToolBarVisible(true)
  }

  private fun createTextPalette(video: Boolean, onColor: (Int) -> Unit): LinearLayout = LinearLayout(this).apply {
    setBackgroundColor(themeColor("toolbarColor", DesignTokens.surfaceContainerLow))
    val swatches = if (video) videoTextSwatches else textSwatches
    addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
      isHorizontalScrollBarEnabled = false
      addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        listOf(Color.WHITE, Color.BLACK, Color.rgb(255, 77, 94), Color.rgb(255, 155, 61), Color.rgb(255, 214, 51), Color.rgb(76, 217, 100), Color.rgb(0, 145, 255), DesignTokens.primary, Color.rgb(255, 105, 180)).forEach { color ->
          val swatch = createColorSwatch(color) { onColor(color) }
          swatch.contentDescription = "Text color #" + Integer.toHexString(color).takeLast(6)
          swatches[color] = swatch
          addView(swatch, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
      })
    }, LinearLayout.LayoutParams(MATCH, dp(48)))
  }

  private fun currentSelectedLayer(): PhotoLayer? {
    val session = photoSession ?: return null
    return session.layerStack.layers.firstOrNull { it.id == selectedLayerId }
  }

  private fun syncLayerPropertySeekBar() {
    val layer = currentSelectedLayer() ?: return
    val (min, max, value) = when (activeLayerPropertyKey) {
      "rotation" -> Triple(-180f, 180f, ((layer.rotationDegrees % 360f + 540f) % 360f) - 180f)
      "opacity" -> Triple(0f, 100f, layer.opacity * 100f)
      "fontSize" -> Triple(12f, 160f, layer.fontSize)
      else -> Triple(20f, 800f, layer.scale * 100f)
    }
    layerPropertySeekBar.max = (max - min).roundToInt()
    layerPropertySeekBar.progress = (value - min).roundToInt()
    (layerPropertySeekBar as? EditorToolSlider)?.propertyKey = activeLayerPropertyKey
  }

  private fun onLayerPropertyChanged(progress: Int) {
    val session = photoSession ?: return
    val id = selectedLayerId ?: return
    val min = when (activeLayerPropertyKey) {
      "rotation" -> -180f
      "opacity" -> 0f
      "fontSize" -> 12f
      else -> 20f
    }
    val value = min + progress
    session.layerStack.updateLive { list ->
      list.map { layer ->
        if (layer.id != id) return@map layer
        when (activeLayerPropertyKey) {
          "rotation" -> layer.copy(rotationDegrees = value)
          "opacity" -> layer.copy(opacity = value / 100f)
          "fontSize" -> layer.copy(fontSize = value)
          else -> layer.copy(scale = value / 100f)
        }
      }
    }
    layerOverlay.layers = session.layerStack.layers
    schedulePhotoPreviewRender()
  }

  private fun createLayerToolBar(session: PhotoEditSession): LinearLayout = createContextToolbar(false) { key ->
    val layer = currentSelectedLayer() ?: return@createContextToolbar
    when (key) {
      "edit" -> showTextInputDialog(session, layer)
      "done" -> selectLayer(null)
      "delete" -> { session.layerStack.commit { it.filterNot { item -> item.id == layer.id } }; selectLayer(null); onLayerStackChanged() }
      "duplicate" -> {
        val copy = layer.copy(id = java.util.UUID.randomUUID().toString(), x = (layer.x + 0.04f).coerceIn(0f, 1f), y = (layer.y + 0.04f).coerceIn(0f, 1f))
        session.layerStack.commit { it + copy }; selectLayer(copy.id); onLayerStackChanged()
      }
      "lock", "visibility", "front", "back" -> {
        session.layerStack.commit { list -> when (key) {
          "front" -> list.filterNot { it.id == layer.id } + layer
          "back" -> listOf(layer) + list.filterNot { it.id == layer.id }
          else -> list.map { if (it.id != layer.id) it else if (key == "lock") it.copy(locked = !it.locked) else it.copy(visible = !it.visible) }
        } }; onLayerStackChanged()
      }
      else -> { activeLayerPropertyKey = if (activeLayerPropertyKey == key) "" else key; setLayerToolBarVisible(true) }
    }
  }

  private fun createContextToolbar(video: Boolean, onAction: (String) -> Unit): LinearLayout {
    val nodes = if (video) videoLayerPropertyButtons else layerPropertyButtons
    val actions = mutableListOf(
      Triple("edit", "Edit", R.drawable.ic_draw), Triple("color", "Color", R.drawable.ic_palette),
      Triple("fontSize", "Size", R.drawable.ic_format_size), Triple("rotation", "Rotate", R.drawable.ic_rotate_right),
      Triple("opacity", "Opacity", R.drawable.ic_opacity), Triple("scale", "Scale", R.drawable.ic_fullscreen),
      Triple("duplicate", "Duplicate", R.drawable.ic_content_copy), Triple("delete", "Delete", R.drawable.ic_delete)
    )
    if (!video) actions.addAll(listOf(Triple("lock", "Lock", R.drawable.ic_lock), Triple("visibility", "Hide", R.drawable.ic_visibility_off), Triple("front", "Front", R.drawable.ic_chevron_right), Triple("back", "Back", R.drawable.ic_chevron_left)))
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setBackgroundColor(themeColor("toolbarColor", DesignTokens.surfaceContainerLow))
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          gravity = Gravity.CENTER_VERTICAL
          actions.forEach { (key, label, icon) ->
            val node = createToolNode(icon, label) { onAction(key) }
            nodes[key] = node
            if (key == "delete") { node.icon.setColorFilter(Color.rgb(255, 100, 115)); node.label.setTextColor(Color.rgb(255, 100, 115)) }
            addView(node.root, LinearLayout.LayoutParams(dp(72), WRAP))
          }
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      val done = createToolNode(R.drawable.ic_check, "Done") { onAction("done") }
      setToolNodeSelected(done, true, Color.WHITE)
      addView(done.root, LinearLayout.LayoutParams(dp(64), WRAP))
    }
  }

  private fun setAdjustMode(enabled: Boolean, session: PhotoEditSession) {
    adjustMode = enabled
    // The slider now lives inside the panel, so showing the panel shows it — one less thing to
    // keep in sync, and no stray full-width slider row left behind above the main dock.
    adjustSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    // Entering Adjust selects a control to scrub but never resets the values themselves, so
    // Adjust -> Filters -> Adjust round-trips keep whatever the user already dialled in.
    if (enabled) { activeAdjustmentKey = "brightness"; syncAdjustmentSeekBar(session); fadeInPanel(adjustSubBar) }
    refreshPhotoToolSelection()
    refreshAdjustSelection(session)
  }

  /** Filters — preset thumbnail carousel with an intensity slider above it. */
  private fun setFiltersMode(enabled: Boolean, session: PhotoEditSession) {
    filtersMode = enabled
    filtersSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    if (enabled) { syncAdjustmentSeekBar(session); fadeInPanel(filtersSubBar) }
    refreshPhotoToolSelection()
    refreshFiltersSelection(session)
  }

  /** Short cross-fade when a panel opens, per the "subtle transition" spec (no slide/scale drama). */
  private fun fadeInPanel(panel: View) {
    panel.animate().cancel()
    panel.alpha = 0f
    panel.animate().alpha(1f).setDuration(180).start()
  }

  private fun refreshAdjustSelection(session: PhotoEditSession) {
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val defaults = PhotoAdjustments()
    adjustmentStripItems.forEach { (key, item) ->
      if (key == "mirror") {
        // A toggle, so "selected" means "on" rather than "being scrubbed".
        item.setSelected(session.adjustments.mirror, primaryColor)
        item.setModified(false, primaryColor)
      } else {
        item.setSelected(key == activeAdjustmentKey, primaryColor)
        item.setModified(session.adjustments.value(key) != defaults.value(key), primaryColor)
      }
    }
  }

  private fun refreshFiltersSelection(session: PhotoEditSession) {
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val activePreset = session.adjustments.filterPreset ?: ""
    filterPresetCards.forEach { (id, thumbnail) -> thumbnail.setSelected(id == activePreset, primaryColor) }
    // Intensity only means something once a preset is applied.
    filterIntensitySeekBar.visibility = if (activePreset.isEmpty()) View.INVISIBLE else View.VISIBLE
  }

  /** Points whichever slider is on screen at the currently selected value. */
  private fun syncAdjustmentSeekBar(session: PhotoEditSession) {
    val (min, max) = PhotoAdjustments.rangeFor(activeAdjustmentKey)
    adjustmentSeekBar.max = (max - min).roundToInt()
    adjustmentSeekBar.progress = (session.adjustments.value(activeAdjustmentKey) - min).roundToInt()
    adjustmentSeekBar.labelText = photoAdjustmentDefinitions.firstOrNull { it.first == activeAdjustmentKey }?.second
    adjustmentSeekBar.valueFormatter = { progress -> formatAdjustmentValue(activeAdjustmentKey, min + progress) }
    // Bipolar adjustments get a centre tick at their neutral value; unipolar ones (blur) do not.
    adjustmentSeekBar.neutralProgress = if (min < 0f) (-min).roundToInt() else null

    val (strengthMin, strengthMax) = PhotoAdjustments.rangeFor("filterStrength")
    filterIntensitySeekBar.max = (strengthMax - strengthMin).roundToInt()
    filterIntensitySeekBar.progress = (session.adjustments.filterStrength - strengthMin).roundToInt()
  }

  private fun formatAdjustmentValue(key: String, value: Float): String {
    val rounded = value.roundToInt()
    return if (key == "blurRadius") "$rounded" else if (rounded > 0) "+$rounded" else "$rounded"
  }

  /**
   * Adjustments the processing engine actually implements, in carousel order. Deliberately not a
   * wishlist: every entry maps to a real [PhotoAdjustments] field, so there are no controls that
   * move a slider without changing the photo. (Highlights/shadows/tint/sharpness were removed from
   * the engine in an earlier pass and would need that processing restored before they could return.)
   */
  private val photoAdjustmentDefinitions = listOf(
    Triple("exposure", "Exposure", R.drawable.ic_flare),
    Triple("brightness", "Brightness", R.drawable.ic_light_mode),
    Triple("contrast", "Contrast", R.drawable.ic_opacity),
    Triple("saturation", "Saturation", R.drawable.ic_palette),
    Triple("temperature", "Warmth", R.drawable.ic_compare),
    Triple("blurRadius", "Blur", R.drawable.ic_filter_center_focus)
  )

  /**
   * Adjust panel: `Adjust / Reset / Done` header, one shared slider bound to whichever adjustment is
   * selected, then a compact scrollable strip. Replaces the previous row of full-height rectangular
   * buttons, which wrapped "Brightness" onto two lines and gave `Done` a quarter of the screen.
   */
  private fun createAdjustSubBar(session: PhotoEditSession, imageView: ZoomableImageView): LinearLayout {
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(DesignTokens.editorSurface)
      addView(
        editorPanelHeader(
          context = this@PhotoVideoEditorActivity,
          title = "Adjust",
          accentColor = primaryColor,
          secondaryLabel = "Reset",
          onSecondary = {
            session.updateAdjustments {
              it.copy(brightness = 0f, contrast = 0f, saturation = 0f, exposure = 0f, temperature = 0f, blurRadius = 0f, mirror = false)
            }
            syncAdjustmentSeekBar(session)
            schedulePhotoPreviewRender()
            refreshAdjustSelection(session)
          },
          onDone = { setAdjustMode(false, session) }
        ),
        LinearLayout.LayoutParams(MATCH, dp(EditorPanelMetrics.HEADER_HEIGHT_DP))
      )
      addView(adjustmentSeekBar, LinearLayout.LayoutParams(MATCH, dp(EditorPanelMetrics.SLIDER_HEIGHT_DP)))
      addView(
        editorToolStrip(this@PhotoVideoEditorActivity) {
          photoAdjustmentDefinitions.forEach { (key, label, iconRes) ->
            val item = editorStripItem(this@PhotoVideoEditorActivity, iconRes, label) {
              activeAdjustmentKey = key
              syncAdjustmentSeekBar(session)
              refreshAdjustSelection(session)
            }
            adjustmentStripItems[key] = item
            addView(item.root, LinearLayout.LayoutParams(dp(EditorPanelMetrics.STRIP_ITEM_WIDTH_DP), WRAP))
          }
          // Mirror is a toggle, not a slider target, but it lives in the same strip so the panel has
          // one visual language rather than a stray button.
          val mirror = editorStripItem(this@PhotoVideoEditorActivity, R.drawable.ic_aspect_ratio, "Mirror") {
            session.updateAdjustments { it.copy(mirror = !it.mirror) }
            schedulePhotoPreviewRender()
            refreshAdjustSelection(session)
          }
          adjustmentStripItems["mirror"] = mirror
          addView(mirror.root, LinearLayout.LayoutParams(dp(EditorPanelMetrics.STRIP_ITEM_WIDTH_DP), WRAP))
        },
        LinearLayout.LayoutParams(MATCH, dp(EditorPanelMetrics.STRIP_HEIGHT_DP))
      )
    }
  }

  /**
   * Filters panel: `Filters / Done` header, an intensity slider that appears once a preset is
   * applied, then the preset carousel. Same header/slider/strip skeleton as Adjust so the two read
   * as one system.
   */
  private fun createFiltersSubBar(session: PhotoEditSession, imageView: ZoomableImageView): LinearLayout {
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(DesignTokens.editorSurface)
      addView(
        editorPanelHeader(
          context = this@PhotoVideoEditorActivity,
          title = "Filters",
          accentColor = primaryColor,
          onDone = { setFiltersMode(false, session) }
        ),
        LinearLayout.LayoutParams(MATCH, dp(EditorPanelMetrics.HEADER_HEIGHT_DP))
      )
      addView(filterIntensitySeekBar, LinearLayout.LayoutParams(MATCH, dp(EditorPanelMetrics.SLIDER_HEIGHT_DP)))
      addView(
        editorToolStrip(this@PhotoVideoEditorActivity) {
          val original = editorFilterThumbnail(this@PhotoVideoEditorActivity, "Original") {
            session.updateAdjustments { it.copy(filterPreset = null, filterStrength = 100f) }
            syncAdjustmentSeekBar(session)
            schedulePhotoPreviewRender()
            refreshFiltersSelection(session)
          }
          original.image.setImageBitmap(session.baseBitmap)
          filterPresetCards[""] = original
          addView(original.root, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) })
          PhotoFilterPresets.PRESET_IDS.forEach { id ->
            val thumbnail = editorFilterThumbnail(this@PhotoVideoEditorActivity, PhotoFilterPresets.labelFor(id)) {
              session.updateAdjustments { it.copy(filterPreset = if (it.filterPreset == id) null else id) }
              syncAdjustmentSeekBar(session)
              schedulePhotoPreviewRender()
              refreshFiltersSelection(session)
            }
            // Thumbnails render off a cached downscaled copy of the photo, never the full-resolution
            // bitmap — 17 presets at full size would stall the panel on open.
            filterThumbnailLoader.load(session.baseBitmap, id, dp(EditorPanelMetrics.THUMBNAIL_WIDTH_DP)) {
              thumbnail.image.setImageBitmap(it)
            }
            filterPresetCards[id] = thumbnail
            addView(thumbnail.root, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) })
          }
        },
        LinearLayout.LayoutParams(MATCH, dp(EditorPanelMetrics.STRIP_HEIGHT_DP + 24))
      )
    }
  }

  private fun setCropMode(enabled: Boolean, session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView) {
    if (enabled && !cropMode) cropEntryState = session.state
    cropMode = enabled
    imageView.panZoomEnabled = !enabled
    overlay.visibility = if (enabled) View.VISIBLE else View.GONE
    cropSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    if (!enabled) cropEntryState = null
    photoCropPanel?.sync(session.state)
    schedulePhotoPreviewRender()
    refreshPhotoToolSelection()
  }

  private fun cancelCropMode(session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView) {
    cropEntryState?.let { snapshot -> session.update { snapshot } }
    setCropMode(false, session, imageView, overlay)
  }

  private fun createCropSubBar(session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView): LinearLayout {
    fun refresh() { photoCropPanel?.sync(session.state); schedulePhotoPreviewRender() }
    return CropPanel(this, themeColor("primaryColor", DesignTokens.primaryContainer), themeColor("toolbarColor", DesignTokens.surfaceContainerLow), themeColor("textColor", DesignTokens.onSurface),
      originalRatio = { session.baseBitmap?.let { it.width.toFloat() / it.height } ?: 1f },
      onReset = { session.update { it.reset() }; refresh() },
      onDone = { setCropMode(false, session, imageView, overlay) },
      onCancel = { cancelCropMode(session, imageView, overlay) },
      onStraighten = { value -> session.update { it.withStraighten(value) }; refresh() },
      onRotate = {
        session.update { it.rotatedRight().copy(zoom = 1f, panX = 0f, panY = 0f, aspectRatio = null, aspectPreset = "Free") }; refresh()
      },
      onRatio = { label, ratio ->
        session.update { it.copy(aspectRatio = ratio, aspectPreset = label) }
        overlay.setAspectRatio(ratio)
        photoCropPanel?.sync(session.state)
      }
    ).also { photoCropPanel = it; it.sync(session.state) }
  }

  private fun setResizeMode(enabled: Boolean, session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView) {
    resizeMode = enabled
    imageView.panZoomEnabled = !enabled
    overlay.visibility = if (enabled) View.VISIBLE else View.GONE
    resizeSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    if (enabled) {
      imageView.resetToFit()
      overlay.setImageBounds(imageView.currentImageBounds(), resetCrop = false)
      overlay.setAspectRatio(session.state.aspectRatio)
    }
    refreshPhotoToolSelection()
    refreshSelection(resizePresetButtons, resizePresetSize, themeColor("textColor", DesignTokens.onSurface), themeColor("primaryColor", DesignTokens.primaryContainer))
  }

  private fun createResizeSubBar(session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    // (label, aspect ratio for cropping, exact export pixel size or null for "Original" = no forced resize)
    val presets = listOf<Triple<String, Float?, Pair<Int, Int>?>>(
      Triple("Original", null, null),
      Triple("IG Post", 4f / 5f, 1080 to 1350),
      Triple("IG Story", 9f / 16f, 1080 to 1920),
      Triple("IG Square", 1f, 1080 to 1080),
      Triple("YouTube", 16f / 9f, 1280 to 720),
      Triple("Twitter", 3f / 1f, 1500 to 500)
    )
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          presets.forEach { (label, ratio, size) ->
            val chip = actionButton(label) {
              overlay.setAspectRatio(ratio)
              session.update { it.withAspectRatio(ratio) }
              resizePresetSize = size
              refreshSelection(resizePresetButtons, size, textColor, primaryColor)
            }.apply { setTextColor(textColor) }
            resizePresetButtons[size] = chip
            addView(chip, LinearLayout.LayoutParams(dp(80), MATCH))
          }
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { setResizeMode(false, session, imageView, overlay) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  private fun exportPhoto() {
    val session = photoSession
    if (session == null) {
      finishWithError("Nothing to export.", "E_INTERNAL")
      return
    }
    if (drawMode) setDrawMode(false, session)
    exportProgressView.visibility = View.VISIBLE
    val state = session.state
    val adjustments = session.adjustments
    val layers = session.layerStack.layers
    // A chosen Resize/social preset overrides the consumer's own maxWidth/maxHeight, since the
    // user explicitly picked an exact output size (matching the crop aspect ratio set alongside it).
    val exportOptions = (request.optJSONObject("export") ?: JSONObject()).apply {
      resizePresetSize?.let { (width, height) -> put("maxWidth", width); put("maxHeight", height) }
    }
    Thread {
      try {
        val result = PhotoExporter.export(applicationContext, sourceUri, state, adjustments, layers, exportOptions)
        runOnUiThread {
          val intent = Intent()
            .putExtra(EXTRA_URI, result.uri)
            .putExtra(EXTRA_TYPE, mediaType)
            .putExtra(EXTRA_MIME_TYPE, result.mimeType)
            .putExtra(EXTRA_WIDTH, result.width)
            .putExtra(EXTRA_HEIGHT, result.height)
            .putExtra(EXTRA_FILE_SIZE, result.fileSize)
          setResult(RESULT_OK, intent)
          finish()
        }
      } catch (error: PhotoExportException) {
        runOnUiThread { finishWithError(error.message ?: "Unable to export the photo.", error.code) }
      } catch (error: Exception) {
        runOnUiThread { finishWithError("Unable to export the photo.", "E_EXPORT_FAILED") }
      }
    }.start()
  }

  // ---------------------------------------------------------------------
  // Video editor: preview, trim, mute, cover frame, rotate/flip/aspect,
  // and H.264/AAC MP4 export (Milestone 5). Overlays/filters are Milestone 6+.
  // ---------------------------------------------------------------------

  private fun createVideoEditorView(): View {
    val backgroundColor = themeColor("backgroundColor", DesignTokens.surfaceContainerLowest)
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(backgroundColor)
    }

    val headerHeight = dp(64)
    val header = createTopBar()
    root.addView(header, LinearLayout.LayoutParams(MATCH, headerHeight))

    val preview = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
    root.addView(
      preview,
      LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
        setMargins(0, dp(DesignTokens.spaceXs), 0, dp(DesignTokens.spaceXs))
      }
    )

    val session = VideoEditSession(this, sourceUri)
    videoSession = session

    val outer = FrameLayout(this)
    val progress = FrameLayout(this).apply {
      visibility = View.GONE
      setBackgroundColor(Color.argb(160, 0, 0, 0))
      val column = LinearLayout(this@PhotoVideoEditorActivity).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
      }
      column.addView(ProgressBar(this@PhotoVideoEditorActivity))
      val cancelButton = Button(this@PhotoVideoEditorActivity).apply {
        text = "Cancel export"
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { cancelVideoExport() }
      }
      videoExportCancelButton = cancelButton
      column.addView(cancelButton)
      addView(column, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
    }
    videoExportProgressView = progress
    outer.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
    outer.addView(progress, FrameLayout.LayoutParams(MATCH, MATCH))

    val playerView = PlayerView(this).apply {
      player = session.player
      useController = false
      contentDescription = "Selected video preview"
    }
    cropPlayerView = playerView
    preview.addView(playerView, FrameLayout.LayoutParams(MATCH, MATCH))

    val overlayView = ImageView(this).apply {
      contentDescription = "Video overlays"
      // Match PlayerView's default RESIZE_MODE_FIT letterboxing so the overlay bitmap (sized to the
      // video's own aspect ratio) lines up with the actual displayed video frame, not the full view.
      scaleType = ImageView.ScaleType.FIT_CENTER
    }
    videoOverlayImageView = overlayView
    preview.addView(overlayView, FrameLayout.LayoutParams(MATCH, MATCH))

    videoCropOverlay = CropOverlayView(this).apply {
      visibility = View.GONE
      onCropChanged = { l, t, r, b -> session.update { it.copy(crop = it.crop.withCrop(l, t, r, b)) } }
      onViewportChanged = { zoom, x, y -> session.update { it.copy(crop = it.crop.copy(zoom = zoom, panX = x, panY = y)) } }
      onMediaBoundsChanged = { bounds ->
        val fitted = videoMediaBounds(session, playerView, false)
        if (fitted.width() > 0) {
          playerView.pivotX = playerView.width / 2f; playerView.pivotY = playerView.height / 2f
          playerView.scaleX = bounds.width() / fitted.width(); playerView.scaleY = playerView.scaleX
          playerView.translationX = bounds.centerX() - fitted.centerX(); playerView.translationY = bounds.centerY() - fitted.centerY()
        }
      }
    }
    preview.addView(videoCropOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
    preview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      if (videoCropMode) videoCropOverlay.restore(videoMediaBounds(session, playerView, false), session.state.crop)
    }
    val layerOverlay = LayerOverlayView(this)
    videoLayerOverlay = layerOverlay
    preview.addView(layerOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
    layerOverlay.onLayerDelete = { id ->
      session.layerStack.commit { list -> list.filterNot { it.id == id } }
      selectVideoLayer(null, session); onVideoLayerStackChanged()
    }
    layerOverlay.onLayerDoubleTapped = { id ->
      session.layerStack.layers.firstOrNull { it.id == id && it.type == LayerType.TEXT }?.let { showVideoTextInputDialog(session, it) }
    }
    layerOverlay.onLayerTapped = { id -> selectVideoLayer(id, session) }
    layerOverlay.onLayerTransformChanged = { id, x, y, scale, rotation ->
      if (videoDragStartSnapshot == null) videoDragStartSnapshot = session.layerStack.layers
      session.layerStack.updateLive { list ->
        list.map { if (it.id == id) it.copy(x = x, y = y, scale = scale, rotationDegrees = rotation) else it }
      }
      videoLayerOverlay.layers = session.layerStack.layers
      refreshVideoOverlayPreview(session)
    }
    layerOverlay.onLayerTransformEnded = {
      videoDragStartSnapshot?.let { session.layerStack.commitSnapshot(it) }
      videoDragStartSnapshot = null
      onVideoLayerStackChanged()
    }

    val accentColor = themeColor("primaryColor", DesignTokens.primaryContainer)

    val playPause = ImageButton(this).apply {
      contentDescription = "Play video"
      setImageResource(R.drawable.ic_play)
      setColorFilter(Color.WHITE)
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      background = android.graphics.drawable.InsetDrawable(circleDrawable(Color.argb(140, 0, 0, 0)), dp(2))
      setPadding(dp(9), dp(9), dp(9), dp(9))
      setOnClickListener {
        if (session.player.isPlaying) {
          session.player.pause()
        } else {
          if (session.player.playbackState == Player.STATE_ENDED || session.globalPositionMs() >= session.durationMs - 50L) {
            session.seekToGlobalMs(0)
          }
          session.player.play()
        }
        setVideoControlsVisible(true)
      }
      applyPressScale()
    }
    playPauseButton = playPause
    val time = TextView(this).apply {
      text = "0:00 / 0:00"
      setTextColor(Color.WHITE)
      textSize = 11f
    }
    timeLabel = time
    val transport = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      addView(playPause, LinearLayout.LayoutParams(dp(44), dp(44)))
      addView(time, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(DesignTokens.spaceSm) })
    }

    val seekTrack = android.graphics.drawable.LayerDrawable(
      arrayOf(
        roundedDrawable(Color.argb(90, 255, 255, 255), 999),
        android.graphics.drawable.ClipDrawable(roundedDrawable(accentColor, 999), Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
      )
    ).apply {
      setId(0, android.R.id.background)
      setId(1, android.R.id.progress)
      setLayerInset(0, 0, dp(9), 0, dp(9))
      setLayerInset(1, 0, dp(9), 0, dp(9))
    }
    val seek = SeekBar(this).apply {
      contentDescription = "Playback position"
      progressDrawable = seekTrack
      thumb = circleDrawable(Color.WHITE).apply { setSize(dp(12), dp(12)) }
      splitTrack = false
      max = 1000
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser || session.durationMs <= 0) return
          session.seekToGlobalMs(session.durationMs * progress / 1000)
          updateTimeLabel()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) { seeking = true; setVideoControlsVisible(true) }
        override fun onStopTrackingTouch(seekBar: SeekBar?) { seeking = false; scheduleAutoHideVideoControls(session) }
      })
    }
    positionSeekBar = seek

    val controlsOverlay = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.argb(150, 0, 0, 0))
      setPadding(dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs), dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs))
      addView(transport, LinearLayout.LayoutParams(WRAP, WRAP))
      addView(seek, LinearLayout.LayoutParams(MATCH, dp(24)))
    }
    videoControlsOverlay = controlsOverlay
    preview.addView(controlsOverlay, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
    preview.isClickable = true
    preview.setOnClickListener { toggleVideoControls(session) }

    val trim = TrimRangeView(this).apply {
      // Bound to the currently selected clip (Milestone 7): fractions are relative to that clip's
      // own `originalDurationMs`, not the composed multi-clip timeline.
      onRangeChanged = { startFraction, endFraction ->
        session.clips.getOrNull(selectedClipIndex)?.let { clip ->
          val newStart = (startFraction * clip.originalDurationMs).toLong()
          val newEnd = (endFraction * clip.originalDurationMs).toLong()
          session.updateClips { clips ->
            clips.mapIndexed { index, c -> if (index == selectedClipIndex) c.copy(trimStartMs = newStart, trimEndMs = newEnd) else c }
          }
          updateTimeLabel()
          refreshClipStrip(session)
        }
      }
    }
    trimRangeView = trim
    // Single-video editor: keep the internal range initialized for export,
    // but do not show the trim/clip timeline UI.

    val strip = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    // Keep the internal container initialized for the single-clip session,
    // but do not add the multi-clip strip/actions to the video editor UI.
    clipStripBar = strip

    session.onClipsReady = {
      runOnUiThread {
        if (isFinishing || isDestroyed) return@runOnUiThread
        selectedClipIndex = 0
        updateTimeLabel()
        bindTrimViewToSelectedClip(session)
        refreshClipStrip(session)
      }
    }
    session.onPlaybackError = { message ->
      runOnUiThread {
        if (!isFinishing && !isDestroyed) {
          Toast.makeText(this, message, Toast.LENGTH_LONG).show()
          finishWithError(message, "E_VIDEO_PLAYBACK_FAILED")
        }
      }
    }

    val cropPanel = CropPanel(this, themeColor("primaryColor", DesignTokens.primaryContainer), toolbarColor, textColor,
      originalRatio = { session.sourceWidth.toFloat() / session.sourceHeight.coerceAtLeast(1) },
      onReset = { session.update { it.copy(crop = PhotoTransformState()) }; refreshVideoCrop(session) },
      onDone = { setVideoCropMode(false, session) },
      onCancel = { videoCropEntryState?.let { saved -> session.update { it.copy(crop = saved) } }; setVideoCropMode(false, session) },
      onStraighten = { value -> session.update { it.copy(crop = it.crop.withStraighten(value)) }; refreshVideoCrop(session) },
      onRotate = { session.update { it.copy(crop = it.crop.rotatedRight().copy(zoom = 1f, panX = 0f, panY = 0f, aspectRatio = null, aspectPreset = "Free")) }; refreshVideoCrop(session) },
      onRatio = { label, ratio -> session.update { it.copy(crop = it.crop.copy(aspectRatio = ratio, aspectPreset = label)) }; videoCropOverlay.setAspectRatio(ratio); videoCropPanel?.sync(session.state.crop) }
    ).apply { visibility = View.GONE }
    videoCropPanel = cropPanel
    root.addView(cropPanel, LinearLayout.LayoutParams(MATCH, dp(CROP_SUB_BAR_HEIGHT_DP)))
    val aspectBar = createVideoAspectSubBar(session, playerView)
    aspectBar.visibility = View.GONE
    videoAspectSubBar = aspectBar
    root.addView(aspectBar, LinearLayout.LayoutParams(MATCH, dp(CROP_SUB_BAR_HEIGHT_DP)))

    val filterSeek = SeekBar(this).apply {
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          session.update { it.withFilter(activeVideoFilterKey, (progress - 100).toFloat()) }
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      })
    }
    videoFilterSeekBar = filterSeek
    root.addView(filterSeek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(8), dp(4), dp(8), 0) })

    val filtersBar = createVideoFiltersSubBar(session)
    filtersBar.visibility = View.GONE
    videoFiltersSubBar = filtersBar
    root.addView(filtersBar, LinearLayout.LayoutParams(MATCH, dp(56)))

    val layerPropertySeek = EditorToolSlider(this).apply {
      progressTintList = android.content.res.ColorStateList.valueOf(themeColor("primaryColor", DesignTokens.primaryContainer))
      thumbTintList = progressTintList
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (fromUser) onVideoLayerPropertyChanged(progress, session)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) { videoDragStartSnapshot = session.layerStack.layers.map { it.copy() } }
        override fun onStopTrackingTouch(seekBar: SeekBar?) { videoDragStartSnapshot?.let { session.layerStack.commitSnapshot(it) }; videoDragStartSnapshot = null; onVideoLayerStackChanged() }
      })
    }
    videoLayerPropertySeekBar = layerPropertySeek
    root.addView(layerPropertySeek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(8), dp(4), dp(8), 0) })

    videoColorRow = createTextPalette(true) { color ->
      val id = selectedVideoLayerId ?: return@createTextPalette
      session.layerStack.commit { list -> list.map { if (it.id == id) it.copy(textColor = color) else it } }
      onVideoLayerStackChanged()
      setVideoLayerToolBarVisible(true, session)
    }.apply { visibility = View.GONE }
    root.addView(videoColorRow, LinearLayout.LayoutParams(MATCH, dp(48)))
    val layerBar = createVideoLayerToolBar(session)
    layerBar.visibility = View.GONE
    videoLayerToolBar = layerBar
    root.addView(layerBar, LinearLayout.LayoutParams(MATCH, dp(76)))

    // The dock builds its own horizontal inset and pill; this holder only reserves the row height
    // (plus an 8dp gap) so the system-bar inset lands on transparent space, not on the pill.
    val toolbarHeight = dp(VIDEO_DOCK_BAR_HEIGHT_DP)
    val toolbar = createVideoToolBar(session, playerView)
    videoToolBar = toolbar
    root.addView(toolbar, LinearLayout.LayoutParams(MATCH, toolbarHeight))
    applySystemBarInsets(
      header,
      headerHeight,
      listOf(cropPanel to dp(CROP_SUB_BAR_HEIGHT_DP), toolbar to toolbarHeight, aspectBar to dp(CROP_SUB_BAR_HEIGHT_DP), filtersBar to dp(56), layerBar to dp(76))
    )

    startPositionPolling(session)
    return outer
  }

  private fun startPositionPolling(session: VideoEditSession) {
    var wasPlaying = false
    val runnable = object : Runnable {
      override fun run() {
        if (!seeking && session.durationMs > 0) {
          positionSeekBar.progress = (session.globalPositionMs() * 1000 / session.durationMs).toInt().coerceIn(0, 1000)
          updateTimeLabel()
        }
        val playing = session.player.isPlaying
        playPauseButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        playPauseButton.contentDescription = if (playing) "Pause video" else "Play video"
        if (playing != wasPlaying) {
          wasPlaying = playing
          if (playing) scheduleAutoHideVideoControls(session) else setVideoControlsVisible(true)
        }
        refreshVideoOverlayPreview(session)
        positionPollHandler.postDelayed(this, 250)
      }
    }
    positionPollRunnable = runnable
    positionPollHandler.postDelayed(runnable, 250)
  }

  /** Shows/hides the in-preview playback controls with a subtle fade (spec: auto-hide while playing). */
  private fun setVideoControlsVisible(visible: Boolean) {
    if (!::videoControlsOverlay.isInitialized) return
    positionPollHandler.removeCallbacks(hideVideoControlsRunnable)
    videoControlsVisible = visible
    videoControlsOverlay.animate().cancel()
    if (visible) {
      videoControlsOverlay.visibility = View.VISIBLE
      videoControlsOverlay.animate().alpha(1f).setDuration(200).start()
    } else {
      videoControlsOverlay.animate().alpha(0f).setDuration(200)
        .withEndAction { if (!videoControlsVisible) videoControlsOverlay.visibility = View.INVISIBLE }.start()
    }
  }

  private fun scheduleAutoHideVideoControls(session: VideoEditSession) {
    positionPollHandler.removeCallbacks(hideVideoControlsRunnable)
    if (session.player.isPlaying) positionPollHandler.postDelayed(hideVideoControlsRunnable, 2500)
  }

  private fun toggleVideoControls(session: VideoEditSession) {
    if (videoControlsVisible) {
      setVideoControlsVisible(false)
    } else {
      setVideoControlsVisible(true)
      scheduleAutoHideVideoControls(session)
    }
  }

  private fun updateTimeLabel() {
    val session = videoSession ?: return
    timeLabel.text = "${formatMs(session.globalPositionMs())} / ${formatMs(session.durationMs)}"
  }

  private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
  }

  /**
   * Renders whichever overlays are active at the current playhead onto a
   * transparent bitmap sized to the video's own aspect ratio (so it letterboxes
   * identically to the `PlayerView` beneath it). Reuses [PhotoLayerRenderer]
   * unchanged — overlays are just [PhotoLayer]s with a time range.
   */
  private fun refreshVideoOverlayPreview(session: VideoEditSession) {
    val activeLayers = session.layerStack.layers.filter { it.isActiveAt(session.globalPositionMs(), session.durationMs) }
    videoLayerOverlay.layers = activeLayers
    videoLayerOverlay.setImageBounds(computeVideoLetterboxBounds(session, videoOverlayImageView))
    if (activeLayers.isEmpty()) {
      videoOverlayImageView.setImageBitmap(null)
      videoOverlayBitmap?.takeIf { !it.isRecycled }?.recycle()
      videoOverlayBitmap = null
      videoOverlayRenderKey = null
      return
    }
    // Deliberately NOT `session.player.videoSize`: ExoPlayer's live-reported size is an
    // independent source of truth from `videoOutputSize()` (which drives `computeVideoLetterboxBounds()`
    // above, i.e. the touch/gesture-normalization bounds for `videoLayerOverlay`). The two can disagree
    // (e.g. before the first decoded frame reports a size, or if the effects pipeline republishes a
    // different size under crop), which silently shifts a layer's normalized x/y/scale relative to what
    // the user saw on screen. Sourcing both from the same deterministic function keeps preview (this
    // bitmap) and gesture math in lockstep, and matches what `VideoExporter` computes for the real frame.
    val (sourceWidthF, sourceHeightF) = videoOutputSize(session, cropped = !videoCropMode)
    val sourceWidth = sourceWidthF.roundToInt().coerceAtLeast(1)
    val sourceHeight = sourceHeightF.roundToInt().coerceAtLeast(1)
    val previewScale = minOf(1f, 1280f / maxOf(sourceWidth, sourceHeight))
    val width = (sourceWidth * previewScale).roundToInt().coerceAtLeast(2)
    val height = (sourceHeight * previewScale).roundToInt().coerceAtLeast(2)
    val renderKey = "${session.layerStack.revision}:$width:$height:${activeLayers.joinToString { it.id }}"
    if (renderKey == videoOverlayRenderKey) return
    val transparent = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val rendered = PhotoLayerRenderer.render(transparent, activeLayers, ::resolveVideoImageUri)
    transparent.recycle()
    val previous = videoOverlayBitmap
    videoOverlayBitmap = rendered
    videoOverlayRenderKey = renderKey
    videoOverlayImageView.setImageBitmap(rendered)
    previous?.takeIf { it !== rendered && !it.isRecycled }?.recycle()
  }

  /** Resolves a sticker-uri or overlay-uri layer's image URI for the video overlay preview, caching by URI. */
  private fun resolveVideoImageUri(uri: String): Bitmap? {
    videoImageLayerCache[uri]?.let { return it }
    if (videoImageLayerCache.containsKey(uri)) return null
    val path = SourceResolver.resolvePath(this, uri, "pve_video_layer_image")
    val bitmap = path?.let { BitmapFactory.decodeFile(it) }
    videoImageLayerCache[uri] = bitmap
    return bitmap
  }

  /**
   * Where the video actually renders within [view] under `FIT_CENTER`-style letterboxing (mirrors
   * `ZoomableImageView.resetToFit`'s math). This is the SINGLE source of truth for overlay-relative
   * coordinates: [computeVideoLetterboxBounds] (touch/gesture normalization) and
   * [refreshVideoOverlayPreview] (the bitmap layers actually draw into) both derive their size from
   * here, so a layer's normalized x/y/scale always means the same on-screen place in both.
   *
   * Known gap (not currently reachable from the UI — the video tool rail only exposes Crop/Text/
   * Stickers, not a whole-video "Rotate" tool): [VideoTransformState.rotationDegrees] (the top-level
   * rotate field, distinct from `crop.rotationDegrees` handled below) is applied to `playerView` as a
   * cosmetic `View.rotation` only and is never folded in here, while `VideoExporter` bakes it into the
   * real output frame (swapping width/height for 90/270°). If that tool is ever wired back up, this
   * function's width/height swap must also account for `state.rotationDegrees`, or preview and export
   * will disagree the same way this function's fix for `crop.rotationDegrees` was needed.
   */
  private fun videoOutputSize(session: VideoEditSession, cropped: Boolean): Pair<Float, Float> {
    var w = session.sourceWidth.coerceAtLeast(1).toFloat(); var h = session.sourceHeight.coerceAtLeast(1).toFloat()
    val crop = session.state.crop
    if (crop.rotationDegrees % 180 != 0) { val swap = w; w = h; h = swap }
    return if (cropped) w * (crop.cropRight - crop.cropLeft) to h * (crop.cropBottom - crop.cropTop) else w to h
  }

  private fun videoMediaBounds(session: VideoEditSession, view: View, cropped: Boolean): android.graphics.RectF {
    val (w, h) = videoOutputSize(session, cropped)
    val scale = minOf(view.width / w, view.height / h)
    val x = (view.width - w * scale) / 2; val y = (view.height - h * scale) / 2
    return android.graphics.RectF(x, y, x + w * scale, y + h * scale)
  }

  private fun computeVideoLetterboxBounds(session: VideoEditSession, view: View): android.graphics.RectF = videoMediaBounds(session, view, !videoCropMode)

  private fun refreshVideoCrop(session: VideoEditSession) {
    session.player.setVideoEffects(com.photovideoeditor.video.render.VideoCropEffects.create(session.state.crop, !videoCropMode))
    videoCropPanel?.sync(session.state.crop)
    cropPlayerView.scaleX = 1f; cropPlayerView.scaleY = 1f; cropPlayerView.translationX = 0f; cropPlayerView.translationY = 0f
    if (videoCropMode) videoCropOverlay.restore(videoMediaBounds(session, cropPlayerView, false), session.state.crop)
    videoOverlayRenderKey = null
    refreshVideoOverlayPreview(session)
  }

  private fun setVideoCropMode(enabled: Boolean, session: VideoEditSession) {
    if (enabled && !videoCropMode) {
      videoCropEntryState = session.state.crop
      session.player.pause()
      selectVideoLayer(null, session)
    }
    videoCropMode = enabled
    videoCropPanel?.visibility = if (enabled) View.VISIBLE else View.GONE
    videoCropOverlay.visibility = if (enabled) View.VISIBLE else View.GONE
    videoLayerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    videoOverlayImageView.visibility = if (enabled) View.GONE else View.VISIBLE
    videoToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    if (!enabled) videoCropEntryState = null
    refreshVideoCrop(session)
  }

  private fun onVideoLayerStackChanged() {
    val session = videoSession ?: return
    if (selectedVideoLayerId != null && session.layerStack.layers.none { it.id == selectedVideoLayerId }) {
      selectedVideoLayerId = null
    }
    videoLayerOverlay.selectedLayerId = selectedVideoLayerId
    refreshVideoOverlayPreview(session)
    undoButton.isEnabled = session.layerStack.canUndo
    redoButton.isEnabled = session.layerStack.canRedo
    undoButton.alpha = if (session.layerStack.canUndo) 1f else 0.4f
    redoButton.alpha = if (session.layerStack.canRedo) 1f else 0.4f
    if (selectedVideoLayerId == null && videoLayerToolBar.visibility == View.VISIBLE) setVideoLayerToolBarVisible(false, session)
  }

  private fun selectVideoLayer(id: String?, session: VideoEditSession) {
    if (selectedVideoLayerId != id) activeVideoLayerPropertyKey = ""
    selectedVideoLayerId = id
    videoLayerOverlay.selectedLayerId = id
    setVideoLayerToolBarVisible(id != null, session)
  }

  private fun setVideoLayerToolBarVisible(visible: Boolean, session: VideoEditSession) {
    if (visible) setVideoControlsVisible(true)
    videoLayerToolBar.visibility = if (visible) View.VISIBLE else View.GONE
    videoToolBar.visibility = if (visible) View.GONE else View.VISIBLE
    val isText = currentSelectedVideoLayer(session)?.type == LayerType.TEXT
    videoLayerPropertyButtons["scale"]?.root?.visibility = if (isText || currentSelectedVideoLayer(session)?.type == LayerType.STICKER) View.GONE else View.VISIBLE
    listOf("edit", "color", "fontSize").forEach { videoLayerPropertyButtons[it]?.root?.visibility = if (isText) View.VISIBLE else View.GONE }
    if (!isText && activeVideoLayerPropertyKey in setOf("color", "fontSize")) activeVideoLayerPropertyKey = ""
    videoLayerPropertySeekBar.visibility = if (visible && activeVideoLayerPropertyKey in setOf("scale", "rotation", "opacity", "fontSize")) View.VISIBLE else View.GONE
    videoColorRow.visibility = if (visible && activeVideoLayerPropertyKey == "color") View.VISIBLE else View.GONE
    if (visible) syncVideoLayerPropertySeekBar(session)
    videoTextSwatches.forEach { (color, view) -> view.background = roundedDrawable(DesignTokens.surfaceContainerHigh, 24).apply { if (color == currentSelectedVideoLayer(session)?.textColor) setStroke(dp(2), themeColor("primaryColor", DesignTokens.primaryContainer)) } }
    refreshToolNodeSelection(videoLayerPropertyButtons, activeVideoLayerPropertyKey, Color.WHITE)
  }

  private fun currentSelectedVideoLayer(session: VideoEditSession): PhotoLayer? =
    session.layerStack.layers.firstOrNull { it.id == selectedVideoLayerId }

  private fun syncVideoLayerPropertySeekBar(session: VideoEditSession) {
    val layer = currentSelectedVideoLayer(session) ?: return
    val (min, max, value) = when (activeVideoLayerPropertyKey) {
      "rotation" -> Triple(-180f, 180f, ((layer.rotationDegrees % 360f + 540f) % 360f) - 180f)
      "opacity" -> Triple(0f, 100f, layer.opacity * 100f)
      "fontSize" -> Triple(12f, 160f, layer.fontSize)
      "start" -> Triple(0f, session.durationMs.toFloat(), layer.startMs.toFloat())
      "end" -> Triple(0f, session.durationMs.toFloat(), session.durationMs.effectiveEndOr(layer.endMs).toFloat())
      else -> Triple(20f, 800f, layer.scale * 100f)
    }
    (videoLayerPropertySeekBar as? EditorToolSlider)?.propertyKey = activeVideoLayerPropertyKey
    videoLayerPropertySeekBar.max = (max - min).roundToInt()
    videoLayerPropertySeekBar.progress = (value - min).roundToInt()
  }

  private fun Long.effectiveEndOr(endMs: Long): Long = if (endMs in 1..this) endMs else this

  private fun onVideoLayerPropertyChanged(progress: Int, session: VideoEditSession) {
    val id = selectedVideoLayerId ?: return
    val min = when (activeVideoLayerPropertyKey) {
      "rotation" -> -180f
      "opacity", "start", "end" -> 0f
      "fontSize" -> 12f
      else -> 20f
    }
    val value = min + progress
    session.layerStack.updateLive { list ->
      list.map { layer ->
        if (layer.id != id) return@map layer
        when (activeVideoLayerPropertyKey) {
          "rotation" -> layer.copy(rotationDegrees = value)
          "opacity" -> layer.copy(opacity = value / 100f)
          "fontSize" -> layer.copy(fontSize = value)
          "start" -> layer.copy(startMs = value.toLong().coerceAtMost(layer.endMs.takeIf { it > 0 } ?: session.durationMs))
          "end" -> layer.copy(endMs = value.toLong().coerceAtLeast(layer.startMs))
          else -> layer.copy(scale = value / 100f)
        }
      }
    }
    refreshVideoOverlayPreview(session)
  }

  private fun createVideoLayerToolBar(session: VideoEditSession): LinearLayout = createContextToolbar(true) { key ->
    val layer = currentSelectedVideoLayer(session) ?: return@createContextToolbar
    when (key) {
      "edit" -> showVideoTextInputDialog(session, layer)
      "done" -> selectVideoLayer(null, session)
      "delete" -> { session.layerStack.commit { it.filterNot { item -> item.id == layer.id } }; selectVideoLayer(null, session); onVideoLayerStackChanged() }
      "duplicate" -> {
        val copy = layer.copy(id = java.util.UUID.randomUUID().toString(), x = (layer.x + 0.04f).coerceIn(0f, 1f), y = (layer.y + 0.04f).coerceIn(0f, 1f))
        session.layerStack.commit { it + copy }; selectVideoLayer(copy.id, session); onVideoLayerStackChanged()
      }
      else -> { activeVideoLayerPropertyKey = if (activeVideoLayerPropertyKey == key) "" else key; setVideoLayerToolBarVisible(true, session) }
    }
  }

  private fun showVideoTextInputDialog(session: VideoEditSession, editingLayer: PhotoLayer? = null) {
    showTextEditor(editingLayer) { text ->
      val layer = editingLayer?.copy(text = text) ?: PhotoLayer(type = LayerType.TEXT, text = text)
      session.layerStack.commit { list -> if (editingLayer == null) list + layer else list.map { if (it.id == layer.id) layer else it } }
      onVideoLayerStackChanged()
      selectVideoLayer(layer.id, session)
    }
  }

  /**
   * Compact video dock: two equal-width icon+label actions in one rounded pill. Deliberately not
   * [createToolNode] — that rail stacks a 44dp tile above a label (76dp of chrome), which is far more
   * than two actions warrant. Returns a transparent holder so [applySystemBarInsets] can pad the
   * gesture/nav inset without stretching the pill itself into the system bar.
   */
  private fun createVideoToolBar(session: VideoEditSession, playerView: PlayerView): View {
    val features = request.optJSONObject("features")
    val dock = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      background = roundedDrawable(DesignTokens.surfaceContainer, VIDEO_DOCK_RADIUS_DP)
      listOf("text" to "Text", "stickers" to "Stickers")
        .filter { features?.optBoolean(it.first, true) != false }
        .forEach { (key, label) ->
          addView(
            createVideoDockAction(key, label) { onVideoToolTapped(key, session, playerView) },
            LinearLayout.LayoutParams(0, MATCH, 1f)
          )
        }
    }
    return FrameLayout(this).apply {
      addView(
        dock,
        FrameLayout.LayoutParams(MATCH, dp(VIDEO_DOCK_HEIGHT_DP), Gravity.TOP).apply {
          setMargins(dp(DesignTokens.spaceMd), 0, dp(DesignTokens.spaceMd), 0)
        }
      )
    }
  }

  /** One dock action: icon + label on a single row, purple pill while pressed. */
  private fun createVideoDockAction(key: String, labelText: String, onClick: () -> Unit): LinearLayout {
    val icon: View = if (key == "text") {
      // A literal "Aa" reads as typography far better than R.drawable.ic_title, which is a lone
      // oversized "T".
      TextView(this).apply {
        text = "Aa"
        textSize = 15f
        includeFontPadding = false
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(DesignTokens.textPrimary)
      }
    } else {
      ImageView(this).apply {
        setImageResource(R.drawable.ic_sentiment_satisfied)
        setColorFilter(DesignTokens.textPrimary)
      }
    }
    val accent = themeColor("primaryColor", DesignTokens.primaryContainer)
    return LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
      isClickable = true
      isFocusable = true
      contentDescription = labelText
      // A StateListDrawable rather than a touch listener, so it composes with applyPressScale()
      // (which installs its own OnTouchListener).
      background = android.graphics.drawable.StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), roundedDrawable(accent, VIDEO_DOCK_RADIUS_DP))
        addState(intArrayOf(), roundedDrawable(Color.TRANSPARENT, VIDEO_DOCK_RADIUS_DP))
      }
      addView(icon, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(DesignTokens.spaceSm) })
      addView(TextView(this@PhotoVideoEditorActivity).apply {
        text = labelText
        textSize = 13f
        maxLines = 1
        includeFontPadding = false
        setTextColor(DesignTokens.textPrimary)
      })
      setOnClickListener { onClick() }
      applyPressScale()
    }
  }

  private fun refreshVideoToolSelection(session: VideoEditSession) {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    videoToolButtons.forEach { (key, node) ->
      val selected = when (key) {
        "crop" -> videoAspectMode
        "filters" -> videoFiltersMode
        else -> false
      }
      setToolNodeSelected(node, selected, textColor)
    }
  }

  private fun onVideoToolTapped(key: String, session: VideoEditSession, playerView: PlayerView) {
    when (key) {
      "crop" -> setVideoCropMode(true, session)
      "cover" -> {
        val globalMs = session.globalPositionMs()
        session.update { it.withCoverFrame(globalMs) }
        Toast.makeText(this, "Cover frame set at ${formatMs(globalMs)}", Toast.LENGTH_SHORT).show()
      }
      "rotate" -> {
        session.update { it.rotatedRight() }
        playerView.rotation = session.state.rotationDegrees.toFloat()
      }
      "crop" -> setVideoAspectMode(true, session)
      "speed" -> {
        session.update { it.cycledSpeed() }
        session.player.setPlaybackSpeed(session.state.speed)
        videoToolButtons["speed"]?.label?.text = formatSpeedLabel(session.state.speed)
      }
      "text" -> showVideoTextInputDialog(session)
      "stickers" -> showVideoStickerPicker(session)
      "overlay" -> launchOverlayPicker(REQUEST_CODE_VIDEO_OVERLAY_UPLOAD)
      "filters" -> setVideoFiltersMode(true, session)
      else -> Toast.makeText(this, "$key is coming in a later milestone.", Toast.LENGTH_SHORT).show()
    }
  }

  private fun showVideoStickerPicker(session: VideoEditSession) {
    OnlineStickerSheet(this, runtimeStickerAssets()) { path ->
      val layer = newSticker(stickerUri = Uri.fromFile(File(path)).toString())
      session.layerStack.commit { it + layer }
      onVideoLayerStackChanged()
      selectVideoLayer(layer.id, session)
    }.show()
  }

  private fun runtimeStickerAssets(): List<RuntimeSticker> {
    val assets = request.optJSONArray("stickerAssets") ?: return emptyList()
    return (0 until assets.length()).mapNotNull { index ->
      val asset = assets.optJSONObject(index) ?: return@mapNotNull null
      val uri = asset.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      RuntimeSticker(asset.optString("id").takeIf { it.isNotBlank() } ?: "sticker-${index + 1}", uri)
    }
  }

  private fun formatSpeedLabel(speed: Float): String {
    val trimmed = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "Speed ${trimmed}x"
  }

  private fun setVideoAspectMode(enabled: Boolean, session: VideoEditSession) {
    videoAspectMode = enabled
    videoAspectSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    videoToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    refreshVideoToolSelection(session)
    refreshSelection(videoAspectButtons, session.state.aspectRatio, themeColor("textColor", DesignTokens.onSurface), themeColor("primaryColor", DesignTokens.primaryContainer))
  }

  private fun createVideoAspectSubBar(session: VideoEditSession, playerView: PlayerView): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val presets = listOf<Pair<String, Float?>>(
      "Original" to null, "1:1" to 1f, "4:5" to 4f / 5f, "9:16" to 9f / 16f, "16:9" to 16f / 9f
    )
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(toolbarColor)

      // Rotate row — mirrors the photo Crop sub-bar's treatment.
      addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(
          createIconButton(R.drawable.ic_rotate_right, "Rotate right", textColor) {
            session.update { it.rotatedRight() }
            playerView.rotation = session.state.rotationDegrees.toFloat()
          },
          LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
        )
      }, LinearLayout.LayoutParams(MATCH, dp(DesignTokens.touchTargetMin)))

      addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), 0, dp(12), 0)
        addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
          isHorizontalScrollBarEnabled = false
          addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            presets.forEach { (label, ratio) ->
              val chip = actionButton(label) {
                session.update { it.withAspectRatio(ratio) }
                Toast.makeText(this@PhotoVideoEditorActivity, "Export aspect: $label", Toast.LENGTH_SHORT).show()
                refreshSelection(videoAspectButtons, ratio, textColor, primaryColor)
              }.apply { setTextColor(textColor) }
              videoAspectButtons[ratio] = chip
              addView(chip, LinearLayout.LayoutParams(dp(84), MATCH))
            }
          })
        }, LinearLayout.LayoutParams(0, MATCH, 1f))
        addView(
          actionButton("Done") { setVideoAspectMode(false, session) }.apply { setTextColor(primaryColor) },
          LinearLayout.LayoutParams(dp(72), MATCH)
        )
      }, LinearLayout.LayoutParams(MATCH, dp(56)))
    }
  }

  private fun setVideoFiltersMode(enabled: Boolean, session: VideoEditSession) {
    videoFiltersMode = enabled
    videoFiltersSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    videoFilterSeekBar.visibility = if (enabled) View.VISIBLE else View.GONE
    videoToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    refreshVideoToolSelection(session)
    if (enabled) syncVideoFilterSeekBar(session)
  }

  private fun syncVideoFilterSeekBar(session: VideoEditSession) {
    videoFilterSeekBar.max = 200
    videoFilterSeekBar.progress = (session.state.filterValue(activeVideoFilterKey) + 100f).roundToInt()
  }

  private fun createVideoFiltersSubBar(session: VideoEditSession): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val filters = listOf("brightness" to "Brightness", "contrast" to "Contrast", "saturation" to "Saturation")
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          filters.forEach { (key, label) ->
            val chip = actionButton(label) {
              activeVideoFilterKey = key
              syncVideoFilterSeekBar(session)
              refreshSelection(videoFilterButtons, activeVideoFilterKey, textColor, primaryColor)
            }.apply { setTextColor(textColor) }
            videoFilterButtons[key] = chip
            addView(chip, LinearLayout.LayoutParams(dp(84), MATCH))
          }
          addView(
            actionButton("Reset") {
              session.update { it.withFiltersReset() }
              syncVideoFilterSeekBar(session)
            }.apply { setTextColor(textColor) },
            LinearLayout.LayoutParams(dp(72), MATCH)
          )
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { setVideoFiltersMode(false, session) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  private fun exportVideo() {
    val session = videoSession
    if (session == null) {
      finishWithError("Nothing to export.", "E_INTERNAL")
      return
    }
    session.player.pause()
    videoExportProgressView.visibility = View.VISIBLE
    val exporter = VideoExporter(applicationContext)
    videoExporter = exporter
    try {
      exporter.export(
      clips = session.clips,
      state = session.state,
      layers = session.layerStack.layers,
      exportOptions = request.optJSONObject("export"),
      onProgress = { /* Progress UI is a spinner for now; see docs/video-editor.md. */ },
      onComplete = { result ->
        runOnUiThread {
          videoExporter = null
          videoExportProgressView.visibility = View.GONE
          val intent = Intent()
            .putExtra(EXTRA_URI, result.uri)
            .putExtra(EXTRA_TYPE, mediaType)
            .putExtra(EXTRA_MIME_TYPE, result.mimeType)
            .putExtra(EXTRA_WIDTH, result.width)
            .putExtra(EXTRA_HEIGHT, result.height)
            .putExtra(EXTRA_FILE_SIZE, result.fileSize)
            .putExtra(EXTRA_DURATION, result.durationMs)
          setResult(RESULT_OK, intent)
          finish()
        }
      },
      onError = { error ->
        runOnUiThread {
          videoExporter = null
          videoExportProgressView.visibility = View.GONE
          Toast.makeText(this, error.message ?: "Unable to export the video.", Toast.LENGTH_LONG).show()
        }
      }
      )
    } catch (error: Exception) {
      videoExporter = null
      videoExportProgressView.visibility = View.GONE
      Toast.makeText(this, error.message ?: "Unable to export the video.", Toast.LENGTH_LONG).show()
    }
  }

  private fun cancelVideoExport() {
    videoExporter?.cancel()
    videoExporter = null
    videoExportProgressView.visibility = View.GONE
  }

  // ---------------------------------------------------------------------
  // Multi-clip timeline (Milestone 7): clip strip UI plus split/duplicate/
  // delete/reorder, all operating on `session.clips` via `session.updateClips`.
  // ---------------------------------------------------------------------

  /** Rebinds the trim range view to whichever clip is currently selected. */
  private fun bindTrimViewToSelectedClip(session: VideoEditSession) {
    val clip = session.clips.getOrNull(selectedClipIndex) ?: return
    val duration = clip.originalDurationMs.coerceAtLeast(1)
    trimRangeView.setRange(
      clip.trimStartMs.toFloat() / duration,
      clip.effectiveTrimEndMs().toFloat() / duration
    )
    timelineThumbnailRequest?.cancel()
    trimRangeView.thumbnails = emptyList()
    val frames = arrayOfNulls<Bitmap>(TIMELINE_THUMBNAIL_COUNT)
    val thumbnailRepository = timelineThumbnailRepository
      ?: TimelineThumbnailRepository(applicationContext).also { timelineThumbnailRepository = it }
    timelineThumbnailRequest = thumbnailRepository.load(
      mediaId = clip.sourceUri,
      uri = clip.sourceUri.toMediaUri(),
      durationMs = clip.originalDurationMs,
      count = TIMELINE_THUMBNAIL_COUNT,
      width = dp(72),
      height = dp(48),
      onThumbnail = { index, bitmap ->
        runOnUiThread {
          if (!isFinishing && session.clips.getOrNull(selectedClipIndex)?.id == clip.id) {
            frames[index] = bitmap
            trimRangeView.thumbnails = frames.filterNotNull()
          }
        }
      },
      onComplete = {
        runOnUiThread {
          if (session.clips.getOrNull(selectedClipIndex)?.id == clip.id) {
            timelineThumbnailRequest = null
          }
        }
      }
    )
  }

  private fun String.toMediaUri(): Uri = Uri.parse(this).let { parsed ->
    if (parsed.scheme == null) Uri.fromFile(File(this)) else parsed
  }

  /** Rebuilds the clip strip: one chip per clip plus Split/Duplicate/Delete/Move-Left/Move-Right actions. */
  private fun refreshClipStrip(session: VideoEditSession) {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    clipStripBar.removeAllViews()
    session.clips.forEachIndexed { index, clip ->
      val chip = Button(this).apply {
        text = "Clip ${index + 1}\n${formatMs(clip.trimmedDurationMs())}"
        textSize = 11f
        isAllCaps = false
        setButtonSelected(this, index == selectedClipIndex, textColor, primaryColor)
        setOnClickListener {
          selectedClipIndex = index
          session.seekToGlobalMs(session.clips.take(index).sumOf { it.trimmedDurationMs() })
          bindTrimViewToSelectedClip(session)
          refreshClipStrip(session)
        }
      }
      clipStripBar.addView(chip, LinearLayout.LayoutParams(dp(88), WRAP))
    }
    clipStripBar.addView(
      actionButton("Split") { splitSelectedClip(session) }.apply { setTextColor(textColor); textSize = 12f },
      LinearLayout.LayoutParams(dp(64), WRAP)
    )
    clipStripBar.addView(
      actionButton("Copy") { duplicateSelectedClip(session) }.apply { setTextColor(textColor); textSize = 12f },
      LinearLayout.LayoutParams(dp(64), WRAP)
    )
    clipStripBar.addView(
      actionButton("Delete") { deleteSelectedClip(session) }.apply { setTextColor(textColor); textSize = 12f },
      LinearLayout.LayoutParams(dp(64), WRAP)
    )
    clipStripBar.addView(
      actionButton("<") { moveSelectedClip(session, -1) }.apply { setTextColor(textColor); textSize = 12f },
      LinearLayout.LayoutParams(dp(40), WRAP)
    )
    clipStripBar.addView(
      actionButton(">") { moveSelectedClip(session, 1) }.apply { setTextColor(textColor); textSize = 12f },
      LinearLayout.LayoutParams(dp(40), WRAP)
    )
  }

  /** Splits the selected clip at the current playhead into two clips sharing the same source. */
  private fun splitSelectedClip(session: VideoEditSession) {
    val index = selectedClipIndex
    val clip = session.clips.getOrNull(index) ?: return
    val precedingMs = session.clips.take(index).sumOf { it.trimmedDurationMs() }
    val splitAtWithinClipMs = (session.globalPositionMs() - precedingMs).coerceIn(0, clip.trimmedDurationMs())
    val splitSourceMs = clip.trimStartMs + splitAtWithinClipMs
    // Refuse to create a zero-length clip on either side of the cut.
    if (splitSourceMs <= clip.trimStartMs + 50 || splitSourceMs >= clip.effectiveTrimEndMs() - 50) {
      Toast.makeText(this, "Move the playhead into the clip first to split it there.", Toast.LENGTH_SHORT).show()
      return
    }
    session.updateClips { clips ->
      clips.flatMapIndexed { i, c ->
        if (i != index) listOf(c)
        else listOf(
          c.copy(trimEndMs = splitSourceMs),
          VideoClip(sourceUri = c.sourceUri, originalDurationMs = c.originalDurationMs, trimStartMs = splitSourceMs, trimEndMs = c.effectiveTrimEndMs())
        )
      }
    }
    refreshClipStrip(session)
  }

  /** Duplicates the selected clip immediately after itself. */
  private fun duplicateSelectedClip(session: VideoEditSession) {
    val index = selectedClipIndex
    val clip = session.clips.getOrNull(index) ?: return
    session.updateClips { clips ->
      clips.flatMapIndexed { i, c -> if (i == index) listOf(c, c.copy(id = java.util.UUID.randomUUID().toString())) else listOf(c) }
    }
    selectedClipIndex = index + 1
    bindTrimViewToSelectedClip(session)
    refreshClipStrip(session)
  }

  /** Deletes the selected clip, refusing to leave the timeline empty. */
  private fun deleteSelectedClip(session: VideoEditSession) {
    if (session.clips.size <= 1) {
      Toast.makeText(this, "A video needs at least one clip.", Toast.LENGTH_SHORT).show()
      return
    }
    val index = selectedClipIndex
    session.updateClips { clips -> clips.filterIndexed { i, _ -> i != index } }
    selectedClipIndex = selectedClipIndex.coerceIn(0, session.clips.size - 1)
    bindTrimViewToSelectedClip(session)
    refreshClipStrip(session)
  }

  /** Moves the selected clip one position left (-1) or right (+1) in the timeline. */
  private fun moveSelectedClip(session: VideoEditSession, direction: Int) {
    val index = selectedClipIndex
    val target = index + direction
    if (target < 0 || target >= session.clips.size) return
    session.updateClips { clips ->
      clips.toMutableList().apply { add(target, removeAt(index)) }
    }
    selectedClipIndex = target
    refreshClipStrip(session)
  }

  // ---------------------------------------------------------------------
  // Shared shell: header, insets, lifecycle result delivery.
  // ---------------------------------------------------------------------

  /**
   * targetSdk 35+ enforces edge-to-edge, so the window draws behind the status
   * and gesture/nav bars. Grow the header/toolbar height by the inset and pad
   * that extra space, so the fixed-size content inside is never obscured or
   * squeezed by the system bars.
   *
   * [bottomBars] must list *every* bottom-row bar that can be the last visible
   * child of the root column — the main toolbar plus every tool's sub-bar
   * (crop/filters/stickers/draw/layers/video-aspect). Only one is visible at a
   * time, but each needs its own inset listener applied up front: Android
   * dispatches insets to every view in the hierarchy regardless of visibility,
   * so whichever bar becomes visible when a tool is selected already has the
   * correct bottom padding/height baked in, instead of sitting under the
   * gesture nav bar until the *next* inset change happens to reach it.
   */
  private fun applySystemBarInsets(header: View, headerHeight: Int, bottomBars: List<Pair<View, Int>>) {
    val headerBase = intArrayOf(header.paddingLeft, header.paddingRight)
    ViewCompat.setOnApplyWindowInsetsListener(header) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
      view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply { height = headerHeight + bars.top }
      view.setPadding(headerBase[0] + bars.left, bars.top, headerBase[1] + bars.right, 0)
      insets
    }
    bottomBars.forEach { (bar, baseHeight) ->
      val base = intArrayOf(bar.paddingLeft, bar.paddingRight, bar.paddingTop)
      ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply { height = baseHeight + bars.bottom }
        view.setPadding(base[0] + bars.left, base[2], base[1] + bars.right, bars.bottom)
        insets
      }
    }
  }

  private fun createTopBar(): LinearLayout {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val onPrimaryColor = themeColor("onPrimaryColor", DesignTokens.onPrimaryContainer)
    val toolbarColor = themeColor("toolbarColor", DesignTokens.withAlphaPercent(DesignTokens.surfaceDim, 95))
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(DesignTokens.spaceLg), 0, dp(DesignTokens.spaceLg), 0)
      setBackgroundColor(toolbarColor)

      addView(
        createIconButton(R.drawable.ic_close, "Close editor", textColor) { confirmDiscardChanges() }.apply {
          background = circleDrawable(DesignTokens.surfaceContainerLow)
        },
        LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
      )

      addView(TextView(this@PhotoVideoEditorActivity).apply {
        text = if (mediaType == "photo") "Photo Editor" else "Video Editor"
        textSize = 14f
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
      }, LinearLayout.LayoutParams(0, MATCH, 1f))

      val undo = createIconButton(R.drawable.ic_undo, "Undo", textColor) {
        if (mediaType == "photo") { photoSession?.layerStack?.undo(); onLayerStackChanged() }
        else { videoSession?.layerStack?.undo(); onVideoLayerStackChanged() }
      }
      val redo = createIconButton(R.drawable.ic_redo, "Redo", textColor) {
        if (mediaType == "photo") { photoSession?.layerStack?.redo(); onLayerStackChanged() }
        else { videoSession?.layerStack?.redo(); onVideoLayerStackChanged() }
      }
      undo.isEnabled = false; undo.alpha = 0.4f
      redo.isEnabled = false; redo.alpha = 0.4f
      undoButton = undo
      redoButton = redo
      addView(undo, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin)))
      addView(redo, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin)))


      addView(
        editorPrimaryButton(this@PhotoVideoEditorActivity, "Export", null, primaryColor, onPrimaryColor) {
          if (mediaType == "photo") showPhotoExportConfiguration() else finishDone()
        },
        LinearLayout.LayoutParams(WRAP, dp(DesignTokens.touchTargetMin)).apply { marginStart = dp(DesignTokens.spaceXs) }
      )
    }
  }

  private fun showOriginalPreview(show: Boolean) {
    if (!::photoImageView.isInitialized || comparingOriginal == show) return
    comparingOriginal = show
    val session = photoSession ?: return
    if (show) photoImageView.setImageBitmap(session.baseBitmap) else schedulePhotoPreviewRender()
  }

  private fun showPhotoExportConfiguration() {
    val configured = request.optJSONObject("export")
    val formats = arrayOf("JPEG", "PNG", "WebP")
    val current = when (configured?.optString("imageFormat", "jpeg")) { "png" -> 1; "webp" -> 2; else -> 0 }
    var selected = current
    AlertDialog.Builder(this)
      .setTitle("Export & Share")
      .setMessage("Choose the output format. Quality and size limits continue to use your export settings.")
      .setSingleChoiceItems(formats, current) { _, which -> selected = which }
      .setNegativeButton("Cancel", null)
      .setPositiveButton("Export") { _, _ ->
        val output = configured ?: JSONObject().also { request.put("export", it) }
        output.put("imageFormat", arrayOf("jpeg", "png", "webp")[selected])
        finishDone()
      }
      .show()
  }

  /** 44x44dp icon-only button, matching the Studio Violet "Tool Icon Button" spec. */
  private fun createIconButton(iconRes: Int, contentDescriptionText: String, tint: Int, onClick: () -> Unit): ImageButton {
    return ImageButton(this).apply {
      setImageResource(iconRes)
      contentDescription = contentDescriptionText
      background = null
      scaleType = ImageView.ScaleType.CENTER_INSIDE
      setColorFilter(tint)
      setPadding(dp(11), dp(11), dp(11), dp(11))
      setOnClickListener { onClick() }
      applyPressScale()
    }
  }

  private fun roundedDrawable(color: Int, radiusDp: Int): android.graphics.drawable.GradientDrawable =
    android.graphics.drawable.GradientDrawable().apply {
      cornerRadius = dp(radiusDp).toFloat()
      setColor(color)
    }

  private fun circleDrawable(color: Int): android.graphics.drawable.GradientDrawable =
    android.graphics.drawable.GradientDrawable().apply {
      shape = android.graphics.drawable.GradientDrawable.OVAL
      setColor(color)
    }

  /** Refreshes the preview + selection + undo/redo enabled state after any layer-stack change. */
  private fun onLayerStackChanged() {
    val session = photoSession ?: return
    if (selectedLayerId != null && session.layerStack.layers.none { it.id == selectedLayerId }) {
      selectedLayerId = null
    }
    layerOverlay.layers = session.layerStack.layers
    layerOverlay.selectedLayerId = selectedLayerId
    schedulePhotoPreviewRender()
    undoButton.isEnabled = session.layerStack.canUndo
    redoButton.isEnabled = session.layerStack.canRedo
    undoButton.alpha = if (session.layerStack.canUndo) 1f else 0.4f
    redoButton.alpha = if (session.layerStack.canRedo) 1f else 0.4f
    if (selectedLayerId == null && layerToolBar.visibility == View.VISIBLE) setLayerToolBarVisible(false)
  }

  /** Delegates expensive rendering to a coalescing background feature component. */
  private fun schedulePhotoPreviewRender() {
    if (!::photoImageView.isInitialized) return
    photoPreviewRenderer?.request()
  }

  private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
    text = label
    textSize = 13f
    isAllCaps = false
    minHeight = dp(DesignTokens.touchTargetMin)
    minimumHeight = dp(DesignTokens.touchTargetMin)
    setPadding(dp(DesignTokens.spaceMd), 0, dp(DesignTokens.spaceMd), 0)
    setTextColor(DesignTokens.onSurface)
    background = roundedDrawable(DesignTokens.surfaceContainerHigh, DesignTokens.radiusLg)
    setOnClickListener { action() }
  }

  /** Highlights a tool/chip button so the user can tell at a glance which one is active. */
  private fun setButtonSelected(button: Button, selected: Boolean, textColor: Int, primaryColor: Int) {
    button.setTextColor(if (selected) DesignTokens.onPrimaryContainer else textColor)
    button.background = roundedDrawable(
      if (selected) primaryColor else DesignTokens.surfaceContainerHigh,
      DesignTokens.radiusLg
    )
  }

  private fun <K> refreshSelection(buttons: Map<K, Button>, activeKey: K?, textColor: Int, primaryColor: Int) {
    buttons.forEach { (key, button) -> setButtonSelected(button, key == activeKey, textColor, primaryColor) }
  }

  /** One icon-tile + label entry in the main tool rail (Studio Violet "Tool Node"). */
  private data class ToolNode(val root: View, val tile: View, val icon: ImageView, val label: TextView)

  /** Builds a 44dp rounded icon tile with a label below, matching the mockups' tool-rail nodes. */
  private fun createToolNode(iconRes: Int, labelText: String, onClick: () -> Unit): ToolNode {
    val icon = ImageView(this).apply {
      setImageResource(iconRes)
      setColorFilter(DesignTokens.outline)
    }
    val tile = FrameLayout(this).apply {
      background = roundedDrawable(DesignTokens.surfaceContainerLow, DesignTokens.radiusLg)
      addView(icon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
    }
    val label = TextView(this).apply {
      text = labelText
      textSize = 11f
      maxLines = 1
      includeFontPadding = false
      gravity = Gravity.CENTER
      setTextColor(DesignTokens.outline)
      setPadding(0, dp(2), 0, 0)
    }
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      isClickable = true
      isFocusable = true
      contentDescription = labelText
      background = roundedDrawable(Color.TRANSPARENT, DesignTokens.radiusLg)
      setPadding(0, dp(2), 0, dp(2))
      addView(tile, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin)))
      addView(label, LinearLayout.LayoutParams(WRAP, WRAP))
      setOnClickListener { onClick() }
      applyPressScale()
    }
    return ToolNode(root, tile, icon, label)
  }

  /** Highlights a tool-rail node the same way the mockups do: filled tile + text/icon tint switching to the theme's text color. */
  /**
   * Selection is a purple icon + label over a faint purple disc — not a filled purple tile, which at
   * dock size reads as a large button and fights the photo for attention.
   */
  private fun setToolNodeSelected(node: ToolNode, selected: Boolean, textColor: Int) {
    val accent = themeColor("primaryColor", DesignTokens.primaryContainer)
    node.tile.background = if (selected) circleDrawable(DesignTokens.withAlphaPercent(accent, 18)) else null
    node.root.isSelected = selected
    node.icon.setColorFilter(if (selected) accent else DesignTokens.textSecondary)
    node.label.setTextColor(if (selected) accent else DesignTokens.textSecondary)
  }

  private fun <K> refreshToolNodeSelection(nodes: Map<K, ToolNode>, activeKey: K?, textColor: Int) {
    nodes.forEach { (key, node) ->
      setToolNodeSelected(node, key == activeKey, textColor)
      if (key == "delete") { node.icon.setColorFilter(Color.rgb(255, 100, 115)); node.label.setTextColor(Color.rgb(255, 100, 115)) }
    }
  }

  private fun showPreviewError(container: FrameLayout) {
    container.addView(TextView(this).apply {
      text = "Unable to preview this video"
      textSize = 16f
      gravity = Gravity.CENTER
      setTextColor(Color.WHITE)
    }, FrameLayout.LayoutParams(MATCH, MATCH))
  }

  private fun finishDone() {
    if (mediaType == "photo") {
      exportPhoto()
    } else {
      exportVideo()
    }
  }

  /** Studio Violet close flow. Draft persistence is intentionally omitted until it is real. */
  private fun confirmDiscardChanges() {
    AlertDialog.Builder(this)
      .setTitle("Discard Unsaved Changes?")
      .setMessage("If you exit now, your edits will be lost.")
      .setPositiveButton("Keep Editing", null)
      .setNegativeButton("Discard Changes") { _, _ -> finishCancelled() }
      .create()
      .apply {
        setOnShowListener {
          getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(themeColor("primaryColor", DesignTokens.primary))
          getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(DesignTokens.error)
        }
      }
      .show()
  }

  private fun finishCancelled() {
    videoExporter?.cancel()
    setResult(RESULT_CANCELED)
    finish()
  }

  private fun finishWithError(message: String, code: String) {
    setResult(RESULT_ERROR, Intent().putExtra(EXTRA_ERROR, message).putExtra(EXTRA_ERROR_CODE, code))
    finish()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    if (cropMode) photoSession?.let { cancelCropMode(it, photoImageView, cropOverlay) }
    else if (videoCropMode) videoSession?.let { session ->
      videoCropEntryState?.let { saved -> session.update { it.copy(crop = saved) } }; setVideoCropMode(false, session)
    }
    else confirmDiscardChanges()
  }

  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (level >= TRIM_MEMORY_RUNNING_LOW) timelineThumbnailRepository?.clearMemory()
  }

  override fun onDestroy() {
    positionPollRunnable?.let { positionPollHandler.removeCallbacks(it) }
    if (::photoImageView.isInitialized) photoImageView.setImageDrawable(null)
    filterThumbnailLoader.close()
    timelineThumbnailRequest?.cancel()
    timelineThumbnailRequest = null
    if (::trimRangeView.isInitialized) trimRangeView.thumbnails = emptyList()
    timelineThumbnailRepository?.close()
    timelineThumbnailRepository = null
    val sessionToRelease = photoSession
    photoSession = null
    photoPreviewRenderer?.close { sessionToRelease?.release() }
    photoPreviewRenderer = null
    videoOverlayBitmap?.takeIf { !it.isRecycled }?.recycle()
    videoOverlayBitmap = null
    videoImageLayerCache.values.filterNotNull().distinct().forEach { if (!it.isRecycled) it.recycle() }
    videoImageLayerCache.clear()
    videoExporter?.cancel()
    videoSession?.release()
    super.onDestroy()
  }

  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

  companion object {
    /** Rotate icon row (44dp) + aspect-preset chip row (56dp). */
    const val CROP_SUB_BAR_HEIGHT_DP = 232
    /** Header + shared slider + adjustment strip; see EditorPanelMetrics for the breakdown. */
    const val ADJUST_SUB_BAR_HEIGHT_DP = EditorPanelMetrics.TOTAL_HEIGHT_DP
    /** Same skeleton as Adjust, with a taller strip for the thumbnail + label. */
    const val FILTERS_SUB_BAR_HEIGHT_DP = EditorPanelMetrics.TOTAL_HEIGHT_DP + 24
    /** 64dp asset card + optional caption + vertical padding, for the Stickers/Shapes grid. */
    const val ASSET_GRID_BAR_HEIGHT_DP = 104
    /** Compact video dock (Text/Stickers pill) and the gap between it and the safe-area edge. */
    const val VIDEO_DOCK_HEIGHT_DP = 52
    const val VIDEO_DOCK_RADIUS_DP = 18
    const val VIDEO_DOCK_BAR_HEIGHT_DP = VIDEO_DOCK_HEIGHT_DP + 8
    private const val TIMELINE_THUMBNAIL_COUNT = 12
    const val EXTRA_URI = "photoVideoEditor.uri"
    const val EXTRA_TYPE = "photoVideoEditor.type"
    const val EXTRA_MIME_TYPE = "photoVideoEditor.mimeType"
    const val EXTRA_WIDTH = "photoVideoEditor.width"
    const val EXTRA_HEIGHT = "photoVideoEditor.height"
    const val EXTRA_FILE_SIZE = "photoVideoEditor.fileSize"
    const val EXTRA_DURATION = "photoVideoEditor.duration"
    const val EXTRA_ERROR = "photoVideoEditor.error"
    const val EXTRA_ERROR_CODE = "photoVideoEditor.errorCode"
    const val EXTRA_REQUEST = "photoVideoEditor.request"
    const val RESULT_ERROR = Activity.RESULT_FIRST_USER
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private const val REQUEST_CODE_STICKER_UPLOAD = 9001
    private const val REQUEST_CODE_OVERLAY_UPLOAD = 9002
    private const val REQUEST_CODE_VIDEO_STICKER_UPLOAD = 9003
    private const val REQUEST_CODE_VIDEO_OVERLAY_UPLOAD = 9004
  }
}
