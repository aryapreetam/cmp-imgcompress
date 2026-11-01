<h1 align="center">CMP Image Compressor</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Compose%20Multiplatform-v1.10.1-4285F4?logo=jetpackcompose" alt="Compose Multiplatform Version">
  <img src="https://img.shields.io/badge/Kotlin-v2.3.21-blueviolet?logo=kotlin" alt="Kotlin Version">
</p>

<p align="center">
  <a href="https://github.com/aryapreetam/cmp-imgcompress/actions/workflows/release.yml">
    <img src="https://github.com/aryapreetam/cmp-imgcompress/actions/workflows/release.yml/badge.svg" alt="Release workflow status">
  </a>
  <a href="https://mvnrepository.com/artifact/io.github.aryapreetam/cmp-imgcompress">
    <img src="https://img.shields.io/maven-central/v/io.github.aryapreetam/cmp-imgcompress?label=Maven%20Central&color=blue" alt="Maven Central Version">
  </a>
  <a href="https://github.com/aryapreetam/cmp-imgcompress/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white" alt="Android">
  <img src="https://img.shields.io/badge/iOS-000000?logo=apple&logoColor=white" alt="iOS">
  <img src="https://img.shields.io/badge/Desktop/JVM-0078D6?logo=desktop&logoColor=white" alt="Desktop">
  <img src="https://img.shields.io/badge/Web(wasm)-4285F4?logo=google-chrome&logoColor=white" alt="Web">
</p>

<p align="center">
  <a href="https://aryapreetam.github.io/cmp-imgcompress/demo/"><strong>Live Web Demo</strong></a> •
  <a href="https://aryapreetam.github.io/cmp-imgcompress/api/"><strong>API Reference</strong></a> •
  <a href="#-download-sample-app-executables"><strong>Download Sample Apps</strong></a>
</p>

A Kotlin Multiplatform image compression library for Android, iOS, Desktop (JVM), and Web (Wasm). It provides an in-process compression API with aspect-ratio-aware downscaling, target quality, and target file size constraints.

<table align="center">
<tr>
<td>

https://github.com/user-attachments/assets/fd74f21d-4aaf-44e6-b54c-4a7d4480eff9

</td>
</tr>
</table>

---

## What It Does

- **Compression Modes**: Compress by quality percentage (`0` to `100`) or by target file size limit in kilobytes.
- **Aspect-Ratio Downscaling**: Downscales images whose longest edge exceeds a specified pixel limit before encoding, reducing peak memory usage.
- **Zero UI Dependencies**: The core compression library has no UI or Compose dependencies and can be used in background workers, ViewModels, or command-line tools.
- **Compression Diagnostics**: Returns input size, output size, MIME type, elapsed execution time, and iterations taken.

---

## Platform Support and Format Behavior

Different platforms use different native encoders. The table below lists the underlying engine, output format, and transparency handling for each supported target:

| Platform | Output Format | Alpha Transparency | Engine |
| :--- | :--- | :---: | :--- |
| **Android** | `image/webp` | Preserved | `android.graphics.Bitmap` |
| **iOS** | `image/heic`, `image/png`, or `image/jpeg` | Preserved (via HEIC/PNG) | Apple `ImageIO` + `CoreGraphics` |
| **Desktop (JVM)** | `image/webp` | Preserved | JetBrains Skia (`org.jetbrains.skia`) |
| **Web (Wasm)** | `image/webp` | Preserved | Browser Canvas API |

### iOS Format Selection

Apple's system `ImageIO` framework does not include an encoder for WebP. To prevent transparency loss, iOS selects formats dynamically:

1. **Images with transparency**: Encoded as HEIC (`image/heic`) with lossy compression on iOS 11+. If HEIC is unavailable, it falls back to lossless PNG (`image/png`).
2. **Images without transparency**: Encoded as JPEG (`image/jpeg`) with lossy compression.
3. **JPEG fallback for transparent images**: If forced to JPEG, the image is composited over a solid white background so transparent areas do not render black.

---

## Installation

Add the dependency to your project:

### Option A: Gradle Version Catalog (Recommended)

1. Add to `gradle/libs.versions.toml`:

```toml
[versions]
cmp-imgcompress = "0.0.4"

[libraries]
cmp-imgcompress = { module = "io.github.aryapreetam:cmp-imgcompress", version.ref = "cmp-imgcompress" }
```

2. Add to your module's `build.gradle.kts`:

