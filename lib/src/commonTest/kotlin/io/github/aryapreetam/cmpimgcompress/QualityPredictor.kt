package io.github.aryapreetam.cmpimgcompress

import kotlin.math.sqrt

/**
 * Test-only predictor used to unblock unit tests for img-compress-cmp.
 * Maps target/original size ratio to a JPEG/WebP-like quality in [5, 95].
 */
object QualityPredictor {
  /**
   * @param originalBytes Original image size in bytes
   * @param targetKb Target size in kilobytes
   * @return predicted quality (5..95)
   */
  fun predictOptimalQuality(
    originalBytes: Int,
    targetKb: Int
  ): Int {
    val targetBytes = targetKb * 1024
    if (targetBytes >= originalBytes) return 95
    val ratio = targetBytes.toDouble() / originalBytes.toDouble()
    val quality = sqrt(ratio) * 100.0 - 5.0
    return quality.toInt().coerceIn(5, 95)
  }

  /**
   * Returns a search range of ±15 quality points around the prediction, clamped to [5, 95].
   */
  fun getSearchRange(prediction: Int): IntRange {
    val start = (prediction - 15).coerceAtLeast(5)
    val end = (prediction + 15).coerceAtMost(95)
    return start..end
  }
}
