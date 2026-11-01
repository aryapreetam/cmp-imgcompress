package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class SearchResult(
  val bytes: ByteArray,
  val finalQuality: Int,
  val iterations: Int,
  val predictedQuality: Int
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SearchResult) return false
    if (!bytes.contentEquals(other.bytes)) return false
    if (finalQuality != other.finalQuality) return false
    if (iterations != other.iterations) return false
    return predictedQuality == other.predictedQuality
  }

  override fun hashCode(): Int {
    var result = bytes.contentHashCode()
    result = 31 * result + finalQuality
    result = 31 * result + iterations
    result = 31 * result + predictedQuality
    return result
  }
}

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
   * Executes a calibrated search aiming to deliver the highest quality image
   * that conforms to [targetBytes], never discarding a compliant sub-budget candidate.
   */
  suspend fun searchTargetQuality(
    originalBytes: Int,
    targetBytes: Int,
    encode: suspend (quality: Int) -> ByteArray
  ): SearchResult {
    currentCoroutineContext().ensureActive()
    val predicted = predictOptimalQuality(originalBytes, targetBytes)
    val tolerance = targetToleranceBytes(targetBytes)
    val lowerBound = max(0, targetBytes - tolerance)

    val firstBytes = encode(predicted)
    var iterations = 1

    if (firstBytes.size in lowerBound..targetBytes) {
      return SearchResult(
        bytes = firstBytes,
        finalQuality = predicted,
        iterations = iterations,
        predictedQuality = predicted
      )
    }

    if (firstBytes.size > targetBytes) {
      currentCoroutineContext().ensureActive()
      val sizeRatio = firstBytes.size.toDouble() / targetBytes.toDouble()
      val qualityReduction = when {
        sizeRatio > 2.0 -> 0.55
        sizeRatio > 1.5 -> 0.70
        else -> 0.85
      }
      val lowerQuality = (predicted * qualityReduction).toInt().coerceIn(5, (predicted - 1).coerceAtLeast(5))
      val secondBytes = encode(lowerQuality)
      iterations++

      if (secondBytes.size <= targetBytes) {
        return SearchResult(
          bytes = secondBytes,
          finalQuality = lowerQuality,
          iterations = iterations,
          predictedQuality = predicted
        )
      }

      if (lowerQuality > 5) {
        currentCoroutineContext().ensureActive()
        val thirdQuality = 5
        val thirdBytes = encode(thirdQuality)
        iterations++

        val bestBytes = if (thirdBytes.size <= targetBytes) thirdBytes else secondBytes
        val bestQuality = if (thirdBytes.size <= targetBytes) thirdQuality else lowerQuality
        return SearchResult(
          bytes = bestBytes,
          finalQuality = bestQuality,
          iterations = iterations,
          predictedQuality = predicted
        )
      }

      return SearchResult(
        bytes = secondBytes,
        finalQuality = lowerQuality,
        iterations = iterations,
        predictedQuality = predicted
      )
    } else {
      currentCoroutineContext().ensureActive()
      val sizeRatio = targetBytes.toDouble() / max(1, firstBytes.size)
      val qualityIncrease = when {
        sizeRatio > 2.0 -> 1.50
        sizeRatio > 1.5 -> 1.30
        else -> 1.15
      }
      val higherQuality = (predicted * qualityIncrease).toInt().coerceIn((predicted + 1).coerceAtMost(95), 95)
      if (higherQuality > predicted) {
        val secondBytes = encode(higherQuality)
        iterations++

        if (secondBytes.size <= targetBytes) {
          return SearchResult(
            bytes = secondBytes,
            finalQuality = higherQuality,
            iterations = iterations,
            predictedQuality = predicted
          )
        }
      }

      return SearchResult(
        bytes = firstBytes,
        finalQuality = predicted,
        iterations = iterations,
        predictedQuality = predicted
      )
    }
  }
}
