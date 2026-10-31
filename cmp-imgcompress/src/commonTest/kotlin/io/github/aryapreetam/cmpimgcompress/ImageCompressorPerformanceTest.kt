package io.github.aryapreetam.cmpimgcompress

import io.github.aryapreetam.cmpimgcompress.testutils.BasePlatformTest
import io.github.aryapreetam.cmpimgcompress.testutils.TestImages
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageCompressorPerformanceTest : BasePlatformTest() {
  private val testImage: ByteArray by lazy { TestImages.multiPixelPng }

  private var compressionCount = 0

  private fun recordStartTime() {
    compressionCount++
  }

  private fun isWebP(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    return bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
      bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
  }

  private fun isJpeg(bytes: ByteArray): Boolean {
    if (bytes.size < 2) return false
    return (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8
  }

  private fun isPng(bytes: ByteArray): Boolean {
    if (bytes.size < 8) return false
    return (bytes[0].toInt() and 0xFF) == 0x89 && (bytes[1].toInt() and 0xFF) == 0x50 &&
      (bytes[2].toInt() and 0xFF) == 0x4E && (bytes[3].toInt() and 0xFF) == 0x47
  }

  private fun assertValidFormat(result: CompressedImage) {
    if (result.metadata?.engineUsed == "Android") {
      assertTrue(isWebP(result.bytes) || isPng(result.bytes) || isJpeg(result.bytes), "Android output should be a valid image")
    } else if (result.mimeType == "image/jpeg") {
      assertTrue(isJpeg(result.bytes), "Output should be JPEG")
    } else {
      assertTrue(isWebP(result.bytes), "Output should be WebP")
    }
  }

  @Test
  fun benchmarkQualityCompression() =
    runTest {
      val input = ImageData(rawBytes = testImage, mimeType = "image/png")
      val qualityLevels = listOf(95f, 75f, 50f, 25f)

      qualityLevels.forEach { quality ->
        recordStartTime()

        val result =
          ImageCompressor.compress(
            input = input,
            config = CompressionConfig.ByQuality(quality),
            resize = ResizeOptions(maxLongEdgePx = 2560)
          )

        assertValidFormat(result)
        assertTrue(result.compressedSize > 0, "Compressed size should be positive")
        assertNotNull(result.metadata, "Metadata should be present")
      }
    }

  @Test
  fun benchmarkTargetSizeCompression() =
    runTest {
      val input = ImageData(rawBytes = testImage, mimeType = "image/png")
      val targetSizes = listOf(2, 5, 10)

      targetSizes.forEach { targetKb ->
        recordStartTime()

        val result =
          ImageCompressor.compress(
            input = input,
            config = CompressionConfig.ByTargetSize(targetKb),
            resize = ResizeOptions(maxLongEdgePx = 2560)
          )

        assertValidFormat(result)
        assertTrue(result.compressedSize > 0, "Compressed size should be positive")
        assertNotNull(result.metadata, "Metadata should be present")
      }
    }

  @Test
  fun benchmarkOptimizationEffectiveness() =
    runTest {
      val input = ImageData(rawBytes = testImage, mimeType = "image/png")
      val targetKb = 3
      recordStartTime()

      val result =
        ImageCompressor.compress(
          input = input,
          config = CompressionConfig.ByTargetSize(targetKb),
          resize = ResizeOptions(maxLongEdgePx = 2560)
        )

      assertValidFormat(result)
      assertNotNull(result.metadata, "Metadata should be present")

      result.metadata.let { metadata ->
        assertTrue(
          metadata.iterations <= 4,
          "Should use 4 or fewer iterations (used ${metadata.iterations})"
        )

        metadata.searchRange?.let { range ->
          assertTrue(
            (range.endInclusive - range.start) <= 35,
            "Search range should be optimized to ≤35 points (was ${range.endInclusive - range.start})"
          )
        }
      }
    }

  @Test
  fun benchmarkResizeOptions() =
    runTest {
      val input = ImageData(rawBytes = testImage, mimeType = "image/png")
      val resizeOptions =
        listOf(
          ResizeOptions(maxLongEdgePx = 4000),
          ResizeOptions(maxLongEdgePx = 2560),
          ResizeOptions(maxLongEdgePx = 1920),
          ResizeOptions(maxLongEdgePx = 1024)
        )

      resizeOptions.forEach { resize ->
        recordStartTime()

        val result =
          ImageCompressor.compress(
            input = input,
            config = CompressionConfig.ByQuality(75f),
            resize = resize
          )

        assertValidFormat(result)
        assertTrue(result.compressedSize > 0, "Compressed size should be positive")
      }
    }
}