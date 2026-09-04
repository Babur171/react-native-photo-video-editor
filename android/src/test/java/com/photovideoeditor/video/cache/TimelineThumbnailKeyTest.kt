package com.photovideoeditor.video.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineThumbnailKeyTest {
  @Test
  fun `key is deterministic and filesystem safe`() {
    val first = TimelineThumbnailKey.create("content://media/video/42", 1_250, 144, 96)
    val second = TimelineThumbnailKey.create("content://media/video/42", 1_250, 144, 96)

    assertEquals(first, second)
    assertTrue(first.matches(Regex("timeline_[0-9a-f]+_1250_144x96")))
  }

  @Test
  fun `timestamp dimensions and media identity do not collide`() {
    val base = TimelineThumbnailKey.create("clip-a", 1_000, 144, 96)

    assertNotEquals(base, TimelineThumbnailKey.create("clip-b", 1_000, 144, 96))
    assertNotEquals(base, TimelineThumbnailKey.create("clip-a", 1_001, 144, 96))
    assertNotEquals(base, TimelineThumbnailKey.create("clip-a", 1_000, 288, 192))
  }
}
