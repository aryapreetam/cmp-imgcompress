package sample.app

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageCompressionIntegrationTest : E2ETestLifecycle {

  @Test
  fun testPickAndCompressLargeImage() = e2eTest {
    // 1. Assert initial UI elements are visible
    assertVisible("pick_image_button")
    assertVisible("source_asset_card")

    // 2. Virtual file picker selects large_sample.png
    withFilePicker("large_sample.png") {
      click("pick_image_button")
    }

    // 3. Verify the picked file name and large size are rendered in Compose UI
    assertContains("source_image_name", "large_sample.png")
    assertText("source_image_size", "1049275 B")

    // 4. Trigger image compression engine
    click("compress_button")

    // 5. Verify compression analytics appear with a compressed output size
    assertVisible("compression_analytics_card")
    assertVisible("compressed_image_size")

    // 6. Verify that the output size was actually reduced
    val compressedBytes = resolveVisibleNode("compressed_image_size").text.orEmpty().removeSuffix(" B").toInt()
    assertTrue(compressedBytes in 1..<1049275, "Expected size to be reduced from 1049275 B, got $compressedBytes B")
  }
}
