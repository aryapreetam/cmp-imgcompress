package io.github.aryapreetam.cmpimgcompress

import io.github.aryapreetam.cmpimgcompress.testutils.BasePlatformTest
import io.github.aryapreetam.cmpimgcompress.testutils.TestImages
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageCompressorCancellationTest : BasePlatformTest() {
  @Test
  fun testCompressionCancellation() = runTest {
    val input = ImageData(rawBytes = TestImages.multiPixelPng, mimeType = "image/png")
    val job = launch {
      ImageCompressor.compress(input, CompressionConfig.ByTargetSize(10))
    }
    job.cancel()
    job.join()
    assertTrue(job.isCancelled, "Compression job must cancel cleanly without unhandled exceptions")
  }
}
