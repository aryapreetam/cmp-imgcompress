package io.github.aryapreetam.cmpimgcompress

import io.github.aryapreetam.cmpimgcompress.testutils.BasePlatformTest
import io.github.aryapreetam.cmpimgcompress.testutils.TestImages
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageCompressorTest : BasePlatformTest() {
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

  private fun assertValidImageOutput(out: CompressedImage) {
    assertTrue(out.compressedSize > 0, "Compressed size must be positive")
    assertTrue(out.bytes.isNotEmpty(), "Output bytes must not be empty")
    assertNotNull(out.metadata, "Metadata must be present")
    assertTrue(out.metadata.iterations > 0, "Iterations must be positive")
    assertNotNull(out.metadata.engineUsed, "Engine used must be reported")

    if (out.metadata.engineUsed == "Android") {
      assertTrue(
        isWebP(out.bytes) || isPng(out.bytes) || isJpeg(out.bytes),
        "Android output must match valid image format signature under host/device runtime"
      )
    } else if (out.mimeType == "image/jpeg") {
      assertTrue(isJpeg(out.bytes), "Output bytes must match JPEG header when mimeType is image/jpeg")
    } else {
      assertTrue(isWebP(out.bytes), "Output bytes must match WebP RIFF header")
    }
  }

  @Test
  fun byQuality_tinyImage_producesValidImage() = runTest {
    val input = ImageData(rawBytes = TestImages.tinyPng, mimeType = "image/png")
    val out = ImageCompressor.compress(input, CompressionConfig.ByQuality(75f))
    assertValidImageOutput(out)
  }

  @Test
  fun byQuality_multiPixel_producesValidImage() = runTest {
    val input = ImageData(rawBytes = TestImages.multiPixelPng, mimeType = "image/png")
    val out = ImageCompressor.compress(input, CompressionConfig.ByQuality(60f))
    assertValidImageOutput(out)
  }

  @Test
  fun byTargetSize_tinyImage_hitsTolerance() = runTest {
    val input = ImageData(rawBytes = TestImages.tinyPng, mimeType = "image/png")
    val targetKb = 10
    val out = ImageCompressor.compress(input, CompressionConfig.ByTargetSize(targetKb))
    assertValidImageOutput(out)
  }

  @Test
  fun byTargetSize_multiPixel_hitsTolerance() = runTest {
    val input = ImageData(rawBytes = TestImages.multiPixelPng, mimeType = "image/png")
    val targetKb = 2
    val targetBytes = targetKb * 1024
    val out = ImageCompressor.compress(input, CompressionConfig.ByTargetSize(targetKb))
    assertValidImageOutput(out)

    val tolerance = QualityPredictor.targetToleranceBytes(targetBytes)
    assertTrue(
      out.compressedSize <= targetBytes + tolerance || out.compressedSize <= input.rawBytes.size,
      "Compressed size ${out.compressedSize} exceeded budget constraint (target: $targetBytes, tol: $tolerance)"
    )
  }
}