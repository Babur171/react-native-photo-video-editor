package com.photovideoeditor.video.render

import java.util.UUID

/**
 * One segment in the multi-clip timeline. Every clip in this milestone shares
 * the same source file (created via Split/Duplicate of one input) — importing
 * separate additional source files from a system picker is a follow-up, not
 * attempted here.
 */
data class VideoClip(
  val id: String = UUID.randomUUID().toString(),
  val sourceUri: String,
  val originalDurationMs: Long,
  var trimStartMs: Long = 0L,
  var trimEndMs: Long = 0L
) {
  fun effectiveTrimEndMs(): Long = if (trimEndMs in 1..originalDurationMs) trimEndMs else originalDurationMs
  fun trimmedDurationMs(): Long = (effectiveTrimEndMs() - trimStartMs).coerceAtLeast(0L)
}
