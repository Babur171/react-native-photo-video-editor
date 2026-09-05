package com.photovideoeditor.photo.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorGeometryTest {
  @Test fun `new stickers occupy thirty percent of portrait landscape and square media width`() {
    listOf(1080f to 1920f, 1920f to 1080f, 1080f to 1080f).forEach { (width, height) ->
      val scale = OverlayGeometry.initialStickerScale(width, height)
      val size = OverlayGeometry.stickerSize(minOf(width, height), 2f)
      assertEquals(width * 0.3f, size.first * scale, 0.001f)
      assertEquals(2f, size.first / size.second, 0.001f)
      val previewSize = OverlayGeometry.stickerSize(minOf(width, height) / 4, 2f)
      assertEquals(size.first / 4, previewSize.first, 0.001f)
      assertEquals(size.second / 4, previewSize.second, 0.001f)
    }
  }

  @Test fun `overlay canvas always covers the whole frame so export matches preview at any resolution`() {
    val cap = 2560f
    listOf(1080 to 1920, 1920 to 1080, 1080 to 1080, 720 to 1280, 2160 to 3840, 4320 to 7680).forEach { (width, height) ->
      val plan = OverlayGeometry.canvasPlan(width, height, cap)
      // The invariant the export/preview match depends on: bitmap size x compositor scale == frame.
      assertEquals(width.toFloat(), plan.width * plan.scaleX, 0.001f)
      assertEquals(height.toFloat(), plan.height * plan.scaleY, 0.001f)
      assertTrue("bitmap must stay within the memory cap", maxOf(plan.width, plan.height) <= cap.toInt())
      // Aspect preserved, so a sticker keeps its shape rather than stretching.
      assertEquals(width.toFloat() / height, plan.width.toFloat() / plan.height, 0.01f)
    }
  }

  @Test fun `frames within the cap get a pixel-exact overlay canvas needing no compensation`() {
    val plan = OverlayGeometry.canvasPlan(1080, 1920, 2560f)
    assertEquals(1080, plan.width)
    assertEquals(1920, plan.height)
    assertEquals(1f, plan.scaleX, 0.0001f)
    assertEquals(1f, plan.scaleY, 0.0001f)
  }

  @Test fun `a sticker keeps the same fraction of the frame whichever canvas resolution renders it`() {
    // Preview renders into a downscaled canvas, export into a full-resolution one. Both must place
    // and size the layer identically *relative to the media*, which is what the user compares.
    val layer = PhotoLayer(type = LayerType.STICKER, x = .7f, y = .3f, scale = 2f, overlayAspectRatio = 1f)
    val preview = OverlayGeometry.map(layer, 720, 1280)
    val export = OverlayGeometry.map(layer, 2160, 3840)
    assertEquals(preview.centerX / 720f, export.centerX / 2160f, 0.0001f)
    assertEquals(preview.centerY / 1280f, export.centerY / 3840f, 0.0001f)
    val previewWidth = OverlayGeometry.stickerSize(preview.shortSide, 1f).first * preview.scale
    val exportWidth = OverlayGeometry.stickerSize(export.shortSide, 1f).first * export.scale
    assertEquals(previewWidth / 720f, exportWidth / 2160f, 0.0001f)
    assertEquals(layer.rotationDegrees, export.rotationDegrees, 0.0001f)
  }

  @Test fun `color and text edits preserve all transform timing and ordering data and undo together`() {
    val stack = PhotoLayerStack()
    val text = PhotoLayer(type = LayerType.TEXT, text = "Before", x = .27f, y = .61f,
      scale = 2.5f, rotationDegrees = 318f, opacity = .45f, fontSize = 72f,
      fontFamily = "serif", startMs = 1200, endMs = 4500)
    val sticker = PhotoLayer(type = LayerType.STICKER, overlayAspectRatio = 2f)
    stack.commit { listOf(text, sticker) }
    stack.commit { list -> list.map { if (it.id == text.id) it.copy(text = "After", textColor = 0xFFFF00FF.toInt()) else it } }
    assertEquals(text, stack.layers.first().copy(text = text.text, textColor = text.textColor))
    assertEquals(sticker, stack.layers.last())
    stack.undo()
    assertEquals(listOf(text, sticker), stack.layers)
    stack.redo()
    assertEquals("After", stack.layers.first().text)
  }
}
