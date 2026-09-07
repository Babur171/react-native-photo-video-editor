package com.photovideoeditor.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.*
import com.photovideoeditor.R
import com.photovideoeditor.photo.render.PhotoTransformState
import com.photovideoeditor.ui.DesignTokens
import kotlin.math.roundToInt

/** Shared photo/video crop surface. Only UI state lives here; the controller owns the draft. */
class CropPanel(
  context: Context,
  private val accent: Int,
  surface: Int,
  textColor: Int,
  originalRatio: () -> Float,
  onReset: () -> Unit,
  onDone: () -> Unit,
  onCancel: () -> Unit,
  onStraighten: (Float) -> Unit,
  onRotate: () -> Unit,
  onRatio: (String, Float?) -> Unit
) : LinearLayout(context) {
  private fun dp(value: Int) = dpToPx(value, context)
  private val valueLabel = TextView(context)
  private val slider: SeekBar
  private val presets = mutableMapOf<String, LinearLayout>()
  private var syncing = false

  init {
    orientation = VERTICAL
    setPadding(dp(12), dp(8), dp(12), dp(8))
    background = GradientDrawable().apply {
      setColor(surface); cornerRadii = floatArrayOf(dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), dp(20).toFloat(), 0f, 0f, 0f, 0f)
    }
    fun icon(resource: Int, label: String, action: () -> Unit) = ImageButton(context).apply {
      setImageResource(resource); setColorFilter(textColor); contentDescription = label
      background = roundedDrawable(Color.TRANSPARENT, 12, context)
      setOnClickListener { action() }; applyPressScale()
    }
    val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    val reset = LinearLayout(context).apply {
      gravity = Gravity.CENTER_VERTICAL; isClickable = true; contentDescription = "Reset crop"
      addView(ImageView(context).apply { setImageResource(R.drawable.ic_undo); setColorFilter(textColor) }, LayoutParams(dp(20), dp(20)))
      addView(TextView(context).apply { text = "Reset"; textSize = 13f; setTextColor(textColor); setPadding(dp(6), 0, 0, 0) })
      setOnClickListener { onReset() }; applyPressScale()
    }
    header.addView(reset, LayoutParams(dp(88), dp(48)))
    header.addView(TextView(context).apply { text = "Crop"; textSize = 17f; gravity = Gravity.CENTER; setTextColor(textColor); setTypeface(typeface, 1) }, LayoutParams(0, dp(48), 1f))
    header.addView(icon(R.drawable.ic_close, "Cancel crop", onCancel), LayoutParams(dp(44), dp(48)))
    header.addView(icon(R.drawable.ic_check, "Apply crop", onDone).apply { setColorFilter(accent) }, LayoutParams(dp(44), dp(48)))
    addView(header)
    val labels = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    labels.addView(TextView(context).apply { text = "Straighten"; textSize = 13f; setTextColor(textColor) }, LayoutParams(0, dp(44), 1f))
    valueLabel.setTextColor(accent); valueLabel.textSize = 13f; valueLabel.gravity = Gravity.CENTER
    labels.addView(valueLabel, LayoutParams(dp(56), dp(44)))
    labels.addView(icon(R.drawable.ic_undo, "Reset straighten") { onStraighten(0f) }, LayoutParams(dp(44), dp(44)))
    labels.addView(icon(R.drawable.ic_rotate_right, "Rotate 90 degrees", onRotate), LayoutParams(dp(44), dp(44)))
    addView(labels)
    slider = object : SeekBar(context) {
      private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = resources.displayMetrics.density; color = DesignTokens.outline }
      override fun onDraw(canvas: Canvas) {
        val start = paddingLeft.toFloat(); val end = width - paddingRight.toFloat()
        for (i in 0..90) {
          val x = start + (end - start) * i / 90
          tick.alpha = if (i % 5 == 0) 160 else 65
          canvas.drawLine(x, 6f, x, if (i % 5 == 0) dp(12).toFloat() else dp(7).toFloat(), tick)
        }
        super.onDraw(canvas)
      }
    }.apply {
      max = 900; progress = 450; contentDescription = "Straighten, minus 45 to plus 45 degrees"
      progressTintList = android.content.res.ColorStateList.valueOf(accent); thumbTintList = progressTintList
      setPadding(dp(12), dp(12), dp(12), 0)
      setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser && !syncing) onStraighten((progress - 450) / 10f) }
        override fun onStartTrackingTouch(bar: SeekBar?) {}
        override fun onStopTrackingTouch(bar: SeekBar?) {}
      })
    }
    addView(slider, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
    val rail = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    val ratios = listOf("Free" to null, "Original" to null, "1:1" to 1f, "9:16" to 9f / 16, "16:9" to 16f / 9, "10:16" to 10f / 16, "16:10" to 16f / 10, "4:5" to 4f / 5, "5:4" to 5f / 4, "3:4" to 3f / 4, "4:3" to 4f / 3)
    ratios.forEach { (name, ratio) ->
      val tile = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true; contentDescription = "$name crop ratio"
        addView(object : android.view.View(context) {
          val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat(); color = textColor }
          override fun onDraw(canvas: Canvas) {
            paint.color = if (this@apply.isSelected) accent else textColor
            val aspect = if (name == "Original") originalRatio() else ratio ?: 1.25f
            val w = minOf(dp(24).toFloat(), dp(24) * aspect); val h = w / aspect
            canvas.drawRoundRect((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2, dp(2).toFloat(), dp(2).toFloat(), paint)
          }
        }, LayoutParams(dp(40), dp(38)))
        addView(TextView(context).apply { text = name; textSize = 11f; gravity = Gravity.CENTER; setTextColor(textColor) })
        setOnClickListener { onRatio(name, if (name == "Original") originalRatio() else ratio) }; applyPressScale()
      }
      presets[name] = tile
      rail.addView(tile, LayoutParams(dp(64), dp(68)))
    }
    addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(rail) }, LayoutParams(LayoutParams.MATCH_PARENT, dp(72)))
  }

  fun sync(state: PhotoTransformState) {
    syncing = true
    slider.progress = (state.straightenDegrees * 10 + 450).roundToInt()
    valueLabel.text = String.format(java.util.Locale.US, "%.1f°", state.straightenDegrees)
    presets.forEach { (name, tile) ->
      tile.isSelected = name == state.aspectPreset
      tile.background = roundedDrawable(if (tile.isSelected) (accent and 0x00FFFFFF) or 0x22000000 else Color.TRANSPARENT, 12, context)
      (tile.getChildAt(1) as TextView).setTextColor(if (tile.isSelected) accent else DesignTokens.textSecondary)
      tile.getChildAt(0).invalidate()
    }
    syncing = false
  }
}
