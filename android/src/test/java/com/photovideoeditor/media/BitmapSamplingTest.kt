package com.photovideoeditor.media

import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapSamplingTest {
  @Test
  fun `large landscape and portrait inputs are bounded by longest edge`() {
    assertEquals(4, BitmapSampling.inSampleSize(8_000, 1_000, 1_600))
    assertEquals(4, BitmapSampling.inSampleSize(1_000, 8_000, 1_600))
  }

  @Test
  fun `already-small and invalid inputs decode without sampling`() {
    assertEquals(1, BitmapSampling.inSampleSize(1_280, 720, 1_600))
    assertEquals(1, BitmapSampling.inSampleSize(0, 0, 1_600))
  }

  @Test
  fun `sample remains a decoder-compatible power of two`() {
    assertEquals(2, BitmapSampling.inSampleSize(4_000, 3_000, 1_600))
  }
}
