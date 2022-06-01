// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

plugins {
    id("com.android.application")
    id("kotlin-android")

    // 1. Include the plugin
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    compileSdk = 32
    buildToolsVersion = "30.0.3"

    defaultConfig {
        applicationId = "com.google.secrets_plugin.sample"
        minSdk = 21
        targetSdk = 32
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.21")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.appcompat:appcompat:1.4.2")
}

// 2. Optionally configure the plugin
secrets {
    // Optionally specify a different file name containing your secrets.
    // The plugin defaults to "local.properties"
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "secrets.defaults.properties"

    // Add keys that the plugin should ignore from the properties file
    ignoreList.add("keyToIgnore")
    ignoreList.add("ignore*")
}
