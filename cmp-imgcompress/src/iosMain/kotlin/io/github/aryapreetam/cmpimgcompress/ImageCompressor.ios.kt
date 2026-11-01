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
import platform.CoreFoundation.kCFBooleanTrue
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
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.kCGImageDestinationLossyCompressionQuality
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
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

        val maxEdge = (resize.maxLongEdgePx ?: 2560).toLong()

        val originalImage: CGImageRef =
          try {
            memScoped {
              val options = CFDictionaryCreateMutable(null, 3, null, null)
              CFDictionarySetValue(options, kCGImageSourceCreateThumbnailFromImageAlways, kCFBooleanTrue)
              CFDictionarySetValue(options, kCGImageSourceCreateThumbnailWithTransform, kCFBooleanTrue)
              val maxEdgeVar = alloc<DoubleVar>()
              maxEdgeVar.value = maxEdge.toDouble()
              val maxEdgeNum = CFNumberCreate(null, kCFNumberDoubleType, maxEdgeVar.ptr)
              CFDictionarySetValue(options, kCGImageSourceThumbnailMaxPixelSize, maxEdgeNum)

              val thumb = CGImageSourceCreateThumbnailAtIndex(imageSource, 0.toULong(), options)
              if (options != null) CFRelease(options)
              if (maxEdgeNum != null) CFRelease(maxEdgeNum)

              thumb ?: CGImageSourceCreateImageAtIndex(imageSource, 0.toULong(), null)
              ?: throw IllegalArgumentException("Failed to decode image at index 0")
            }
          } finally {
            CFRelease(imageSource)
          }

        var workingImage: CGImageRef = originalImage
        var isNewImage = false

        val width = CGImageGetWidth(originalImage).toLong()
        val height = CGImageGetHeight(originalImage).toLong()
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
              val (resultBytes, mimeType) = encodeAtQuality(workingImage, quality, targetWidth, targetHeight)

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
              var outMimeType = "image/jpeg"

              val searchResult =
                QualityPredictor.searchTargetQuality(originalSize, targetBytes) { q ->
                  val (bytes, mime) = encodeAtQuality(workingImage, q, targetWidth, targetHeight)
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

  private fun compositeOverWhite(
    image: CGImageRef,
    width: Long,
    height: Long
  ): Pair<CGImageRef, Boolean> {
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    val context =
      CGBitmapContextCreate(
        data = null,
        width = width.toULong(),
        height = height.toULong(),
        bitsPerComponent = 8.toULong(),
        bytesPerRow = (width * 4).toULong(),
        space = colorSpace,
        bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value
      )
    CGColorSpaceRelease(colorSpace)
    if (context != null) {
      platform.CoreGraphics.CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
      platform.CoreGraphics.CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
      CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), image)
      val flattened = CGBitmapContextCreateImage(context)
      CGContextRelease(context)
      if (flattened != null) {
        return Pair(flattened, true)
      }
    }
    return Pair(image, false)
  }

  private fun encodeAtQuality(
    cgImage: CGImageRef,
    quality: Int,
    targetWidth: Long,
    targetHeight: Long
  ): Pair<ByteArray, String> = autoreleasepool {
    val alphaInfo = platform.CoreGraphics.CGImageGetAlphaInfo(cgImage)
    val hasAlpha = when (alphaInfo) {
      CGImageAlphaInfo.kCGImageAlphaPremultipliedLast,
      CGImageAlphaInfo.kCGImageAlphaPremultipliedFirst,
      CGImageAlphaInfo.kCGImageAlphaLast,
      CGImageAlphaInfo.kCGImageAlphaFirst -> true
      else -> false
    }

    val canUseHeic = hasAlpha && targetWidth >= 16 && targetHeight >= 16

    fun tryEncodeWithFormat(
      uti: String,
      outMime: String,
      needsCompositeWhite: Boolean
    ): Pair<ByteArray, String>? {
      val mutableData = CFDataCreateMutable(null, 0) ?: return null
      val utiCf = CFStringCreateWithCString(null, uti, kCFStringEncodingUTF8) ?: run {
        CFRelease(mutableData)
        return null
      }
      val destination = CGImageDestinationCreateWithData(mutableData, utiCf, 1.toULong(), null)
      CFRelease(utiCf)
      if (destination == null) {
        CFRelease(mutableData)
        return null
      }

      var imageToEncode = cgImage
      var isComposite = false
      if (needsCompositeWhite) {
        val (flattened, isNew) = compositeOverWhite(cgImage, targetWidth, targetHeight)
        imageToEncode = flattened
        isComposite = isNew
      }

      try {
        memScoped {
          val options = CFDictionaryCreateMutable(null, 1, null, null)
          val qVar = alloc<DoubleVar>()
          qVar.value = quality.toDouble() / 100.0
          val qNum = CFNumberCreate(null, kCFNumberDoubleType, qVar.ptr)
          CFDictionarySetValue(options, kCGImageDestinationLossyCompressionQuality, qNum)

          CGImageDestinationAddImage(destination, imageToEncode, options)

          if (options != null) CFRelease(options)
          if (qNum != null) CFRelease(qNum)
        }

        val finalized = CGImageDestinationFinalize(destination)
        if (!finalized) {
          return null
        }

        val length = CFDataGetLength(mutableData).toInt()
        val bytePtr = CFDataGetBytePtr(mutableData)
        val resultBytes =
          if (bytePtr != null && length > 0) {
            bytePtr.readBytes(length)
          } else {
            ByteArray(0)
          }
        return Pair(resultBytes, outMime)
      } finally {
        if (isComposite) {
          CGImageRelease(imageToEncode)
        }
        CFRelease(destination)
        CFRelease(mutableData)
      }
    }

    // Format selection priority:
    // 1. If alpha present and dimensions >= 16x16: attempt HEIC (lossy with alpha)
    // 2. If alpha present: PNG (lossless alpha preservation)
    // 3. JPEG (standard lossy)
    // 4. Fallback with white compositing if all else fails
    if (canUseHeic) {
      val heicResult = tryEncodeWithFormat("public.heic", "image/heic", needsCompositeWhite = false)
      if (heicResult != null) return heicResult
    }

    if (hasAlpha) {
      val pngResult = tryEncodeWithFormat("public.png", "image/png", needsCompositeWhite = false)
      if (pngResult != null) return pngResult
    }

    val jpegResult = tryEncodeWithFormat("public.jpeg", "image/jpeg", needsCompositeWhite = hasAlpha)
    if (jpegResult != null) return jpegResult

    throw RuntimeException("Failed to encode image with HEIC, PNG, or JPEG")
  }
}
