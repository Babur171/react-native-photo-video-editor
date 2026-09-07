package com.photovideoeditor.photo.render

import android.graphics.Matrix

/**
 * Non-destructive photo edit state. The crop rectangle is normalized (0..1)
 * against the *transformed* (rotated/straightened) image, matching
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
  val aspectRatio: Float? = null,
  val aspectPreset: String = "Free",
  // Viewport metadata only: zoom/pan are already encoded in normalized crop coordinates for export.
  val zoom: Float = 1f,
  val panX: Float = 0f,
  val panY: Float = 0f
) {
  val isIdentity: Boolean
    get() = cropLeft == 0f && cropTop == 0f && cropRight == 1f && cropBottom == 1f &&
      rotationDegrees == 0 && straightenDegrees == 0f

  /** Rotating changes which pixels a normalized crop rect refers to, so the crop resets to full-frame. */
  fun rotatedRight(): PhotoTransformState = copy(
    rotationDegrees = (rotationDegrees + 90) % 360,
    cropLeft = 0f,
    cropTop = 0f,
    cropRight = 1f,
    cropBottom = 1f
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

  /** Combined rotate + straighten matrix, applied around the bitmap's own origin/size by the caller. */
  fun toMatrix(): Matrix {
    val matrix = Matrix()
    matrix.postRotate(rotationDegrees.toFloat() + straightenDegrees)
    return matrix
  }
}
