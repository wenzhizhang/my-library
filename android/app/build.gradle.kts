import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Release signing material lives outside version control: keystore/ + keystore.properties are gitignored.
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
  if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
val signingKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")

// Null when the signing material is complete, otherwise the reason it is not.
val signingProblem: String? = when {
  !keystoreFile.exists() -> "$keystoreFile does not exist"
  else -> signingKeys
    .filter { keystoreProps.getProperty(it).isNullOrBlank() }
    .takeIf { it.isNotEmpty() }
    ?.let { "$keystoreFile has no ${it.joinToString(", ")}" }
}

// AGP silently writes app-release-unsigned.apk when the release signing config is incomplete, so
// gate every task that produces a release artifact. Recording and lint tasks stay usable without
// signing material.
val releaseArtifactTask = Regex("^(assemble|bundle|package|install).*Release")

gradle.taskGraph.whenReady {
  val releaseTask = allTasks.firstOrNull { releaseArtifactTask.containsMatchIn(it.name) }
  if (releaseTask != null && signingProblem != null) {
    throw GradleException(
      "Cannot run ${releaseTask.path}: $signingProblem. " +
        "A release APK/AAB must be signed - provide ${signingKeys.joinToString(", ")}."
    )
  }
}

// Base URL defaults to production; the -PbaseUrl override is debug-only so a dev host (or a stale
// ~/.gradle/gradle.properties entry) can never reach a release build:
//   ./gradlew assembleDebug -PbaseUrl=http://192.168.1.20/my-library
// Point it at the nginx entry, not the raw uvicorn port: the collection endpoints answer a
// 307 whose Location carries the /my-library prefix, which 404s when the backend is hit directly.
val defaultBaseUrl = "https://dingfengbo.top/my-library"
val debugBaseUrl = (project.findProperty("baseUrl") as String?) ?: defaultBaseUrl

android {
    namespace = "top.dingfengbo.mylibrary"
    compileSdk = 36
    defaultConfig {
        applicationId = "top.dingfengbo.mylibrary"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            keystoreProps.getProperty("storeFile")?.let { storeFile = rootProject.file(it) }
            keystoreProps.getProperty("storePassword")?.let { storePassword = it }
            keystoreProps.getProperty("keyAlias")?.let { keyAlias = it }
            keystoreProps.getProperty("keyPassword")?.let { keyPassword = it }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BASE_URL", "\"$defaultBaseUrl\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    jvmToolchain(21)
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
  implementation(libs.androidx.datastore.preferences)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  // Curated Material icon set (~100 KB): the app had no iconography at all, and the extended
  // set is a 10 MB artefact for a handful of glyphs.
  implementation(libs.androidx.compose.material.icons.core)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Networking: generated OpenAPI client + Retrofit/OkHttp + kotlinx.serialization
  implementation(libs.retrofit)
  implementation(libs.retrofit.kotlinx.serialization)
  implementation(libs.retrofit.scalars)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.serialization.json)
  // Referenced unconditionally by AppContainer; the interceptor itself is only installed in debug
  // builds (guarded by BuildConfig.DEBUG), so release never logs requests.
  implementation(libs.okhttp.logging)

  testImplementation(libs.okhttp.mockwebserver)

  // Images (book covers, author photos, backgrounds). Media endpoints are public, so Coil uses its
  // own OkHttp client rather than the authenticated one.
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  // ISBN scanning: CameraX preview + bundled ML Kit model (no Play Services needed, so it works on
  // devices without GMS). The bundled model adds ~3MB to the APK.
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)
}
