<h1 align="center">CMP Image Compressor - Compose Multiplatform Image Compression Library</h1>

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

A high-performance, lightweight image compression library for Compose Multiplatform (Android, iOS, Desktop/JVM, Web/WASM). It allows you to efficiently compress and resize images into high-quality WebP format using a simple, unified multiplatform API.

**Key Features:**

- ⚡ **Dual Compression Modes**: Compress by target quality percentage or target file size constraint (KB).
- 📐 **Smart Resizing**: Fast, aspect-ratio-aware resizing prior to compression to save memory and network bandwidth.
- ⚙️ **Multi-Engine Execution**: Uses high-performance native engines under the hood (Android's Bitmap, JVM's Skia, iOS's UIKit, Wasm's Canvas).
- 📊 **Rich Analytics**: Delivers metadata and diagnostics on compression ratios, elapsed times, and iteration counts.
- 🧪 **Testable Design**: Designed to support clean UI unit tests and portable multiplatform verification.

---

## ✅ Platform Capability Matrix

| Capability | Android | iOS | Desktop (JVM) | Web (WASM) |
|---|---:|---:|---:|---:|
| WebP Compression | ✅ | ✅ | ✅ | ✅ |
| Compress By Quality | ✅ | ✅ | ✅ | ✅ |
| Compress By Target Size | ✅ | ✅ *(Iterative)* | ✅ *(Iterative)* | ✅ *(Iterative)* |
| Smart Auto-Resizing | ✅ | ✅ | ✅ | ✅ |
| Compression Analytics | ✅ | ✅ | ✅ | ✅ |

