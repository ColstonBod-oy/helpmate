/*
 * Copyright 2023 Colston Bod-oy
 *
 * Copyright 2019 Jeremy Walker and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

