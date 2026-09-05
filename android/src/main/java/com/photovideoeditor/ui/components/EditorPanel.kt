package com.photovideoeditor.ui.components

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.photovideoeditor.ui.DesignTokens

/**
 * Shared building blocks for the editor's bottom panels (Crop / Adjust / Filters / Text / Stickers).
 *
 * Before this existed each panel hand-rolled its own header, chips and spacing, which is how the
 * Adjust panel ended up with four full-height rectangular buttons while Filters used thumbnail cards.
 * Every panel now composes the same three pieces — [editorPanelHeader], one slider, one
 * [editorToolStrip] — so heights, padding, radii, typography and selected states stay in step.
 *
 * Fixed vertical budget (see PANEL_* below): header 40dp + slider ~48dp + strip 76dp, so a panel
 * never grows past ~164dp and the photo keeps the rest of the screen.
 */
object EditorPanelMetrics {
  const val HEADER_HEIGHT_DP = 40
  const val SLIDER_HEIGHT_DP = 48
  const val STRIP_HEIGHT_DP = 76
  const val STRIP_ITEM_WIDTH_DP = 68
  const val ICON_SIZE_DP = 22
  const val THUMBNAIL_WIDTH_DP = 64
  const val THUMBNAIL_HEIGHT_DP = 72
  const val TOTAL_HEIGHT_DP = HEADER_HEIGHT_DP + SLIDER_HEIGHT_DP + STRIP_HEIGHT_DP
  /** Panel without the slider row (e.g. a picker that has nothing to scrub). */
  const val HEADER_AND_STRIP_HEIGHT_DP = HEADER_HEIGHT_DP + STRIP_HEIGHT_DP
}

/**
 * Compact panel header: `Title            [Reset]  Done`.
 *
 * "Done" is an action, not a tool — it gets a 44dp touch target via padding while staying visually
 * small, rather than becoming another full-width card.
 */
fun editorPanelHeader(
  context: Context,
  title: String,
  accentColor: Int,
  secondaryLabel: String? = null,
  onSecondary: (() -> Unit)? = null,
  onDone: () -> Unit
): LinearLayout = LinearLayout(context).apply {
  orientation = LinearLayout.HORIZONTAL
  gravity = Gravity.CENTER_VERTICAL
  setPadding(dpToPx(DesignTokens.spaceLg, context), 0, dpToPx(DesignTokens.spaceSm, context), 0)
  addView(TextView(context).apply {
    text = title
    textSize = 14f
    maxLines = 1
    includeFontPadding = false
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(DesignTokens.textPrimary)
  }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
  if (secondaryLabel != null && onSecondary != null) {
    addView(editorHeaderAction(context, secondaryLabel, DesignTokens.textSecondary, onSecondary))
  }
  addView(editorHeaderAction(context, "Done", accentColor, onDone))
}

/** Small text action sized for touch (44dp) without looking like a button. */
private fun editorHeaderAction(context: Context, label: String, color: Int, onClick: () -> Unit): TextView =
  TextView(context).apply {
    text = label
    textSize = 13f
    maxLines = 1
    gravity = Gravity.CENTER
    includeFontPadding = false
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(color)
    minWidth = dpToPx(DesignTokens.touchTargetMin, context)
    minHeight = dpToPx(DesignTokens.touchTargetMin, context)
    val padH = dpToPx(DesignTokens.spaceMd, context)
    setPadding(padH, 0, padH, 0)
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
    applyPressScale()
  }

/** Horizontally scrollable strip of compact tool items; the container every panel's list sits in. */
fun editorToolStrip(context: Context, build: LinearLayout.() -> Unit): HorizontalScrollView =
  HorizontalScrollView(context).apply {
    isHorizontalScrollBarEnabled = false
    clipToPadding = false
    val padH = dpToPx(DesignTokens.spaceMd, context)
    setPadding(padH, 0, padH, 0)
    addView(LinearLayout(context).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      build()
    }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT))
  }

/** An icon + single-line label entry in a strip, with an optional "value changed" dot. */
class EditorStripItem(
  val root: LinearLayout,
  private val iconHolder: FrameLayout,
  private val icon: ImageView,
  private val label: TextView,
  private val dot: View
) {
  /**
   * Selected state is a purple icon + label over a faint purple disc — never a filled purple tile,
   * which at this size reads as a giant button rather than a selection.
   */
  fun setSelected(selected: Boolean, accentColor: Int) {
    icon.setColorFilter(if (selected) accentColor else DesignTokens.textSecondary)
    label.setTextColor(if (selected) accentColor else DesignTokens.textSecondary)
    iconHolder.background = if (selected) {
      circleDrawable(DesignTokens.withAlphaPercent(accentColor, 18))
    } else {
      null
    }
    root.isSelected = selected
  }

  /** Tiny dot marking an adjustment that is no longer at its default value. */
  fun setModified(modified: Boolean, accentColor: Int) {
    dot.visibility = if (modified) View.VISIBLE else View.INVISIBLE
    dot.background = circleDrawable(accentColor)
  }
}

