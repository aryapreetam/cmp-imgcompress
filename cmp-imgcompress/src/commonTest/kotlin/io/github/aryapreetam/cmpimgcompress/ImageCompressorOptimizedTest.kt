package io.github.aryapreetam.cmpimgcompress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageCompressorOptimizedTest {
  @Test
  fun testQualityPredictorAccuracy() {
    // Light compression needed (large target vs original)
    val lightPrediction = QualityPredictor.predictOptimalQuality(1_000_000, 800 * 1024) // 1MB -> 800KB
    assertTrue(lightPrediction >= 85, "Should predict high quality for light compression, got $lightPrediction")

    // Heavy compression needed (small target vs original)
    val heavyPrediction = QualityPredictor.predictOptimalQuality(2_000_000, 100 * 1024) // 2MB -> 100KB
    assertTrue(heavyPrediction <= 40, "Should predict low quality for heavy compression, got $heavyPrediction")

    // Moderate compression
    val moderatePrediction = QualityPredictor.predictOptimalQuality(1_000_000, 400 * 1024) // 1MB -> 400KB
    assertTrue(moderatePrediction in 55..85, "Should predict moderate quality for moderate compression, got $moderatePrediction")
  }

  @Test
  fun testSearchRangeOptimization() {
    val prediction = QualityPredictor.predictOptimalQuality(1_000_000, 300 * 1024) // Should predict ~65
    val range = QualityPredictor.getSearchRange(prediction)

    // Range should be ±15 around prediction
    assertEquals((prediction - 15).coerceAtLeast(5), range.first)
    assertEquals((prediction + 15).coerceAtMost(95), range.last)
    assertTrue(range.last - range.first <= 30, "Range should be at most 30 quality points")
  }

  @Test
  fun testQualityPredictorEdgeCases() {
    // Very large target (should use high quality)
    val veryLargeTarget = QualityPredictor.predictOptimalQuality(100_000, 500 * 1024) // 100KB -> 500KB
    assertEquals(95, veryLargeTarget, "Should use maximum quality when target is larger than original")

    // Very small target (should use low quality)
    val verySmallTarget = QualityPredictor.predictOptimalQuality(5_000_000, 50 * 1024) // 5MB -> 50KB
    assertTrue(verySmallTarget <= 30, "Should use low quality for extreme compression, got $verySmallTarget")

    // Edge case: equal sizes
    val equalSize = QualityPredictor.predictOptimalQuality(200_000, 200 * 1024) // 200KB -> 200KB
    assertTrue(equalSize in 85..95, "Should use high quality when target equals original, got $equalSize")
  }

  @Test
  fun testIterationOptimization() {
    val originalRange = 95 - 5
    val optimizedPrediction = QualityPredictor.predictOptimalQuality(1_000_000, 250 * 1024)
    val optimizedRange = QualityPredictor.getSearchRange(optimizedPrediction)
    val optimizedRangeSize = optimizedRange.last - optimizedRange.first

    assertTrue(optimizedRangeSize < originalRange, "Optimized range should be smaller than original 5-95 range")
    assertTrue(optimizedRangeSize <= 30, "Optimized range should be at most 30 quality points")
  }

  @Test
  fun testCompressionRatioCalculation() {
    val testCases =
      listOf(
        Triple(1000000, 800, 0.8192),
        Triple(2000000, 400, 0.2048),
        Triple(500000, 250, 0.512)
      )

    testCases.forEach { (originalBytes, targetKb, expectedRatio) ->
      val targetBytes = targetKb * 1024
      val actualRatio = targetBytes.toDouble() / originalBytes
      assertEquals(expectedRatio, actualRatio, 0.001, "Compression ratio calculation should be accurate")
    }
  }
}
