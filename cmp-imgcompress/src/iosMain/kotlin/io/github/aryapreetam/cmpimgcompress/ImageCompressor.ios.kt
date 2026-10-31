@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package io.github.aryapreetam.cmpimgcompress

import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.autoreleasepool
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataCreateMutable
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFNumberDoubleType
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.ImageIO.CGImageDestinationAddImage
import platform.ImageIO.CGImageDestinationCreateWithData
import platform.ImageIO.CGImageDestinationFinalize
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.posix.uint8_tVar
import kotlin.math.max
import kotlin.time.TimeSource

actual object ImageCompressor {
  actual suspend fun compress(
    input: ImageData,
    config: CompressionConfig,
    resize: ResizeOptions
  ): CompressedImage =
    withContext(Dispatchers.Default) {
      val startTime = TimeSource.Monotonic.markNow()
      val originalSize = input.rawBytes.size

      val cfData =
        input.rawBytes.usePinned { pinned ->
          val bytePtr = pinned.addressOf(0).reinterpret<uint8_tVar>()
          CFDataCreate(null, bytePtr, input.rawBytes.size.toLong())
        } ?: throw RuntimeException("Failed to allocate CFData for image input")

        val imageSource =
          try {
            CGImageSourceCreateWithData(cfData, null)
              ?: throw IllegalArgumentException("Unable to decode input image: ${input.mimeType}")
          } finally {
            CFRelease(cfData)
          }

        val originalImage: CGImageRef =
          CGImageSourceCreateImageAtIndex(imageSource, 0.toULong(), null)
            ?: throw IllegalArgumentException("Failed to decode image at index 0")

        var workingImage: CGImageRef = originalImage
        var isNewImage = false

        val width = CGImageGetWidth(originalImage).toLong()
        val height = CGImageGetHeight(originalImage).toLong()
        val maxEdge = (resize.maxLongEdgePx ?: 2560).toLong()
        val longEdge = max(width, height)

        val targetScale =
          if (resize.downscaleOnly && longEdge <= maxEdge) {
            1.0
          } else if (longEdge > maxEdge) {
            maxEdge.toDouble() / longEdge.toDouble()
          } else {
            1.0
          }

        val targetWidth = max(1L, (width * targetScale).toLong())
        val targetHeight = max(1L, (height * targetScale).toLong())

        if (targetWidth != width || targetHeight != height) {
          val colorSpace = CGColorSpaceCreateDeviceRGB()
          val context =
            CGBitmapContextCreate(
              data = null,
              width = targetWidth.toULong(),
              height = targetHeight.toULong(),
              bitsPerComponent = 8.toULong(),
              bytesPerRow = (targetWidth * 4).toULong(),
              space = colorSpace,
              bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value
            )
          CGColorSpaceRelease(colorSpace)

          if (context != null) {
            CGContextDrawImage(
              context,
              CGRectMake(0.0, 0.0, targetWidth.toDouble(), targetHeight.toDouble()),
              originalImage
            )
            val scaled = CGBitmapContextCreateImage(context)
            CGContextRelease(context)
            if (scaled != null) {
              workingImage = scaled
              isNewImage = true
            }
          }
        }

        try {
          when (config) {
            is CompressionConfig.ByQuality -> {
              val quality = config.qualityPercent.coerceIn(0f, 100f).toInt()
              val (resultBytes, mimeType) = encodeAtQuality(workingImage, quality)

              CompressedImage(
                bytes = resultBytes,
                originalSize = originalSize,
                compressedSize = resultBytes.size,
                mimeType = mimeType,
                metadata =
                  CompressionMetadata(
                    effectiveQualityPercent = quality.toFloat(),
                    iterations = 1,
                    elapsedMillis = startTime.elapsedNow().inWholeMilliseconds,
                    engineUsed = "iOS-ImageIO"
                  )
              )
            }

            is CompressionConfig.ByTargetSize -> {
              val targetBytes = max(1, config.targetSizeKb) * 1024
              var outMimeType = "image/webp"

              val searchResult =
                QualityPredictor.searchTargetQuality(originalSize, targetBytes) { q ->
                  val (bytes, mime) = encodeAtQuality(workingImage, q)
                  outMimeType = mime
                  bytes
                }

              CompressedImage(
                bytes = searchResult.bytes,
                originalSize = originalSize,
                compressedSize = searchResult.bytes.size,
                mimeType = outMimeType,
                metadata =
                  CompressionMetadata(
                    effectiveQualityPercent = searchResult.finalQuality.toFloat(),
                    iterations = searchResult.iterations,
                    elapsedMillis = startTime.elapsedNow().inWholeMilliseconds,
                    estimatedQuality = searchResult.predictedQuality,
                    searchRange = QualityPredictor.getSearchRange(searchResult.predictedQuality),
                    engineUsed = "iOS-ImageIO"
                  )
              )
            }
          }
        } finally {
          if (isNewImage) {
            CGImageRelease(workingImage)
          }
          CGImageRelease(originalImage)
        }
    }

  private fun encodeAtQuality(cgImage: CGImageRef, quality: Int): Pair<ByteArray, String> = autoreleasepool {
    val mutableData =
      CFDataCreateMutable(null, 0)
        ?: throw RuntimeException("Failed to allocate CFMutableData")

    val utiWebP = CFStringCreateWithCString(null, "public.webp", kCFStringEncodingUTF8)
    var destination =
      if (utiWebP != null) {
        CGImageDestinationCreateWithData(mutableData, utiWebP, 1.toULong(), null).also {
          CFRelease(utiWebP)
        }
      } else {
        null
      }
    var mimeType = "image/webp"

    if (destination == null) {
      val utiJpeg = CFStringCreateWithCString(null, "public.jpeg", kCFStringEncodingUTF8)
      destination =
        if (utiJpeg != null) {
          CGImageDestinationCreateWithData(mutableData, utiJpeg, 1.toULong(), null).also {
            CFRelease(utiJpeg)
          }
        } else {
          null
        } ?: throw RuntimeException("Failed to create CGImageDestination for WebP and JPEG")
      mimeType = "image/jpeg"
    }

    memScoped {
      val options = CFDictionaryCreateMutable(null, 1, null, null)
      val qVar = alloc<DoubleVar>()
      qVar.value = quality.toDouble() / 100.0
      val qNum = CFNumberCreate(null, kCFNumberDoubleType, qVar.ptr)
      CFDictionarySetValue(options, kCGImageDestinationLossyCompressionQuality, qNum)

      CGImageDestinationAddImage(destination, cgImage, options)

      if (options != null) CFRelease(options)
      if (qNum != null) CFRelease(qNum)
    }

    val finalized = CGImageDestinationFinalize(destination)
    CFRelease(destination)
    if (!finalized) {
      CFRelease(mutableData)
      throw RuntimeException("Failed to finalize CGImageDestination")
    }

    val length = CFDataGetLength(mutableData).toInt()
    val bytePtr = CFDataGetBytePtr(mutableData)
    val resultBytes =
      if (bytePtr != null && length > 0) {
        bytePtr.readBytes(length)
      } else {
        ByteArray(0)
      }
    CFRelease(mutableData)
    Pair(resultBytes, mimeType)
  }
}
