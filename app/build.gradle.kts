plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 19
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    flavorDimensions.add("mode")

    productFlavors {
        create("manual") {
            dimension = "mode"
            applicationId = "com.colston.helpmate"
        }
        create("automatic") {
            dimension = "mode"
            applicationId = "com.colston.helpmate"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    namespace = "com.colston.helpmate"
}

dependencies {
    implementation("com.google.android.gms:play-services-nearby:18.5.0")

    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.material:material:1.12.0")
}

