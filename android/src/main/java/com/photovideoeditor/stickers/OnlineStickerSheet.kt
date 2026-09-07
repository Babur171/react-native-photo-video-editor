package com.photovideoeditor.stickers

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photovideoeditor.ui.DesignTokens
import com.photovideoeditor.ui.components.dpToPx
import com.photovideoeditor.ui.components.editorChip
import com.photovideoeditor.ui.components.roundedDrawable
import com.photovideoeditor.files.SourceResolver
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.Executors

data class RuntimeSticker(val id: String, val uri: String)

/**
 * Bottom sheet listing bundled sticker assets (search + category filter + grid),
 * built as a plain [Dialog] (no Material `BottomSheetDialog`, which isn't a dependency here) that
 * slides up from the bottom and covers ~85% of the screen height.
 */
class OnlineStickerSheet(
  context: Context,
  private val runtimeStickers: List<RuntimeSticker> = emptyList(),
  private val onStickerPicked: (localFilePath: String) -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private val ioExecutor = Executors.newFixedThreadPool(3)
  private var searchDebounce: Runnable? = null

  private val localAssets: List<String> by lazy {
    context.assets.list("Stickers")?.filter { it.endsWith(".png", ignoreCase = true) }?.sorted() ?: emptyList()
  }
  private var selectedGroup: String? = null // null == "All"
  private var searchQuery: String = ""

  private lateinit var searchInput: EditText
  private lateinit var chipRow: LinearLayout
  private lateinit var recycler: RecyclerView
  private lateinit var adapter: StickerAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(buildRootView())
    configureWindow()
  }

  override fun dismiss() {
    ioExecutor.shutdownNow()
    super.dismiss()
  }

  private fun configureWindow() {
    val window = this.window ?: return
    window.setBackgroundDrawableResource(android.R.color.transparent)
    window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    window.setGravity(Gravity.BOTTOM)
    window.setWindowAnimations(android.R.style.Animation_InputMethod)
    setCanceledOnTouchOutside(true)
  }

  private fun dp(value: Int) = dpToPx(value, context)

  private fun buildRootView(): View {
    val screenHeight = context.resources.displayMetrics.heightPixels
    val sheetHeight = (screenHeight * 0.78f).toInt()

    val dimBackground = FrameLayout(context).apply {
      setBackgroundColor(Color.argb(140, 0, 0, 0))
      setOnClickListener { dismiss() }
    }

    val sheet = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      background = roundedTopDrawable()
      isClickable = true
      setPadding(dp(DesignTokens.spaceLg), dp(DesignTokens.spaceMd), dp(DesignTokens.spaceLg), dp(DesignTokens.spaceLg))
    }

    // Functional drag handle with a generous touch target. The sheet follows
    // the finger, dismisses past the threshold, or springs back into place.
    var dragStartY = 0f
    var sheetStartTranslation = 0f
    val dragHandle = FrameLayout(context).apply {
      isClickable = true
      contentDescription = "Drag down to close stickers"
      addView(
        View(context).apply { setBackgroundColor(DesignTokens.outlineVariant) },
        FrameLayout.LayoutParams(dp(40), dp(4), Gravity.CENTER)
      )
      setOnTouchListener { _, event ->
        when (event.actionMasked) {
          MotionEvent.ACTION_DOWN -> {
            dragStartY = event.rawY
            sheetStartTranslation = sheet.translationY
            true
          }
          MotionEvent.ACTION_MOVE -> {
            sheet.translationY = (sheetStartTranslation + event.rawY - dragStartY).coerceAtLeast(0f)
            true
          }
          MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val shouldDismiss = sheet.translationY >= minOf(sheet.height * 0.18f, dp(140).toFloat())
            if (shouldDismiss) {
              sheet.animate().translationY(sheet.height.toFloat()).setDuration(180).withEndAction { dismiss() }.start()
            } else {
              sheet.animate().translationY(0f).setDuration(180).start()
            }
            true
          }
          else -> false
        }
      }
    }
    sheet.addView(dragHandle, LinearLayout.LayoutParams(MATCH, dp(28)).apply {
      bottomMargin = dp(DesignTokens.spaceSm)
    })

    sheet.addView(TextView(context).apply {
      text = "Stickers"
      setTextColor(DesignTokens.textPrimary)
      textSize = 16f
      setTypeface(typeface, android.graphics.Typeface.BOLD)
    })

    searchInput = EditText(context).apply {
      hint = "Search stickers"
      setHintTextColor(DesignTokens.outline)
      setTextColor(DesignTokens.onSurface)
      background = roundedDrawable(DesignTokens.surfaceContainerHigh, DesignTokens.radiusLg, context)
      setPadding(dp(DesignTokens.spaceMd), dp(DesignTokens.spaceSm), dp(DesignTokens.spaceMd), dp(DesignTokens.spaceSm))
      setSingleLine(true)
      addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
          searchDebounce?.let { mainHandler.removeCallbacks(it) }
          val runnable = Runnable {
            searchQuery = s?.toString().orEmpty()
            refreshList()
          }
          searchDebounce = runnable
          mainHandler.postDelayed(runnable, 300)
        }
      })
    }
    sheet.addView(searchInput, LinearLayout.LayoutParams(MATCH, WRAP).apply {
      topMargin = dp(DesignTokens.spaceMd)
    })

    chipRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    sheet.addView(HorizontalScrollView(context).apply {
      isHorizontalScrollBarEnabled = false
      addView(chipRow)
    }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(DesignTokens.spaceMd) })

    val gridContainer = FrameLayout(context)

    recycler = RecyclerView(context).apply {
      layoutManager = GridLayoutManager(context, 5)
      setHasFixedSize(true)
    }
    adapter = StickerAdapter()
    recycler.adapter = adapter
    gridContainer.addView(recycler, FrameLayout.LayoutParams(MATCH, MATCH))

    sheet.addView(gridContainer, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
      topMargin = dp(DesignTokens.spaceMd)
    })

    rebuildChips()
    refreshList()

    val root = FrameLayout(context)
    root.addView(dimBackground, FrameLayout.LayoutParams(MATCH, MATCH))
    root.addView(sheet, FrameLayout.LayoutParams(MATCH, sheetHeight, Gravity.BOTTOM))
    return root
  }

  private fun roundedTopDrawable(): android.graphics.drawable.GradientDrawable =
    android.graphics.drawable.GradientDrawable().apply {
      setColor(DesignTokens.elevatedPanel)
      cornerRadii = floatArrayOf(
        dp(DesignTokens.radiusSheet).toFloat(), dp(DesignTokens.radiusSheet).toFloat(),
        dp(DesignTokens.radiusSheet).toFloat(), dp(DesignTokens.radiusSheet).toFloat(),
        0f, 0f, 0f, 0f
      )
    }

  private fun rebuildChips() {
    chipRow.removeAllViews()
    val localGroups = localAssets.map { "local:" + it.substringBefore('_').lowercase() }.distinct()
    val groups = listOf<String?>(null) + (if (runtimeStickers.isNotEmpty()) listOf("runtime") else emptyList()) + localGroups
    groups.forEach { group ->
      val label = group?.let { humanizeGroup(it) } ?: "All"
      val chip = editorChip(context, label, selected = group == selectedGroup) {
        selectedGroup = group
        rebuildChips()
        refreshList()
      }
      chipRow.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginEnd = dp(DesignTokens.spaceSm) })
    }
  }

  private fun humanizeGroup(group: String): String =
    if (group == "runtime") "My stickers" else group.removePrefix("local:").split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

  private fun refreshList() {
    val query = searchQuery.trim()
    val group = selectedGroup
    val local = localAssets.filter { asset ->
      val matchesQuery = query.isEmpty() || asset.contains(query, ignoreCase = true)
      val matchesGroup = group == null || (group.startsWith("local:") && asset.startsWith(group.removePrefix("local:"), ignoreCase = true))
      matchesQuery && matchesGroup
    }.map { StickerItem(assetName = it) }
    val runtime = runtimeStickers.filter {
      (group == null || group == "runtime") && (query.isEmpty() || it.id.contains(query, ignoreCase = true))
    }.map { StickerItem(runtime = it) }
    adapter.submit(runtime + local)
  }

  private inner class StickerViewHolder(itemView: FrameLayout) : RecyclerView.ViewHolder(itemView) {
    val imageView: ImageView = itemView.getChildAt(0) as ImageView
  }

  private inner class StickerAdapter : RecyclerView.Adapter<StickerViewHolder>() {
    private var items: List<StickerItem> = emptyList()

    fun submit(newItems: List<StickerItem>) {
      items = newItems
      notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
      val availableWidth = context.resources.displayMetrics.widthPixels - dp(DesignTokens.spaceLg * 2) - dp(8 * 5)
      val cardSize = (availableWidth / 5).coerceAtLeast(dp(52))
      val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
      }
      val cell = FrameLayout(context).apply {
        background = roundedDrawable(DesignTokens.surfaceContainerLow, DesignTokens.radiusMd, context)
        addView(image, FrameLayout.LayoutParams(cardSize - dp(16), cardSize - dp(16), Gravity.CENTER))
      }
      cell.layoutParams = RecyclerView.LayoutParams(cardSize, cardSize).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
      }
      return StickerViewHolder(cell)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
      val item = items[position]
      holder.imageView.setImageDrawable(null)
      item.assetName?.let { asset ->
        holder.imageView.setImageBitmap(context.assets.open("Stickers/$asset").use { BitmapFactory.decodeStream(it) })
        holder.itemView.setOnClickListener { onLocalStickerTapped(asset) }
      }
      item.runtime?.let { runtime ->
        loadRuntimeThumbnail(runtime, holder.imageView)
        holder.itemView.setOnClickListener { onRuntimeStickerTapped(runtime) }
      }
    }
  }

  private data class StickerItem(val assetName: String? = null, val runtime: RuntimeSticker? = null)

  private fun loadRuntimeThumbnail(sticker: RuntimeSticker, imageView: ImageView) {
    imageView.tag = sticker.uri
    ioExecutor.execute {
      val bitmap = try {
        if (sticker.uri.startsWith("https://")) downloadRuntimeSticker(sticker.uri).inputStream().use(BitmapFactory::decodeStream)
        else SourceResolver.resolvePath(context, sticker.uri, "pve_runtime_sticker")?.let(BitmapFactory::decodeFile)
      } catch (_: Exception) { null }
      mainHandler.post { if (imageView.tag == sticker.uri) imageView.setImageBitmap(bitmap) }
    }
  }

  private fun onRuntimeStickerTapped(sticker: RuntimeSticker) {
    ioExecutor.execute {
      val path = try {
        if (sticker.uri.startsWith("https://")) {
          val output = File.createTempFile("pve_runtime_sticker_", ".img", context.cacheDir)
          output.writeBytes(downloadRuntimeSticker(sticker.uri))
          output.absolutePath
        } else SourceResolver.resolvePath(context, sticker.uri, "pve_runtime_sticker")
      } catch (_: Exception) { null }
      mainHandler.post {
        if (path != null) { onStickerPicked(path); dismiss() }
        else Toast.makeText(context, "Couldn't open that sticker.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun downloadRuntimeSticker(uri: String): ByteArray {
    val connection = URL(uri).openConnection().apply {
      connectTimeout = 10_000
      readTimeout = 15_000
    }
    return connection.getInputStream().use { it.readBytes() }
  }

  private fun onLocalStickerTapped(assetName: String) {
    try {
      val output = File(context.cacheDir, "sticker-${assetName.lowercase()}")
      context.assets.open("Stickers/$assetName").use { input -> FileOutputStream(output).use(input::copyTo) }
      onStickerPicked(output.absolutePath)
      dismiss()
    } catch (_: Exception) {
      Toast.makeText(context, "Couldn't open that sticker.", Toast.LENGTH_SHORT).show()
    }
  }

  companion object {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
  }
}
