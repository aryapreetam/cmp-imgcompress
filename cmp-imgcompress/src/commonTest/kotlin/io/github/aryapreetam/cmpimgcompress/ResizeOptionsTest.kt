package io.github.aryapreetam.cmpimgcompress

import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResizeOptionsTest {
  @Test
  fun testDefaultParameters() {
    val options = ResizeOptions()
    assertEquals(2560, options.maxLongEdgePx)
    assertTrue(options.downscaleOnly)
    assertTrue(options.maintainAspectRatio)
  }

  @Test
  fun testLandscapeAspectPreservation() {
    val width = 4000
    val height = 2000
    val maxEdge = 2000
    val longEdge = max(width, height)

    val scale = maxEdge.toFloat() / longEdge.toFloat()
    val targetWidth = (width * scale).toInt()
    val targetHeight = (height * scale).toInt()

    assertEquals(2000, targetWidth)
    assertEquals(1000, targetHeight)
    assertEquals(width.toDouble() / height, targetWidth.toDouble() / targetHeight, 0.001)
  }

  @Test
  fun testPortraitAspectPreservation() {
    val width = 1500
    val height = 3000
    val maxEdge = 1000
    val longEdge = max(width, height)

    val scale = maxEdge.toFloat() / longEdge.toFloat()
    val targetWidth = (width * scale).toInt()
    val targetHeight = (height * scale).toInt()

    assertEquals(500, targetWidth)
    assertEquals(1000, targetHeight)
    assertEquals(width.toDouble() / height, targetWidth.toDouble() / targetHeight, 0.001)
  }

  @Test
  fun testDownscaleOnlyBoundary() {
    val width = 800
    val height = 600
    val maxEdge = 1920
    val longEdge = max(width, height)

    val options = ResizeOptions(maxLongEdgePx = maxEdge, downscaleOnly = true)
    val shouldScale = !(options.downscaleOnly && longEdge <= maxEdge)

    assertTrue(!shouldScale, "Images smaller than maxLongEdgePx must not be upscaled when downscaleOnly is true")
  }
}
