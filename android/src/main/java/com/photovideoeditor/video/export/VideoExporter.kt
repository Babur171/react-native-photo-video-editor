package com.photovideoeditor.video.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.photovideoeditor.files.SourceResolver
import com.photovideoeditor.photo.render.OverlayGeometry
import com.photovideoeditor.photo.render.PhotoLayer
import com.photovideoeditor.photo.render.PhotoLayerRenderer
import com.photovideoeditor.video.render.VideoClip
import com.photovideoeditor.video.render.VideoTransformState
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class VideoExportResult(
  val uri: String,
  val width: Int,
  val height: Int,
  val durationMs: Long,
  val fileSize: Long,
  val mimeType: String
)

class VideoExportException(val code: String, message: String) : Exception(message)

/**
 * Wraps Media3 Transformer to apply trim/rotate/aspect-ratio/overlays
 * to the full-resolution source video and mux an H.264/AAC MP4. The source
 * file is never modified. Must be constructed and used from a thread with a
 * Looper (Transformer requirement) — the main thread satisfies this.
 *
 * Milestone 7 (multi-clip timeline): each [VideoClip] becomes its own
 * [EditedMediaItem] (own trim, same global rotate/aspect/filter effects),
 * concatenated gapless via a single [EditedMediaItemSequence] passed to
 * [Composition] — verified against the actual Media3 1.4.1 bytecode (`javap`)
 * before writing this: `EditedMediaItemSequence(List<EditedMediaItem>)`,
 * `Composition.Builder(EditedMediaItemSequence, EditedMediaItemSequence...)`,
 * and `Transformer.start(Composition, String)` all exist in this version.
 * Overlay timing is authored against the *global* composed timeline (matching
 * `VideoEditSession.globalPositionMs()`), so each clip's overlay callback
 * offsets its own (per-item, zero-based) `presentationTimeUs` by the summed
 * trimmed duration of the clips before it to recover the global position.
 *
 * `state.speed` is intentionally NOT applied here. Media3 1.4.1 (the version
 * this module is pinned to, verified via a live Gradle dependency resolution)
 * does not expose `SpeedProvider`/`TimestampAdjustment(SpeedProvider)` — that
 * landed in a later release. Bumping to a newer Media3 version to get it broke
 * other already-verified APIs used elsewhere in this file (`Transformer.Builder
 * .setTransformationRequest`, `OverlayEffect`'s list parameter type), so rather
 * than chase version bumps mid-session and risk destabilizing the working
 * export pipeline, this is a deliberate, documented scope cut — see
 * docs/video-editor.md. Preview still honors speed via `ExoPlayer.setPlaybackSpeed`.
 */
class VideoExporter(private val context: Context) {
  private var transformer: Transformer? = null
  private var outputFile: File? = null
  private var polling = false
  private val handler = Handler(Looper.getMainLooper())
  private val overlayBitmaps = ConcurrentHashMap.newKeySet<Bitmap>()

