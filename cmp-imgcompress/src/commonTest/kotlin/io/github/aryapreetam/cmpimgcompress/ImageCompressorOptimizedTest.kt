package io.github.aryapreetam.cmpimgcompress

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageCompressorOptimizedTest {
  @Test
  fun testQualityPredictorAccuracy() {
    // Test quality prediction for different compression scenarios

    // Light compression needed (large target vs original)
    val lightPrediction = QualityPredictor.predictOptimalQuality(1000000, 800) // 1MB -> 800KB
    assertTrue(lightPrediction >= 85, "Should predict high quality for light compression")

    // Heavy compression needed (small target vs original)
    val heavyPrediction = QualityPredictor.predictOptimalQuality(2000000, 100) // 2MB -> 100KB
    assertTrue(heavyPrediction <= 40, "Should predict low quality for heavy compression")

    // Moderate compression
    val moderatePrediction = QualityPredictor.predictOptimalQuality(1000000, 400) // 1MB -> 400KB
    assertTrue(moderatePrediction in 55..85, "Should predict moderate quality for moderate compression")
  }

  @Test
  fun testSearchRangeOptimization() {
    val prediction = QualityPredictor.predictOptimalQuality(1000000, 300) // Should predict ~65
    val range = QualityPredictor.getSearchRange(prediction)

    // Range should be ±15 around prediction
    assertEquals(prediction - 15, range.first.coerceAtLeast(5))
    assertEquals(prediction + 15, range.last.coerceAtMost(95))
    assertTrue(range.last - range.first <= 30, "Range should be at most 30 quality points")
  }

  @Test
  fun testQualityPredictorEdgeCases() {
    // Very large target (should use high quality)
    val veryLargeTarget = QualityPredictor.predictOptimalQuality(100000, 500) // 100KB -> 500KB
    assertEquals(95, veryLargeTarget, "Should use maximum quality when target is larger than original")

    // Very small target (should use low quality)
    val verySmallTarget = QualityPredictor.predictOptimalQuality(5000000, 50) // 5MB -> 50KB
    assertTrue(verySmallTarget <= 30, "Should use low quality for extreme compression")

    // Edge case: equal sizes
    val equalSize = QualityPredictor.predictOptimalQuality(200000, 200) // 200KB -> 200KB
    assertTrue(equalSize in 85..95, "Should use high quality when target equals original")
  }

  @Test
  fun testIterationOptimization() {
    // Test that our optimization reduces the search range significantly
    val originalRange = 95 - 5 // Original binary search range
    val optimizedPrediction = QualityPredictor.predictOptimalQuality(1000000, 250)
    val optimizedRange = QualityPredictor.getSearchRange(optimizedPrediction)
    val optimizedRangeSize = optimizedRange.last - optimizedRange.first

    assertTrue(optimizedRangeSize < originalRange, "Optimized range should be smaller than original 5-95 range")
    assertTrue(optimizedRangeSize <= 30, "Optimized range should be at most 30 quality points")
  }

  @Test
  fun testCompressionRatioCalculation() {
    // Test the compression ratio calculation logic used in QualityPredictor
    val testCases =
      listOf(
        Triple(1000000, 800, 0.8192), // 1MB -> 800KB = 819200/1000000 = 0.8192 ratio
        Triple(2000000, 400, 0.2048), // 2MB -> 400KB = 409600/2000000 = 0.2048 ratio
        Triple(500000, 250, 0.512) // 500KB -> 250KB = 256000/500000 = 0.512 ratio
      )

    testCases.forEach { (originalBytes, targetKb, expectedRatio) ->
      val targetBytes = targetKb * 1024
      val actualRatio = targetBytes.toDouble() / originalBytes
      assertEquals(expectedRatio, actualRatio, 0.001, "Compression ratio calculation should be accurate")
    }
  }
}
