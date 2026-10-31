package io.github.aryapreetam.cmpimgcompress

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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

      fun encodeWebP(
        bitmap: Bitmap,
        quality: Int
      ): ByteArray {
        val outputStream = ByteArrayOutputStream()
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

      fun getImageDimensions(): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(input.rawBytes, 0, input.rawBytes.size, bounds)
        return Pair(max(1, bounds.outWidth), max(1, bounds.outHeight))
      }

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

      val (width, height) = getImageDimensions()
      val maxEdge = resize.maxLongEdgePx ?: 2560
      val longEdge = max(width, height)

      val targetScale =
        if (resize.downscaleOnly && longEdge <= maxEdge) {
          1.0f
        } else if (longEdge > maxEdge) {
          maxEdge.toFloat() / longEdge
        } else {
          1.0f
        }

      val targetWidth = max(1, (width * targetScale).toInt())
      val targetHeight = max(1, (height * targetScale).toInt())

      val sampleSize = calculateOptimalSampleSize(width, height, targetWidth, targetHeight)
      val decodeOptions =
        BitmapFactory.Options().apply {
          inPreferredConfig = Bitmap.Config.ARGB_8888
          inSampleSize = sampleSize
        }

      val decodedBitmap =
        BitmapFactory.decodeByteArray(input.rawBytes, 0, input.rawBytes.size, decodeOptions)
          ?: throw IllegalArgumentException("Unable to decode input image: ${input.mimeType}")

      var scaledBitmap: Bitmap? = null
      val workingBitmap =
        if (decodedBitmap.width != targetWidth || decodedBitmap.height != targetHeight) {
          scaledBitmap = Bitmap.createScaledBitmap(decodedBitmap, targetWidth, targetHeight, true)
          scaledBitmap
        } else {
          decodedBitmap
        }

      try {
        when (config) {
          is CompressionConfig.ByQuality -> {
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
          }

          is CompressionConfig.ByTargetSize -> {
            val targetBytes = max(1, config.targetSizeKb) * 1024
            val searchResult =
              QualityPredictor.searchTargetQuality(originalSize, targetBytes) { q ->
                encodeWebP(workingBitmap, q)
              }

            CompressedImage(
              bytes = searchResult.bytes,
              originalSize = originalSize,
              compressedSize = searchResult.bytes.size,
              mimeType = "image/webp",
              metadata =
                CompressionMetadata(
                  effectiveQualityPercent = searchResult.finalQuality.toFloat(),
                  iterations = searchResult.iterations,
                  elapsedMillis = System.currentTimeMillis() - startTime,
                  estimatedQuality = searchResult.predictedQuality,
                  searchRange = QualityPredictor.getSearchRange(searchResult.predictedQuality),
                  engineUsed = "Android"
                )
            )
          }
        }
      } finally {
        if (scaledBitmap != null && scaledBitmap != decodedBitmap) {
          scaledBitmap.recycle()
        }
        decodedBitmap.recycle()
      }
    }
}
