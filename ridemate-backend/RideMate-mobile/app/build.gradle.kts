plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
            val baseUrl = project.findProperty("RIDEMATE_BASE_URL")?.toString()
                ?: "http://10.0.2.2:3000"
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            buildConfigField("String", "OLA_MAPS_API_KEY", "\"W1AuHz2nZpE209SCceS7rLGGzNSjpMqw6izkx25d\"")
            isMinifyEnabled = false
        }
        release {
            val baseUrl = project.findProperty("RIDEMATE_BASE_URL")?.toString()
                ?: "https://your-production-domain.com"
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            buildConfigField("String", "OLA_MAPS_API_KEY", "\"W1AuHz2nZpE209SCceS7rLGGzNSjpMqw6izkx25d\"")
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
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Core Android
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Socket.IO
    implementation("io.socket:socket.io-client:2.1.1")

    // MapLibre
    implementation("org.maplibre.gl:android-sdk:11.5.2")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // DataStore for persistent prefs
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
}
