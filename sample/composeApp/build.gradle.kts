@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose)
  alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
  jvmToolchain(17)

  androidLibrary {
    namespace = "sample.app"
    compileSdk = 35
    minSdk = 23
  }
  jvm()
  wasmJs {
    browser()
    binaries.executable()
  }
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(libs.compose.runtime)
      implementation(libs.compose.ui.multiplatform)
      implementation(libs.compose.foundation)
      implementation(project(":lib"))
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
    }

    androidMain.dependencies {
      implementation(libs.androidx.activityCompose)
    }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
    }

  }
}

compose.desktop {
  application {
    mainClass = "MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = findProperty("libArtifactId")?.toString()?.let { "sample-$it" } ?: "sample"
      packageVersion = "1.0.0"
    }
  }
}

tasks.withType<Test>().configureEach {
  // works for both testDebugUnitTest & testReleaseUnitTest
  if (name.endsWith("UnitTest")) {
    exclude("**/*UITest*")
  }
}

