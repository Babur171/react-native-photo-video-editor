package com.photovideoeditor.photo.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.photovideoeditor.files.SourceResolver
import com.photovideoeditor.photo.render.PhotoAdjustmentRenderer
import com.photovideoeditor.photo.render.PhotoAdjustments
import com.photovideoeditor.photo.render.PhotoLayer
import com.photovideoeditor.photo.render.PhotoLayerRenderer
import com.photovideoeditor.photo.render.PhotoTransformState
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

data class PhotoExportResult(
  val uri: String,
  val width: Int,
  val height: Int,
  val fileSize: Long,
  val mimeType: String
)

class PhotoExportException(val code: String, message: String) : Exception(message)

/**
 * Applies a [PhotoTransformState] to the full-resolution source photo and
 * writes the result to a new file in the app cache dir. The source file is
 * never modified. Bitmaps are downsampled to the requested export bounds (or
 * a safe default) to avoid decoding an unbounded full-resolution image twice.
 */
object PhotoExporter {
  private const val DEFAULT_MAX_DIMENSION = 4096

  fun export(
    context: Context,
    sourceUri: String,
    transform: PhotoTransformState,
    adjustments: PhotoAdjustments,
    layers: List<PhotoLayer>,
    exportOptions: JSONObject?
  ): PhotoExportResult {
    val path = SourceResolver.resolvePath(context, sourceUri, "pve_export")
      ?: throw PhotoExportException("E_SOURCE_NOT_FOUND", "The selected photo could not be found.")

    val exif = try { ExifInterface(path) } catch (_: Exception) { null }
    val exifOrientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
      ?: ExifInterface.ORIENTATION_NORMAL

    val maxWidth = exportOptions?.optInt("maxWidth", 0)?.takeIf { it > 0 }
    val maxHeight = exportOptions?.optInt("maxHeight", 0)?.takeIf { it > 0 }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      throw PhotoExportException("E_SOURCE_UNREADABLE", "The selected photo could not be decoded.")
    }

    val decodeTarget = maxOf(maxWidth ?: DEFAULT_MAX_DIMENSION, maxHeight ?: DEFAULT_MAX_DIMENSION)
    val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, decodeTarget)
    val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
      ?: throw PhotoExportException("E_OUT_OF_MEMORY", "The photo is too large to process on this device.")

    val upright = applyExifOrientation(decoded, exifOrientation)
    val transformed = applyTransform(upright, transform)
    val adjusted = PhotoAdjustmentRenderer.apply(transformed, adjustments)
    val layered = PhotoLayerRenderer.render(adjusted, layers) { uri -> resolveImageUri(context, uri) }
    val cropped = cropBitmap(layered, transform)
    val resized = resizeToFit(cropped, maxWidth, maxHeight)

    val format = exportOptions?.optString("imageFormat", "jpeg")?.lowercase() ?: "jpeg"
    val quality = qualityFor(exportOptions?.optString("quality", "high") ?: "high")
    val (compressFormat, mimeType, extension) = encodingFor(format)

    val outputDir = File(context.cacheDir, "photovideoeditor").apply { mkdirs() }
    val outputFile = File(outputDir, "${UUID.randomUUID()}.$extension")
    FileOutputStream(outputFile).use { stream ->
      if (!resized.compress(compressFormat, quality, stream)) {
        throw PhotoExportException("E_EXPORT_FAILED", "Unable to encode the exported photo.")
      }
    }

    if (format == "jpeg" && (exportOptions?.optBoolean("preserveMetadata", true) != false)) {
      preserveMetadata(exif, outputFile.absolutePath)
    }

    if (decoded !== upright) decoded.recycle()
    if (upright !== transformed) upright.recycle()
    if (transformed !== adjusted) transformed.recycle()
    if (adjusted !== layered) adjusted.recycle()
    if (layered !== cropped) layered.recycle()
    if (cropped !== resized) cropped.recycle()

    return PhotoExportResult(
      uri = Uri.fromFile(outputFile).toString(),
      width = resized.width,
      height = resized.height,
      fileSize = outputFile.length(),
      mimeType = mimeType
    )
  }

  private fun resolveImageUri(context: Context, uri: String): Bitmap? {
    val path = SourceResolver.resolvePath(context, uri, "pve_layer_image_export") ?: return null
    return BitmapFactory.decodeFile(path)
  }

  private fun computeSampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
    return sample
  }

  private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  private fun applyTransform(bitmap: Bitmap, transform: PhotoTransformState): Bitmap {
    if (transform.rotationDegrees == 0 && transform.straightenDegrees == 0f) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform.toMatrix(), true)
  }

  private fun cropBitmap(bitmap: Bitmap, transform: PhotoTransformState): Bitmap {
    if (transform.cropLeft <= 0f && transform.cropTop <= 0f && transform.cropRight >= 1f && transform.cropBottom >= 1f) {
      return bitmap
    }
    val left = (transform.cropLeft * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
    val top = (transform.cropTop * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
    val right = (transform.cropRight * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
    val bottom = (transform.cropBottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
  }

  private fun resizeToFit(bitmap: Bitmap, maxWidth: Int?, maxHeight: Int?): Bitmap {
    if (maxWidth == null && maxHeight == null) return bitmap
    val widthLimit = maxWidth ?: bitmap.width
    val heightLimit = maxHeight ?: bitmap.height
    if (bitmap.width <= widthLimit && bitmap.height <= heightLimit) return bitmap
    val scale = minOf(widthLimit.toFloat() / bitmap.width, heightLimit.toFloat() / bitmap.height)
    val targetWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
  }

  private fun qualityFor(preset: String): Int = when (preset) {
    "low" -> 50
    "medium" -> 75
    "original" -> 100
    else -> 90
  }

  @Suppress("DEPRECATION")
  private fun legacyWebp() = Bitmap.CompressFormat.WEBP

  private fun encodingFor(format: String): Triple<Bitmap.CompressFormat, String, String> = when (format) {
    "png" -> Triple(Bitmap.CompressFormat.PNG, "image/png", "png")
    "webp" -> Triple(
      if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else legacyWebp(),
      "image/webp",
      "webp"
    )
    else -> Triple(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
  }

  private fun preserveMetadata(source: ExifInterface?, outputPath: String) {
    if (source == null) return
    try {
      val output = ExifInterface(outputPath)
      listOf(
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF
      ).forEach { tag -> source.getAttribute(tag)?.let { output.setAttribute(tag, it) } }
      // Pixels are already upright post-transform; the copied source orientation tag must not be reapplied.
      output.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
      output.saveAttributes()
    } catch (_: Exception) {
      // Best-effort only; a metadata write failure must not fail the export.
    }
  }
}
