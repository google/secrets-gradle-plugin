plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.12.0"
    id("kotlin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    compileOnly("com.android.tools.build:gradle:4.1.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.10")
    testImplementation("junit:junit:4.13.1")
}

gradlePlugin {
    plugins {
        create(PluginInfo.name) {
            id = PluginInfo.id
            implementationClass = PluginInfo.implementationClass
        }
    }
}

pluginBundle {
    website = "https://github.com/googlemaps"
    vcsUrl = "https://github.com/googlemaps"
    description = "A thing"
    version = "0.1"

    (plugins) {
        PluginInfo.name {
            displayName = "GMP API Key Provider"
            tags = listOf("kotlin")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenPub") {
            group = "com.google.maps.android"
            artifactId = "api_key_provider"
            version = "0.1"
        }
    }
    repositories {
        maven(url = "build/repository")
    }
}

object PluginInfo {
    const val id = "com.google.maps.android.api_key_provider"
    const val name = "gmpApiKeyProvider"
    const val implementationClass = "com.google.maps.android.api_key_provider.GmpApiKeyProvider"
}