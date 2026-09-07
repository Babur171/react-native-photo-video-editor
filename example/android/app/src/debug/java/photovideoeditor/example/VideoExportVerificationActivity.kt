package photovideoeditor.example

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.photovideoeditor.photo.render.LayerType
import com.photovideoeditor.photo.render.PhotoLayer
import com.photovideoeditor.photo.render.PhotoLayerRenderer
import com.photovideoeditor.video.export.VideoExporter
import com.photovideoeditor.video.render.VideoClip
import com.photovideoeditor.video.render.VideoTransformState
import java.io.File

/** Debug-only, headless export fixture used for frame-level native verification. */
class VideoExportVerificationActivity : Activity() {
  private var exporter: VideoExporter? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val source = intent.getStringExtra("source") ?: return finish()
    val name = intent.getStringExtra("name") ?: "overlay-verification"
    val durationMs = intent.getLongExtra("durationMs", 3_000)
    val ratio = intent.getFloatExtra("aspectRatio", 0f).takeIf { it > 0 }
    val layers = verificationLayers(durationMs)
    val state = VideoTransformState(aspectRatio = ratio)
    exporter = VideoExporter(applicationContext).also { nativeExporter ->
      nativeExporter.export(
        clips = listOf(VideoClip(sourceUri = source, originalDurationMs = durationMs)),
        state = state,
        layers = layers,
        exportOptions = null,
        onProgress = {},
        onComplete = { result ->
          val directory = getExternalFilesDir("verification") ?: filesDir
          val video = File(directory, "$name.mp4")
          File(requireNotNull(Uri.parse(result.uri).path)).copyTo(video, overwrite = true)
          val expected = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
          val finalLayers = layers.filter { it.isActiveAt(result.durationMs, result.durationMs) }
          val reference = PhotoLayerRenderer.render(expected, finalLayers) { null }
          File(directory, "$name-final-reference.png").outputStream().use {
            reference.compress(Bitmap.CompressFormat.PNG, 100, it)
          }
          if (reference !== expected) expected.recycle()
          reference.recycle()
          Log.i(TAG, "PASS name=$name video=${video.absolutePath} width=${result.width} height=${result.height} durationMs=${result.durationMs}")
          finish()
        },
        onError = { error ->
          Log.e(TAG, "FAIL name=$name code=${error.code} message=${error.message}")
          finish()
        }
      )
    }
  }

  override fun onDestroy() {
    exporter?.cancel()
    exporter = null
    super.onDestroy()
  }

  private fun verificationLayers(durationMs: Long) = listOf(
    PhotoLayer(type = LayerType.TEXT, text = "Scaled text", x = 0.27f, y = 0.24f, scale = 2.1f, rotationDegrees = 18f),
    PhotoLayer(type = LayerType.TEXT, text = "Rotated text", x = 0.72f, y = 0.68f, scale = 2.8f, rotationDegrees = 337f),
    PhotoLayer(type = LayerType.TEXT, text = "Faded text", textColor = Color.CYAN, x = 0.58f, y = 0.31f, scale = 0.65f, rotationDegrees = 42f, opacity = 0.55f),
    PhotoLayer(type = LayerType.TEXT, text = "Must be absent", startMs = 0, endMs = durationMs / 2)
  )

  companion object { private const val TAG = "PVE_VERIFY" }
}