```kotlin
kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation(libs.cmp.imgcompress)
    }
  }
}
```

### Option B: Direct Dependency

```kotlin
kotlin {
  sourceSets {
    commonMain.dependencies {
      implementation("io.github.aryapreetam:cmp-imgcompress:0.0.4")
    }
  }
}
```

---

## Usage Examples

### Compress by Quality Percentage

```kotlin
import io.github.aryapreetam.cmpimgcompress.CompressedImage
import io.github.aryapreetam.cmpimgcompress.CompressionConfig
import io.github.aryapreetam.cmpimgcompress.ImageData
import io.github.aryapreetam.cmpimgcompress.ImageCompressor

suspend fun compressSample(inputBytes: ByteArray) {
  val input = ImageData(rawBytes = inputBytes, mimeType = "image/png")

  val result: CompressedImage = ImageCompressor.compress(
    input = input,
    config = CompressionConfig.ByQuality(qualityPercent = 80f)
  )

  println("Original: ${result.originalSize} B, Compressed: ${result.compressedSize} B (${result.mimeType})")
  val compressedBytes: ByteArray = result.bytes
}
```

### Compress to a Target Size Budget (KB)

```kotlin
import io.github.aryapreetam.cmpimgcompress.CompressionConfig
import io.github.aryapreetam.cmpimgcompress.ImageData
import io.github.aryapreetam.cmpimgcompress.ImageCompressor
import io.github.aryapreetam.cmpimgcompress.ResizeOptions

suspend fun compressUnderBudget(inputBytes: ByteArray) {
  val input = ImageData(rawBytes = inputBytes, mimeType = "image/jpeg")

  // Attempts to produce an output within 100 KB
  val result = ImageCompressor.compress(
    input = input,
    config = CompressionConfig.ByTargetSize(targetSizeKb = 100),
    resize = ResizeOptions(maxLongEdgePx = 1920)
  )

  println("Size: ${result.compressedSize} B in ${result.metadata?.elapsedMillis} ms across ${result.metadata?.iterations} iterations")
}
```

---

## 📦 Download Sample App Executables

Download pre-built sample application binaries directly to verify library behavior on your platform without building from source:

| Platform | Format / Package | Download Link |
| :--- | :--- | :--- |
| 🍏 **macOS (Intel)** | DMG Installer | [![Download](https://img.shields.io/badge/Download--Mac--x64--DMG-blue?style=for-the-badge)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-desktop-x64.dmg) |
| 🍎 **macOS (Apple Silicon)** | DMG Installer | [![Download](https://img.shields.io/badge/Download--Mac--arm64--DMG-blue?style=for-the-badge)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-desktop-arm64.dmg) |
| 🪟 **Windows** | MSI Installer | [![Download](https://img.shields.io/badge/Download--Windows--MSI-0078D4?style=for-the-badge&logo=windows)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-windows.msi) |
| 🐧 **Linux** | DEB Package | [![Download](https://img.shields.io/badge/Download--Linux--DEB-313131?style=for-the-badge&logo=debian)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-linux.deb) |
| 🤖 **Android** | APK Executable | [![Download](https://img.shields.io/badge/Download--Android--APK-green?style=for-the-badge&logo=android)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-android-unsigned.apk) |
| 🌐 **Web (Wasm)** | ZIP Bundle | [![Download](https://img.shields.io/badge/Download--Web--Wasm--zip-purple?style=for-the-badge)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-wasm.zip) |
|  **iOS Simulator** | ZIP Bundle | [![Download](https://img.shields.io/badge/Download--iOS--Simulator--zip-orange?style=for-the-badge&logo=apple)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-ios-simulator.zip) |

### 🚀 How to Run the Sample App

For instructions on installing pre-built binaries or compiling the sample app from source, see the **[How to Run the Sample App](docs/run-sample-app.md)** guide.

---

## Demos and Reference

- **Interactive Wasm Demo**: [aryapreetam.github.io/cmp-imgcompress/demo/](https://aryapreetam.github.io/cmp-imgcompress/demo/)
- **API Reference**: [aryapreetam.github.io/cmp-imgcompress/api/](https://aryapreetam.github.io/cmp-imgcompress/api/)
- **Sample App Setup**: See [How to Run the Sample App](docs/run-sample-app.md) for details on running the desktop, Android, and web sample applications.

---

## License

MIT License. See [LICENSE](./LICENSE) for details.
