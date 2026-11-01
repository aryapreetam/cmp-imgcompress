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
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ImageData) return false
    if (!rawBytes.contentEquals(other.rawBytes)) return false
    return mimeType == other.mimeType
  }

  override fun hashCode(): Int {
    var result = rawBytes.contentHashCode()
    result = 31 * result + mimeType.hashCode()
    return result
  }
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
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is CompressedImage) return false
    if (!bytes.contentEquals(other.bytes)) return false
    if (originalSize != other.originalSize) return false
    if (compressedSize != other.compressedSize) return false
    if (mimeType != other.mimeType) return false
    return metadata == other.metadata
  }

  override fun hashCode(): Int {
    var result = bytes.contentHashCode()
    result = 31 * result + originalSize
    result = 31 * result + compressedSize
    result = 31 * result + mimeType.hashCode()
    result = 31 * result + (metadata?.hashCode() ?: 0)
    return result
  }
}

expect object ImageCompressor {
  suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions = ResizeOptions(maxLongEdgePx = 2560)
  ): CompressedImage
}
