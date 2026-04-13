plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.mouthguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.mouthguard"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "adMode"
    productFlavors {
        create("adSupported") {
            dimension = "adMode"
            applicationIdSuffix = ".ad"
        }
        create("adFree") {
            dimension = "adMode"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // ML Kit Face Detection
    implementation(libs.mlkit.face)

    // Coroutines
    implementation(libs.coroutines.android)

    // AdMob (adSupported flavor only)
    "adSupportedImplementation"(libs.play.services.ads)

    // Guava ListenableFuture (AdMob pulls a conflicting version)
    implementation("com.google.guava:guava:32.1.3-android")
}
