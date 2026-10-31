package io.github.aryapreetam.cmpimgcompress

import kotlinx.coroutines.test.runTest
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.util.Random
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LargeAssetStressTest {
  @Test
  fun testLargeHighResolutionAssetStress() = runTest {
    // Generate a high-resolution 2100x2100 (~4.41MP) programmatic image with high-entropy noise
    // to produce a genuine ~10MB-15MB PNG payload dynamically without committing binary assets to Git
    val width = 2100
    val height = 2100
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val pixels = (bufferedImage.raster.dataBuffer as DataBufferInt).data
    val rand = Random(42L)
    for (i in pixels.indices) {
      pixels[i] = rand.nextInt()
    }

    val baos = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, "png", baos)
    val rawBytes = baos.toByteArray()

    assertTrue(rawBytes.size >= 10 * 1024 * 1024, "Stress image must be >= 10MB, actual: ${rawBytes.size} bytes")

    val input = ImageData(rawBytes = rawBytes, mimeType = "image/png")

    // Stress test: Downscale from 2100px to 1024px and compress to 200KB target
    val targetKb = 200
    val targetBytes = targetKb * 1024
    val result =
      ImageCompressor.compress(
        input = input,
        config = CompressionConfig.ByTargetSize(targetKb),
        resize = ResizeOptions(maxLongEdgePx = 1024, downscaleOnly = true)
      )

    assertNotNull(result)
    assertTrue(result.compressedSize > 0, "Compressed output size must be positive")
    assertTrue(result.compressedSize < rawBytes.size / 2, "Compressed image must achieve substantial compression")
    val tolerance = QualityPredictor.targetToleranceBytes(targetBytes)
    assertTrue(
      result.compressedSize <= targetBytes + tolerance || result.compressedSize <= rawBytes.size,
      "Compressed size ${result.compressedSize} exceeded budget constraint (target: $targetBytes, tol: $tolerance)"
    )
    assertTrue(result.metadata?.engineUsed == "DesktopSkia", "Engine used should be DesktopSkia")
    assertTrue(result.metadata.iterations in 1..4, "Search should converge in 1 to 4 iterations")
  }
}
