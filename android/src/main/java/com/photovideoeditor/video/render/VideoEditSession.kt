package com.photovideoeditor.video.render

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import com.photovideoeditor.photo.render.PhotoLayerStack
import java.io.File

/**
 * Owns the ExoPlayer preview instance, the ordered [VideoClip] list (the
 * multi-clip timeline, Milestone 7), and the [VideoTransformState] that
 * applies globally across the whole composed sequence. The source is never
 * modified — [com.photovideoeditor.video.export.VideoExporter] reads the
 * clip list again to produce the exported file.
 */
class VideoEditSession(context: Context, val sourceUri: String) {
  var sourceWidth = 0
    private set
  var sourceHeight = 0
    private set
  // Bound compressed samples independently of bitrate/duration. Time-first buffering
  // can otherwise exhaust the host app's heap on high-bitrate camera footage.
  val player: ExoPlayer = ExoPlayer.Builder(context)
    .setLoadControl(DefaultLoadControl.Builder()
      .setTargetBufferBytes(VideoBufferBudget.targetBytes(Runtime.getRuntime().maxMemory()))
      .setBufferDurationsMs(2_000, 5_000, 500, 1_000)
      .setPrioritizeTimeOverSizeThresholds(false)
      .setBackBuffer(0, false)
      .build())
    .build()
  private var previewStoppedForExport = false

  /** Text/sticker/shape overlays. Reuses the photo layer model/stack — see `PhotoLayer.startMs`/`endMs` for timing. */
  val layerStack = PhotoLayerStack()

  var clips: List<VideoClip> = emptyList()
    private set

  var state: VideoTransformState = VideoTransformState()
    private set

  /** Fires once the source is probed and the initial single-clip timeline is ready. */
  var onClipsReady: (() -> Unit)? = null
    set(value) {
      field = value
      // Activity view construction may finish after a very fast local source
      // reaches STATE_READY. Never lose that one-shot notification.
      if (value != null && clips.isNotEmpty()) value.invoke()
    }

  /** Reports decoder/source failures instead of leaving a blank player forever. */
  var onPlaybackError: ((String) -> Unit)? = null
    set(value) {
      field = value
      pendingPlaybackError?.let { error -> value?.invoke(error) }
    }

  private var pendingPlaybackError: String? = null
  private var initialTimelineCreated = false

  init {
    player.addListener(object : Player.Listener {
      override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
        if (sourceWidth == 0 && size.width > 0 && size.height > 0) {
          sourceWidth = (size.width * size.pixelWidthHeightRatio).toInt()
          sourceHeight = size.height
        }
      }
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY && !initialTimelineCreated && player.duration > 0) {
          initialTimelineCreated = true
          clips = listOf(VideoClip(sourceUri = sourceUri, originalDurationMs = player.duration))
          rebuildPlayerTimeline(preserveIndex = 0)
          onClipsReady?.invoke()
        }
      }

      override fun onPlayerError(error: PlaybackException) {
        val message = error.message ?: "The selected video could not be played."
        pendingPlaybackError = message
        onPlaybackError?.invoke(message)
      }
    })
    player.setMediaItem(MediaItem.fromUri(playbackUri(sourceUri)))
    player.prepare()
  }

  /** Sum of every clip's trimmed duration — the length of the composed timeline. */
  val durationMs: Long get() = clips.sumOf { it.trimmedDurationMs() }

  fun update(transform: (VideoTransformState) -> VideoTransformState) {
    state = transform(state)
  }

  /** Applies [transform] to the clip list and rebuilds the player's playlist to match. */
  fun updateClips(transform: (List<VideoClip>) -> List<VideoClip>) {
    val previousIndex = player.currentMediaItemIndex
    clips = transform(clips)
    rebuildPlayerTimeline(preserveIndex = previousIndex)
  }

  private fun rebuildPlayerTimeline(preserveIndex: Int) {
    if (clips.isEmpty()) return
    val items = clips.map { clip ->
      MediaItem.Builder()
        .setUri(Uri.parse(clip.sourceUri))
        .setClippingConfiguration(
          MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(clip.trimStartMs)
            .setEndPositionMs(clip.effectiveTrimEndMs())
            .build()
        )
        .build()
    }
    player.setMediaItems(items)
    player.prepare()
    val safeIndex = preserveIndex.coerceIn(0, clips.size - 1)
    player.seekTo(safeIndex, 0)
  }

  /** Position within the whole multi-clip timeline: preceding clips' trimmed durations + position in the current clip. */
  fun globalPositionMs(): Long {
    if (clips.isEmpty()) return 0
    val index = player.currentMediaItemIndex.coerceIn(0, clips.size - 1)
    val preceding = clips.take(index).sumOf { it.trimmedDurationMs() }
    return preceding + player.currentPosition.coerceAtLeast(0)
  }

  /** Seeks the player to a position expressed in the composed (global) timeline. */
  fun seekToGlobalMs(targetMs: Long) {
    if (clips.isEmpty()) return
    var remaining = targetMs.coerceIn(0, durationMs)
    for ((index, clip) in clips.withIndex()) {
      val clipDuration = clip.trimmedDurationMs()
      if (remaining <= clipDuration || index == clips.size - 1) {
        player.seekTo(index, remaining.coerceIn(0, clipDuration))
        return
      }
      remaining -= clipDuration
    }
  }

  /** Pause alone retains samples and decoders; stop releases them while retaining the playlist/position. */
  fun stopPreviewForExport() {
    if (previewStoppedForExport) return
    previewStoppedForExport = true
    player.pause()
    player.stop()
  }

  /** Export failure/cancellation returns to the same paused preview. */
  fun restorePreviewAfterExport() {
    if (!previewStoppedForExport) return
    previewStoppedForExport = false
    player.prepare()
  }

  fun release() {
    onClipsReady = null
    onPlaybackError = null
    player.release()
  }

  private fun playbackUri(value: String): Uri {
    val parsed = Uri.parse(value)
    return if (parsed.scheme == null) Uri.fromFile(File(value)) else parsed
  }
}
