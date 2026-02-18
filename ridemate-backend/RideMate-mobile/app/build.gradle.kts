import org.gradle.api.GradleException
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Local-only config (ignored by git) + env vars are supported to avoid committing secrets.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { load(it) }
    }
}

fun projectOrLocalOrEnv(name: String): String? {
    val fromProject = project.findProperty(name)?.toString()?.takeIf { it.isNotBlank() }
    val fromLocal = localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
    val fromEnv = System.getenv(name)?.takeIf { !it.isNullOrBlank() }
    return fromProject ?: fromLocal ?: fromEnv
}

android {
    namespace = "com.rydius.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rydius.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val baseUrl = projectOrLocalOrEnv("RIDEMATE_BASE_URL")
                ?: "http://10.0.2.2:3000"
            val olaMapsApiKey = projectOrLocalOrEnv("OLA_MAPS_API_KEY").orEmpty()
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            buildConfigField("String", "OLA_MAPS_API_KEY", "\"$olaMapsApiKey\"")
            isMinifyEnabled = false
        }
        release {
            val baseUrl = projectOrLocalOrEnv("RIDEMATE_BASE_URL")
                ?: "https://your-production-domain.com"
            val olaMapsApiKey = projectOrLocalOrEnv("OLA_MAPS_API_KEY").orEmpty()
            val isReleaseBuild = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            if (isReleaseBuild) {
                if (baseUrl.contains("your-production-domain.com")) {
                    throw GradleException("RIDEMATE_BASE_URL must be set to your production HTTPS URL for release builds.")
                }
                if (!baseUrl.startsWith("https://")) {
                    throw GradleException("Release RIDEMATE_BASE_URL must use https://")
                }
                if (olaMapsApiKey.isBlank()) {
                    throw GradleException("OLA_MAPS_API_KEY is required for release builds.")
                }
            } else {
                if (baseUrl.contains("your-production-domain.com") || !baseUrl.startsWith("https://")) {
                    println("Note: Release build guards are bypassed during non-release tasks / sync.")
                }
            }
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            buildConfigField("String", "OLA_MAPS_API_KEY", "\"$olaMapsApiKey\"")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }
}

dependencies {
    // Compose BOM (Upgraded to support PullToRefreshBox and latest Material 3)
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    // Material Components (XML themes). Compose Material3 does not provide Theme.Material3.* resources.
    implementation("com.google.android.material:material:1.12.0")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")

    // Socket.IO
    implementation("io.socket:socket.io-client:2.1.1")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.5.2")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore for persistent prefs
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
