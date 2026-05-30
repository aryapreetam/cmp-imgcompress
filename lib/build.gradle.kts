@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.maven.publish)
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.dokka)
}

kotlin {
  jvmToolchain(17)

  androidLibrary {
    namespace = "io.github.aryapreetam.cmpimgcompress"
    compileSdk = 35
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
      implementation(libs.compose.runtime)
      implementation(libs.compose.ui.multiplatform)
      implementation(libs.compose.foundation)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.kotlinx.coroutines.test)
    }

    val jvmMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
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
    url = "https://aryapreetam.github.io/cmp-imgcompress" //todo

    licenses {
      license {
        name = "MIT"
        url = "https://opensource.org/licenses/MIT"
      }
    }

    developers {
      developer {
        id = "aryapreetam" //todo
        name = "Preetam Bhosle" //todo
      }
    }

    scm {
      url = "https://github.com/aryapreetam/cmp-imgcompress" //todo
    }
  }
  // Sign publications if either local keyId or CI signingInMemoryKey is available
  if (project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey")) {
    signAllPublications()
  }
}
