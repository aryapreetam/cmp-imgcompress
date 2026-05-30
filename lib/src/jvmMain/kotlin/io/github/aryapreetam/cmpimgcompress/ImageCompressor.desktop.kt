package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.*
import kotlin.math.max

actual object ImageCompressor {
  actual suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions
  ): CompressedImage =
    withContext(Dispatchers.Default) {
      val startTime = System.currentTimeMillis()
      val originalSize = input.rawBytes.size

      // Optimized image loading - happens ONCE
      fun loadImageOnce(bytes: ByteArray): Image {
        return Image.makeFromEncoded(bytes)
          ?: throw IllegalArgumentException("Unable to decode input image: ${input.mimeType}")
      }

      // Get actual image dimensions for analytics
      fun getImageDimensions(image: Image): Pair<Int, Int> {
        return Pair(image.width, image.height)
      }

      // Fast resizing with performance-optimized Skia settings
      fun resizeIfNeeded(sourceImage: Image): Image {
        val maxEdge = resize.maxLongEdgePx ?: return sourceImage
        val width = sourceImage.width
        val height = sourceImage.height
        val longEdge = max(width, height)

        if (resize.downscaleOnly && longEdge <= maxEdge) {
          return sourceImage
        }

        val scale = maxEdge.toFloat() / longEdge.toFloat()
        val newWidth = max(1, (width * scale).toInt())
        val newHeight = max(1, (height * scale).toInt())

        val surface = Surface.makeRasterN32Premul(newWidth, newHeight)
        val canvas = surface.canvas
        canvas.clear(Color.TRANSPARENT)

        // OPTIMIZED: Performance-focused paint settings
        val paint =
          Paint().apply {
            isAntiAlias = false // Faster for large downscales
          }

        val srcRect = Rect.makeWH(width.toFloat(), height.toFloat())
        val dstRect = Rect.makeWH(newWidth.toFloat(), newHeight.toFloat())
        canvas.drawImageRect(sourceImage, srcRect, dstRect, paint)

        return surface.makeImageSnapshot()
      }

      // Optimized WebP encoding with error handling
      fun encodeWebP(
        image: Image,
        quality: Int
      ): ByteArray {
        val data =
          image.encodeToData(EncodedImageFormat.WEBP, quality.coerceIn(0, 100))
            ?: throw RuntimeException("Skia failed to encode WebP at quality $quality")
        return data.bytes
      }

      // Load image only once
      val sourceImage = loadImageOnce(input.rawBytes)
      val originalDimensions = getImageDimensions(sourceImage)
      val workingImage = resizeIfNeeded(sourceImage)
      val finalDimensions = getImageDimensions(workingImage)

      val result =
        when (config) {
          is CompressionConfig.ByQuality -> {
            // Simple quality-based compression - single encoding
            val quality = config.qualityPercent.coerceIn(0f, 100f).toInt()
            val resultBytes = encodeWebP(workingImage, quality)

            CompressedImage(
              bytes = resultBytes,
              originalSize = originalSize,
              compressedSize = resultBytes.size,
              mimeType = "image/webp",
              metadata =
                CompressionMetadata(
                  effectiveQualityPercent = quality.toFloat(),
                  iterations = 1,
                  elapsedMillis = System.currentTimeMillis() - startTime,
                  engineUsed = "DesktopSkia"
                )
            )
          }

          is CompressionConfig.ByTargetSize -> {
            val targetBytes = max(1, config.targetSizeKb) * 1024

            // Ultra-conservative quality prediction to ensure we NEVER go below target size
            val compressionRatio = targetBytes.toDouble() / originalSize
            val predictedQuality =
              when {
                compressionRatio > 0.7 -> 90 // Very light compression
                compressionRatio > 0.5 -> 82 // Light compression
                compressionRatio > 0.3 -> 72 // Moderate compression
                compressionRatio > 0.2 -> 62 // Good compression
                compressionRatio > 0.1 -> 52 // Heavy compression
                compressionRatio > 0.05 -> 35 // Very heavy compression
                else -> 25 // Extreme compression
              }.let { baseQuality ->
                // Add very strong conservative bias for Desktop
                val conservativeBias =
                  when {
                    compressionRatio < 0.05 -> +12 // Extreme compression - very strong bias
                    compressionRatio < 0.15 -> +15 // Very aggressive compression - very strong bias
                    compressionRatio < 0.3 -> +12 // Moderate compression - strong bias
                    else -> +8 // Light compression - good bias
                  }
                (baseQuality + conservativeBias).coerceIn(20, 95)
              }

            // First attempt
            val firstBytes = encodeWebP(workingImage, predictedQuality)
            var iterations = 1
            var finalQuality = predictedQuality
            var finalBytes = firstBytes

            // Target size accuracy with wider tolerance
            val tolerance = max(35 * 1024, targetBytes / 15)
            val minAcceptable = targetBytes // Never go below target
            val maxAcceptable = targetBytes + tolerance

            // Triple-safety correction logic like Android
            if (firstBytes.size !in minAcceptable..maxAcceptable) {
              if (firstBytes.size > maxAcceptable) {
                // Too large - reduce quality carefully
                val sizeRatio = firstBytes.size.toDouble() / targetBytes
                val qualityReduction =
                  when {
                    sizeRatio > 2.0 -> 0.60 // Very large, cut by 40%
                    sizeRatio > 1.5 -> 0.70 // Large, cut by 30%
                    else -> 0.80 // Moderate, cut by 20%
                  }
                val lowerQuality = (predictedQuality * qualityReduction).toInt().coerceAtLeast(8)
                val secondBytes = encodeWebP(workingImage, lowerQuality)
                iterations++

                if (secondBytes.size >= targetBytes) {
                  finalQuality = lowerQuality
                  finalBytes = secondBytes
                } else {
                  // If went below target, use original (prefer overshooting)
                  finalQuality = predictedQuality
                  finalBytes = firstBytes
                }
              } else {
                // Too small - be very aggressive in quality increase
                val sizeRatio = targetBytes.toDouble() / firstBytes.size
                val qualityIncrease =
                  when {
                    sizeRatio > 2.0 -> 1.80 // Much too small, very big increase
                    sizeRatio > 1.5 -> 1.60 // Too small, big increase
                    sizeRatio > 1.2 -> 1.40 // Moderately small, good increase
                    else -> 1.25 // Slightly small, moderate increase
                  }
                val higherQuality = (predictedQuality * qualityIncrease).toInt().coerceAtMost(95)
                val secondBytes = encodeWebP(workingImage, higherQuality)
                iterations++

                // If still below target, make one final aggressive attempt
                if (secondBytes.size < targetBytes && iterations < 3) {
                  val finalQuality3 = kotlin.math.min(95, higherQuality + 15) // Add 15 more quality points
                  val thirdBytes = encodeWebP(workingImage, finalQuality3)
                  iterations++

                  if (thirdBytes.size >= targetBytes) {
                    finalQuality = finalQuality3
                    finalBytes = thirdBytes
                  } else {
                    // Use the best result we have (closest to target)
                    finalQuality = higherQuality
                    finalBytes = secondBytes
                  }
                } else {
                  finalQuality = higherQuality
                  finalBytes = secondBytes
                }
              }
            }
            CompressedImage(
              bytes = finalBytes,
              originalSize = originalSize,
              compressedSize = finalBytes.size,
              mimeType = "image/webp",
              metadata =
                CompressionMetadata(
                  effectiveQualityPercent = finalQuality.toFloat(),
                  iterations = iterations,
                  elapsedMillis = System.currentTimeMillis() - startTime,
                  estimatedQuality = predictedQuality,
                  searchRange =
                    IntRange(
                      kotlin.math.min(predictedQuality, finalQuality),
                      kotlin.math.max(predictedQuality, finalQuality)
                    ),
                  engineUsed = "Desktop"
                )
            )
          }
        }

      result
    }
}
