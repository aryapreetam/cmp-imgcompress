package io.github.aryapreetam.cmpimgcompress

sealed class CompressionConfig {
  data class ByQuality(val qualityPercent: Float) : CompressionConfig()

  data class ByTargetSize(val targetSizeKb: Int) : CompressionConfig()
}

/** Optional resizing prior to compression to improve ratios and UX across desktop and mobile. */
data class ResizeOptions(
  val maxLongEdgePx: Int? = 2560,
  val downscaleOnly: Boolean = true,
  val maintainAspectRatio: Boolean = true
)

data class ImageData(
  val rawBytes: ByteArray,
  val mimeType: String
)

/**
 * Comprehensive analytics for compression operations to improve accuracy
 */
data class CompressionAnalytics(
  val sessionId: String,
  val platform: String,
  val originalSize: Int,
  val targetSize: Int,
  val actualSize: Int,
  val predictedQuality: Int,
  val actualQuality: Int,
  val compressionRatio: Double,
  val targetAccuracy: Double, // actualSize/targetSize ratio
  val imageComplexity: ImageComplexity,
  val dimensions: Pair<Int, Int>,
  val iterations: Int,
  val elapsedMillis: Long,
  val success: Boolean // within 30% tolerance
)

/**
 * Image complexity metrics for better quality prediction
 */
data class ImageComplexity(
  val pixelDensity: Double, // bytes per pixel
  val aspectRatio: Double,
  val megapixels: Double,
  val compressionDifficulty: CompressionDifficulty
)

enum class CompressionDifficulty {
  VERY_EASY, // Simple images, gradients, low detail
  EASY, // Photos with large uniform areas
  MODERATE, // Typical photos
  HARD, // High detail photos, textures
  VERY_HARD // Complex images with fine detail, noise
}

data class CompressionMetadata(
  val effectiveQualityPercent: Float?,
  val iterations: Int,
  val elapsedMillis: Long,
  val estimatedQuality: Int? = null,
  val searchRange: IntRange? = null,
  val engineUsed: String? = null
)

data class CompressedImage(
  val bytes: ByteArray,
  val originalSize: Int,
  val compressedSize: Int,
  val mimeType: String,
  val metadata: CompressionMetadata? = null
)

expect object ImageCompressor {
  suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions = ResizeOptions(maxLongEdgePx = 2560)
  ): CompressedImage
}
