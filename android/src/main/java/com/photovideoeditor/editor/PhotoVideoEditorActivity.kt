package com.photovideoeditor.editor

import com.photovideoeditor.R
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import com.photovideoeditor.photo.render.LayerType
import com.photovideoeditor.photo.render.PhotoAdjustmentRenderer
import com.photovideoeditor.photo.render.PhotoAdjustments
import com.photovideoeditor.photo.render.PhotoDepthBlur
import com.photovideoeditor.photo.render.PhotoEditSession
import com.photovideoeditor.photo.render.PhotoFilterPresets
import com.photovideoeditor.photo.render.PhotoLayer
import com.photovideoeditor.photo.render.PhotoLayerRenderer
import com.photovideoeditor.photo.render.ShapeKind
import com.photovideoeditor.photo.ui.CropOverlayView
import com.photovideoeditor.photo.ui.DrawOverlayView
import com.photovideoeditor.video.export.VideoExportException
import com.photovideoeditor.video.export.VideoExporter
import com.photovideoeditor.video.render.VideoClip
import com.photovideoeditor.video.render.VideoEditSession
import com.photovideoeditor.video.render.VideoThumbnailGenerator
import com.photovideoeditor.video.ui.TrimRangeView
import com.photovideoeditor.photo.ui.LayerOverlayView
import com.photovideoeditor.photo.ui.ZoomableImageView
import com.photovideoeditor.ui.DesignTokens
import org.json.JSONObject
import kotlin.math.roundToInt

class PhotoVideoEditorActivity : Activity() {
  private val sourceUri by lazy { intent.getStringExtra(EXTRA_URI).orEmpty() }
  private val mediaType by lazy { intent.getStringExtra(EXTRA_TYPE).orEmpty() }
  private val request by lazy { JSONObject(intent.getStringExtra(EXTRA_REQUEST).orEmpty()) }
  private val theme by lazy { request.optJSONObject("theme") }

  private var photoSession: PhotoEditSession? = null
  private var cropMode = false
  private var adjustMode = false
  private var filtersMode = false
  private var drawMode = false
  private var stickersMode = false
  private var activeAdjustmentKey = "brightness"
  private var selectedLayerId: String? = null
  private var activeLayerPropertyKey = "scale"
  private var dragStartSnapshot: List<PhotoLayer>? = null
  private lateinit var photoImageView: ZoomableImageView
  private lateinit var cropOverlay: CropOverlayView
  private lateinit var layerOverlay: LayerOverlayView
  private lateinit var drawOverlay: DrawOverlayView
  private lateinit var straightenSeekBar: SeekBar
  private lateinit var cropSubBar: View
  private lateinit var adjustmentSeekBar: SeekBar
  private lateinit var filtersSubBar: View
  private lateinit var adjustSubBar: View
  private lateinit var stickersSubBar: View
  private lateinit var drawSubBar: View
  private lateinit var layerToolBar: View
  private lateinit var layerPropertySeekBar: SeekBar
  private lateinit var layerColorSwatchRow: View
  private lateinit var mainToolBar: View
  private lateinit var photoQuickActions: View
  private lateinit var undoButton: ImageButton
  private lateinit var redoButton: ImageButton
  private lateinit var exportProgressView: View
  private var photoPreviewRenderPending = false
  private var comparingOriginal = false

  private var videoSession: VideoEditSession? = null
  private var videoExporter: VideoExporter? = null
  private var videoAspectMode = false
  private lateinit var trimRangeView: TrimRangeView
  private lateinit var positionSeekBar: SeekBar
  private lateinit var playPauseButton: Button
  private lateinit var timeLabel: TextView
  private lateinit var videoAspectSubBar: View
  private lateinit var videoToolBar: View
  private lateinit var videoExportProgressView: View
  private lateinit var videoExportCancelButton: Button
  private lateinit var videoOverlayImageView: ImageView
  private var videoOverlayBitmap: Bitmap? = null
  private var videoOverlayRenderKey: String? = null
  private val videoStickerCache = mutableMapOf<String, Bitmap?>()
  private val positionPollHandler = android.os.Handler(android.os.Looper.getMainLooper())
  private var positionPollRunnable: Runnable? = null
  private var seeking = false

  private val photoToolButtons = mutableMapOf<String, ToolNode>()
  private val cropAspectButtons = mutableMapOf<Float?, Button>()
  private val filterAdjustmentButtons = mutableMapOf<String, Button>()
  /** Preset id ("" = Original) -> (thumbnail frame, label) for the Filters preset-card scroller. */
  private val filterPresetCards = mutableMapOf<String, Pair<FrameLayout, TextView>>()
  private var blurMode = false
  private lateinit var blurSubBar: View
  private lateinit var blurSeekBar: SeekBar
  private var activeBlurKey = "intensity"
  private val blurKeyButtons = mutableMapOf<String, Button>()
  private val blurModeButtons = mutableMapOf<PhotoDepthBlur.Mode?, Button>()
  private var resizeMode = false
  private lateinit var resizeSubBar: View
  private var resizePresetSize: Pair<Int, Int>? = null
  private val resizePresetButtons = mutableMapOf<Pair<Int, Int>?, Button>()
  private val layerPropertyButtons = mutableMapOf<String, Button>()
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
  private var activeVideoLayerPropertyKey = "scale"
  private var videoDragStartSnapshot: List<PhotoLayer>? = null
  private val videoLayerPropertyButtons = mutableMapOf<String, Button>()
  private var selectedClipIndex = 0
  private lateinit var clipStripBar: LinearLayout
  private val clipThumbnailCache = mutableMapOf<String, List<Bitmap>>()

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
    previewContainer.addView(imageView, FrameLayout.LayoutParams(MATCH, MATCH))

