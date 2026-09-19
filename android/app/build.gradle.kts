import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

// Release signing material lives outside version control: keystore/ + keystore.properties are gitignored.
val keystoreProps = Properties().apply {
  val file = rootProject.file("keystore.properties")
  if (file.exists()) file.inputStream().use { load(it) }
}

// Base URL defaults to production for both build types; override for a local backend with
//   ./gradlew assembleDebug -PbaseUrl=http://192.168.1.20/my-library
// Point it at the nginx entry, not the raw uvicorn port: the collection endpoints answer a
// 307 whose Location carries the /my-library prefix, which 404s when the backend is hit directly.
val defaultBaseUrl = "https://dingfengbo.top/my-library"
val baseUrl = (project.findProperty("baseUrl") as String?) ?: defaultBaseUrl

android {
    namespace = "top.dingfengbo.mylibrary"
    compileSdk = 36
    defaultConfig {
        applicationId = "top.dingfengbo.mylibrary"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
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
