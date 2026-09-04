package com.photovideoeditor.stickers

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** One entry from OpenMoji's `data/openmoji.json` index. */
data class OpenMojiEntry(
  val emoji: String,
  val hexcode: String,
  val group: String,
  val annotation: String,
  val tags: String
)

/**
 * Fetches and disk-caches the OpenMoji catalog index (CC BY-SA 4.0, https://openmoji.org),
 * exposing search/filter helpers plus the CDN URL builders for thumbnail/full-res assets.
 *
 * Networking uses plain [HttpURLConnection] on a background executor (this codebase has no
 * networking dependency and shouldn't gain one for a simple GET) and parses with
 * [org.json.JSONArray]/[org.json.JSONObject], matching the JSON style already used elsewhere
 * (e.g. `PhotoVideoEditorActivity`'s `stickerAssets` parsing).
 */
object OpenMojiCatalog {
  private const val INDEX_URL =
    "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/data/openmoji.json"
  private const val CACHE_FILE_NAME = "openmoji_index.json"
  private const val CACHE_TIMESTAMP_PREFS = "openmoji_cache"
  private const val CACHE_TIMESTAMP_KEY = "fetched_at"
  private val CACHE_TTL_MS = 7L * 24L * 60L * 60L * 1000L

  /** Fixed, sensible display order for the "All" chip row; unknown groups are appended after. */
  private val GROUP_DISPLAY_ORDER = listOf(
    "smileys-emotion",
    "people-body",
    "animals-nature",
    "food-drink",
    "travel-places",
    "activities",
    "objects",
    "symbols",
    "flags"
  )

  private val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "openmoji-catalog") }
  private val mainHandler = Handler(Looper.getMainLooper())

  @Volatile private var cachedEntries: List<OpenMojiEntry>? = null

  /**
   * Loads the catalog (memory -> disk cache -> network, falling back to any cached copy on
   * network failure), delivering the result on the main thread.
   */
  fun load(context: Context, onResult: (entries: List<OpenMojiEntry>?, error: Exception?) -> Unit) {
    val inMemory = cachedEntries
    if (inMemory != null) {
      mainHandler.post { onResult(inMemory, null) }
      return
    }
    val appContext = context.applicationContext
    executor.execute {
      try {
        val entries = loadBlocking(appContext)
        cachedEntries = entries
        mainHandler.post { onResult(entries, null) }
      } catch (e: Exception) {
        mainHandler.post { onResult(null, e) }
      }
    }
  }

  private fun loadBlocking(context: Context): List<OpenMojiEntry> {
    val cacheFile = File(context.cacheDir, CACHE_FILE_NAME)
    val cacheAgeMs = cacheAgeMs(context)
    val cacheFresh = cacheFile.exists() && cacheAgeMs != null && cacheAgeMs < CACHE_TTL_MS

    if (cacheFresh) {
      return parse(cacheFile.readText())
    }

    return try {
      val json = fetch(INDEX_URL)
      cacheFile.writeText(json)
      markCacheTimestamp(context)
      parse(json)
    } catch (e: Exception) {
      // Network failed: fall back to any cached copy, regardless of age, per spec.
      if (cacheFile.exists()) parse(cacheFile.readText()) else throw e
    }
  }

  private fun fetch(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.instanceFollowRedirects = true
    connection.connectTimeout = 15_000
    connection.readTimeout = 30_000
    connection.requestMethod = "GET"
    try {
      val code = connection.responseCode
      if (code !in 200..299) throw java.io.IOException("HTTP $code fetching $urlString")
      return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
      connection.disconnect()
    }
  }

  private fun parse(json: String): List<OpenMojiEntry> {
    val array = JSONArray(json)
    val result = ArrayList<OpenMojiEntry>(array.length())
    for (i in 0 until array.length()) {
      val obj: JSONObject = array.optJSONObject(i) ?: continue
      val hexcode = obj.optString("hexcode").takeIf { it.isNotBlank() } ?: continue
      result.add(
        OpenMojiEntry(
          emoji = obj.optString("emoji"),
          hexcode = hexcode,
          group = obj.optString("group"),
          annotation = obj.optString("annotation"),
          tags = obj.optString("tags")
        )
      )
    }
    return result
  }

  private fun cacheAgeMs(context: Context): Long? {
    val prefs = context.getSharedPreferences(CACHE_TIMESTAMP_PREFS, Context.MODE_PRIVATE)
    val fetchedAt = prefs.getLong(CACHE_TIMESTAMP_KEY, -1L)
    if (fetchedAt < 0) return null
    return System.currentTimeMillis() - fetchedAt
  }

  private fun markCacheTimestamp(context: Context) {
    context.getSharedPreferences(CACHE_TIMESTAMP_PREFS, Context.MODE_PRIVATE)
      .edit()
      .putLong(CACHE_TIMESTAMP_KEY, System.currentTimeMillis())
      .apply()
  }

  fun groups(): List<String> {
    val entries = cachedEntries ?: return emptyList()
    val present = entries.map { it.group }.distinct()
    val ordered = GROUP_DISPLAY_ORDER.filter { it in present }
    val rest = present.filter { it !in GROUP_DISPLAY_ORDER }.sorted()
    return ordered + rest
  }

  fun byGroup(group: String): List<OpenMojiEntry> =
    cachedEntries?.filter { it.group == group } ?: emptyList()

  fun search(query: String): List<OpenMojiEntry> {
    val entries = cachedEntries ?: return emptyList()
    val needle = query.trim().lowercase()
    if (needle.isEmpty()) return entries
    return entries.filter {
      it.annotation.lowercase().contains(needle) || it.tags.lowercase().contains(needle)
    }
  }

  fun thumbnailUrl(hexcode: String): String =
    "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/72x72/$hexcode.png"

  fun fullUrl(hexcode: String): String =
    "https://cdn.jsdelivr.net/gh/hfg-gmuend/openmoji@master/color/618x618/$hexcode.png"
}
