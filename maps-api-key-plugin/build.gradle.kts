plugins {
    id("java-library")
    id("kotlin")
    id("java-gradle-plugin")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_7
    targetCompatibility = JavaVersion.VERSION_1_7
}

dependencies {
    compileOnly("com.android.tools.build:gradle:4.1.1")
    implementation(Libs.Kotlin.stdlib)
}

gradlePlugin {
    plugins {
        create("helloWorld") {
            id = "me.hello.world"
            implementationClass = "me.chrisarriola.androidthings.maps_api_key_plugin.MapsApiKeyProvider"
        }
    }
}