package com.photovideoeditor.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.widget.SeekBar
import com.photovideoeditor.ui.DesignTokens

/**
 * The editor's one slider. Draws its own `Label ................ value` line above a thin track, so
 * a panel never needs a separate caption row, and the same instance can be re-pointed at whichever
 * adjustment/property is selected instead of every property owning its own slider.
 */
class EditorToolSlider(context: Context) : SeekBar(context) {
  var propertyKey: String = ""
    set(value) { field = value; contentDescription = labelText ?: value; invalidate() }

  /** Overrides the [propertyKey]-derived caption (e.g. "Brightness", "Intensity"). */
  var labelText: String? = null
    set(value) { field = value; contentDescription = value ?: propertyKey; invalidate() }

  /** Overrides the value readout, given the raw progress. */
  var valueFormatter: ((Int) -> String)? = null
    set(value) { field = value; invalidate() }

  /**
   * Progress at which the underlying value is neutral/default, for bipolar ranges. When set, a tick
   * is drawn there so it is obvious where "no change" sits.
   */
  var neutralProgress: Int? = null
    set(value) { field = value; invalidate() }

  private val density = resources.displayMetrics.density
  private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = DesignTokens.textSecondary
    textSize = 12 * density
  }
  private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DesignTokens.textSubtle }

  init {
    minimumHeight = (48 * density).toInt()
    setPadding((16 * density).toInt(), (20 * density).toInt(), (16 * density).toInt(), 0)
  }

  /** Thin track, accent-coloured progress, compact thumb — the slider styling used editor-wide. */
  fun applyCompactTrack(accentColor: Int) {
    val inset = (9 * density).toInt()
    progressDrawable = LayerDrawable(
      arrayOf(
        pill(Color.argb(70, 255, 255, 255)),
        ClipDrawable(pill(accentColor), Gravity.START, ClipDrawable.HORIZONTAL)
      )
    ).apply {
      setId(0, android.R.id.background)
      setId(1, android.R.id.progress)
      setLayerInset(0, 0, inset, 0, inset)
      setLayerInset(1, 0, inset, 0, inset)
    }
    thumb = GradientDrawable().apply {
      shape = GradientDrawable.OVAL
      setColor(Color.WHITE)
      setSize((12 * density).toInt(), (12 * density).toInt())
    }
    splitTrack = false
  }

  private fun pill(color: Int) = GradientDrawable().apply {
    cornerRadius = 999f
    setColor(color)
  }

  override fun onDraw(canvas: Canvas) {
    neutralProgress?.let { neutral ->
      if (max > 0) {
        val trackWidth = width - paddingLeft - paddingRight
        val x = paddingLeft + trackWidth * (neutral.toFloat() / max)
        val centerY = (height + paddingTop) / 2f
        canvas.drawRect(x - density, centerY - 5 * density, x + density, centerY + 5 * density, tickPaint)
      }
    }
    super.onDraw(canvas)
    val label = labelText ?: when (propertyKey) {
      "fontSize" -> "Size"
      "rotation" -> "Rotate"
      else -> propertyKey.replaceFirstChar { it.uppercase() }
    }
    val value = valueFormatter?.invoke(progress) ?: when (propertyKey) {
      "rotation" -> "${progress - 180}°"
      "fontSize" -> "${progress + 12}"
      "opacity" -> "$progress%"
      else -> "${progress + 20}%"
    }
    labelPaint.textAlign = Paint.Align.LEFT
    canvas.drawText(label, paddingLeft.toFloat(), 14 * density, labelPaint)
    labelPaint.textAlign = Paint.Align.RIGHT
    canvas.drawText(value, (width - paddingRight).toFloat(), 14 * density, labelPaint)
  }
}
