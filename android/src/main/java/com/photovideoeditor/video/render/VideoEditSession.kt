package com.photovideoeditor.video.render

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.photovideoeditor.photo.render.PhotoLayerStack

/**
 * Owns the ExoPlayer preview instance, the ordered [VideoClip] list (the
 * multi-clip timeline, Milestone 7), and the [VideoTransformState] that
 * applies globally across the whole composed sequence. The source is never
 * modified — [com.photovideoeditor.video.export.VideoExporter] reads the
 * clip list again to produce the exported file.
 */
class VideoEditSession(context: Context, val sourceUri: String) {
  val player: ExoPlayer = ExoPlayer.Builder(context).build()

  /** Text/sticker/shape overlays. Reuses the photo layer model/stack — see `PhotoLayer.startMs`/`endMs` for timing. */
  val layerStack = PhotoLayerStack()

  var clips: List<VideoClip> = emptyList()
    private set

  var state: VideoTransformState = VideoTransformState()
    private set

  /** Fires once the source is probed and the initial single-clip timeline is ready. */
  var onClipsReady: (() -> Unit)? = null

  init {
    player.setMediaItem(MediaItem.fromUri(Uri.parse(sourceUri)))
    player.prepare()
    player.addListener(object : Player.Listener {
      override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY && clips.isEmpty() && player.duration > 0) {
          clips = listOf(VideoClip(sourceUri = sourceUri, originalDurationMs = player.duration))
          rebuildPlayerTimeline(preserveIndex = 0)
          onClipsReady?.invoke()
        }
      }
    })
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

  fun release() {
    player.release()
  }
}
