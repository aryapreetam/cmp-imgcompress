package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Color
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
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
        image: Image,
        quality: Int
      ): ByteArray {
        val data =
          image.encodeToData(EncodedImageFormat.WEBP, quality.coerceIn(0, 100))
            ?: throw RuntimeException("Skia failed to encode WebP at quality $quality")
        return data.use { it.bytes }
      }

      fun resizeIfNeeded(
        sourceImage: Image,
        options: ResizeOptions
      ): Pair<Image, Boolean> {
        val maxEdge = options.maxLongEdgePx ?: return Pair(sourceImage, false)
        val width = sourceImage.width
        val height = sourceImage.height
        val longEdge = max(width, height)

        if (options.downscaleOnly && longEdge <= maxEdge) {
          return Pair(sourceImage, false)
        }

        val scale = maxEdge.toFloat() / longEdge.toFloat()
        val newWidth = max(1, (width * scale).toInt())
        val newHeight = max(1, (height * scale).toInt())

        val surface = Surface.makeRasterN32Premul(newWidth, newHeight)
        val snapshot =
          surface.use { surf ->
            val canvas = surf.canvas
            canvas.clear(Color.TRANSPARENT)

            Paint().apply { isAntiAlias = true }.use { paint ->
              val srcRect = Rect.makeWH(width.toFloat(), height.toFloat())
              val dstRect = Rect.makeWH(newWidth.toFloat(), newHeight.toFloat())
              canvas.drawImageRect(sourceImage, srcRect, dstRect, paint)
            }

            surf.makeImageSnapshot()
          }

        return Pair(snapshot, true)
      }

      val sourceImage = Image.makeFromEncoded(input.rawBytes)

      var workingImage = sourceImage
      var isNewWorkingImage = false

      try {
        val resized = resizeIfNeeded(sourceImage, resize)
        workingImage = resized.first
        isNewWorkingImage = resized.second

        when (config) {
          is CompressionConfig.ByQuality -> {
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
            val searchResult =
              QualityPredictor.searchTargetQuality(originalSize, targetBytes) { q ->
                encodeWebP(workingImage, q)
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
                  engineUsed = "DesktopSkia"
                )
            )
          }
        }
      } finally {
        if (isNewWorkingImage) {
          workingImage.close()
        }
        sourceImage.close()
      }
    }
}
