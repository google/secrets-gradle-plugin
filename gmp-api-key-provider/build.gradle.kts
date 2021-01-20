plugins {
    id("java-library")
    id("kotlin")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish") version "0.12.0"
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly("com.android.tools.build:gradle:4.1.1")
    implementation(Libs.Kotlin.stdlib)
}

gradlePlugin {
    plugins {
        create("gmpApiKeyProvider") {
            id = "com.google.maps.android.api_key_provider"
            implementationClass = "com.google.maps.android.api_key_provider.GmpApiKeyProvider"
        }
    }
}

pluginBundle {
    website = "https://github.com/googlemaps"
    vcsUrl = "https://github.com/googlemaps"
    description = "A thing"
    version = "0.1"

    (plugins) {
        "gmpApiKeyProvider" {
            displayName = "GMP API Key Provider"
            tags = listOf("kotlin")
        }
    }
}