  fun export(
    clips: List<VideoClip>,
    state: VideoTransformState,
    layers: List<PhotoLayer>,
    exportOptions: JSONObject?,
    onProgress: (Float) -> Unit,
    onComplete: (VideoExportResult) -> Unit,
    onError: (VideoExportException) -> Unit
  ) {
    releaseOverlayBitmaps()
    if (clips.isEmpty()) {
      onError(VideoExportException("E_SOURCE_NOT_FOUND", "There are no clips to export."))
      return
    }
    val totalDurationMs = clips.sumOf { it.trimmedDurationMs() }

    val outputDir = File(context.cacheDir, "photovideoeditor").apply { mkdirs() }
    val output = File(outputDir, "${UUID.randomUUID()}.mp4")
    outputFile = output

    val (sourceWidth, sourceHeight) = readUprightVideoSize(mediaUri(clips.first().sourceUri))

    val editedItems = mutableListOf<EditedMediaItem>()
    var precedingDurationMs = 0L
    for (clip in clips) {
      val effects = mutableListOf<Effect>()
      if (state.rotationDegrees != 0) {
        effects.add(
          ScaleAndRotateTransformation.Builder()
            .setRotationDegrees(state.rotationDegrees.toFloat())
            .build()
        )
      }
      state.aspectRatio?.let { ratio ->
        effects.add(Presentation.createForAspectRatio(ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
      }

      effects.addAll(com.photovideoeditor.video.render.VideoCropEffects.create(state.crop))

      // Video-level filters: export-only for now, no live preview — see docs/video-editor.md.
      if (state.brightness != 0f) effects.add(Brightness(state.brightness / 100f))
      if (state.contrast != 0f) effects.add(Contrast(state.contrast / 100f))
      if (state.saturation != 0f) {
        effects.add(HslAdjustment.Builder().adjustSaturation(state.saturation / 100f).build())
      }

      // Each overlay has its own [PhotoLayer.startMs]/[endMs] on the *global* composed timeline, so the
      // composited bitmap is genuinely time-varying across clip boundaries. Re-rendering on every decoded
      // frame would be wasteful since the active-layer set only actually changes at a handful of
      // timestamps (each overlay's start/end), so the rendered result is cached and only recomputed when
      // the active-layer signature changes between frames.
      //
      // Timestamp caveat (unverified — no device available to confirm): this item's own `getBitmap`
      // presentationTimeUs is assumed to be zero-based at this clip's own trimmed start (matching
      // Transformer's typical per-item effect-pipeline behavior), so the summed duration of preceding
      // clips is added to recover the global timeline position that the overlay's startMs/endMs are
      // authored against.
      if (layers.isNotEmpty()) {
        val clipOffsetMs = precedingDurationMs
        var cachedSignature: List<String>? = null
        var cachedBitmap: Bitmap? = null
        effects.add(
          OverlayEffect(
            listOf<TextureOverlay>(
              object : BitmapOverlay() {
                @Volatile private var outputWidth = sourceWidth.coerceAtLeast(2)
                @Volatile private var outputHeight = sourceHeight.coerceAtLeast(2)
                @Volatile private var settings: OverlaySettings = StaticOverlaySettings.Builder().build()

                override fun configure(videoSize: Size) {
                  super.configure(videoSize)
                  // Media3 composites an overlay texture **1:1 in pixels, centred** on the frame — it
                  // does NOT stretch it to fill (that is what iOS's `contentsGravity = .resizeAspect`
                  // does, which is why only Android was affected). Verified against the 1.8.0 bytecode
                  // of `OverlayMatrixProvider.getTransformationMatrix`: with default anchors/rotation
                  // its matrix chain collapses to
                  //     net scale = (overlaySize / frameSize) * OverlaySettings.scale
                  // (the inner scale/scaleInv pair only exists to apply the anchor offset in unscaled
                  // space; the chain multiplies by `scaleMatrix` once more at the end).
                  //
                  // `PhotoLayerRenderer` draws in coordinates normalized to the bitmap it is handed,
                  // so an overlay bitmap smaller than the frame made every layer render *smaller and
                  // pulled toward the frame centre* by exactly the shortfall — e.g. a 4K export
                  // (3840px long edge) downscaled to a 1920px overlay put every sticker/text at half
                  // its preview size. Videos at or under the cap were unaffected, which is why this
                  // only reproduced on high-resolution sources.
                  //
                  // Fix: keep the memory cap, but tell Media3 to scale the overlay back up to the
                  // full frame. The compensation divides by the *rounded* bitmap size actually
                  // allocated (not the pre-rounding ratio) so rounding cannot reintroduce drift.
                  val plan = OverlayGeometry.canvasPlan(videoSize.width, videoSize.height, MAX_OVERLAY_EDGE_PX)
                  outputWidth = plan.width
                  outputHeight = plan.height
                  settings = StaticOverlaySettings.Builder().setScale(plan.scaleX, plan.scaleY).build()
                }

                override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings

                override fun getBitmap(presentationTimeUs: Long): Bitmap {
                  val positionMs = presentationTimeUs / 1000 + clipOffsetMs
                  val activeLayers = layers.filter { it.isActiveAt(positionMs, totalDurationMs) }
                  val signature = activeLayers.map { it.id }
                  if (signature != cachedSignature || cachedBitmap == null) {
                    val input = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
                    cachedBitmap = PhotoLayerRenderer.render(input, activeLayers) { uri -> resolveImageUri(context, uri) }
                    if (cachedBitmap !== input && !input.isRecycled) input.recycle()
                    // BitmapOverlay may retain the returned frame in the GL
                    // pipeline. Keep it alive until Transformer terminates.
                    cachedBitmap?.let { overlayBitmaps.add(it) }
                    cachedSignature = signature
                  }
                  return cachedBitmap!!
                }
              }
            )
          )
        )
      }

      val mediaItem = MediaItem.Builder()
        .setUri(mediaUri(clip.sourceUri))
        .setClippingConfiguration(
          MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.trimStartMs)
            .setEndPositionMs(clip.effectiveTrimEndMs())
            .build()
        )
        .build()

      // `state.speed` is intentionally not applied here — see the class doc comment above.
      editedItems.add(
        EditedMediaItem.Builder(mediaItem)
          .setEffects(Effects(emptyList(), effects))
          .build()
      )
      precedingDurationMs += clip.trimmedDurationMs()
    }

    val composition = Composition.Builder(EditedMediaItemSequence(editedItems)).build()

    val transformerBuilder = Transformer.Builder(context)
      .setVideoMimeType(MimeTypes.VIDEO_H264)
      .setAudioMimeType(MimeTypes.AUDIO_AAC)

    val builtTransformer = transformerBuilder
      .addListener(object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
          polling = false
          transformer = null
          // The completed file now belongs to the caller. cancel()/destroy must
          // never delete it after the editor activity finishes.
          outputFile = null
          onComplete(
            VideoExportResult(
              uri = Uri.fromFile(output).toString(),
              width = exportResult.width.coerceAtLeast(0),
              height = exportResult.height.coerceAtLeast(0),
              durationMs = if (exportResult.durationMs > 0) exportResult.durationMs else totalDurationMs,
              fileSize = output.length(),
              mimeType = "video/mp4"
            )
          )
          releaseOverlayBitmaps()
        }

        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
          polling = false
          transformer = null
          output.delete()
          releaseOverlayBitmaps()
          onError(VideoExportException("E_EXPORT_FAILED", exportException.message ?: "Unable to export the video."))
        }
      })
      .build()
    transformer = builtTransformer
    try {
      builtTransformer.start(composition, output.absolutePath)
      startPolling(onProgress)
    } catch (error: Exception) {
      polling = false
      transformer = null
      output.delete()
      releaseOverlayBitmaps()
      onError(VideoExportException("E_EXPORT_FAILED", error.message ?: "Unable to start video export."))
    }
  }

  private fun resolveImageUri(context: Context, uri: String): Bitmap? {
    val path = SourceResolver.resolvePath(context, uri, "pve_video_layer_image_export") ?: return null
    return BitmapFactory.decodeFile(path)
  }

  /** The video's decoded frame size after its own rotation metadata is applied (i.e. as it will actually be displayed/exported before any additional user rotation/crop). */
  private fun readUprightVideoSize(uri: Uri): Pair<Int, Int> {
    val retriever = MediaMetadataRetriever()
    return try {
      retriever.setDataSource(context, uri)
      val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
      val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
      val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
      if (rotation == 90 || rotation == 270) height to width else width to height
    } catch (_: Exception) {
      1280 to 720
    } finally {
      retriever.release()
    }
  }

  private fun mediaUri(value: String): Uri {
    val parsed = Uri.parse(value)
    return if (parsed.scheme == null) Uri.fromFile(File(value)) else parsed
  }

  private fun startPolling(onProgress: (Float) -> Unit) {
    polling = true
    val holder = ProgressHolder()
    val runnable = object : Runnable {
      override fun run() {
        val current = transformer
        if (current == null || !polling) return
        try {
          current.getProgress(holder)
          onProgress((holder.progress.coerceIn(0, 100)) / 100f)
          if (polling) handler.postDelayed(this, 300)
        } catch (_: IllegalStateException) {
          // Transformer completed/cancelled between the null check and poll.
        }
      }
    }
    handler.postDelayed(runnable, 300)
  }

  /** Cancels an in-progress export and deletes the partial output file. */
  fun cancel() {
    polling = false
    handler.removeCallbacksAndMessages(null)
    transformer?.cancel()
    transformer = null
    outputFile?.delete()
    outputFile = null
    releaseOverlayBitmaps()
  }

  private fun releaseOverlayBitmaps() {
    overlayBitmaps.forEach { if (!it.isRecycled) it.recycle() }
    overlayBitmaps.clear()
  }

  private companion object {
    /**
     * Overlay-compositing bitmap longest-edge cap — purely a memory guard (an ARGB_8888 bitmap this
     * size is allocated twice transiently while rendering). Geometry no longer depends on it: any
     * shortfall is scaled back to the full frame via `OverlaySettings` (see the `configure` comment),
     * so the cap only trades overlay sharpness for peak memory on above-1440p exports.
     */
    const val MAX_OVERLAY_EDGE_PX = 2560f
  }
}
