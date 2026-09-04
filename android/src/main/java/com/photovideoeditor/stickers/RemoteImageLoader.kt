package com.photovideoeditor.stickers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Small async image pipeline for the online-stickers sheet: thumbnails for the grid (memory LRU
 * + disk cache) and full-res download-on-tap for the sticker that gets inserted as a layer.
 *
 * No image library dependency (Glide/Coil/Picasso) is used, matching this codebase's
 * dependency-light approach elsewhere.
 */
object RemoteImageLoader {
  private val executor = Executors.newFixedThreadPool(4) { task -> Thread(task, "openmoji-image") }
  private val mainHandler = Handler(Looper.getMainLooper())

  private val memoryCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
  }

  private fun diskCacheDir(context: Context): File =
    File(context.cacheDir, "openmoji").apply { mkdirs() }

  private fun cacheFileFor(context: Context, url: String): File {
    val name = MessageDigest.getInstance("MD5").digest(url.toByteArray())
      .joinToString("") { "%02x".format(it) }
    return File(diskCacheDir(context), "$name.png")
  }

  /**
   * Loads [url] into [imageView], tagging the view with the URL being loaded so a recycled
   * RecyclerView cell doesn't get a stale bitmap set on it after the fact.
   */
  fun loadThumbnail(context: Context, url: String, imageView: ImageView) {
    imageView.tag = url
    val memoryHit = memoryCache.get(url)
    if (memoryHit != null) {
      imageView.setImageBitmap(memoryHit)
      return
    }
    imageView.setImageDrawable(null)
    val appContext = context.applicationContext
    executor.execute {
      val bitmap = try {
        val cacheFile = cacheFileFor(appContext, url)
        if (cacheFile.exists()) {
          BitmapFactory.decodeFile(cacheFile.absolutePath)
        } else {
          val bytes = download(url)
          cacheFile.writeBytes(bytes)
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
      } catch (_: Exception) {
        null
      }
      if (bitmap != null) {
        memoryCache.put(url, bitmap)
        mainHandler.post {
          if (imageView.tag == url) imageView.setImageBitmap(bitmap)
        }
      }
    }
  }

  /**
   * Downloads (or returns the cached copy of) the full-res sticker image, delivering the local
   * file path on the main thread.
   */
  fun downloadFull(context: Context, url: String, onResult: (localFilePath: String?, error: Exception?) -> Unit) {
    val appContext = context.applicationContext
    executor.execute {
      try {
        val cacheFile = cacheFileFor(appContext, url)
        if (!cacheFile.exists()) {
          val bytes = download(url)
          FileOutputStream(cacheFile).use { it.write(bytes) }
        }
        mainHandler.post { onResult(cacheFile.absolutePath, null) }
      } catch (e: Exception) {
        mainHandler.post { onResult(null, e) }
      }
    }
  }

  private fun download(urlString: String): ByteArray {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.requestMethod = "GET"
    try {
      val code = connection.responseCode
      if (code !in 200..299) throw java.io.IOException("HTTP $code fetching $urlString")
      return connection.inputStream.use { it.readBytes() }
    } finally {
      connection.disconnect()
    }
  }
}
