package com.photovideoeditor.photo.render

import android.graphics.Matrix

enum class FlipState { NONE, HORIZONTAL, VERTICAL }

/**
 * Non-destructive photo edit state. The crop rectangle is normalized (0..1)
 * against the *transformed* (rotated/flipped/straightened) image, matching
 * what the user sees in the crop overlay — the original source file is never
 * modified, only read again at export time.
 */
data class PhotoTransformState(
  val cropLeft: Float = 0f,
  val cropTop: Float = 0f,
  val cropRight: Float = 1f,
  val cropBottom: Float = 1f,
  val rotationDegrees: Int = 0,
  val straightenDegrees: Float = 0f,
  val flip: FlipState = FlipState.NONE,
  val aspectRatio: Float? = null
) {
  val isIdentity: Boolean
    get() = cropLeft == 0f && cropTop == 0f && cropRight == 1f && cropBottom == 1f &&
      rotationDegrees == 0 && straightenDegrees == 0f && flip == FlipState.NONE

  /** Rotating changes which pixels a normalized crop rect refers to, so the crop resets to full-frame. */
  fun rotatedRight(): PhotoTransformState = copy(
    rotationDegrees = (rotationDegrees + 90) % 360,
    cropLeft = 0f,
    cropTop = 0f,
    cropRight = 1f,
    cropBottom = 1f
  )

  fun cycledFlip(): PhotoTransformState = copy(
    flip = when (flip) {
      FlipState.NONE -> FlipState.HORIZONTAL
      FlipState.HORIZONTAL -> FlipState.VERTICAL
      FlipState.VERTICAL -> FlipState.NONE
    }
  )

  fun withStraighten(degrees: Float): PhotoTransformState = copy(straightenDegrees = degrees.coerceIn(-45f, 45f))

  fun withCrop(left: Float, top: Float, right: Float, bottom: Float): PhotoTransformState = copy(
    cropLeft = left.coerceIn(0f, 1f),
    cropTop = top.coerceIn(0f, 1f),
    cropRight = right.coerceIn(0f, 1f),
    cropBottom = bottom.coerceIn(0f, 1f)
  )

  fun withAspectRatio(ratio: Float?): PhotoTransformState = copy(aspectRatio = ratio)

  fun reset(): PhotoTransformState = PhotoTransformState()

  /** Combined rotate + straighten + flip matrix, applied around the bitmap's own origin/size by the caller. */
  fun toMatrix(): Matrix {
    val matrix = Matrix()
    when (flip) {
      FlipState.HORIZONTAL -> matrix.postScale(-1f, 1f)
      FlipState.VERTICAL -> matrix.postScale(1f, -1f)
      FlipState.NONE -> {}
    }
    matrix.postRotate(rotationDegrees.toFloat() + straightenDegrees)
    return matrix
  }
}
