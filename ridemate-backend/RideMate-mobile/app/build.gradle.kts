import org.gradle.api.GradleException

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.rydius.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rydius.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val baseUrl =
                project.findProperty("RIDEMATE_BASE_URL")?.toString() ?: "http://10.0.2.2:3000"
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
            isMinifyEnabled = false
        }

        release {
            val baseUrl = project.findProperty("RIDEMATE_BASE_URL")?.toString()
                ?: "https://your-production-domain.com"
            if (baseUrl.contains("your-production-domain.com")) {
                throw GradleException("RIDEMATE_BASE_URL must be set to your production HTTPS URL for release builds.")
            }
            if (!baseUrl.startsWith("https://")) {
                throw GradleException("Release RIDEMATE_BASE_URL must use https://")
            }
            buildConfigField("String", "BASE_URL", "\"$baseUrl\"")
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
