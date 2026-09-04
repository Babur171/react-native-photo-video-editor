package com.photovideoeditor.stickers

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photovideoeditor.ui.DesignTokens
import com.photovideoeditor.ui.components.dpToPx
import com.photovideoeditor.ui.components.editorChip
import com.photovideoeditor.ui.components.editorPrimaryButton
import com.photovideoeditor.ui.components.roundedDrawable

/**
 * Bottom sheet listing OpenMoji's free sticker catalog (search + category filter + grid),
 * built as a plain [Dialog] (no Material `BottomSheetDialog`, which isn't a dependency here) that
 * slides up from the bottom and covers ~85% of the screen height.
 */
class OnlineStickerSheet(
  context: Context,
  private val onStickerPicked: (localFilePath: String) -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar) {

  private val mainHandler = Handler(Looper.getMainLooper())
  private var searchDebounce: Runnable? = null

  private var allEntries: List<OpenMojiEntry> = emptyList()
  private var selectedGroup: String? = null // null == "All"
  private var searchQuery: String = ""
  private var loadError: Exception? = null
  private var loading = true

  private lateinit var searchInput: EditText
  private lateinit var chipRow: LinearLayout
  private lateinit var recycler: RecyclerView
  private lateinit var progress: ProgressBar
  private lateinit var statusText: TextView
  private lateinit var retryButton: View
  private lateinit var adapter: StickerAdapter

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(buildRootView())
    configureWindow()
    loadCatalog()
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
    val sheetHeight = (screenHeight * 0.85f).toInt()

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

    // Decorative drag handle.
    sheet.addView(
      View(context).apply { setBackgroundColor(DesignTokens.outlineVariant) },
      LinearLayout.LayoutParams(dp(36), dp(4)).apply {
        gravity = Gravity.CENTER_HORIZONTAL
        bottomMargin = dp(DesignTokens.spaceMd)
      }
    )

    sheet.addView(TextView(context).apply {
      text = "Browse stickers"
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
      layoutManager = GridLayoutManager(context, 4)
    }
    adapter = StickerAdapter()
    recycler.adapter = adapter
    gridContainer.addView(recycler, FrameLayout.LayoutParams(MATCH, MATCH))

    progress = ProgressBar(context)
    gridContainer.addView(progress, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))

    val statusContainer = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
    }
    statusText = TextView(context).apply {
      setTextColor(DesignTokens.textSecondary)
      textSize = 13f
      gravity = Gravity.CENTER
    }
    statusContainer.addView(statusText, LinearLayout.LayoutParams(WRAP, WRAP))
    retryButton = editorPrimaryButton(context, "Retry") { loadCatalog() }
    statusContainer.addView(retryButton, LinearLayout.LayoutParams(WRAP, dp(DesignTokens.touchTargetMin)).apply {
      topMargin = dp(DesignTokens.spaceMd)
    })
    gridContainer.addView(statusContainer, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))

    sheet.addView(gridContainer, LinearLayout.LayoutParams(MATCH, 0, 1f).apply {
      topMargin = dp(DesignTokens.spaceMd)
    })

    sheet.addView(TextView(context).apply {
      text = "Stickers by OpenMoji (CC BY-SA 4.0)"
      setTextColor(DesignTokens.textSubtle)
      textSize = 10f
      gravity = Gravity.CENTER
    }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(DesignTokens.spaceSm) })

    updateStatusViews()

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

  private fun loadCatalog() {
    loading = true
    loadError = null
    updateStatusViews()
    OpenMojiCatalog.load(context) { entries, error ->
      loading = false
      if (entries != null) {
        allEntries = entries
        loadError = null
        rebuildChips()
        refreshList()
      } else {
        loadError = error
      }
      updateStatusViews()
    }
  }

  private fun rebuildChips() {
    chipRow.removeAllViews()
    val groups = listOf<String?>(null) + OpenMojiCatalog.groups()
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
    group.split("-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

  private fun refreshList() {
    val query = searchQuery.trim()
    val list = if (query.isNotEmpty()) {
      OpenMojiCatalog.search(query)
    } else {
      val group = selectedGroup
      if (group == null) allEntries else OpenMojiCatalog.byGroup(group)
    }
    adapter.submit(list)
    updateStatusViews()
  }

  private fun updateStatusViews() {
    progress.visibility = if (loading) View.VISIBLE else View.GONE
    val error = loadError
    val empty = !loading && error == null && adapter.itemCount == 0
    recycler.visibility = if (!loading && error == null && !empty) View.VISIBLE else View.GONE
    statusText.visibility = if (!loading && (error != null || empty)) View.VISIBLE else View.GONE
    retryButton.visibility = if (!loading && error != null) View.VISIBLE else View.GONE
    statusText.text = when {
      error != null -> "Couldn't load stickers — check your connection."
      empty -> "No stickers found."
      else -> ""
    }
  }

  private inner class StickerViewHolder(itemView: FrameLayout) : RecyclerView.ViewHolder(itemView) {
    val imageView: ImageView = itemView.getChildAt(0) as ImageView
  }

  private inner class StickerAdapter : RecyclerView.Adapter<StickerViewHolder>() {
    private var items: List<OpenMojiEntry> = emptyList()

    fun submit(newItems: List<OpenMojiEntry>) {
      items = newItems
      notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
      val cardSize = dp(72)
      val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
      }
      val cell = FrameLayout(context).apply {
        background = roundedDrawable(DesignTokens.surfaceContainer, DesignTokens.radiusMd, context)
        addView(image, FrameLayout.LayoutParams(cardSize - dp(16), cardSize - dp(16), Gravity.CENTER))
      }
      cell.layoutParams = RecyclerView.LayoutParams(cardSize, cardSize).apply {
        setMargins(dp(4), dp(4), dp(4), dp(4))
      }
      return StickerViewHolder(cell)
    }

    override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
      val entry = items[position]
      RemoteImageLoader.loadThumbnail(context, OpenMojiCatalog.thumbnailUrl(entry.hexcode), holder.imageView)
      holder.itemView.setOnClickListener { onStickerTapped(entry) }
    }
  }

  private fun onStickerTapped(entry: OpenMojiEntry) {
    Toast.makeText(context, "Downloading sticker…", Toast.LENGTH_SHORT).show()
    RemoteImageLoader.downloadFull(context, OpenMojiCatalog.fullUrl(entry.hexcode)) { path, error ->
      if (path != null) {
        onStickerPicked(path)
        dismiss()
      } else {
        Toast.makeText(context, "Couldn't download that sticker, try another.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  companion object {
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
  }
}
