# Running the Sample App

This guide describes how to run the `cmp-imgcompress` sample application, either using pre-built release executables or running from source.

---

## 📦 How to Run Pre-built Executables

### 🤖 Android

**Option 1: Drag and Drop**

- Download the APK file.
- Open an Android emulator or connect a physical device.
- Drag the APK onto the emulator window.

**Option 2: ADB Install**

```bash
adb install sample-app-android-unsigned.apk
```

### 🌐 Web (Wasm)

- Download and unzip `sample-app-wasm.zip`
- Open `index.html` in your web browser.
- Or visit the live demo: [Try Live Demo](https://aryapreetam.github.io/cmp-imgcompress/demo/)

### 🍏 iOS Simulator

- Download `sample-app-ios-simulator.zip`
- Unzip to get `sample-app-ios-simulator.app`
- Open your iOS Simulator from Xcode.
- Drag the `.app` onto the Simulator window, or run:
  ```sh
  xcrun simctl install booted /path/to/sample-app-ios-simulator.app
  ```

### 🍎 macOS

- Download the DMG for your architecture (Intel/x64 or Apple Silicon/arm64).
- Open the DMG and drag the app to your Applications folder.
- **When you try to open the app for the first time, macOS Gatekeeper will block it since it is an open-source build signed ad-hoc. Follow these 6 sequential steps to allow running the app:**

  <table width="100%">
    <tr>
      <td align="center" valign="top"><strong>1. Block Alert Dialog</strong><br/><br/><img src="../readme_images/mac_app_run1.png" alt="mac_app_run1" width="220"/></td>
      <td align="center" valign="top"><strong>2. Open System Settings</strong><br/><br/><img src="../readme_images/mac_app_run2.png" alt="mac_app_run2" width="220"/></td>
      <td align="center" valign="top"><strong>3. Privacy & Security Section</strong><br/><br/><img src="../readme_images/mac_app_run3.png" alt="mac_app_run3" width="220"/></td>
    </tr>
    <tr>
      <td align="center" valign="top"><strong>4. Click "Open Anyway"</strong><br/><br/><img src="../readme_images/mac_app_run4.png" alt="mac_app_run4" width="220"/></td>
      <td align="center" valign="top"><strong>5. Authenticate Security Dialog</strong><br/><br/><img src="../readme_images/mac_app_run5.png" alt="mac_app_run5" width="220"/></td>
      <td align="center" valign="top"><strong>6. Click "Open" to Launch</strong><br/><br/><img src="../readme_images/mac_app_run6.png" alt="mac_app_run6" width="220"/></td>
    </tr>
  </table>

- Summary:
     1. Double-click the app. If blocked, open **System Settings**.
     2. Navigate to **Privacy & Security** and scroll down to the **Security** section.
     3. You will see a notice stating: *"sample was blocked from use because it is not from an identified developer"*.
     4. Click **"Open Anyway"** and authenticate with your Touch ID / password.
     5. Re-open the app and click **"Open"** on the confirmation dialog.

### 🐧 Linux

- Download the `.deb` package.
- Install it via your GUI software manager, or run:
  ```bash
  sudo dpkg -i sample-app-linux.deb
  ```

---

## 🛠️ Run Sample App from Source

To run the sample app locally on your machine:

* **Desktop JVM:** `./gradlew :sample:composeApp:run`
* **Android:** Run the `:sample:androidApp` run configuration in Android Studio.
* **iOS:** Open `sample/iosApp/iosApp.xcodeproj` in Xcode and run it.
* **Wasm:** `./gradlew :sample:composeApp:wasmJsBrowserRun`
