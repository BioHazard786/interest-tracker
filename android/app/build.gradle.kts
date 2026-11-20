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
    // The release workflow passes these from the git tag (v1.2.3 -> 1.2.3 / 10203).
    versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
    versionName = providers.gradleProperty("versionName").getOrElse("1.0")
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
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  testOptions {
    unitTests.isReturnDefaultValues = true
    // The golden parser tests read fixtures/ at runtime: changing them must re-run the tests.
    unitTests.all { it.inputs.dir(rootProject.file("fixtures")) }
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
      "androidx.compose.foundation.ExperimentalFoundationApi",
      "androidx.compose.ui.text.ExperimentalTextApi",
    )
  }
}

// PDFBox-Android reads its glyph lists and fonts from the aar's assets. Unit tests have no AssetManager,
// so unpack them onto the test classpath and PdfText runs on the JVM too.
val pdfboxTestAssets = layout.buildDirectory.dir("pdfbox-test-assets")
val unpackPdfboxAssets by tasks.registering(Copy::class) {
  val aar = configurations.detachedConfiguration(dependencies.create("${libs.pdfbox.android.get()}@aar"))
  from(provider { zipTree(aar.singleFile) }) {
    include("assets/**")
    eachFile { path = path.removePrefix("assets/") }
  }
  includeEmptyDirs = false
  into(pdfboxTestAssets)
}

android.sourceSets.getByName("test").resources.srcDir(pdfboxTestAssets.get().asFile)
tasks.matching { it.name.endsWith("UnitTestJavaRes") }.configureEach { dependsOn(unpackPdfboxAssets) }

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  // Installs the baseline profiles Compose ships, so sideloaded APKs aren't JIT-only at first launch.
  implementation(libs.androidx.profileinstaller)

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

  // Statement parsing: PDF text (the .xlsx and .csv readers are hand-rolled, see parsing/)
  implementation(libs.pdfbox.android)
}
