package io.github.aryapreetam.cmpimgcompress

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class SearchResult(
  val bytes: ByteArray,
  val finalQuality: Int,
  val iterations: Int,
  val predictedQuality: Int
)

internal object QualityPredictor {
  /**
   * Predicts an optimal initial encoding quality in [5, 95] based on
   * compression ratio (targetBytes / originalBytes).
   */
  fun predictOptimalQuality(
    originalBytes: Int,
    targetBytes: Int
  ): Int {
    if (originalBytes <= 0 || targetBytes <= 0) return 50
    if (targetBytes >= originalBytes) return 95
    val ratio = targetBytes.toDouble() / originalBytes.toDouble()
    val quality = sqrt(ratio) * 100.0 - 5.0
    return quality.roundToInt().coerceIn(5, 95)
  }

  /**
   * Returns a search range of ±15 quality points around the prediction, clamped to [5, 95].
   */
  fun getSearchRange(predictedQuality: Int): IntRange {
    val start = (predictedQuality - 15).coerceAtLeast(5)
    val end = (predictedQuality + 15).coerceAtMost(95)
    return start..end
  }

  /**
   * Tolerance for ByTargetSize: max(35KB, targetBytes / 15).
   */
  fun targetToleranceBytes(targetBytes: Int): Int {
    return max(35 * 1024, targetBytes / 15)
  }

  /**
   * Unifies the iterative search loop across all platform engines.
   * Executes a mathematically calibrated search attempting to land within
   * [targetBytes, targetBytes + tolerance], never below target if feasible.
   */
  suspend fun searchTargetQuality(
    originalBytes: Int,
    targetBytes: Int,
    encode: suspend (quality: Int) -> ByteArray
  ): SearchResult {
    val predicted = predictOptimalQuality(originalBytes, targetBytes)
    val tolerance = targetToleranceBytes(targetBytes)
    val minAcceptable = targetBytes
    val maxAcceptable = targetBytes + tolerance

    val firstBytes = encode(predicted)
    var iterations = 1
    var finalQuality = predicted
    var finalBytes = firstBytes

    if (firstBytes.size !in minAcceptable..maxAcceptable) {
      if (firstBytes.size > maxAcceptable) {
        val sizeRatio = firstBytes.size.toDouble() / targetBytes
        val qualityReduction =
          when {
            sizeRatio > 2.0 -> 0.60
            sizeRatio > 1.5 -> 0.70
            else -> 0.80
          }
        val lowerQuality = (predicted * qualityReduction).toInt().coerceAtLeast(5)
        val secondBytes = encode(lowerQuality)
        iterations++

        if (secondBytes.size >= targetBytes) {
          finalQuality = lowerQuality
          finalBytes = secondBytes
        } else {
          finalQuality = predicted
          finalBytes = firstBytes
        }
      } else {
        val sizeRatio = targetBytes.toDouble() / max(1, firstBytes.size)
        val qualityIncrease =
          when {
            sizeRatio > 2.0 -> 1.80
            sizeRatio > 1.5 -> 1.60
            sizeRatio > 1.2 -> 1.40
            else -> 1.25
          }
        val higherQuality = (predicted * qualityIncrease).toInt().coerceAtMost(95)
        val secondBytes = encode(higherQuality)
        iterations++

        if (secondBytes.size < targetBytes && iterations < 3) {
          val thirdQuality = min(95, higherQuality + 15)
          val thirdBytes = encode(thirdQuality)
          iterations++

          if (thirdBytes.size >= targetBytes) {
            finalQuality = thirdQuality
            finalBytes = thirdBytes
          } else {
            finalQuality = higherQuality
            finalBytes = secondBytes
          }
        } else {
          finalQuality = higherQuality
          finalBytes = secondBytes
        }
      }
    }

    return SearchResult(
      bytes = finalBytes,
      finalQuality = finalQuality,
      iterations = iterations,
      predictedQuality = predicted
    )
  }
}
