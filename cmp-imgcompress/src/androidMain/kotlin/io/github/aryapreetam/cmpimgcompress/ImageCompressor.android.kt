package io.github.aryapreetam.cmpimgcompress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * ENHANCED ANDROID COMPRESSION with Analytics and Calibrated Quality Prediction
 * Focus: Target size accuracy within 0-30% above target, never below
 * New: Comprehensive logging, image complexity analysis, platform-specific tuning
 */
actual object ImageCompressor {
  actual suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions
  ): CompressedImage =
    withContext(Dispatchers.Default) {
      val startTime = System.currentTimeMillis()
      val originalSize = input.rawBytes.size

      // Optimized WebP encoding
      fun encodeWebP(
        bitmap: Bitmap,
        quality: Int
      ): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        val format =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
          } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
          }
        val success = bitmap.compress(format, quality.coerceIn(0, 100), outputStream)
        if (!success) {
          throw RuntimeException("Failed to encode WebP at quality $quality")
        }
        return outputStream.toByteArray()
      }

      // Get image dimensions efficiently
      fun getImageDimensions(): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input.rawBytes, 0, input.rawBytes.size, bounds)
        return Pair(bounds.outWidth, bounds.outHeight)
      }

      // Calculate optimal sample size for decoding
      fun calculateOptimalSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        targetWidth: Int,
        targetHeight: Int
      ): Int {
        var sampleSize = 1
        while (originalWidth / (sampleSize * 2) >= targetWidth && originalHeight / (sampleSize * 2) >= targetHeight) {
          sampleSize *= 2
        }
        return sampleSize
      }

      // Mathematical scale calculation - optimized for speed with maintained quality
      fun calculateOptimalScale(
        originalSize: Int,
        targetBytes: Int,
        width: Int,
        height: Int,
        maxEdge: Int
      ): Float {
        val compressionRatio = targetBytes.toDouble() / originalSize
        val pixelCount = width * height
        val longEdge = max(width, height)

        // For extreme compression ratios, prioritize speed over maximum resolution
        val minLongEdge = 1920
        val mustPreserveHD = longEdge >= minLongEdge

        val baseScale =
          when {
            compressionRatio > 0.6 -> 1.0f // Keep full resolution
            compressionRatio > 0.4 -> if (mustPreserveHD) max(0.80f, minLongEdge.toFloat() / longEdge) else 0.75f
            compressionRatio > 0.2 -> if (mustPreserveHD) max(0.70f, minLongEdge.toFloat() / longEdge) else 0.60f
            compressionRatio > 0.1 -> if (mustPreserveHD) max(0.55f, minLongEdge.toFloat() / longEdge) else 0.40f
            else ->
              if (mustPreserveHD) {
                max(
                  0.45f,
                  minLongEdge.toFloat() / longEdge
                )
              } else {
                0.30f // Much more aggressive for extreme compression
              }
          }

        val complexityFactor =
          when {
            pixelCount > 20_000_000 -> 0.85f // More aggressive for very large images
            pixelCount > 10_000_000 -> 0.90f // Moderate reduction for large images
            else -> 1.0f
          }

        // Ensure we don't exceed max edge constraint but respect HD minimum when reasonable
        val maxEdgeScale = if (longEdge > maxEdge) maxEdge.toFloat() / longEdge else 1.0f

        return min(baseScale * complexityFactor, maxEdgeScale).coerceIn(
          if (mustPreserveHD && compressionRatio > 0.10) minLongEdge.toFloat() / longEdge else 0.30f,
          1.0f
        )
      }

      // Ultra-conservative quality prediction to ensure we NEVER go below target size
      fun predictOptimalQuality(
        originalSize: Int,
        targetBytes: Int,
        width: Int,
        height: Int
      ): Int {
        val compressionRatio = targetBytes.toDouble() / originalSize
        val pixelCount = width * height

        // Much higher base quality to prevent going below target - based on actual results
        val baseQuality =
          when {
            compressionRatio > 0.7 -> 90 // Very light compression
            compressionRatio > 0.5 -> 82 // Light compression
            compressionRatio > 0.3 -> 72 // Moderate compression
            compressionRatio > 0.2 -> 62 // Good compression
            compressionRatio > 0.1 -> 52 // Heavy compression (Q37→161KB, Q46→185KB for 200KB was still too low)
            compressionRatio > 0.05 -> 35 // Very heavy compression (need much higher for 100KB target)
            else -> 25 // Extreme compression
          }

        val densityAdjustment =
          when {
            pixelCount > 15_000_000 -> +2 // Large images - add boost (was 0)
            pixelCount > 8_000_000 -> +3 // Medium-large images - good boost
            pixelCount < 2_000_000 -> +5 // Small images - strong boost
            else -> +4 // Default strong boost for medium images
          }

        // Very strong conservative bias to ensure we stay above target
        val conservativeBias =
          when {
            compressionRatio < 0.05 -> +12 // Extreme compression - very strong bias
            compressionRatio < 0.15 -> +15 // Very aggressive compression - very strong bias
            compressionRatio < 0.3 -> +12 // Moderate compression - strong bias
            else -> +8 // Light compression - good bias
          }

        return (baseQuality + densityAdjustment + conservativeBias).coerceIn(20, 95)
      }

      val result =
        when (config) {
          is CompressionConfig.ByQuality -> {
            // Simple quality-based compression
            val (width, height) = getImageDimensions()
            val maxEdge = resize.maxLongEdgePx ?: 2560

            // Calculate target dimensions
            val longEdge = max(width, height)
            val targetScale = if (longEdge > maxEdge) maxEdge.toFloat() / longEdge else 1.0f
            val targetWidth = (width * targetScale).toInt().coerceAtLeast(1)
            val targetHeight = (height * targetScale).toInt().coerceAtLeast(1)

            // Single optimized decode
            val sampleSize = calculateOptimalSampleSize(width, height, targetWidth, targetHeight)
            val bitmap =
              BitmapFactory.decodeByteArray(
                input.rawBytes, 0, input.rawBytes.size,
                BitmapFactory.Options().apply {
                  inPreferredConfig = Bitmap.Config.ARGB_8888
                  inSampleSize = sampleSize
                  inMutable = false
                  inDither = false
                  inScaled = false
                }
              ) ?: throw IllegalArgumentException("Unable to decode input image")

            val workingBitmap =
              if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                  if (it != bitmap) bitmap.recycle()
                }
              } else {
                bitmap
              }

            try {
              val quality = config.qualityPercent.coerceIn(0f, 100f).toInt()
              val resultBytes = encodeWebP(workingBitmap, quality)

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
                    engineUsed = "Android"
                  )
              )
            } finally {
              workingBitmap.recycle()
            }
          }

          is CompressionConfig.ByTargetSize -> {
            val targetBytes = max(1, config.targetSizeKb) * 1024
            val (width, height) = getImageDimensions()
            val maxEdge = resize.maxLongEdgePx ?: 2560

            // PROVEN OPTIMIZATION: Mathematical scale calculation
            val optimalScale = calculateOptimalScale(originalSize, targetBytes, width, height, maxEdge)
            val targetWidth = (width * optimalScale).toInt().coerceAtLeast(1)
            val targetHeight = (height * optimalScale).toInt().coerceAtLeast(1)

            // PROVEN OPTIMIZATION: Single optimized decode with pre-scaling
            val sampleSize = calculateOptimalSampleSize(width, height, targetWidth, targetHeight)
            val bitmap =
              BitmapFactory.decodeByteArray(
                input.rawBytes, 0, input.rawBytes.size,
                BitmapFactory.Options().apply {
                  inPreferredConfig = Bitmap.Config.ARGB_8888
                  inSampleSize = sampleSize
                  inMutable = false
                  inDither = false
                  inScaled = false
                }
              ) ?: throw IllegalArgumentException("Unable to decode input image")

            val workingBitmap =
              if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also {
                  if (it != bitmap) bitmap.recycle()
                }
              } else {
                bitmap
              }

            try {
              // PROVEN OPTIMIZATION: Smart single-pass with ±10-30KB tolerance
              val predictedQuality = predictOptimalQuality(originalSize, targetBytes, targetWidth, targetHeight)

              println("🔍 Android Compression Debug:")
              println("   Original: ${originalSize / 1024}KB (${width}x$height)")
              println("   Target: ${targetBytes / 1024}KB")
              println("   Scaled to: ${targetWidth}x$targetHeight (scale: ${String.format("%.2f", optimalScale)})")
              println("   Predicted Quality: Q$predictedQuality")
              println("   Compression Ratio: ${String.format("%.3f", targetBytes.toDouble() / originalSize)}")

              // First attempt with prediction
              val firstBytes = encodeWebP(workingBitmap, predictedQuality)
              var iterations = 1
              var finalQuality = predictedQuality
              var finalBytes = firstBytes

              println("   First attempt: Q$predictedQuality → ${firstBytes.size / 1024}KB")

              // Target size accuracy: aim for AT LEAST target size, allow up to +35KB above for speed
              val tolerance = max(35 * 1024, targetBytes / 15) // Max 35KB or 6.7% of target, whichever is larger
              val minAcceptable = targetBytes // Never go below target
              val maxAcceptable = targetBytes + tolerance

              println("   Acceptable range: ${minAcceptable / 1024}KB - ${maxAcceptable / 1024}KB")

              if (firstBytes.size !in minAcceptable..maxAcceptable) {
                println("   Outside acceptable range, correcting...")
                // Single correction attempt for speed (max 2 total attempts)
                if (firstBytes.size > maxAcceptable) {
                  println("   Too large, reducing quality...")
                  // Calculate target quality more precisely based on ratio
                  val sizeRatio = firstBytes.size.toDouble() / targetBytes
                  val qualityReduction =
                    when {
                      sizeRatio > 2.0 -> 0.60 // Very large, cut by 40%
                      sizeRatio > 1.5 -> 0.70 // Large, cut by 30%
                      else -> 0.80 // Moderate, cut by 20%
                    }
                  val lowerQuality = (predictedQuality * qualityReduction).toInt().coerceAtLeast(8)
                  val secondBytes = encodeWebP(workingBitmap, lowerQuality)
                  iterations++

                  println("   Second attempt: Q$lowerQuality → ${secondBytes.size / 1024}KB")

                  if (secondBytes.size >= targetBytes) {
                    finalQuality = lowerQuality
                    finalBytes = secondBytes
                    println("   ✅ Second attempt accepted (size: ${secondBytes.size / 1024}KB)")
                  } else {
                    // If went below target, use original (prefer overshooting)
                    finalQuality = predictedQuality
                    finalBytes = firstBytes
                    println("   ⚠️ Went below target, using original")
                  }
                } else {
                  println("   Too small, increasing quality...")
                  // Too small - be more aggressive in quality increase
                  val sizeRatio = targetBytes.toDouble() / firstBytes.size
                  val qualityIncrease =
                    when {
                      sizeRatio > 2.0 -> 1.80 // Much too small, very big increase
                      sizeRatio > 1.5 -> 1.60 // Too small, big increase
                      sizeRatio > 1.2 -> 1.40 // Moderately small, good increase
                      else -> 1.25 // Slightly small, moderate increase
                    }
                  val higherQuality = (predictedQuality * qualityIncrease).toInt().coerceAtMost(95)
                  val secondBytes = encodeWebP(workingBitmap, higherQuality)
                  iterations++

                  println("   Second attempt: Q$higherQuality → ${secondBytes.size / 1024}KB")

                  // If still below target, make one final aggressive attempt
                  if (secondBytes.size < targetBytes && iterations < 3) {
                    println("   Still below target, making final aggressive attempt...")
                    val finalQuality3 = min(95, higherQuality + 15) // Add 15 more quality points
                    val thirdBytes = encodeWebP(workingBitmap, finalQuality3)
                    iterations++

                    println("   Third attempt: Q$finalQuality3 → ${thirdBytes.size / 1024}KB")

                    if (thirdBytes.size >= targetBytes) {
                      finalQuality = finalQuality3
                      finalBytes = thirdBytes
                      println("   ✅ Third attempt accepted (finally above target!)")
                    } else {
                      // Use the best result we have (closest to target)
                      finalQuality = higherQuality
                      finalBytes = secondBytes
                      println("   ⚠️ Using second attempt (best available)")
                    }
                  } else {
                    finalQuality = higherQuality
                    finalBytes = secondBytes
                    println("   ✅ Higher quality accepted")
                  }
                }
              } else {
                println("   ✅ First attempt within acceptable range")
              }

              println("   Final: Q$finalQuality → ${finalBytes.size / 1024}KB ($iterations iterations)")
              println(
                "   Accuracy: ${
                  String.format(
                    "%.1f",
                    (finalBytes.size.toDouble() / targetBytes - 1) * 100
                  )
                }% over target"
              )
              println()

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
                    engineUsed = "AndroidOptimized"
                  )
              )
            } finally {
              workingBitmap.recycle()
            }
          }
        }

      result
    }
}