private fun circleDrawable(color: Int): android.graphics.drawable.GradientDrawable =
  android.graphics.drawable.GradientDrawable().apply {
    shape = android.graphics.drawable.GradientDrawable.OVAL
    setColor(color)
  }

fun editorStripItem(
  context: Context,
  iconRes: Int,
  labelText: String,
  onClick: () -> Unit
): EditorStripItem {
  val icon = ImageView(context).apply {
    setImageResource(iconRes)
    setColorFilter(DesignTokens.textSecondary)
  }
  val iconHolder = FrameLayout(context).apply {
    addView(
      icon,
      FrameLayout.LayoutParams(
        dpToPx(EditorPanelMetrics.ICON_SIZE_DP, context),
        dpToPx(EditorPanelMetrics.ICON_SIZE_DP, context),
        Gravity.CENTER
      )
    )
  }
  val label = TextView(context).apply {
    text = labelText
    textSize = 11f
    // Labels like "Brightness" and "Saturation" must never wrap to a second line.
    maxLines = 1
    includeFontPadding = false
    gravity = Gravity.CENTER
    setTextColor(DesignTokens.textSecondary)
    setPadding(0, dpToPx(DesignTokens.spaceXs, context), 0, 0)
  }
  val dot = View(context).apply { visibility = View.INVISIBLE }
  val root = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    isClickable = true
    isFocusable = true
    contentDescription = labelText
    minimumHeight = dpToPx(DesignTokens.touchTargetMin, context)
    addView(
      iconHolder,
      LinearLayout.LayoutParams(dpToPx(32, context), dpToPx(32, context))
    )
    addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    addView(dot, LinearLayout.LayoutParams(dpToPx(4, context), dpToPx(4, context)).apply {
      topMargin = dpToPx(3, context)
    })
    setOnClickListener { onClick() }
    applyPressScale()
  }
  return EditorStripItem(root, iconHolder, icon, label, dot)
}

/**
 * Filter thumbnail: a preview of the current photo, outlined in purple when selected.
 * Deliberately a thin outline rather than a filled container, so the photo stays readable.
 */
class EditorFilterThumbnail(val root: LinearLayout, val image: ImageView, private val frame: FrameLayout, private val label: TextView) {
  fun setSelected(selected: Boolean, accentColor: Int) {
    frame.background = roundedDrawable(
      color = Color.TRANSPARENT,
      radiusDp = DesignTokens.radiusMd,
      context = root.context,
      strokeColor = if (selected) accentColor else Color.TRANSPARENT,
      strokeWidthDp = 2
    )
    label.setTextColor(if (selected) accentColor else DesignTokens.textSecondary)
    root.isSelected = selected
  }
}

fun editorFilterThumbnail(context: Context, labelText: String, onClick: () -> Unit): EditorFilterThumbnail {
  val image = ImageView(context).apply {
    scaleType = ImageView.ScaleType.CENTER_CROP
    background = roundedDrawable(DesignTokens.surfaceContainerHigh, DesignTokens.radiusMd, context)
    clipToOutline = true
    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
  }
  // The outline lives on a frame *around* the image so the stroke never crops the preview.
  val frame = FrameLayout(context).apply {
    val inset = dpToPx(2, context)
    setPadding(inset, inset, inset, inset)
    addView(
      image,
      FrameLayout.LayoutParams(
        dpToPx(EditorPanelMetrics.THUMBNAIL_WIDTH_DP, context),
        dpToPx(EditorPanelMetrics.THUMBNAIL_HEIGHT_DP, context)
      )
    )
  }
  val label = TextView(context).apply {
    text = labelText
    textSize = 11f
    maxLines = 1
    includeFontPadding = false
    gravity = Gravity.CENTER
    setTextColor(DesignTokens.textSecondary)
    setPadding(0, dpToPx(DesignTokens.spaceXs, context), 0, 0)
  }
  val root = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER_HORIZONTAL
    isClickable = true
    isFocusable = true
    contentDescription = labelText
    addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    addView(label, LinearLayout.LayoutParams(dpToPx(EditorPanelMetrics.THUMBNAIL_WIDTH_DP + 8, context), LinearLayout.LayoutParams.WRAP_CONTENT))
    setOnClickListener { onClick() }
    applyPressScale()
  }
  return EditorFilterThumbnail(root, image, frame, label)
}
