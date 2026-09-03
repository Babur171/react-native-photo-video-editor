package com.photovideoeditor.photo.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.photovideoeditor.files.SourceResolver

/**
 * Owns an in-memory, downsampled working copy of the source photo for fast,
 * memory-safe live preview. The full-resolution source is decoded again, once,
 * only at export time (see [com.photovideoeditor.photo.export.PhotoExporter]).
 */
class PhotoEditSession(private val context: Context, sourceUri: String, maxPreviewDimension: Int = 1600) {
  var state: PhotoTransformState = PhotoTransformState()
    private set
  var adjustments: PhotoAdjustments = PhotoAdjustments()
    private set
  var depthBlur: PhotoDepthBlur = PhotoDepthBlur()
    private set
  val layerStack = PhotoLayerStack()

  val baseBitmap: Bitmap? = decodeUpright(context, sourceUri, maxPreviewDimension)

  private var cachedBaseBitmap: Bitmap? = null
  private var cachedKey: Triple<PhotoTransformState, PhotoAdjustments, PhotoDepthBlur>? = null
  private var cachedPreviewBitmap: Bitmap? = null
  private var cachedPreviewBase: Bitmap? = null
  private var cachedPreviewLayerRevision = -1L
  private val stickerCache = mutableMapOf<String, Bitmap?>()

  fun update(transform: (PhotoTransformState) -> PhotoTransformState) {
    state = transform(state)
  }

  fun updateAdjustments(transform: (PhotoAdjustments) -> PhotoAdjustments) {
    adjustments = transform(adjustments)
  }

  fun updateDepthBlur(transform: (PhotoDepthBlur) -> PhotoDepthBlur) {
    depthBlur = transform(depthBlur)
  }

  /** Rotate/flip/straighten + adjustments + depth blur, cached so dragging a layer doesn't re-run the full pipeline every frame. */
  private fun computeBase(): Bitmap? {
    val base = baseBitmap ?: return null
    val key = Triple(state, adjustments, depthBlur)
    cachedBaseBitmap?.let { if (cachedKey == key) return it }
    val transformed = if (state.rotationDegrees == 0 && state.straightenDegrees == 0f && state.flip == FlipState.NONE) {
      base
    } else {
      Bitmap.createBitmap(base, 0, 0, base.width, base.height, state.toMatrix(), true)
    }
    val adjusted = PhotoAdjustmentRenderer.apply(transformed, adjustments)
    val blurred = PhotoDepthBlurRenderer.apply(adjusted, depthBlur)
    cachedBaseBitmap = blurred
    cachedKey = key
    return blurred
  }

  /** Renders rotate/flip/straighten + adjustments + layers — the crop overlay draws the crop live on top of this. */
  fun renderPreview(): Bitmap? {
    val base = computeBase() ?: return null
    val layers = layerStack.layers
    cachedPreviewBitmap?.let { cached ->
      if (cachedPreviewBase === base && cachedPreviewLayerRevision == layerStack.revision) return cached
    }
    return PhotoLayerRenderer.render(base, layers, ::resolveSticker).also {
      cachedPreviewBitmap = it
      cachedPreviewBase = base
      cachedPreviewLayerRevision = layerStack.revision
    }
  }

  /** Releases large preview and sticker bitmaps when the editor leaves the screen. */
  fun release() {
    val owned = buildSet {
      baseBitmap?.let(::add)
      cachedBaseBitmap?.let(::add)
      cachedPreviewBitmap?.let(::add)
      stickerCache.values.filterNotNull().forEach(::add)
    }
    owned.forEach { if (!it.isRecycled) it.recycle() }
    cachedBaseBitmap = null
    cachedPreviewBitmap = null
    cachedPreviewBase = null
    cachedPreviewLayerRevision = -1L
    stickerCache.clear()
  }

  private fun resolveSticker(uri: String): Bitmap? {
    stickerCache[uri]?.let { return it }
    if (stickerCache.containsKey(uri)) return null
    val path = SourceResolver.resolvePath(context, uri, "pve_sticker")
    val bitmap = path?.let { BitmapFactory.decodeFile(it) }
    stickerCache[uri] = bitmap
    return bitmap
  }

  private fun decodeUpright(context: Context, sourceUri: String, maxDimension: Int): Bitmap? {
    val path = SourceResolver.resolvePath(context, sourceUri, "pve_preview") ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimension && bounds.outHeight / (sample * 2) >= maxDimension) {
      sample *= 2
    }
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

    val orientation = try {
      ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
      ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      else -> return decoded
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
  }
}
