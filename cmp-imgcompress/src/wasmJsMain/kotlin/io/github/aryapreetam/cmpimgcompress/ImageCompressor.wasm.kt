@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.github.aryapreetam.cmpimgcompress

import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

external class Uint8Array : JsAny {
  val length: Int
  operator fun get(index: Int): Byte
  operator fun set(index: Int, value: Byte)
}

@JsFun("(size) => new Uint8Array(size)")
private external fun jsCreateUint8Array(size: Int): Uint8Array

@JsFun(
  """(uint8, mime, maxLongEdgePx, downscaleOnly, cb) => {
    (async () => {
      try {
        const blob = new Blob([uint8], { type: mime });
        let bitmap = await createImageBitmap(blob);
        let w = bitmap.width;
        let h = bitmap.height;
        const maxEdge = (maxLongEdgePx > 0) ? maxLongEdgePx : 2560;
        const longEdge = Math.max(w, h);
        if (!downscaleOnly || longEdge > maxEdge) {
          const scale = maxEdge / longEdge;
          const targetW = Math.max(1, Math.round(w * scale));
          const targetH = Math.max(1, Math.round(h * scale));
          try {
            const resizedBitmap = await createImageBitmap(blob, {
              resizeWidth: targetW,
              resizeHeight: targetH,
              resizeQuality: 'high'
            });
            bitmap.close();
            bitmap = resizedBitmap;
          } catch (_) {
            let canvas;
            if (typeof OffscreenCanvas !== 'undefined') {
              canvas = new OffscreenCanvas(targetW, targetH);
            } else if (typeof document !== 'undefined' && document.createElement) {
              canvas = document.createElement('canvas');
              canvas.width = targetW;
              canvas.height = targetH;
            }
            if (canvas) {
              const ctx = canvas.getContext('2d');
              ctx.imageSmoothingEnabled = true;
              ctx.imageSmoothingQuality = 'high';
              ctx.drawImage(bitmap, 0, 0, targetW, targetH);
              const downscaledBitmap = await createImageBitmap(canvas);
              bitmap.close();
              bitmap = downscaledBitmap;
            }
          }
        }
        cb(bitmap, null);
      } catch (e) {
        cb(null, e.message || 'Decode error');
      }
    })();
  }"""
)
private external fun jsDecodeBitmap(
  uint8: Uint8Array,
  mime: String,
  maxLongEdgePx: Int,
  downscaleOnly: Boolean,
  cb: (JsAny?, String?) -> Unit
)

@JsFun(
  """(bitmap, quality, cb) => {
    (async () => {
      try {
        const w = bitmap.width;
        const h = bitmap.height;
        let canvas, ctx;
        if (typeof OffscreenCanvas !== 'undefined') {
          canvas = new OffscreenCanvas(w, h);
          ctx = canvas.getContext('2d');
        } else if (typeof document !== 'undefined' && document.createElement) {
          canvas = document.createElement('canvas');
          canvas.width = w;
          canvas.height = h;
          ctx = canvas.getContext('2d');
        } else {
          throw new Error('Canvas not supported in current runtime');
        }
        if (!ctx) throw new Error('Failed to get 2D canvas context');
        ctx.drawImage(bitmap, 0, 0, w, h);

        const qualityRatio = Math.max(0.01, Math.min(1.0, quality / 100.0));
        let blob;
        if (canvas.convertToBlob) {
          blob = await canvas.convertToBlob({ type: 'image/webp', quality: qualityRatio });
        } else if (canvas.toBlob) {
          blob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/webp', qualityRatio));
        } else {
          throw new Error('Canvas blob conversion unsupported');
        }
        if (!blob) throw new Error('Failed to create blob from canvas');
        const buffer = await blob.arrayBuffer();
        cb(new Uint8Array(buffer), null);
      } catch (e) {
        cb(null, e.message || 'Render and encode error');
      }
    })();
  }"""
)
private external fun jsRenderAndEncode(
  bitmap: JsAny,
  quality: Int,
  cb: (Uint8Array?, String?) -> Unit
)

@JsFun(
  """(bitmap) => {
    if (bitmap && typeof bitmap.close === 'function') {
      bitmap.close();
    }
  }"""
)
private external fun jsCloseBitmap(bitmap: JsAny)

@JsFun("() => Date.now()")
private external fun nowMs(): Double

private fun ByteArray.toJsUint8Array(): Uint8Array {
  val u8 = jsCreateUint8Array(this.size)
  for (i in indices) {
    u8[i] = this[i]
  }
  return u8
}

private fun Uint8Array.toByteArray(): ByteArray {
  val bytes = ByteArray(this.length)
  for (i in 0 until this.length) {
    bytes[i] = this[i]
  }
  return bytes
}

actual object ImageCompressor {
  private suspend fun decodeBitmap(
    uint8: Uint8Array,
    mime: String,
    maxLongEdgePx: Int,
    downscaleOnly: Boolean
  ): JsAny =
    suspendCancellableCoroutine { cont ->
      jsDecodeBitmap(uint8, mime, maxLongEdgePx, downscaleOnly) { bitmap, error ->
        if (error != null || bitmap == null) {
          cont.resumeWithException(RuntimeException(error ?: "Failed to decode image"))
        } else {
          cont.resume(bitmap)
        }
      }
    }

  private suspend fun renderAndEncode(
    bitmap: JsAny,
    quality: Int
  ): ByteArray =
    suspendCancellableCoroutine { cont ->
      jsRenderAndEncode(bitmap, quality) { u8Array, error ->
        if (error != null || u8Array == null) {
          cont.resumeWithException(RuntimeException(error ?: "Failed to encode image"))
        } else {
          cont.resume(u8Array.toByteArray())
        }
      }
    }

  actual suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions
  ): CompressedImage {
    val startTime = nowMs().toLong()
    val originalSize = input.rawBytes.size
    val uint8 = input.rawBytes.toJsUint8Array()
    val bitmap =
      decodeBitmap(
        uint8 = uint8,
        mime = input.mimeType,
        maxLongEdgePx = resize.maxLongEdgePx ?: 2560,
        downscaleOnly = resize.downscaleOnly
      )

    try {
      when (config) {
        is CompressionConfig.ByQuality -> {
          val quality = config.qualityPercent.coerceIn(0f, 100f).toInt()
          val resultBytes = renderAndEncode(bitmap = bitmap, quality = quality)

          return CompressedImage(
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
          val searchResult =
            QualityPredictor.searchTargetQuality(originalSize, targetBytes) { q ->
              renderAndEncode(bitmap = bitmap, quality = q)
            }

          return CompressedImage(
            bytes = searchResult.bytes,
            originalSize = originalSize,
            compressedSize = searchResult.bytes.size,
            mimeType = "image/webp",
            metadata =
              CompressionMetadata(
                effectiveQualityPercent = searchResult.finalQuality.toFloat(),
                iterations = searchResult.iterations,
                elapsedMillis = nowMs().toLong() - startTime,
                estimatedQuality = searchResult.predictedQuality,
                searchRange = QualityPredictor.getSearchRange(searchResult.predictedQuality),
                engineUsed = "WasmJs"
              )
          )
        }
      }
    } finally {
      jsCloseBitmap(bitmap)
    }
  }
}
