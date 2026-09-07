package com.photovideoeditor.files

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Resolves an editor source uri (file://, content://, or a bare path) to a
 * local filesystem path that platform decoders (BitmapFactory, ExifInterface)
 * can read directly. content:// sources are copied to a cache-dir temp file
 * since those decoders require a real path.
 */
object SourceResolver {
  fun resolvePath(context: Context, sourceUri: String, tempPrefix: String): String? {
    val uri = Uri.parse(sourceUri)
    return when (uri.scheme) {
      "file", null -> uri.path ?: sourceUri
      "content" -> {
        val tempFile = File.createTempFile(tempPrefix, ".tmp", context.cacheDir)
        val copied = context.contentResolver.openInputStream(uri)?.use { input ->
          tempFile.outputStream().use { output -> input.copyTo(output) }
          true
        } ?: false
        if (copied) tempFile.absolutePath else null
      }
      else -> null
    }
  }
}
