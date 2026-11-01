package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QualityPredictorTest {
  @Test
  fun testPredictionBoundaries() {
    val original = 1_000_000
    // Target larger than original
    assertEquals(95, QualityPredictor.predictOptimalQuality(original, 2_000_000))
    // Target equal to original
    assertEquals(95, QualityPredictor.predictOptimalQuality(original, original))
    // Non-positive dimensions guard
    assertEquals(50, QualityPredictor.predictOptimalQuality(0, 1000))
    assertEquals(50, QualityPredictor.predictOptimalQuality(1000, 0))
    assertEquals(50, QualityPredictor.predictOptimalQuality(-1, 1000))
  }

  @Test
  fun testExtremeCompressionRatios() {
    // 100MB -> 50KB: extreme down-compression, should clamp to minimum quality (5)
    val extremeLow = QualityPredictor.predictOptimalQuality(100 * 1024 * 1024, 50 * 1024)
    assertEquals(5, extremeLow, "Extreme compression (100MB -> 50KB) must clamp to minimum quality of 5")

    // 500KB -> 1MB: target exceeds original, should clamp to 95
    val extremeHigh = QualityPredictor.predictOptimalQuality(500 * 1024, 1024 * 1024)
    assertEquals(95, extremeHigh, "Target exceeding original (500KB -> 1MB) must clamp to 95")
  }

  @Test
  fun testMonotonicDecrease() {
    val originalBytes = 10 * 1024 * 1024 // 10MB
    val targetSizes = listOf(
      9 * 1024 * 1024,
      7 * 1024 * 1024,
      5 * 1024 * 1024,
      3 * 1024 * 1024,
      1 * 1024 * 1024,
      500 * 1024,
      100 * 1024
    )

    var previousQuality = 100
    for (target in targetSizes) {
      val quality = QualityPredictor.predictOptimalQuality(originalBytes, target)
      assertTrue(
        quality <= previousQuality,
        "Quality should decrease monotonically as target size decreases: target=$target, q=$quality, prev=$previousQuality"
      )
      assertTrue(quality in 5..95, "Quality must remain within [5, 95]")
      previousQuality = quality
    }
  }

  @Test
  fun testSearchRangeClamping() {
    val rangeAtMin = QualityPredictor.getSearchRange(5)
    assertEquals(5, rangeAtMin.first)
    assertEquals(20, rangeAtMin.last)

    val rangeAtMax = QualityPredictor.getSearchRange(95)
    assertEquals(80, rangeAtMax.first)
    assertEquals(95, rangeAtMax.last)

    val rangeMid = QualityPredictor.getSearchRange(50)
    assertEquals(35, rangeMid.first)
    assertEquals(65, rangeMid.last)
    assertTrue(rangeMid.last - rangeMid.first <= 30)
  }

  @Test
  fun testSearchTargetQualityConvergence() = runTest {
    val originalBytes = 1_000_000
    val targetBytes = 300_000

    // Mock encoder: size directly proportional to quality (quality * 5000 bytes)
    var calls = 0
    val result = QualityPredictor.searchTargetQuality(originalBytes, targetBytes) { quality ->
      calls++
      ByteArray(quality * 5_000)
    }

    assertTrue(calls in 1..3, "Search should converge in 1 to 3 iterations, took $calls")
    assertTrue(result.bytes.isNotEmpty())
    assertTrue(result.iterations == calls)
    assertTrue(result.finalQuality in 5..95)
    assertTrue(result.bytes.size <= targetBytes, "Result must not exceed target bytes")
  }

  @Test
  fun testNeverReturnsOversizedCandidateWhenCompliantFound() = runTest {
    val originalBytes = 1_000_000
    val targetBytes = 100_000

    val result = QualityPredictor.searchTargetQuality(originalBytes, targetBytes) { quality ->
      when {
        quality > 20 -> ByteArray(200_000)
        else -> ByteArray(90_000)
      }
    }

    assertTrue(result.bytes.size <= targetBytes, "Must choose the 90KB compliant candidate over 200KB")
  }
}
