plugins {
  alias(libs.plugins.multiplatform).apply(false)
  alias(libs.plugins.android.library).apply(false)
  alias(libs.plugins.maven.publish).apply(false)
  alias(libs.plugins.compose).apply(false)
  alias(libs.plugins.compose.compiler).apply(false)
  alias(libs.plugins.android.application).apply(false)
  alias(libs.plugins.composeHotReload) apply false
  alias(libs.plugins.android.kotlin.multiplatform.library).apply(false)
}

// Apply template setup check
apply(from = "gradle/check-template-setup.gradle.kts")

allprojects {
  group = findProperty("libGroup") ?: "io.github.aryapreetam"
  version = findProperty("libVersion") ?: "0.0.1"
}

