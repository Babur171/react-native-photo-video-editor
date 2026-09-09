package com.photovideoeditor.photo.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.photovideoeditor.files.SourceResolver
import com.photovideoeditor.media.BitmapSampling

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
  val layerStack = PhotoLayerStack()

  val baseBitmap: Bitmap? = decodeUpright(context, sourceUri, maxPreviewDimension)

  private var cachedBaseBitmap: Bitmap? = null
  private var cachedKey: Pair<PhotoTransformState, PhotoAdjustments>? = null
  private var cachedPreviewBitmap: Bitmap? = null
  private var cachedPreviewBase: Bitmap? = null
  private var cachedPreviewLayerRevision = -1L
  private val imageLayerCache = mutableMapOf<String, Bitmap?>()

  fun update(transform: (PhotoTransformState) -> PhotoTransformState) {
    state = transform(state)
  }

  fun updateAdjustments(transform: (PhotoAdjustments) -> PhotoAdjustments) {
    adjustments = transform(adjustments)
  }

  /** Rotate/straighten + adjustments, cached so dragging a layer doesn't re-run the full pipeline every frame. */
  private fun computeBase(): Bitmap? {
    val base = baseBitmap ?: return null
    val key = Pair(state, adjustments)
    cachedBaseBitmap?.let { if (cachedKey == key) return it }
    val transformed = PhotoTransformRenderer.transform(base, state)
    val adjusted = PhotoAdjustmentRenderer.apply(transformed, adjustments)
    cachedBaseBitmap = adjusted
    cachedKey = key
    return adjusted
  }

  /** Renders rotate/straighten + adjustments + layers — the crop overlay draws the crop live on top of this. */
  fun renderPreview(cropping: Boolean = false): Bitmap? {
    val transformed = computeBase() ?: return null
    if (cropping) return transformed
    val base = PhotoTransformRenderer.crop(transformed, state)
    val layers = layerStack.layers
    cachedPreviewBitmap?.let { cached ->
      if (cachedPreviewBase === base && cachedPreviewLayerRevision == layerStack.revision) return cached
    }
    return PhotoLayerRenderer.render(base, layers, ::resolveImageUri).also {
      cachedPreviewBitmap = it
      cachedPreviewBase = base
      cachedPreviewLayerRevision = layerStack.revision
    }
  }

  /** Releases large preview and sticker/overlay bitmaps when the editor leaves the screen. */
  fun release() {
    val owned = buildSet {
      baseBitmap?.let(::add)
      cachedBaseBitmap?.let(::add)
      cachedPreviewBitmap?.let(::add)
      imageLayerCache.values.filterNotNull().forEach(::add)
    }
    owned.forEach { if (!it.isRecycled) it.recycle() }
    cachedBaseBitmap = null
    cachedPreviewBitmap = null
    cachedPreviewBase = null
    cachedPreviewLayerRevision = -1L
    imageLayerCache.clear()
  }

  /** Resolves a sticker-uri or overlay-uri layer's image URI to a decoded bitmap, caching by URI. */
  private fun resolveImageUri(uri: String): Bitmap? {
    imageLayerCache[uri]?.let { return it }
    if (imageLayerCache.containsKey(uri)) return null
    val path = SourceResolver.resolvePath(context, uri, "pve_layer_image")
    val bitmap = path?.let { BitmapFactory.decodeFile(it) }
    imageLayerCache[uri] = bitmap
    return bitmap
  }

  private fun decodeUpright(context: Context, sourceUri: String, maxDimension: Int): Bitmap? {
    val path = SourceResolver.resolvePath(context, sourceUri, "pve_preview") ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val sample = BitmapSampling.inSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
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
      // Mirrored-and-rotated: what a front-facing camera writes for a portrait
      // shot (CameraX marks the capture as reversed-horizontal, which combines
      // with the 90/270 sensor rotation into TRANSPOSE/TRANSVERSE rather than
      // a plain ROTATE_90/270). Falling through to `else` here left those
      // photos unrotated in the editor.
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(270f)
        matrix.postScale(-1f, 1f)
      }
      else -> return decoded
    }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
  }
}
