package com.photovideoeditor.video.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object TimelineThumbnailKey {
  fun create(mediaId: String, timestampMs: Long, width: Int, height: Int): String =
    "timeline_${mediaId.sha256()}_${timestampMs}_${width}x$height"

  private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}

/** Bounded memory -> disk -> decoder pipeline for timeline thumbnails. */
class TimelineThumbnailRepository(
  context: Context,
  private val maxDiskBytes: Long = 96L * 1024L * 1024L,
) {
  class Request internal constructor() {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    internal fun isCancelled() = cancelled.get()
  }

  private val appContext = context.applicationContext
  private val cacheDir = File(appContext.cacheDir, "pve_timeline_thumbnails").apply { mkdirs() }
  private val executor = Executors.newFixedThreadPool(2) { task ->
    Thread(task, "timeline-thumbnail").apply { priority = Thread.NORM_PRIORITY - 1 }
  }
  private val requests = ConcurrentHashMap.newKeySet<Request>()
  private val memoryLimitKb = (Runtime.getRuntime().maxMemory() / 16 / 1024)
    .coerceIn(4L * 1024L, 64L * 1024L).toInt()
  private val memory = object : LruCache<String, Bitmap>(memoryLimitKb) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount / 1024
  }

  fun load(
    mediaId: String,
    uri: Uri,
    durationMs: Long,
    count: Int,
    width: Int,
    height: Int,
    onThumbnail: (index: Int, bitmap: Bitmap) -> Unit,
    onComplete: () -> Unit,
  ): Request {
    val request = Request()
    requests.add(request)
    executor.execute {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(appContext, uri)
        val safeCount = count.coerceIn(1, 24)
        val step = durationMs.coerceAtLeast(1) / safeCount
        for (index in 0 until safeCount) {
          if (request.isCancelled()) break
          val timestampMs = index * step + step / 2
          val key = TimelineThumbnailKey.create(mediaId, timestampMs, width, height)
          val bitmap = memory.get(key) ?: readDisk(key) ?: decode(retriever, timestampMs, width, height)?.also {
            memory.put(key, it)
            writeDisk(key, it)
          }
          if (bitmap != null && !request.isCancelled()) onThumbnail(index, bitmap)
        }
        if (!request.isCancelled()) onComplete()
      } catch (_: Exception) {
        if (!request.isCancelled()) onComplete()
      } finally {
        retriever.release()
        requests.remove(request)
        trimDisk()
      }
    }
    return request
  }

  private fun decode(retriever: MediaMetadataRetriever, timestampMs: Long, width: Int, height: Int): Bitmap? {
    val frame = if (Build.VERSION.SDK_INT >= 27) {
      retriever.getScaledFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, height)
    } else {
      retriever.getFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let {
        Bitmap.createScaledBitmap(it, width, height, true).also { scaled -> if (scaled !== it) it.recycle() }
      }
    }
    return frame
  }

  private fun readDisk(key: String): Bitmap? {
    val file = diskFile(key).takeIf { it.isFile } ?: return null
    return BitmapFactory.decodeFile(file.absolutePath)?.also {
      memory.put(key, it)
      file.setLastModified(System.currentTimeMillis())
    }
  }

  private fun writeDisk(key: String, bitmap: Bitmap) {
    runCatching { diskFile(key).outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) } }
  }

  private fun trimDisk() {
    val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
    var bytes = files.sumOf { it.length() }
    for (file in files) {
      if (bytes <= maxDiskBytes) break
      bytes -= file.length()
      file.delete()
    }
  }

  private fun diskFile(key: String) = File(cacheDir, "$key.jpg")

  fun clearMemory() = memory.evictAll()

  fun close() {
    requests.forEach(Request::cancel)
    requests.clear()
    executor.shutdownNow()
    memory.evictAll()
  }
}
