import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Dev config lives in the gitignored local.properties (see README).
val localProps = Properties().apply {
  rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localProp(key: String, fallback: String) = localProps.getProperty(key, fallback)

// Signing key lives in the gitignored android/keystore.properties; storeFile is relative to this module.
val keystoreProps = Properties().apply {
  rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
  namespace = "com.biohazard786.interesttracker"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.biohazard786.interesttracker"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0"
    buildConfigField("String", "API_BASE_URL", "\"${localProp("API_BASE_URL", "https://example.com")}\"")
    buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("GOOGLE_WEB_CLIENT_ID", "")}\"")
  }

  signingConfigs {
    if (keystoreProps.isNotEmpty()) {
      create("release") {
        storeFile = file(keystoreProps.getProperty("storeFile"))
        storePassword = keystoreProps.getProperty("storePassword")
        keyAlias = keystoreProps.getProperty("keyAlias")
        keyPassword = keystoreProps.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    // Debug is signed with the release key too, so one SHA-1 serves Google sign-in for both.
    debug {
      signingConfigs.findByName("release")?.let { signingConfig = it }
    }
    release {
      signingConfigs.findByName("release")?.let { signingConfig = it }
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
  compilerOptions {
    optIn.addAll(
      "androidx.compose.material3.ExperimentalMaterial3Api",
      "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
    )
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Network + JSON
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)

  // Google sign-in via Credential Manager
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play)
  implementation(libs.googleid)
}
