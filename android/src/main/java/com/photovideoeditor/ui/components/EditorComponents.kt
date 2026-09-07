package com.photovideoeditor.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.photovideoeditor.ui.DesignTokens

/**
 * Shared "Studio Violet" UI primitives (DESIGN.md), reused across the photo/video editor shell
 * instead of hand-rolling styling per screen. Kept as small factory functions rather than View
 * subclasses to match this codebase's existing `create*`/`actionButton` builder style.
 */

fun dpToPx(dp: Int, context: Context): Int = (dp * context.resources.displayMetrics.density).toInt()

fun roundedDrawable(
  color: Int,
  radiusDp: Int,
  context: Context,
  strokeColor: Int? = null,
  strokeWidthDp: Int = 1
): GradientDrawable = GradientDrawable().apply {
  cornerRadius = dpToPx(radiusDp, context).toFloat()
  setColor(color)
  if (strokeColor != null) setStroke(dpToPx(strokeWidthDp, context).coerceAtLeast(1), strokeColor)
}

/**
 * Tactile press feedback (scale 1.0 -> 0.97) matching DESIGN.md's "Tactile Instrument Feel".
 * Returns false so the view's own click handling (existing `setOnClickListener`) still fires.
 */
fun View.applyPressScale(pressedScale: Float = 0.97f) {
  setOnTouchListener { v, event ->
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> v.animate().scaleX(pressedScale).scaleY(pressedScale).setDuration(80).start()
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
    }
    false
  }
}

/** Elevated rounded container (DESIGN.md "Elevated Panels & Tiles"), used for cards/tiles/sheets. */
fun editorCard(
  context: Context,
  radiusDp: Int = DesignTokens.radiusLg,
  backgroundColor: Int = DesignTokens.elevatedPanel,
  borderColor: Int? = DesignTokens.interactiveNeutral
): FrameLayout = FrameLayout(context).apply {
  background = roundedDrawable(backgroundColor, radiusDp, context, strokeColor = borderColor)
}

/** Pill chip for quick actions / filter tags (DESIGN.md "Chips & Filter Presets"). */
fun editorChip(context: Context, text: String, selected: Boolean, onClick: () -> Unit): TextView =
  TextView(context).apply {
    this.text = text
    textSize = 12f
    gravity = Gravity.CENTER
    minHeight = dpToPx(32, context)
    setTextColor(if (selected) DesignTokens.textPrimary else DesignTokens.textSecondary)
    background = roundedDrawable(
      color = if (selected) DesignTokens.accentViolet else DesignTokens.elevatedPanel,
      radiusDp = 16,
      context = context,
      strokeColor = if (selected) null else DesignTokens.interactiveNeutral
    )
    val padH = dpToPx(DesignTokens.spaceLg, context)
    setPadding(padH, dpToPx(DesignTokens.spaceXs, context), padH, dpToPx(DesignTokens.spaceXs, context))
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
    applyPressScale()
  }

/**
 * Primary Export CTA (DESIGN.md "Primary Export Button"): solid violet fill, 44dp min height,
 * 12px radius, subtle elevation glow, 0.97 press scale.
 */
fun editorPrimaryButton(
  context: Context,
  text: String,
  iconRes: Int? = null,
  fillColor: Int = DesignTokens.accentViolet,
  textColor: Int = DesignTokens.textPrimary,
  onClick: () -> Unit
): LinearLayout = LinearLayout(context).apply {
  orientation = LinearLayout.HORIZONTAL
  gravity = Gravity.CENTER
  background = roundedDrawable(fillColor, DesignTokens.radiusLg, context)
  elevation = dpToPx(4, context).toFloat()
  minimumHeight = dpToPx(DesignTokens.touchTargetMin, context)
  setPadding(dpToPx(DesignTokens.spaceLg, context), 0, dpToPx(DesignTokens.spaceLg, context), 0)
  isClickable = true
  isFocusable = true
  if (iconRes != null) {
    addView(
      ImageView(context).apply {
        setImageResource(iconRes)
        setColorFilter(textColor)
      },
      LinearLayout.LayoutParams(dpToPx(18, context), dpToPx(18, context)).apply { marginEnd = dpToPx(DesignTokens.spaceXs, context) }
    )
  }
  addView(TextView(context).apply {
    this.text = text
    setTextColor(textColor)
    textSize = 13f
    setTypeface(typeface, Typeface.BOLD)
  })
  setOnClickListener { onClick() }
  applyPressScale()
}
