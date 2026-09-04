package com.photovideoeditor.photo.render

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayInteractionStateTest {
  @Test
  fun `live transform persists and is committed as one undo operation`() {
    val stack = PhotoLayerStack()
    val text = PhotoLayer(type = LayerType.TEXT, text = "Hello")
    stack.commit { it + text }
    val beforeGesture = stack.layers

    stack.updateLive { layers ->
      layers.map { if (it.id == text.id) it.copy(x = 0.24f, y = 0.73f, scale = 4.5f, rotationDegrees = 37f) else it }
    }
    stack.commitSnapshot(beforeGesture)

    val transformed = stack.layers.single()
    assertEquals(0.24f, transformed.x, 0.0001f)
    assertEquals(0.73f, transformed.y, 0.0001f)
    assertEquals(4.5f, transformed.scale, 0.0001f)
    assertEquals(37f, transformed.rotationDegrees, 0.0001f)

    stack.undo()
    assertEquals(text, stack.layers.single())
  }

  @Test
  fun `transforming one overlay does not affect another or its z order`() {
    val text = PhotoLayer(type = LayerType.TEXT, text = "Text")
    val emoji = PhotoLayer(type = LayerType.TEXT)
    val stack = PhotoLayerStack()
    stack.commit { listOf(text, emoji) }

    stack.updateLive { layers -> layers.map { if (it.id == emoji.id) it.copy(scale = 8f, rotationDegrees = 355f) else it } }

    assertEquals(listOf(text.id, emoji.id), stack.layers.map { it.id })
    assertEquals(text, stack.layers.first())
    assertEquals(8f, stack.layers.last().scale, 0.0001f)
  }

  @Test
  fun `same normalized transform maps proportionally across canvas sizes`() {
    val layer = PhotoLayer(type = LayerType.TEXT, x = 0.3f, y = 0.7f, scale = 2f, rotationDegrees = 25f)
    val portrait = OverlayGeometry.map(layer, 1080, 1920)
    val landscape = OverlayGeometry.map(layer, 1920, 1080)

    assertEquals(0.3f, portrait.centerX / 1080f, 0.0001f)
    assertEquals(0.7f, portrait.centerY / 1920f, 0.0001f)
    assertEquals(0.3f, landscape.centerX / 1920f, 0.0001f)
    assertEquals(0.7f, landscape.centerY / 1080f, 0.0001f)
    assertEquals(portrait.scale, landscape.scale, 0.0001f)
  }
}