    previewContainer.addView(TextView(this).apply {
      text = "●  ORIGINAL   •   100%"
      textSize = 10f
      letterSpacing = 0.08f
      setTextColor(DesignTokens.onSurfaceVariant)
      gravity = Gravity.CENTER
      background = roundedDrawable(DesignTokens.withAlphaPercent(DesignTokens.surfaceContainerHigh, 92), DesignTokens.radiusLg)
      setPadding(dp(DesignTokens.spaceMd), dp(DesignTokens.spaceXs), dp(DesignTokens.spaceMd), dp(DesignTokens.spaceXs))
    }, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
      topMargin = dp(DesignTokens.spaceSm)
    })

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
      if (cropMode) overlay.setImageBounds(bounds, resetCrop = false)
      layers.setImageBounds(bounds)
      drawing.setImageBounds(bounds)
    }
    overlay.onCropChanged = { left, top, right, bottom -> session.update { it.withCrop(left, top, right, bottom) } }
    layers.onLayerTapped = { id -> selectLayer(id) }
    layers.onLayerDragged = { id, x, y ->
      if (dragStartSnapshot == null) dragStartSnapshot = session.layerStack.layers
      session.layerStack.updateLive { list -> list.map { if (it.id == id) it.copy(x = x, y = y) else it } }
      layerOverlay.layers = session.layerStack.layers
      schedulePhotoPreviewRender()
    }
    layers.onLayerDragEnded = {
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
    root.addView(
      straighten,
      LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(16), dp(4), dp(16), 0) }
    )

    val cropBar = createCropSubBar(session, imageView, overlay)
    cropSubBar = cropBar
    cropBar.visibility = View.GONE
    root.addView(cropBar, LinearLayout.LayoutParams(MATCH, dp(CROP_SUB_BAR_HEIGHT_DP)))

    val resizeBar = createResizeSubBar(session, imageView, overlay)
    resizeSubBar = resizeBar
    resizeBar.visibility = View.GONE
    root.addView(resizeBar, LinearLayout.LayoutParams(MATCH, dp(56)))

    val adjustmentSeek = SeekBar(this).apply {
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          val (min, _) = PhotoAdjustments.rangeFor(activeAdjustmentKey)
          val value = min + progress
          session.updateAdjustments { it.with(activeAdjustmentKey, value) }
          schedulePhotoPreviewRender()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      })
    }
    adjustmentSeekBar = adjustmentSeek
    root.addView(adjustmentSeek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(16), dp(4), dp(16), 0) })

    val adjustBar = createAdjustSubBar(session, imageView)
    adjustSubBar = adjustBar
    adjustBar.visibility = View.GONE
    root.addView(adjustBar, LinearLayout.LayoutParams(MATCH, dp(ADJUST_SUB_BAR_HEIGHT_DP)))

    val blurSeek = SeekBar(this).apply {
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          session.updateDepthBlur { withBlurValue(it, activeBlurKey, progress.toFloat()) }
          schedulePhotoPreviewRender()
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      })
    }
    blurSeekBar = blurSeek
    root.addView(blurSeek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(16), dp(4), dp(16), 0) })

    val blurBar = createBlurSubBar(session, imageView)
    blurSubBar = blurBar
    blurBar.visibility = View.GONE
    root.addView(blurBar, LinearLayout.LayoutParams(MATCH, dp(56)))

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

    val layerPropertySeek = SeekBar(this).apply {
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser) return
          onLayerPropertyChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
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
    root.addView(layerBar, LinearLayout.LayoutParams(MATCH, dp(56)))

    val quickActions = createPhotoQuickActions(session, imageView, overlay)
    photoQuickActions = quickActions
    root.addView(quickActions, LinearLayout.LayoutParams(MATCH, dp(52)))

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
        blurBar to dp(56),
        resizeBar to dp(56),
        filtersBar to dp(FILTERS_SUB_BAR_HEIGHT_DP),
        stickersBar to dp(ASSET_GRID_BAR_HEIGHT_DP),
        drawBar to dp(56),
        layerBar to dp(56)
      )
    )
    return outer
  }

  private fun createPhotoQuickActions(
    session: PhotoEditSession,
    imageView: ZoomableImageView,
    overlay: CropOverlayView
  ): View = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    setBackgroundColor(DesignTokens.surfaceContainerLow)
    addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(DesignTokens.spaceLg), dp(DesignTokens.spaceXs), dp(DesignTokens.spaceLg), dp(DesignTokens.spaceXs))
      addView(actionButton("✦  Auto Enhance") {
        session.updateAdjustments { it.copy(brightness = 8f, contrast = 6f, saturation = 5f, sharpness = 8f) }
        schedulePhotoPreviewRender()
      }, LinearLayout.LayoutParams(WRAP, dp(44)).apply { marginEnd = dp(DesignTokens.spaceSm) })
      val compare = actionButton("◫  Hold Compare") {}
      compare.setOnTouchListener { _, event ->
          when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> showOriginalPreview(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showOriginalPreview(false)
          }
          true
      }
      addView(compare, LinearLayout.LayoutParams(WRAP, dp(44)).apply { marginEnd = dp(DesignTokens.spaceSm) })
      addView(actionButton("□  Crop 1:1") {
        setCropMode(true, session, imageView, overlay)
        cropAspectButtons[1f]?.performClick()
      }, LinearLayout.LayoutParams(WRAP, dp(44)))
    })
  }

  /** Full Studio Violet tool rail (see main_photo_editor_default_state mockup). Rotate/Flip live inside the Crop sub-bar, not as top-level tools. */
  private val photoToolDefinitions = listOf(
    Triple("crop", "Crop", R.drawable.ic_crop),
    Triple("adjust", "Adjust", R.drawable.ic_tune),
    Triple("filters", "Filters", R.drawable.ic_photo_filter),
    Triple("effects", "Effects", R.drawable.ic_auto_awesome),
    Triple("blur", "Blur", R.drawable.ic_blur_on),
    Triple("text", "Text", R.drawable.ic_title),
    Triple("stickers", "Stickers", R.drawable.ic_sentiment_satisfied),
    Triple("shapes", "Shapes", R.drawable.ic_category),
    Triple("draw", "Draw", R.drawable.ic_draw),
    Triple("frames", "Frames", R.drawable.ic_wallpaper),
    Triple("overlays", "Overlays", R.drawable.ic_filter_vintage),
    Triple("background", "Background", R.drawable.ic_image),
    Triple("retouch", "Retouch", R.drawable.ic_face),
    Triple("layers", "Layers", R.drawable.ic_layers),
    Triple("resize", "Resize", R.drawable.ic_aspect_ratio)
  )

  private fun createPhotoToolBar(session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView): LinearLayout {
    val backgroundColor = themeColor("toolbarColor", DesignTokens.withAlphaPercent(DesignTokens.surfaceContainerLowest, 95))
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(backgroundColor)
      setPadding(dp(DesignTokens.spaceXs), dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs), dp(DesignTokens.spaceLg))

      addView(
        View(this@PhotoVideoEditorActivity).apply { background = roundedDrawable(DesignTokens.surfaceVariant, DesignTokens.radiusSm) },
        LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(DesignTokens.spaceXs) }
      )

      val features = request.optJSONObject("features")
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          setPadding(dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs), dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs))
          photoToolDefinitions.filter { features?.optBoolean(it.first, true) != false }.forEach { (key, label, iconRes) ->
            val node = createToolNode(iconRes, label) { onPhotoToolTapped(key, session, imageView, overlay) }
            photoToolButtons[key] = node
            addView(node.root, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) })
          }
        })
      }, LinearLayout.LayoutParams(MATCH, WRAP))
    }
  }

  private fun refreshPhotoToolSelection() {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    photoToolButtons.forEach { (key, node) ->
      val selected = when (key) {
        "crop" -> cropMode
        "adjust" -> adjustMode
        "filters" -> filtersMode
        "blur" -> blurMode
        "stickers" -> stickersMode
        "draw" -> drawMode
        "resize" -> resizeMode
        else -> false
      }
      setToolNodeSelected(node, selected, textColor)
    }
  }

  private fun onPhotoToolTapped(key: String, session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView) {
    when (key) {
      "crop" -> setCropMode(true, session, imageView, overlay)
      "adjust" -> setAdjustMode(true, session)
      "filters", "effects" -> setFiltersMode(true, session)
      "blur" -> setBlurMode(true, session, imageView)
      "text" -> showTextInputDialog(session)
      "stickers", "shapes", "overlays", "frames" -> setStickersMode(true, session)
      "draw" -> setDrawMode(true, session)
      "resize", "background" -> setResizeMode(true, session, imageView, overlay)
      "retouch" -> setAdjustMode(true, session)
      "layers" -> openLayerManager(session)
      else -> Toast.makeText(this, "$key is coming in a later milestone.", Toast.LENGTH_SHORT).show()
    }
  }

  private fun openLayerManager(session: PhotoEditSession) {
    val top = session.layerStack.layers.lastOrNull()
    if (top == null) Toast.makeText(this, "Add content to start a layer stack.", Toast.LENGTH_LONG).show()
    else selectLayer(top.id)
  }

  // ---------------------------------------------------------------------
  // Layers: text, stickers, shapes, freehand drawing, selection, and
  // transform/duplicate/reorder/lock/hide/delete with undo/redo (Milestone 4).
  // ---------------------------------------------------------------------

  private fun showTextInputDialog(session: PhotoEditSession) {
    val input = EditText(this).apply {
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
      setTextColor(DesignTokens.onSurface)
      setHintTextColor(DesignTokens.outline)
      hint = "Type something..."
      setPadding(dp(DesignTokens.spaceLg), dp(DesignTokens.spaceLg), dp(DesignTokens.spaceLg), dp(DesignTokens.spaceLg))
    }
    val container = FrameLayout(this).apply {
      background = roundedDrawable(DesignTokens.surfaceContainerHigh, DesignTokens.radiusLg)
      addView(input, FrameLayout.LayoutParams(MATCH, WRAP))
    }
    AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
      .setTitle("Add text")
      .setView(container)
      .setPositiveButton("Add") { _, _ ->
        val text = input.text?.toString().orEmpty()
        if (text.isNotBlank()) {
          val layer = PhotoLayer(type = LayerType.TEXT, text = text)
          session.layerStack.commit { it + layer }
          selectLayer(layer.id)
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun setStickersMode(enabled: Boolean, session: PhotoEditSession) {
    stickersMode = enabled
    stickersSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    refreshPhotoToolSelection()
  }

  /** One asset card in the Stickers/Shapes library grid (see stickers_shapes_library mockup). */
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
                val layer = PhotoLayer(type = LayerType.STICKER, stickerId = id)
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
                  val layer = PhotoLayer(type = LayerType.STICKER, stickerUri = uri)
                  session.layerStack.commit { it + layer }
                  selectLayer(layer.id)
                  setStickersMode(false, session)
                },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) }
              )
            }
          }
          val shapes = listOf(ShapeKind.RECTANGLE to "Rect", ShapeKind.OVAL to "Oval", ShapeKind.LINE to "Line")
          shapes.forEach { (kind, label) ->
            val face = when (kind) {
              ShapeKind.RECTANGLE -> View(this@PhotoVideoEditorActivity).apply { background = roundedDrawable(DesignTokens.primary, DesignTokens.radiusSm) }
              ShapeKind.OVAL -> View(this@PhotoVideoEditorActivity).apply { background = circleDrawable(DesignTokens.primary) }
              ShapeKind.LINE -> View(this@PhotoVideoEditorActivity).apply { setBackgroundColor(DesignTokens.primary) }
            }
            val faceSize = if (kind == ShapeKind.LINE) FrameLayout.LayoutParams(dp(28), dp(3), Gravity.CENTER) else FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER)
            addView(
              createAssetCard(face, label, faceSize) {
                val layer = PhotoLayer(type = LayerType.SHAPE, shapeKind = kind)
                session.layerStack.commit { it + layer }
                selectLayer(layer.id)
                setStickersMode(false, session)
              },
              LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) }
            )
          }
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
    val shortSide = minOf(photoImageView.currentImageBounds().width(), photoImageView.currentImageBounds().height())
    if (shortSide <= 0f) return
    val newLayers = drawOverlay.normalizedStrokes().mapNotNull { (points, stroke) ->
      if (points.size < 2) return@mapNotNull null
      val centerX = points.sumOf { it.first.toDouble() }.toFloat() / points.size
      val centerY = points.sumOf { it.second.toDouble() }.toFloat() / points.size
      val offsets = points.map { (x, y) -> (x - centerX) to (y - centerY) }
      PhotoLayer(
        type = LayerType.DRAWING,
        x = centerX,
        y = centerY,
        drawColor = stroke.color,
        drawStrokeWidth = stroke.widthPx / shortSide,
        drawPoints = offsets
      )
    }
    if (newLayers.isNotEmpty()) session.layerStack.commit { it + newLayers }
    onLayerStackChanged()
  }

  /** Circular color swatch, matching the drawing_brush_studio mockup's palette dots. */
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

  private fun createDrawSubBar(session: PhotoEditSession): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val colors = listOf(
      Color.RED, Color.rgb(255, 214, 51), Color.rgb(76, 217, 100),
      Color.rgb(0, 145, 255), Color.WHITE, Color.BLACK
    )
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          colors.forEach { color ->
            addView(
              createColorSwatch(color) { drawOverlay.strokeColor = color },
              LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(DesignTokens.spaceSm) }
            )
          }
          addView(
            createIconButton(R.drawable.ic_line_weight, "Thin brush", textColor) { drawOverlay.strokeWidthPx = dp(2).toFloat() },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_draw, "Thick brush", textColor) { drawOverlay.strokeWidthPx = dp(10).toFloat() },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_undo, "Undo stroke", textColor) { drawOverlay.undoLastStroke() },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_delete, "Clear drawing", textColor) { drawOverlay.clearStrokes() },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { setDrawMode(false, session) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  private fun selectLayer(id: String?) {
    val session = photoSession
    selectedLayerId = id
    layerOverlay.selectedLayerId = id
    if (session != null) {
      layerOverlay.layers = session.layerStack.layers
      schedulePhotoPreviewRender()
    }
    setLayerToolBarVisible(id != null)
  }

  private fun setLayerToolBarVisible(visible: Boolean) {
    layerToolBar.visibility = if (visible) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (visible) View.GONE else View.VISIBLE
    if (visible) {
      val isText = currentSelectedLayer()?.type == LayerType.TEXT
      layerPropertyButtons["fontSize"]?.visibility = if (isText) View.VISIBLE else View.GONE
      layerPropertyButtons["color"]?.visibility = if (isText) View.VISIBLE else View.GONE
      if (!isText && activeLayerPropertyKey in setOf("fontSize", "color")) {
        activeLayerPropertyKey = "scale"
      }
      layerPropertySeekBar.visibility = if (activeLayerPropertyKey == "color") View.GONE else View.VISIBLE
      layerColorSwatchRow.visibility = if (activeLayerPropertyKey == "color") View.VISIBLE else View.GONE
      syncLayerPropertySeekBar()
      refreshSelection(layerPropertyButtons, activeLayerPropertyKey, themeColor("textColor", DesignTokens.onSurface), themeColor("primaryColor", DesignTokens.primaryContainer))
    } else {
      layerPropertySeekBar.visibility = View.GONE
      layerColorSwatchRow.visibility = View.GONE
    }
  }

  /** Text color swatch row, shown when a TEXT layer's "Color" property is active (text_editor_typography_studio mockup). */
  private fun createTextColorSwatchRow(session: PhotoEditSession): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val colors = listOf(
      Color.WHITE, Color.BLACK, Color.RED, Color.rgb(255, 214, 51),
      Color.rgb(76, 217, 100), Color.rgb(0, 145, 255), DesignTokens.primary, DesignTokens.tertiary
    )
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          colors.forEach { color ->
            addView(
              createColorSwatch(color) {
                val id = selectedLayerId ?: return@createColorSwatch
                session.layerStack.commit { list -> list.map { if (it.id == id) it.copy(textColor = color) else it } }
                onLayerStackChanged()
              },
              LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(DesignTokens.spaceSm) }
            )
          }
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { selectLayer(null) }.apply { setTextColor(themeColor("primaryColor", DesignTokens.primaryContainer)) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  private fun currentSelectedLayer(): PhotoLayer? {
    val session = photoSession ?: return null
    return session.layerStack.layers.firstOrNull { it.id == selectedLayerId }
  }

  private fun syncLayerPropertySeekBar() {
    val layer = currentSelectedLayer() ?: return
    val (min, max, value) = when (activeLayerPropertyKey) {
      "rotation" -> Triple(-180f, 180f, layer.rotationDegrees)
      "opacity" -> Triple(0f, 100f, layer.opacity * 100f)
      "fontSize" -> Triple(12f, 160f, layer.fontSize)
      else -> Triple(30f, 300f, layer.scale * 100f)
    }
    layerPropertySeekBar.max = (max - min).roundToInt()
    layerPropertySeekBar.progress = (value - min).roundToInt()
  }

  private fun onLayerPropertyChanged(progress: Int) {
    val session = photoSession ?: return
    val id = selectedLayerId ?: return
    val min = when (activeLayerPropertyKey) {
      "rotation" -> -180f
      "opacity" -> 0f
      "fontSize" -> 12f
      else -> 30f
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

  private fun createLayerToolBar(session: PhotoEditSession): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    // "fontSize"/"color" only make sense for TEXT layers; their chips are hidden/shown per-selection in `setLayerToolBarVisible`.
    val properties = listOf("scale" to "Scale", "rotation" to "Rotate", "opacity" to "Opacity", "fontSize" to "Size", "color" to "Color")
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          properties.forEach { (key, label) ->
            val chip = actionButton(label) {
              activeLayerPropertyKey = key
              layerPropertySeekBar.visibility = if (key == "color") View.GONE else View.VISIBLE
              layerColorSwatchRow.visibility = if (key == "color") View.VISIBLE else View.GONE
              if (key != "color") syncLayerPropertySeekBar()
              refreshSelection(layerPropertyButtons, activeLayerPropertyKey, textColor, primaryColor)
            }.apply { setTextColor(textColor) }
            layerPropertyButtons[key] = chip
            addView(chip, LinearLayout.LayoutParams(dp(80), MATCH))
          }
          addView(
            createIconButton(R.drawable.ic_content_copy, "Duplicate layer", textColor) {
              val original = currentSelectedLayer() ?: return@createIconButton
              val copy = original.copy(id = java.util.UUID.randomUUID().toString(), x = (original.x + 0.04f).coerceIn(0f, 1f), y = (original.y + 0.04f).coerceIn(0f, 1f))
              session.layerStack.commit { it + copy }
              selectLayer(copy.id)
            },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_lock, "Toggle lock", textColor) {
              val id = selectedLayerId ?: return@createIconButton
              session.layerStack.commit { list -> list.map { if (it.id == id) it.copy(locked = !it.locked) else it } }
              onLayerStackChanged()
            },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_visibility_off, "Toggle visibility", textColor) {
              val id = selectedLayerId ?: return@createIconButton
              session.layerStack.commit { list -> list.map { if (it.id == id) it.copy(visible = !it.visible) else it } }
              onLayerStackChanged()
            },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_chevron_right, "Bring to front", textColor) {
              val id = selectedLayerId ?: return@createIconButton
              session.layerStack.commit { list -> (list.filter { it.id != id } + list.first { it.id == id }) }
              onLayerStackChanged()
            },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_chevron_left, "Send to back", textColor) {
              val id = selectedLayerId ?: return@createIconButton
              session.layerStack.commit { list -> (listOf(list.first { it.id == id }) + list.filter { it.id != id }) }
              onLayerStackChanged()
            },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
          addView(
            createIconButton(R.drawable.ic_delete, "Delete layer", textColor) {
              val id = selectedLayerId ?: return@createIconButton
              session.layerStack.commit { list -> list.filterNot { it.id == id } }
              selectLayer(null)
              onLayerStackChanged()
            },
            LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
          )
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { selectLayer(null) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  /** Adjust (image_adjustments_workspace mockup) — Brightness/Contrast/Saturation/etc sliders + Mirror. */
  private fun setAdjustMode(enabled: Boolean, session: PhotoEditSession) {
    adjustMode = enabled
    adjustSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    adjustmentSeekBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    if (enabled) { activeAdjustmentKey = "brightness"; syncAdjustmentSeekBar(session) }
    refreshPhotoToolSelection()
    refreshAdjustSelection()
  }

  /** Filters (filter_browser_library mockup) — preset thumbnail chips + strength slider. */
  private fun setFiltersMode(enabled: Boolean, session: PhotoEditSession) {
    filtersMode = enabled
    filtersSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    adjustmentSeekBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    if (enabled) { activeAdjustmentKey = "filterStrength"; syncAdjustmentSeekBar(session) }
    refreshPhotoToolSelection()
    refreshFiltersSelection(session)
  }

  private fun refreshAdjustSelection() {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    refreshSelection(filterAdjustmentButtons, activeAdjustmentKey, textColor, primaryColor)
  }

  private fun refreshFiltersSelection(session: PhotoEditSession) {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val activePreset = session.adjustments.filterPreset ?: ""
    filterPresetCards.forEach { (id, pair) ->
      val (frame, label) = pair
      val selected = id == activePreset
      frame.background = roundedDrawable(DesignTokens.surfaceContainerHighest, DesignTokens.radiusLg)
      frame.foreground = if (selected) {
        android.graphics.drawable.GradientDrawable().apply {
          cornerRadius = dp(DesignTokens.radiusLg).toFloat()
          setStroke(dp(2), DesignTokens.primaryContainer)
        }
      } else {
        null
      }
      label.setTextColor(if (selected) primaryColor else DesignTokens.outline)
      label.setTypeface(label.typeface, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }
  }

  private fun syncAdjustmentSeekBar(session: PhotoEditSession) {
    val (min, max) = PhotoAdjustments.rangeFor(activeAdjustmentKey)
    adjustmentSeekBar.max = (max - min).roundToInt()
    adjustmentSeekBar.progress = (session.adjustments.value(activeAdjustmentKey) - min).roundToInt()
  }

  private fun createAdjustSubBar(session: PhotoEditSession, imageView: ZoomableImageView): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val adjustmentChips = listOf(
      "brightness" to "Brightness", "contrast" to "Contrast", "saturation" to "Saturation",
      "exposure" to "Exposure", "gamma" to "Gamma", "temperature" to "Temp", "tint" to "Tint",
      "highlights" to "Highlights", "shadows" to "Shadows", "sharpness" to "Sharpen",
      "blurRadius" to "Blur", "pixelSize" to "Pixelate"
    )
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          adjustmentChips.forEach { (key, label) ->
            val chip = actionButton(label) {
              activeAdjustmentKey = key
              syncAdjustmentSeekBar(session)
              refreshAdjustSelection()
            }.apply { setTextColor(textColor) }
            filterAdjustmentButtons[key] = chip
            addView(chip, LinearLayout.LayoutParams(dp(84), MATCH))
          }
          addView(
            actionButton("Mirror") {
              session.updateAdjustments { it.copy(mirror = !it.mirror) }
              schedulePhotoPreviewRender()
            }.apply { setTextColor(textColor) },
            LinearLayout.LayoutParams(dp(84), MATCH)
          )
          addView(
            actionButton("Reset") {
              session.updateAdjustments { it.copy(brightness = 0f, contrast = 0f, saturation = 0f, exposure = 0f, gamma = 0f, temperature = 0f, tint = 0f, highlights = 0f, shadows = 0f, sharpness = 0f, blurRadius = 0f, pixelSize = 0f, mirror = false) }
              syncAdjustmentSeekBar(session)
              schedulePhotoPreviewRender()
              refreshAdjustSelection()
            }.apply { setTextColor(textColor) },
            LinearLayout.LayoutParams(dp(72), MATCH)
          )
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { setAdjustMode(false, session) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  /** Per-preset small preview bitmaps, lazily rendered once from the source photo — matches the mockup's live preset thumbnails rather than plain text chips. */
  private val photoFilterThumbnailCache = mutableMapOf<String, Bitmap>()

  private fun photoFilterThumbnail(session: PhotoEditSession, presetId: String): Bitmap? {
    photoFilterThumbnailCache[presetId]?.let { return it }
    val base = session.baseBitmap ?: return null
    val size = dp(64)
    val square = Bitmap.createScaledBitmap(base, size, size, true)
    val rendered = PhotoAdjustmentRenderer.apply(square, PhotoAdjustments(filterPreset = presetId, filterStrength = 100f))
    photoFilterThumbnailCache[presetId] = rendered
    return rendered
  }

  /** Preset thumbnail card (64x64 preview + label), matching the "Preset Thumbnail Cards" component. */
  private fun createPresetCard(thumbnail: Bitmap?, labelText: String, textColor: Int, onClick: () -> Unit): Triple<LinearLayout, FrameLayout, TextView> {
    val thumbSize = dp(64)
    val image = ImageView(this).apply {
      thumbnail?.let { setImageBitmap(it) }
      scaleType = ImageView.ScaleType.CENTER_CROP
    }
    val frame = FrameLayout(this).apply {
      background = roundedDrawable(DesignTokens.surfaceContainerHighest, DesignTokens.radiusLg)
      clipToOutline = true
      addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
    }
    val label = TextView(this).apply {
      text = labelText
      textSize = 11f
      gravity = Gravity.CENTER
      setTextColor(DesignTokens.outline)
      setPadding(0, dp(DesignTokens.spaceXs), 0, 0)
      maxLines = 1
    }
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      isClickable = true
      setPadding(dp(4), 0, dp(4), 0)
      addView(frame, LinearLayout.LayoutParams(thumbSize, thumbSize))
      addView(label, LinearLayout.LayoutParams(dp(72), WRAP))
      setOnClickListener { onClick() }
    }
    return Triple(root, frame, label)
  }

  private fun createFiltersSubBar(session: PhotoEditSession, imageView: ZoomableImageView): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), dp(8), dp(12), dp(8))
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          val (originalCard, originalFrame, originalLabel) = createPresetCard(session.baseBitmap, "Original", textColor) {
            session.updateAdjustments { it.copy(filterPreset = null, filterStrength = 100f) }
            syncAdjustmentSeekBar(session)
            schedulePhotoPreviewRender()
            refreshFiltersSelection(session)
          }
          addView(originalCard, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) })
          PhotoFilterPresets.PRESET_IDS.forEach { id ->
            val (card, frame, label) = createPresetCard(photoFilterThumbnail(session, id), PhotoFilterPresets.labelFor(id), textColor) {
              session.updateAdjustments { it.copy(filterPreset = if (it.filterPreset == id) null else id) }
              activeAdjustmentKey = "filterStrength"
              syncAdjustmentSeekBar(session)
              schedulePhotoPreviewRender()
              refreshFiltersSelection(session)
            }
            filterPresetCards[id] = Pair(frame, label)
            addView(card, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) })
          }
          filterPresetCards[""] = Pair(originalFrame, originalLabel)
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { setFiltersMode(false, session) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  /** Blur & Depth (blur_depth_editor mockup) — synthetic tilt-shift/radial focus blur. See [PhotoDepthBlur] for why "Portrait" (ML) mode is out of scope. */
  private fun setBlurMode(enabled: Boolean, session: PhotoEditSession, imageView: ZoomableImageView) {
    blurMode = enabled
    blurSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    blurSeekBar.visibility = if (enabled && session.depthBlur.mode != null) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    if (enabled) syncBlurSeekBar(session)
    refreshPhotoToolSelection()
    refreshBlurSelection(session)
  }

  private fun blurValue(blur: PhotoDepthBlur, key: String): Float = when (key) {
    "intensity" -> blur.intensity
    "feather" -> blur.feather * 100f
    "size" -> if (blur.mode == PhotoDepthBlur.Mode.LINEAR) blur.bandWidth * 100f else blur.radius * 100f
    else -> 0f
  }

  private fun withBlurValue(blur: PhotoDepthBlur, key: String, value: Float): PhotoDepthBlur = when (key) {
    "intensity" -> blur.copy(intensity = value)
    "feather" -> blur.copy(feather = (value / 100f).coerceIn(0.01f, 1f))
    "size" -> if (blur.mode == PhotoDepthBlur.Mode.LINEAR) blur.copy(bandWidth = (value / 100f).coerceIn(0.01f, 1f)) else blur.copy(radius = (value / 100f).coerceIn(0.01f, 1f))
    else -> blur
  }

  private fun syncBlurSeekBar(session: PhotoEditSession) {
    blurSeekBar.max = 100
    blurSeekBar.progress = blurValue(session.depthBlur, activeBlurKey).roundToInt().coerceIn(0, 100)
  }

  private fun refreshBlurSelection(session: PhotoEditSession) {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    refreshSelection(blurModeButtons, session.depthBlur.mode, textColor, primaryColor)
    refreshSelection(blurKeyButtons, activeBlurKey, textColor, primaryColor)
  }

  private fun createBlurSubBar(session: PhotoEditSession, imageView: ZoomableImageView): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val modes = listOf<Pair<PhotoDepthBlur.Mode?, String>>(null to "Off", PhotoDepthBlur.Mode.RADIAL to "Radial", PhotoDepthBlur.Mode.LINEAR to "Linear")
    val keys = listOf("size" to "Size", "feather" to "Feather", "intensity" to "Intensity")
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          modes.forEach { (mode, label) ->
            val chip = actionButton(label) {
              session.updateDepthBlur { it.copy(mode = mode) }
              schedulePhotoPreviewRender()
              blurSeekBar.visibility = if (mode != null) View.VISIBLE else View.GONE
              if (mode != null) syncBlurSeekBar(session)
              refreshBlurSelection(session)
            }.apply { setTextColor(textColor) }
            blurModeButtons[mode] = chip
            addView(chip, LinearLayout.LayoutParams(dp(72), MATCH))
          }
          keys.forEach { (key, label) ->
            val chip = actionButton(label) {
              activeBlurKey = key
              syncBlurSeekBar(session)
              refreshBlurSelection(session)
            }.apply { setTextColor(textColor) }
            blurKeyButtons[key] = chip
            addView(chip, LinearLayout.LayoutParams(dp(80), MATCH))
          }
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { setBlurMode(false, session, imageView) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  private fun setCropMode(enabled: Boolean, session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView) {
    cropMode = enabled
    imageView.panZoomEnabled = !enabled
    overlay.visibility = if (enabled) View.VISIBLE else View.GONE
    straightenSeekBar.visibility = if (enabled) View.VISIBLE else View.GONE
    cropSubBar.visibility = if (enabled) View.VISIBLE else View.GONE
    mainToolBar.visibility = if (enabled) View.GONE else View.VISIBLE
    layerOverlay.visibility = if (enabled) View.GONE else View.VISIBLE
    if (enabled) {
      imageView.resetToFit()
      val state = session.state
      val isIdentityCrop = state.cropLeft == 0f && state.cropTop == 0f && state.cropRight == 1f && state.cropBottom == 1f
      overlay.setImageBounds(imageView.currentImageBounds(), resetCrop = isIdentityCrop)
    }
    refreshPhotoToolSelection()
    refreshSelection(cropAspectButtons, session.state.aspectRatio, themeColor("textColor", DesignTokens.onSurface), themeColor("primaryColor", DesignTokens.primaryContainer))
  }

  private fun createCropSubBar(session: PhotoEditSession, imageView: ZoomableImageView, overlay: CropOverlayView): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val aspectPresets = listOf<Pair<String, Float?>>(
      "Free" to null, "1:1" to 1f, "4:5" to 4f / 5f, "3:4" to 3f / 4f, "9:16" to 9f / 16f, "16:9" to 16f / 9f
    )
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(0, dp(6), 0, dp(6))
      setBackgroundColor(toolbarColor)

      addView(TextView(this@PhotoVideoEditorActivity).apply {
        text = "Crop Mode   •   TRANSFORM & ASPECT RATIO"
        textSize = 14f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(textColor)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(DesignTokens.spaceLg), 0, dp(DesignTokens.spaceLg), 0)
      }, LinearLayout.LayoutParams(MATCH, dp(36)))

      // Rotate/Flip row — moved here from the main tool rail to match the crop_transform_editor mockup.
      addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        addView(
          createIconButton(R.drawable.ic_rotate_left, "Rotate left", textColor) {
            session.update { it.rotatedRight().rotatedRight().rotatedRight() }
            schedulePhotoPreviewRender()
            imageView.post { imageView.resetToFit(); overlay.setImageBounds(imageView.currentImageBounds(), resetCrop = true) }
          },
          LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
        )
        addView(
          createIconButton(R.drawable.ic_rotate_right, "Rotate right", textColor) {
            session.update { it.rotatedRight() }
            schedulePhotoPreviewRender()
            imageView.post { imageView.resetToFit(); overlay.setImageBounds(imageView.currentImageBounds(), resetCrop = true) }
          },
          LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin))
        )
        addView(
          createIconButton(R.drawable.ic_flip, "Flip", textColor) {
            session.update { it.cycledFlip() }
            schedulePhotoPreviewRender()
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
            aspectPresets.forEach { (label, ratio) ->
              val chip = actionButton(label) {
                overlay.setAspectRatio(ratio)
                session.update { it.withAspectRatio(ratio) }
                refreshSelection(cropAspectButtons, ratio, textColor, primaryColor)
              }.apply { setTextColor(textColor) }
              cropAspectButtons[ratio] = chip
              addView(chip, LinearLayout.LayoutParams(dp(64), MATCH))
            }
            addView(
              actionButton("Reset") {
                session.update { it.reset() }
                overlay.setAspectRatio(null)
                straightenSeekBar.progress = 45
                schedulePhotoPreviewRender()
                imageView.resetToFit()
                overlay.setImageBounds(imageView.currentImageBounds(), resetCrop = true)
                refreshSelection(cropAspectButtons, null, textColor, primaryColor)
              }.apply { setTextColor(textColor) },
              LinearLayout.LayoutParams(dp(72), MATCH)
            )
          })
        }, LinearLayout.LayoutParams(0, MATCH, 1f))
        addView(
          actionButton("Done") { setCropMode(false, session, imageView, overlay) }.apply { setTextColor(primaryColor) },
          LinearLayout.LayoutParams(dp(72), MATCH)
        )
      }, LinearLayout.LayoutParams(MATCH, dp(56)))
    }
  }

  /** Canvas Resize & Social Presets (canvas_resize_social_presets mockup): reuses the crop-aspect mechanism to frame the shot, plus an exact export pixel size for the chosen platform. */
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
    if (drawMode) commitDrawStrokes(session)
    exportProgressView.visibility = View.VISIBLE
    val state = session.state
    val adjustments = session.adjustments
    val depthBlur = session.depthBlur
    val layers = session.layerStack.layers
    // A chosen Resize/social preset overrides the consumer's own maxWidth/maxHeight, since the
    // user explicitly picked an exact output size (matching the crop aspect ratio set alongside it).
    val exportOptions = (request.optJSONObject("export") ?: JSONObject()).apply {
      resizePresetSize?.let { (width, height) -> put("maxWidth", width); put("maxHeight", height) }
    }
    Thread {
      try {
        val result = PhotoExporter.export(applicationContext, sourceUri, state, adjustments, depthBlur, layers, exportOptions)
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
    root.addView(preview, LinearLayout.LayoutParams(MATCH, 0, 1f))

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
    preview.addView(playerView, FrameLayout.LayoutParams(MATCH, MATCH))

    val overlayView = ImageView(this).apply {
      contentDescription = "Video overlays"
      // Match PlayerView's default RESIZE_MODE_FIT letterboxing so the overlay bitmap (sized to the
      // video's own aspect ratio) lines up with the actual displayed video frame, not the full view.
      scaleType = ImageView.ScaleType.FIT_CENTER
    }
    videoOverlayImageView = overlayView
    preview.addView(overlayView, FrameLayout.LayoutParams(MATCH, MATCH))

    val layerOverlay = LayerOverlayView(this)
    videoLayerOverlay = layerOverlay
    preview.addView(layerOverlay, FrameLayout.LayoutParams(MATCH, MATCH))
    layerOverlay.onLayerTapped = { id -> selectVideoLayer(id, session) }
    layerOverlay.onLayerDragged = { id, x, y ->
      if (videoDragStartSnapshot == null) videoDragStartSnapshot = session.layerStack.layers
      session.layerStack.updateLive { list -> list.map { if (it.id == id) it.copy(x = x, y = y) else it } }
      videoLayerOverlay.layers = session.layerStack.layers
      refreshVideoOverlayPreview(session)
    }
    layerOverlay.onLayerDragEnded = {
      videoDragStartSnapshot?.let { session.layerStack.commitSnapshot(it) }
      videoDragStartSnapshot = null
      onVideoLayerStackChanged()
    }

    val playPause = Button(this).apply {
      text = "Play"
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.TRANSPARENT)
      setOnClickListener {
        if (session.player.isPlaying) session.player.pause() else session.player.play()
      }
    }
    playPauseButton = playPause
    val time = TextView(this).apply {
      text = "0:00 / 0:00"
      setTextColor(textColor)
      textSize = 12f
    }
    timeLabel = time
    val transport = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setBackgroundColor(toolbarColor)
      setPadding(dp(8), 0, dp(8), 0)
      addView(playPause, LinearLayout.LayoutParams(dp(72), WRAP))
      addView(time, LinearLayout.LayoutParams(WRAP, WRAP))
    }
    root.addView(transport, LinearLayout.LayoutParams(MATCH, dp(40)))

    val seek = SeekBar(this).apply {
      max = 1000
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (!fromUser || session.durationMs <= 0) return
          session.seekToGlobalMs(session.durationMs * progress / 1000)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) { seeking = true }
        override fun onStopTrackingTouch(seekBar: SeekBar?) { seeking = false }
      })
    }
    positionSeekBar = seek
    root.addView(seek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(8), 0, dp(8), 0) })

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
    root.addView(trim, LinearLayout.LayoutParams(MATCH, dp(48)).apply { setMargins(dp(8), dp(4), dp(8), 0) })

    val strip = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setPadding(dp(8), dp(4), dp(8), dp(4))
    }
    clipStripBar = strip
    root.addView(strip, LinearLayout.LayoutParams(MATCH, WRAP))

    session.onClipsReady = {
      runOnUiThread {
        selectedClipIndex = 0
        updateTimeLabel()
        bindTrimViewToSelectedClip(session)
        refreshClipStrip(session)
      }
    }

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

    val layerPropertySeek = SeekBar(this).apply {
      visibility = View.GONE
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (fromUser) onVideoLayerPropertyChanged(progress, session)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      })
    }
    videoLayerPropertySeekBar = layerPropertySeek
    root.addView(layerPropertySeek, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(dp(8), dp(4), dp(8), 0) })

    val layerBar = createVideoLayerToolBar(session)
    layerBar.visibility = View.GONE
    videoLayerToolBar = layerBar
    root.addView(layerBar, LinearLayout.LayoutParams(MATCH, dp(56)))

    val toolbarHeight = dp(76)
    val toolbar = createVideoToolBar(session, playerView)
    videoToolBar = toolbar
    root.addView(toolbar, LinearLayout.LayoutParams(MATCH, toolbarHeight))
    applySystemBarInsets(
      header,
      headerHeight,
      listOf(toolbar to toolbarHeight, aspectBar to dp(CROP_SUB_BAR_HEIGHT_DP), filtersBar to dp(56), layerBar to dp(56))
    )

    startPositionPolling(session)
    return outer
  }

  private fun startPositionPolling(session: VideoEditSession) {
    val runnable = object : Runnable {
      override fun run() {
        if (!seeking && session.durationMs > 0) {
          positionSeekBar.progress = (session.globalPositionMs() * 1000 / session.durationMs).toInt().coerceIn(0, 1000)
          updateTimeLabel()
        }
        playPauseButton.text = if (session.player.isPlaying) "Pause" else "Play"
        refreshVideoOverlayPreview(session)
        positionPollHandler.postDelayed(this, 250)
      }
    }
    positionPollRunnable = runnable
    positionPollHandler.postDelayed(runnable, 250)
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
    val videoSize = session.player.videoSize
    val width = videoSize.width.takeIf { it > 0 } ?: 1280
    val height = videoSize.height.takeIf { it > 0 } ?: 720
    val renderKey = "${session.layerStack.revision}:$width:$height:${activeLayers.joinToString { it.id }}"
    if (renderKey == videoOverlayRenderKey) return
    val transparent = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val rendered = PhotoLayerRenderer.render(transparent, activeLayers, ::resolveVideoSticker)
    transparent.recycle()
    val previous = videoOverlayBitmap
    videoOverlayBitmap = rendered
    videoOverlayRenderKey = renderKey
    videoOverlayImageView.setImageBitmap(rendered)
    previous?.takeIf { it !== rendered && !it.isRecycled }?.recycle()
  }

  /** Where the video actually renders within [view] under `FIT_CENTER`-style letterboxing (mirrors `ZoomableImageView.resetToFit`'s math). */
  private fun computeVideoLetterboxBounds(session: VideoEditSession, view: View): android.graphics.RectF {
    val viewWidth = view.width.toFloat()
    val viewHeight = view.height.toFloat()
    val videoSize = session.player.videoSize
    if (viewWidth <= 0 || viewHeight <= 0 || videoSize.width <= 0 || videoSize.height <= 0) {
      return android.graphics.RectF(0f, 0f, viewWidth, viewHeight)
    }
    val scale = minOf(viewWidth / videoSize.width, viewHeight / videoSize.height)
    val fittedWidth = videoSize.width * scale
    val fittedHeight = videoSize.height * scale
    val left = (viewWidth - fittedWidth) / 2f
    val top = (viewHeight - fittedHeight) / 2f
    return android.graphics.RectF(left, top, left + fittedWidth, top + fittedHeight)
  }

  private fun resolveVideoSticker(uri: String): Bitmap? {
    if (videoStickerCache.containsKey(uri)) return videoStickerCache[uri]
    val path = SourceResolver.resolvePath(this, uri, "pve_video_sticker") ?: return null
    return BitmapFactory.decodeFile(path).also { videoStickerCache[uri] = it }
  }

  /** Mirrors [onLayerStackChanged] for the video overlay stack (Milestone 6). */
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
    selectedVideoLayerId = id
    videoLayerOverlay.selectedLayerId = id
    setVideoLayerToolBarVisible(id != null, session)
  }

  private fun setVideoLayerToolBarVisible(visible: Boolean, session: VideoEditSession) {
    videoLayerToolBar.visibility = if (visible) View.VISIBLE else View.GONE
    videoLayerPropertySeekBar.visibility = if (visible) View.VISIBLE else View.GONE
    videoToolBar.visibility = if (visible) View.GONE else View.VISIBLE
    if (visible) syncVideoLayerPropertySeekBar(session)
  }

  private fun currentSelectedVideoLayer(session: VideoEditSession): PhotoLayer? =
    session.layerStack.layers.firstOrNull { it.id == selectedVideoLayerId }

  private fun syncVideoLayerPropertySeekBar(session: VideoEditSession) {
    val layer = currentSelectedVideoLayer(session) ?: return
    val (min, max, value) = when (activeVideoLayerPropertyKey) {
      "rotation" -> Triple(-180f, 180f, layer.rotationDegrees)
      "opacity" -> Triple(0f, 100f, layer.opacity * 100f)
      "start" -> Triple(0f, session.durationMs.toFloat(), layer.startMs.toFloat())
      "end" -> Triple(0f, session.durationMs.toFloat(), session.durationMs.effectiveEndOr(layer.endMs).toFloat())
      else -> Triple(30f, 300f, layer.scale * 100f)
    }
    videoLayerPropertySeekBar.max = (max - min).roundToInt()
    videoLayerPropertySeekBar.progress = (value - min).roundToInt()
  }

  private fun Long.effectiveEndOr(endMs: Long): Long = if (endMs in 1..this) endMs else this

  private fun onVideoLayerPropertyChanged(progress: Int, session: VideoEditSession) {
    val id = selectedVideoLayerId ?: return
    val min = when (activeVideoLayerPropertyKey) {
      "rotation" -> -180f
      "opacity", "start", "end" -> 0f
      else -> 30f
    }
    val value = min + progress
    session.layerStack.updateLive { list ->
      list.map { layer ->
        if (layer.id != id) return@map layer
        when (activeVideoLayerPropertyKey) {
          "rotation" -> layer.copy(rotationDegrees = value)
          "opacity" -> layer.copy(opacity = value / 100f)
          "start" -> layer.copy(startMs = value.toLong().coerceAtMost(layer.endMs.takeIf { it > 0 } ?: session.durationMs))
          "end" -> layer.copy(endMs = value.toLong().coerceAtLeast(layer.startMs))
          else -> layer.copy(scale = value / 100f)
        }
      }
    }
    refreshVideoOverlayPreview(session)
  }

  private fun createVideoLayerToolBar(session: VideoEditSession): LinearLayout {
    val toolbarColor = themeColor("toolbarColor", DesignTokens.surfaceContainerLow)
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    val primaryColor = themeColor("primaryColor", DesignTokens.primaryContainer)
    val properties = listOf(
      "scale" to "Scale", "rotation" to "Rotate", "opacity" to "Opacity", "start" to "Start", "end" to "End"
    )
    return LinearLayout(this).apply {
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      setBackgroundColor(toolbarColor)
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          properties.forEach { (key, label) ->
            val chip = actionButton(label) {
              activeVideoLayerPropertyKey = key
              syncVideoLayerPropertySeekBar(session)
              refreshSelection(videoLayerPropertyButtons, activeVideoLayerPropertyKey, textColor, primaryColor)
            }.apply { setTextColor(textColor) }
            videoLayerPropertyButtons[key] = chip
            addView(chip, LinearLayout.LayoutParams(dp(72), MATCH))
          }
          addView(
            actionButton("Delete") {
              val id = selectedVideoLayerId ?: return@actionButton
              session.layerStack.commit { list -> list.filterNot { it.id == id } }
              selectVideoLayer(null, session)
              onVideoLayerStackChanged()
            }.apply { setTextColor(textColor) },
            LinearLayout.LayoutParams(dp(80), MATCH)
          )
        })
      }, LinearLayout.LayoutParams(0, MATCH, 1f))
      addView(
        actionButton("Done") { selectVideoLayer(null, session) }.apply { setTextColor(primaryColor) },
        LinearLayout.LayoutParams(dp(72), MATCH)
      )
    }
  }

  private fun showVideoTextInputDialog(session: VideoEditSession) {
    val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
    AlertDialog.Builder(this)
      .setTitle("Add text")
      .setView(input)
      .setPositiveButton("Add") { _, _ ->
        val text = input.text?.toString().orEmpty()
        if (text.isNotBlank()) {
          session.layerStack.commit { it + PhotoLayer(type = LayerType.TEXT, text = text) }
          onVideoLayerStackChanged()
        }
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showVideoStickerPicker(session: VideoEditSession) {
    val builtin = PhotoLayerRenderer.builtinStickerIds()
    val shapes = listOf(ShapeKind.RECTANGLE to "▭ Rectangle", ShapeKind.OVAL to "◯ Oval", ShapeKind.LINE to "▬ Line")
    val labels = (builtin.map { PhotoLayerRenderer.glyphFor(it) } + shapes.map { it.second }).toTypedArray()
    AlertDialog.Builder(this)
      .setTitle("Add sticker or shape")
      .setItems(labels) { _, index ->
        val layer = if (index < builtin.size) {
          PhotoLayer(type = LayerType.STICKER, stickerId = builtin[index])
        } else {
          PhotoLayer(type = LayerType.SHAPE, shapeKind = shapes[index - builtin.size].first)
        }
        session.layerStack.commit { it + layer }
        onVideoLayerStackChanged()
      }
      .show()
  }

  private fun createVideoToolBar(session: VideoEditSession, playerView: PlayerView): LinearLayout {
    val backgroundColor = themeColor("toolbarColor", DesignTokens.withAlphaPercent(DesignTokens.surfaceContainerLowest, 95))
    return LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(backgroundColor)
      setPadding(dp(DesignTokens.spaceXs), dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs), dp(DesignTokens.spaceLg))

      addView(
        View(this@PhotoVideoEditorActivity).apply { background = roundedDrawable(DesignTokens.surfaceVariant, DesignTokens.radiusSm) },
        LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(DesignTokens.spaceXs) }
      )

      val features = request.optJSONObject("features")
      val tools = listOf(
        Triple("mute", "Mute", R.drawable.ic_volume_off),
        Triple("cover", "Cover", R.drawable.ic_movie),
        Triple("speed", "Speed 1x", R.drawable.ic_speed),
        Triple("crop", "Crop", R.drawable.ic_crop),
        Triple("filters", "Filters", R.drawable.ic_photo_filter),
        Triple("text", "Text", R.drawable.ic_title),
        Triple("stickers", "Stickers", R.drawable.ic_sentiment_satisfied)
      )
      addView(HorizontalScrollView(this@PhotoVideoEditorActivity).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@PhotoVideoEditorActivity).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          setPadding(dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs), dp(DesignTokens.spaceSm), dp(DesignTokens.spaceXs))
          tools.filter { features?.optBoolean(it.first, true) != false }.forEach { (key, label, iconRes) ->
            val node = createToolNode(iconRes, label) { onVideoToolTapped(key, session, playerView) }
            videoToolButtons[key] = node
            addView(node.root, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) })
          }
        })
      }, LinearLayout.LayoutParams(MATCH, WRAP))
    }
  }

  private fun refreshVideoToolSelection(session: VideoEditSession) {
    val textColor = themeColor("textColor", DesignTokens.onSurface)
    videoToolButtons.forEach { (key, node) ->
      val selected = when (key) {
        "mute" -> session.state.muted
        "crop" -> videoAspectMode
        "filters" -> videoFiltersMode
        else -> false
      }
      setToolNodeSelected(node, selected, textColor)
    }
  }

  private fun onVideoToolTapped(key: String, session: VideoEditSession, playerView: PlayerView) {
    when (key) {
      "mute" -> {
        session.update { it.toggledMute() }
        session.player.volume = if (session.state.muted) 0f else 1f
        refreshVideoToolSelection(session)
      }
      "cover" -> {
        val globalMs = session.globalPositionMs()
        session.update { it.withCoverFrame(globalMs) }
        Toast.makeText(this, "Cover frame set at ${formatMs(globalMs)}", Toast.LENGTH_SHORT).show()
      }
      "rotate" -> {
        session.update { it.rotatedRight() }
        playerView.rotation = session.state.rotationDegrees.toFloat()
      }
      "flip" -> {
        session.update { it.toggledFlip() }
        playerView.scaleX = if (session.state.flipHorizontal) -1f else 1f
      }
      "crop" -> setVideoAspectMode(true, session)
      "speed" -> {
        session.update { it.cycledSpeed() }
        session.player.setPlaybackSpeed(session.state.speed)
        videoToolButtons["speed"]?.label?.text = formatSpeedLabel(session.state.speed)
      }
      "text" -> showVideoTextInputDialog(session)
      "stickers" -> showVideoStickerPicker(session)
      "filters" -> setVideoFiltersMode(true, session)
      else -> Toast.makeText(this, "$key is coming in a later milestone.", Toast.LENGTH_SHORT).show()
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

      // Rotate/Flip row — mirrors the photo Crop sub-bar's treatment.
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
        addView(
          createIconButton(R.drawable.ic_flip, "Flip horizontal", textColor) {
            session.update { it.toggledFlip() }
            playerView.scaleX = if (session.state.flipHorizontal) -1f else 1f
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
    exporter.export(
      clips = session.clips,
      state = session.state,
      layers = session.layerStack.layers,
      exportOptions = request.optJSONObject("export"),
      onProgress = { /* Progress UI is a spinner for now; see docs/video-editor.md. */ },
      onComplete = { result ->
        runOnUiThread {
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
        runOnUiThread { finishWithError(error.message ?: "Unable to export the video.", error.code) }
      }
    )
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
    val cached = clipThumbnailCache[clip.id]
    if (cached != null) {
      trimRangeView.thumbnails = cached
      return
    }
    trimRangeView.thumbnails = emptyList()
    Thread {
      val thumbnails = VideoThumbnailGenerator.generate(applicationContext, clip.sourceUri, clip.originalDurationMs)
      clipThumbnailCache[clip.id] = thumbnails
      runOnUiThread {
        if (session.clips.getOrNull(selectedClipIndex)?.id == clip.id) {
          trimRangeView.thumbnails = thumbnails
        }
      }
    }.start()
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
        textSize = 16f
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
      undoButton = undo
      redoButton = redo
      addView(undo, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin)))
      addView(redo, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin)))

      if (mediaType == "photo") {
        addView(createIconButton(R.drawable.ic_compare, "Hold to compare with original", textColor) {}.apply {
          setOnTouchListener { _, event ->
            when (event.actionMasked) {
              MotionEvent.ACTION_DOWN -> showOriginalPreview(true)
              MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> showOriginalPreview(false)
            }
            true
          }
        }, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin)))
      }

      addView(
        LinearLayout(this@PhotoVideoEditorActivity).apply {
          gravity = Gravity.CENTER
          background = roundedDrawable(primaryColor, DesignTokens.radiusLg)
          setPadding(dp(DesignTokens.spaceMd), 0, dp(DesignTokens.spaceLg), 0)
          isClickable = true
          setOnClickListener { if (mediaType == "photo") showPhotoExportConfiguration() else finishDone() }
          addView(ImageView(this@PhotoVideoEditorActivity).apply {
            setImageResource(R.drawable.ic_share)
            setColorFilter(onPrimaryColor)
          }, LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(DesignTokens.spaceXs) })
          addView(TextView(this@PhotoVideoEditorActivity).apply {
            text = "Export"
            setTextColor(onPrimaryColor)
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
          })
        },
        LinearLayout.LayoutParams(WRAP, dp(40)).apply { marginStart = dp(DesignTokens.spaceXs) }
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

  /** Coalesces rapid slider/drag events to at most one expensive bitmap render per display frame. */
  private fun schedulePhotoPreviewRender() {
    if (photoPreviewRenderPending || !::photoImageView.isInitialized) return
    photoPreviewRenderPending = true
    photoImageView.postOnAnimation {
      photoPreviewRenderPending = false
      val session = photoSession ?: return@postOnAnimation
      if (!isFinishing && !isDestroyed) photoImageView.setImageBitmap(session.renderPreview())
    }
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
      gravity = Gravity.CENTER
      setTextColor(DesignTokens.outline)
      setPadding(0, dp(DesignTokens.spaceXs), 0, 0)
    }
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      isClickable = true
      isFocusable = true
      background = roundedDrawable(Color.TRANSPARENT, DesignTokens.radiusLg)
      setPadding(0, dp(4), 0, dp(4))
      addView(tile, LinearLayout.LayoutParams(dp(DesignTokens.touchTargetMin), dp(DesignTokens.touchTargetMin)))
      addView(label, LinearLayout.LayoutParams(WRAP, WRAP))
      setOnClickListener { onClick() }
    }
    return ToolNode(root, tile, icon, label)
  }

  /** Highlights a tool-rail node the same way the mockups do: filled tile + text/icon tint switching to the theme's text color. */
  private fun setToolNodeSelected(node: ToolNode, selected: Boolean, textColor: Int) {
    node.tile.background = roundedDrawable(
      if (selected) themeColor("primaryColor", DesignTokens.primaryContainer) else DesignTokens.surfaceContainerLow,
      DesignTokens.radiusLg
    )
    node.icon.setColorFilter(if (selected) DesignTokens.onPrimaryContainer else DesignTokens.outline)
    node.label.setTextColor(if (selected) DesignTokens.primary else DesignTokens.outline)
  }

  private fun <K> refreshToolNodeSelection(nodes: Map<K, ToolNode>, activeKey: K?, textColor: Int) {
    nodes.forEach { (key, node) -> setToolNodeSelected(node, key == activeKey, textColor) }
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
  override fun onBackPressed() = confirmDiscardChanges()

  override fun onDestroy() {
    positionPollRunnable?.let { positionPollHandler.removeCallbacks(it) }
    photoPreviewRenderPending = false
    if (::photoImageView.isInitialized) photoImageView.setImageDrawable(null)
    photoSession?.release()
    videoOverlayBitmap?.takeIf { !it.isRecycled }?.recycle()
    videoOverlayBitmap = null
    videoStickerCache.values.filterNotNull().distinct().forEach { if (!it.isRecycled) it.recycle() }
    videoStickerCache.clear()
    videoExporter?.cancel()
    videoSession?.release()
    super.onDestroy()
  }

  private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

  companion object {
    /** Rotate/flip icon row (44dp) + aspect-preset chip row (56dp). */
    const val CROP_SUB_BAR_HEIGHT_DP = 148
    const val ADJUST_SUB_BAR_HEIGHT_DP = 112
    /** 64dp preset thumbnail + label + vertical padding, matching the "Preset Thumbnail Cards" spec. */
    const val FILTERS_SUB_BAR_HEIGHT_DP = 104
    /** 64dp asset card + optional caption + vertical padding, for the Stickers/Shapes grid. */
    const val ASSET_GRID_BAR_HEIGHT_DP = 104
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
  }
}
