package com.photovideoeditor.photo.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView

/**
 * ImageView supporting pinch-to-zoom and single-finger pan for the photo
 * preview, reporting the current on-screen image rect via [onBoundsChanged]
 * so the crop overlay can stay aligned to it.
 */
class ZoomableImageView(context: Context) : ImageView(context) {
  private val matrixValues = FloatArray(9)
  private var minScale = 1f
  private var maxScale = 4f
  private var lastX = 0f
  private var lastY = 0f
  private var panning = false
  var onBoundsChanged: ((RectF) -> Unit)? = null
  var panZoomEnabled = true

  private val scaleDetector = ScaleGestureDetector(
    context,
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScale(detector: ScaleGestureDetector): Boolean {
        val targetScale = (currentScale() * detector.scaleFactor).coerceIn(minScale, maxScale)
        val appliedFactor = targetScale / currentScale()
        imageMatrix = Matrix(imageMatrix).apply { postScale(appliedFactor, appliedFactor, detector.focusX, detector.focusY) }
        onBoundsChanged?.invoke(currentImageBounds())
        return true
      }
    }
  )

  init {
    scaleType = ScaleType.MATRIX
  }

  /** Fits the current drawable to the view bounds and resets zoom/pan. */
  fun resetToFit() {
    val bitmapDrawable = drawable ?: return
    val viewWidth = width.toFloat()
    val viewHeight = height.toFloat()
    val drawableWidth = bitmapDrawable.intrinsicWidth.toFloat()
    val drawableHeight = bitmapDrawable.intrinsicHeight.toFloat()
    if (viewWidth <= 0 || viewHeight <= 0 || drawableWidth <= 0 || drawableHeight <= 0) return
    val scale = minOf(viewWidth / drawableWidth, viewHeight / drawableHeight)
    val dx = (viewWidth - drawableWidth * scale) / 2f
    val dy = (viewHeight - drawableHeight * scale) / 2f
    imageMatrix = Matrix().apply {
      postScale(scale, scale)
      postTranslate(dx, dy)
    }
    minScale = scale
    maxScale = scale * 4f
    onBoundsChanged?.invoke(currentImageBounds())
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w != oldw || h != oldh) resetToFit()
  }

  fun setRenderedBounds(bounds: RectF) {
    val image = drawable ?: return
    imageMatrix = Matrix().apply { setRectToRect(RectF(0f, 0f, image.intrinsicWidth.toFloat(), image.intrinsicHeight.toFloat()), bounds, Matrix.ScaleToFit.FILL) }
  }

  fun currentImageBounds(): RectF {
    val bitmapDrawable = drawable ?: return RectF(0f, 0f, width.toFloat(), height.toFloat())
    val rect = RectF(0f, 0f, bitmapDrawable.intrinsicWidth.toFloat(), bitmapDrawable.intrinsicHeight.toFloat())
    imageMatrix.mapRect(rect)
    return rect
  }

  private fun currentScale(): Float {
    imageMatrix.getValues(matrixValues)
    return matrixValues[Matrix.MSCALE_X]
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!panZoomEnabled) return false
    scaleDetector.onTouchEvent(event)
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        lastX = event.x
        lastY = event.y
        panning = true
      }
      MotionEvent.ACTION_POINTER_DOWN -> panning = false
      MotionEvent.ACTION_MOVE -> if (panning && event.pointerCount == 1 && !scaleDetector.isInProgress) {
        val dx = event.x - lastX
        val dy = event.y - lastY
        lastX = event.x
        lastY = event.y
        imageMatrix = Matrix(imageMatrix).apply { postTranslate(dx, dy) }
        onBoundsChanged?.invoke(currentImageBounds())
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> panning = event.pointerCount <= 1
    }
    return true
  }
}
