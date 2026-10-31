@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.maven.publish)
  alias(libs.plugins.binary.compatibility.validator)
  alias(libs.plugins.dokka)
}

kotlin {
  jvmToolchain(17)

  androidLibrary {
    namespace = "io.github.aryapreetam.cmpimgcompress"
    compileSdk = 37
    minSdk = 23
    withHostTest {  }
    androidResources {
      enable = true
    }
  }
  jvm()
  wasmJs { browser() }
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }

    val androidHostTest by getting {
      dependencies {
        implementation(libs.robolectric)
      }
    }

    val jvmMain by getting {
      dependencies {
        implementation(libs.skiko)
      }
    }

    val jvmTest by getting {
      dependencies {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val skikoOs = when {
          os.contains("mac") || os.contains("darwin") -> "macos"
          os.contains("linux") -> "linux"
          os.contains("win") -> "windows"
          else -> "macos"
        }
        val skikoArch = when {
          arch == "aarch64" || arch == "arm64" -> "arm64"
          else -> "x64"
        }
        runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$skikoOs-$skikoArch:${libs.versions.skiko.get()}")
      }
    }

  }

  //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
  targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
    compilations["main"].compileTaskProvider.configure {
      compilerOptions {
        freeCompilerArgs.add("-Xexport-kdoc")
      }
    }
  }

}

// NOTE: Host-specific dependency leakage guardrail:
// DO NOT import host-specific binary dependencies (e.g. `compose.desktop.currentOs`) under library targets.
// Any desktop UI implementation should target standard platform-agnostic `jvm()` targets.
// Platform-specific runtime locators must be restricted solely to the executable sample application (:sample).

dependencies {
  dokkaPlugin(libs.android.documentation.plugin)
}

//Publishing your Kotlin Multiplatform library to Maven Central
//https://www.jetbrains.com/help/kotlin-multiplatform-dev/multiplatform-publish-libraries.html
mavenPublishing {
  publishToMavenCentral()
  coordinates(
      project.group.toString(),
      findProperty("libArtifactId")?.toString() ?: "cmp-imgcompress",
      project.version.toString()
  )

  pom {
    name = "Image Compressor"
    description = "Image Compressor for Compose Multiplatform(only webp output for now)"
    url = "https://github.com/aryapreetam/cmp-imgcompress"

    licenses {
      license {
        name = "MIT"
        url = "https://opensource.org/licenses/MIT"
      }
    }

    developers {
      developer {
        id = "aryapreetam"
        name = "Preetam Bhosle"
        url = "https://github.com/aryapreetam"
      }
    }

    scm {
      url = "https://github.com/aryapreetam/cmp-imgcompress"
      connection = "scm:git:git://github.com/aryapreetam/cmp-imgcompress.git"
      developerConnection = "scm:git:ssh://git@github.com/aryapreetam/cmp-imgcompress.git"
    }
  }
  // Sign publications if either local keyId or CI signingInMemoryKey is available
  if (project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey")) {
    signAllPublications()
  }
}
