package com.photovideoeditor.video.render

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.common.util.Size
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.ScaleAndRotateTransformation
import com.photovideoeditor.photo.render.CropGeometry
import com.photovideoeditor.photo.render.PhotoTransformState
import kotlin.math.cos
import kotlin.math.sin

/** Identical effect order for ExoPlayer preview and Transformer export. */
object VideoCropEffects {
  fun create(state: PhotoTransformState, includeCrop: Boolean = true): List<Effect> = buildList {
    if (state.rotationDegrees != 0) add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-state.rotationDegrees.toFloat()).build())
    if (state.straightenDegrees != 0f) add(object : MatrixTransformation {
      private val matrix = Matrix()
      override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val w = inputWidth.toFloat(); val h = inputHeight.toFloat()
        val scale = CropGeometry.fillScale(w, h, state.straightenDegrees)
        val radians = Math.toRadians(state.straightenDegrees.toDouble())
        val c = cos(radians).toFloat() * scale; val s = sin(radians).toFloat() * scale
        // GL's Y axis points up; UI rotation is clockwise with Y pointing down.
        matrix.setValues(floatArrayOf(c, s * h / w, 0f, -s * w / h, c, 0f, 0f, 0f, 1f))
        return Size(inputWidth, inputHeight)
      }
      override fun getMatrix(presentationTimeUs: Long) = matrix
    })
    if (includeCrop && (state.cropLeft != 0f || state.cropTop != 0f || state.cropRight != 1f || state.cropBottom != 1f)) {
      add(Crop(state.cropLeft * 2 - 1, state.cropRight * 2 - 1, 1 - state.cropBottom * 2, 1 - state.cropTop * 2))
    }
  }
}