Wasm Demo: 
[![Try Live Demo](https://img.shields.io/badge/Try%20Live%20Demo-Web%20wasmJs-blueviolet?style=for-the-badge)](https://aryapreetam.github.io/cmp-imgcompress/demo/)

API Docs:
[![Library API Docs](https://img.shields.io/badge/API%20Reference-Library%20Docs-green?style=for-the-badge)](https://aryapreetam.github.io/cmp-imgcompress/api/)

---

## 📦 Installation

Add the dependency to your Compose Multiplatform project:

**Option A — Version Catalog (Recommended)**

1) In `gradle/libs.versions.toml`:

```toml
[versions]
cmpImgcompress = "0.0.1"

[libraries]
cmp-imgcompress = { module = "io.github.aryapreetam:cmp-imgcompress", version.ref = "cmpImgcompress" }
```

2) In your module's `build.gradle.kts`:

```kotlin
kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation(libs.cmp.imgcompress)
      }
    }
  }
}
```

**Option B — Direct Dependency**

```kotlin
kotlin {
  sourceSets {
    val commonMain by getting {
      dependencies {
        implementation("io.github.aryapreetam:cmp-imgcompress:0.0.1")
      }
    }
  }
}
```

---

## 🚀 Quick Start

### Basic Compression by Quality

```kotlin
import io.github.aryapreetam.cmpimgcompress.*

suspend fun compressMyImage(inputBytes: ByteArray) {
  val imageData = ImageData(inputBytes, "image/png")
  
  // Compress to WebP at 80% quality
  val compressed: CompressedImage = ImageCompressor.compress(
    input = imageData,
    config = CompressionConfig.ByQuality(80f)
  )
  
  println("Original: ${compressed.originalSize} bytes")
  println("Compressed: ${compressed.compressedSize} bytes")
  
  val outputWebpBytes = compressed.bytes
}
```

### Compress to a Specific Target Size Constraint

```kotlin
import io.github.aryapreetam.cmpimgcompress.*

suspend fun compressToLimit(inputBytes: ByteArray) {
  val imageData = ImageData(inputBytes, "image/jpeg")
  
  // Attempt to compress the image to fall under 100 KB
  val compressed = ImageCompressor.compress(
    input = imageData,
    config = CompressionConfig.ByTargetSize(100), // Target size limit in KB
    resize = ResizeOptions(maxLongEdgePx = 1920) // Optional resizing
  )
  
  println("Elapsed time: ${compressed.metadata?.elapsedMillis} ms")
  println("Iterations taken: ${compressed.metadata?.iterations}")
}
```

---

## 📦 Download Sample App Executables

Download and try the sample app on your platform without building from source:

| Platform | Format / Package | Download Link |
|----------|------------------|--------------|
| 🍏 **macOS (Intel)** | DMG Installer | [![Download](https://img.shields.io/badge/Download--Mac--x64--DMG-blue?style=for-the-badge)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-desktop-x64.dmg) |
| 🍎 **macOS (Apple Silicon)** | DMG Installer | [![Download](https://img.shields.io/badge/Download--Mac--arm64--DMG-blue?style=for-the-badge)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-desktop-arm64.dmg) |
| 🪟 **Windows** | MSI Installer | [![Download](https://img.shields.io/badge/Download--Windows--MSI-0078D4?style=for-the-badge&logo=windows)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-windows.msi) |
| 🐧 **Linux** | DEB Package | [![Download](https://img.shields.io/badge/Download--Linux--DEB-313131?style=for-the-badge&logo=debian)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-linux.deb) |
| 🤖 **Android** | APK Executable | [![Download](https://img.shields.io/badge/Download--Android--APK-green?style=for-the-badge&logo=android)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-android-unsigned.apk) |
| 🌐 **Web (Wasm)** | ZIP Bundle | [![Download](https://img.shields.io/badge/Download--Web--Wasm--zip-purple?style=for-the-badge)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-wasm.zip) |
|  **iOS Simulator** | ZIP Bundle | [![Download](https://img.shields.io/badge/Download--iOS--Simulator--zip-orange?style=for-the-badge&logo=apple)](https://github.com/aryapreetam/cmp-imgcompress/releases/latest/download/sample-app-ios-simulator.zip) |

### How to Run the Sample App

#### 🤖 Android

- Download the APK file.
- Open an Android emulator or connect a physical device.
- Drag the APK onto the emulator window, or run:
  ```bash
  adb install sample-app-android-unsigned.apk
  ```

#### 🌐 Web (Wasm)

- Download and unzip `sample-app-wasm.zip`
- Open `index.html` in your web browser.
- Or visit the live demo: [Try Live Demo](https://aryapreetam.github.io/cmp-imgcompress/demo/)

#### 🍏 iOS Simulator

- Download `sample-app-ios-simulator.zip`
- Unzip to get `sample-app-ios-simulator.app`
- Open your iOS Simulator from Xcode.
- Drag the `.app` onto the Simulator window, or run:
  ```sh
  xcrun simctl install booted /path/to/sample-app-ios-simulator.app
  ```

#### 🍎 macOS

- Download the DMG for your architecture (Intel/x64 or Apple Silicon/arm64).
- Open the DMG and drag the app to your Applications folder.
- **When you try to open the app for the first time, macOS Gatekeeper will block it since it is an open-source build signed ad-hoc. Follow these 6 sequential steps to allow running the app:**

  <table width="100%">
    <tr>
      <td align="center" valign="top"><strong>1. Block Alert Dialog</strong><br/><br/><img src="readme_images/mac_app_run1.png" alt="mac_app_run1" width="220"/></td>
      <td align="center" valign="top"><strong>2. Open System Settings</strong><br/><br/><img src="readme_images/mac_app_run2.png" alt="mac_app_run2" width="220"/></td>
      <td align="center" valign="top"><strong>3. Privacy & Security Section</strong><br/><br/><img src="readme_images/mac_app_run3.png" alt="mac_app_run3" width="220"/></td>
    </tr>
    <tr>
      <td align="center" valign="top"><strong>4. Click "Open Anyway"</strong><br/><br/><img src="readme_images/mac_app_run4.png" alt="mac_app_run4" width="220"/></td>
      <td align="center" valign="top"><strong>5. Authenticate Security Dialog</strong><br/><br/><img src="readme_images/mac_app_run5.png" alt="mac_app_run5" width="220"/></td>
      <td align="center" valign="top"><strong>6. Click "Open" to Launch</strong><br/><br/><img src="readme_images/mac_app_run6.png" alt="mac_app_run6" width="220"/></td>
    </tr>
  </table>

- Summary:
     1. Double-click the app. If blocked, open **System Settings**.
     2. Navigate to **Privacy & Security** and scroll down to the **Security** section.
     3. You will see a notice stating: *"sample was blocked from use because it is not from an identified developer"*.
     4. Click **"Open Anyway"** and authenticate with your Touch ID / password.
     5. Re-open the app and click **"Open"** on the confirmation dialog.

#### 🐧 Linux

- Download the `.deb` package.
- Install it via your GUI software manager, or run:
  ```bash
  sudo dpkg -i sample-app-linux.deb
  ```

---

## 🛠️ Run Sample App from Source

To run the sample app locally on your machine:

- **Desktop JVM:** `./gradlew :sample:composeApp:run`
- **Android:** Run the `:sample:androidApp` run configuration in Android Studio.
- **iOS:** Open `sample/iosApp/iosApp.xcodeproj` in Xcode and run it.
- **Wasm:** `./gradlew :sample:composeApp:wasmJsBrowserRun`

---

## License

MIT License © 2026 aryapreetam and contributors. See [LICENSE](./LICENSE) for details.
