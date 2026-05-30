package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageCompressorTest {
  // Tiny 1x1 transparent PNG. Decodable by both Skia and Android BitmapFactory.
  private val tinyPng: ByteArray =
    byteArrayOf(
      -119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82,
      0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 31, 21, -60, -119, 0, 0,
      0, 11, 73, 68, 65, 84, 120, -100, 99, 96, 96, 96, 0, 0, 0, 5, 0,
      1, 13, 10, 45, -76, 0, 0, 0, 0, 73, 69, 78, 68, -82, 66, 96, -126
    )

  private fun isWebP(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    return bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
      bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
      bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
      bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
  }

  @Test
  fun byQuality_producesWebP() =
    runTest {
      val input = ImageData(rawBytes = tinyPng, mimeType = "image/png")
      val out = ImageCompressor.compress(input, CompressionConfig.ByQuality(75f))
      assertTrue(isWebP(out.bytes), "Output should be WebP (RIFF/WEBP signature)")
      assertTrue(out.compressedSize > 0)
    }

  @Test
  fun byTargetSize_hitsTolerance() =
    runTest {
      val input = ImageData(rawBytes = tinyPng, mimeType = "image/png")
      val targetKb = 10
      val out = ImageCompressor.compress(input, CompressionConfig.ByTargetSize(targetKb))
      val targetBytes = targetKb * 1024
      val tol = maxOf((targetBytes * 0.05).toInt(), 10 * 1024)
      assertTrue(isWebP(out.bytes))
      assertTrue(out.compressedSize in (targetBytes - tol)..(targetBytes + tol) || out.compressedSize <= targetBytes)
    }
}
