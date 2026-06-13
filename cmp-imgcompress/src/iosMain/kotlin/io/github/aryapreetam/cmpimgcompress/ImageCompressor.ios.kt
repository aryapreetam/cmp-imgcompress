package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual object ImageCompressor {
  actual suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions
  ): CompressedImage =
    withContext(Dispatchers.Default) {
      // No-op implementation for iOS.
      // iOS image compression is handled by FileKit via ImageCompressionService.
      // Returns original bytes uncompressed to satisfy API contracts.
      CompressedImage(
        bytes = input.rawBytes,
        originalSize = input.rawBytes.size,
        compressedSize = input.rawBytes.size,
        mimeType = "image/webp",
        metadata =
          CompressionMetadata(
            effectiveQualityPercent =
              when (config) {
                is CompressionConfig.ByQuality -> config.qualityPercent
                is CompressionConfig.ByTargetSize -> 75f
              },
            iterations = 0,
            elapsedMillis = 0,
            engineUsed = "iOS-NoOp"
          )
      )
    }
}
