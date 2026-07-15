plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.rscoders.videoplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rscoders.videoplayer"
        minSdk = 21
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
}