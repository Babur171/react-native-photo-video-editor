package com.photovideoeditor.photo.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLayerContractTest {
  @Test fun defaultOverlayIsVisibleOnFinalFrame() {
    val layer = PhotoLayer(type = LayerType.TEXT)
    assertTrue(layer.isActiveAt(10_000, 10_000))
    assertEquals(10_000, layer.effectiveEndMs(10_000))
  }

  @Test fun defaultOverlayStaysVisibleDespiteMinorTimestampOvershoot() {
    // `durationMs` is a separately-computed estimate (summed clip-trim durations) that can be a
    // few ms shy of the real last decoded frame's timestamp due to frame-rate rounding. A
    // default (no explicit end) overlay must not be hidden by that rounding gap.
    val layer = PhotoLayer(type = LayerType.TEXT)
    assertTrue(layer.isActiveAt(10_005, 10_000))
    assertTrue(layer.isActiveAt(50_000, 10_000))
  }

  @Test fun explicitEndAtOrPastDurationBehavesLikeDefault() {
    val layer = PhotoLayer(type = LayerType.TEXT, text = "Timed", endMs = 10_000)
    assertTrue(layer.isActiveAt(10_005, 10_000))
  }

  @Test fun explicitTimingIsInclusiveAndRespected() {
    val layer = PhotoLayer(type = LayerType.TEXT, text = "Timed", startMs = 2_000, endMs = 8_000)
    assertFalse(layer.isActiveAt(1_999, 10_000))
    assertTrue(layer.isActiveAt(2_000, 10_000))
    assertTrue(layer.isActiveAt(8_000, 10_000))
    assertFalse(layer.isActiveAt(8_001, 10_000))
  }

  @Test fun resizedAndRotatedPropertiesRemainExactInSnapshots() {
    val layer = PhotoLayer(type = LayerType.TEXT, text = "Large", x = .23f, y = .71f, scale = 2.4f, rotationDegrees = 37f, opacity = .42f, fontSize = 116f)
    val snapshot = layer.copy()
    assertEquals(.23f, snapshot.x)
    assertEquals(.71f, snapshot.y)
    assertEquals(2.4f, snapshot.scale)
    assertEquals(37f, snapshot.rotationDegrees)
    assertEquals(.42f, snapshot.opacity)
    assertEquals(116f, snapshot.fontSize)
  }

  @Test fun emojiScaleIsResolutionIndependent() {
    val layer = PhotoLayer(type = LayerType.TEXT, scale = 1.75f)
    val previewPixels = minOf(360, 640) * .18f * layer.scale
    val exportPixels = minOf(1080, 1920) * .18f * layer.scale
    assertEquals(previewPixels / 360f, exportPixels / 1080f, .0001f)
  }

  @Test fun normalizedPositionRotationOpacityAndScaleMapExactlyAcrossResolutions() {
    val layer = PhotoLayer(type = LayerType.TEXT, x = .25f, y = .75f, scale = 1.8f, rotationDegrees = 33f, opacity = .55f)
    val preview = OverlayGeometry.map(layer, 360, 640)
    val export = OverlayGeometry.map(layer, 1080, 1920)
    assertEquals(.25f, preview.centerX / 360f, .0001f)
    assertEquals(.75f, preview.centerY / 640f, .0001f)
    assertEquals(.25f, export.centerX / 1080f, .0001f)
    assertEquals(.75f, export.centerY / 1920f, .0001f)
    assertEquals(preview.scale, export.scale, 0f)
    assertEquals(preview.rotationDegrees, export.rotationDegrees, 0f)
    assertEquals(preview.opacity, export.opacity, 0f)
  }

  @Test fun squareAndWideOutputsKeepRelativeEmojiSize() {
    val layer = PhotoLayer(type = LayerType.TEXT, scale = 2f)
    val square = OverlayGeometry.map(layer, 1080, 1080)
    val wide = OverlayGeometry.map(layer, 1920, 1080)
    assertEquals(square.shortSide, wide.shortSide, 0f)
  }
}
