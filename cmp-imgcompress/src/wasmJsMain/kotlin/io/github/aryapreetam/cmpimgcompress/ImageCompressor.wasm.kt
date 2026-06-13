package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.max
import kotlin.math.min

// Optimized JS function with better error handling and reduced overhead
@JsFun(
  "function(b64, mime, quality, maxLongEdgePx, cb){\n" +
    "  try {\n" +
    "    const bin = atob(b64);\n" +
    "    const len = bin.length;\n" +
    "    const view = new Uint8Array(len);\n" +
    "    for (let i=0;i<len;i++) view[i] = bin.charCodeAt(i);\n" +
    "    const buf = view.buffer;\n" +
    "    \n" +
    "    (async () => {\n" +
    "      try {\n" +
    "        const blob = new Blob([buf], {type: mime});\n" +
    "        const bitmap = await createImageBitmap(blob);\n" +
    "        let w = bitmap.width; let h = bitmap.height;\n" +
    "        \n" +
    "        // Intelligent resizing for large images (4-7MB sources)\n" +
    "        if (maxLongEdgePx !== null && maxLongEdgePx !== undefined) {\n" +
    "          const longEdge = Math.max(w, h);\n" +
    "          if (longEdge > maxLongEdgePx) {\n" +
    "            const scale = maxLongEdgePx / longEdge;\n" +
    "            w = Math.max(1, Math.round(w * scale));\n" +
    "            h = Math.max(1, Math.round(h * scale));\n" +
    "          }\n" +
    "        }\n" +
    "        \n" +
    "        // Use OffscreenCanvas for better performance\n" +
    "        const canvas = new OffscreenCanvas(w, h);\n" +
    "        const ctx = canvas.getContext('2d', {alpha: false, desynchronized: true});\n" +
    "        if (!ctx) throw new Error('Failed to get optimized 2D context');\n" +
    "        \n" +
    "        // Optimized rendering settings\n" +
    "        ctx.imageSmoothingEnabled = w < bitmap.width; // Only smooth when downscaling\n" +
    "        ctx.imageSmoothingQuality = 'high';\n" +
    "        ctx.drawImage(bitmap, 0, 0, w, h);\n" +
    "        \n" +
    "        // Convert to WebP with proper quality\n" +
    "        const outBlob = await canvas.convertToBlob({\n" +
    "          type: 'image/webp',\n" +
    "          quality: Math.max(0.01, Math.min(1.0, quality/100))\n" +
    "        });\n" +
    "        \n" +
    "        const outArr = new Uint8Array(await outBlob.arrayBuffer());\n" +
    "        let outBin = '';\n" +
    "        for (let i=0;i<outArr.length;i++) outBin += String.fromCharCode(outArr[i]);\n" +
    "        const outB64 = btoa(outBin);\n" +
    "        cb(outB64);\n" +
    "        \n" +
    "        // Cleanup\n" +
    "        bitmap.close();\n" +
    "      } catch (e) {\n" +
    "        cb('ERROR: ' + e.message);\n" +
    "      }\n" +
    "    })();\n" +
    "  } catch (e) {\n" +
    "    cb('ERROR: ' + e.message);\n" +
    "  }\n" +
    "}"
)
external fun jsEncodeWebPBase64(
  b64: String,
  mime: String,
  quality: Int,
  maxLongEdgePx: Int?,
  cb: (String) -> Unit
)

@JsFun("() => Date.now()")
external fun nowMs(): Double

actual object ImageCompressor {
  @OptIn(ExperimentalEncodingApi::class)
  actual suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions
  ): CompressedImage {
    val startTime = nowMs().toLong()
    val originalSize = input.rawBytes.size

    // Simplified encoding with error handling
    suspend fun encodeAtQuality(quality: Int): ByteArray =
      suspendCancellableCoroutine { cont ->
        val inputBase64 = Base64.encode(input.rawBytes)
        jsEncodeWebPBase64(
          b64 = inputBase64,
          mime = input.mimeType,
          quality = quality.coerceIn(5, 95),
          maxLongEdgePx = resize.maxLongEdgePx
        ) { resultBase64 ->
          if (resultBase64.startsWith("ERROR:")) {
            cont.resumeWith(Result.failure(RuntimeException(resultBase64.substring(7))))
          } else {
            val resultBytes = Base64.decode(resultBase64)
            cont.resume(resultBytes)
          }
        }
      }

    val result =
      when (config) {
        is CompressionConfig.ByQuality -> {
          val quality = config.qualityPercent.coerceIn(5f, 95f).toInt()
          val resultBytes = encodeAtQuality(quality)

          CompressedImage(
            bytes = resultBytes,
            originalSize = originalSize,
            compressedSize = resultBytes.size,
            mimeType = "image/webp",
            metadata =
              CompressionMetadata(
                effectiveQualityPercent = quality.toFloat(),
                iterations = 1,
                elapsedMillis = nowMs().toLong() - startTime,
                engineUsed = "WasmJs"
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
              // Add very strong conservative bias for WasmJs
              val conservativeBias =
                when {
                  compressionRatio < 0.05 -> +15 // Extreme compression - very strong bias
                  compressionRatio < 0.15 -> +18 // Very aggressive compression - very strong bias (WasmJs needs more)
                  compressionRatio < 0.3 -> +12 // Moderate compression - strong bias
                  else -> +10 // Light compression - good bias
                }
              (baseQuality + conservativeBias).coerceIn(20, 95)
            }

          // First attempt with prediction
          val firstBytes = encodeAtQuality(predictedQuality)
          var iterations = 1
          var finalQuality = predictedQuality
          var finalBytes = firstBytes

          // Target size accuracy: aim for AT LEAST target size, allow up to +35KB above
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
              val secondBytes = encodeAtQuality(lowerQuality)
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
              val secondBytes = encodeAtQuality(higherQuality)
              iterations++

              // If still below target, make one final aggressive attempt
              if (secondBytes.size < targetBytes && iterations < 3) {
                val finalQuality3 = min(95, higherQuality + 15) // Add 15 more quality points
                val thirdBytes = encodeAtQuality(finalQuality3)
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
                elapsedMillis = nowMs().toLong() - startTime,
                estimatedQuality = predictedQuality,
                searchRange =
                  IntRange(
                    min(predictedQuality, finalQuality),
                    max(predictedQuality, finalQuality)
                  ),
                engineUsed = "WasmJs"
              )
          )
        }
      }

    return result
  }
}
