package com.photovideoeditor.video.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoBufferBudgetTest {
  private val mib = 1024L * 1024L

  @Test fun `256 MiB crash device leaves most heap available to the host`() {
    assertEquals(16 * mib, VideoBufferBudget.targetBytes(256 * mib).toLong())
  }

  @Test fun `small heaps get proportionally smaller sample buffers`() {
    for (heapMiB in listOf(32, 64, 128, 256)) {
      val heap = heapMiB * mib
      val target = VideoBufferBudget.targetBytes(heap)
      assertTrue(target.toLong() <= heap / 16)
      assertTrue(target >= 2 * mib)
    }
  }

  @Test fun `large heaps do not expand the video buffer and overflow is avoided`() {
    assertEquals(16 * mib, VideoBufferBudget.targetBytes(Long.MAX_VALUE).toLong())
    assertEquals(2 * mib, VideoBufferBudget.targetBytes(0).toLong())
  }
}
