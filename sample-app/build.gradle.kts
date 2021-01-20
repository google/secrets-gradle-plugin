import java.util.Properties
import java.io.FileNotFoundException
import kotlin.io.println

plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdkVersion(29)
    buildToolsVersion = "29.0.3"

    defaultConfig {
        applicationId = "me.chrisarriola.androidthings.myapplication"
        minSdkVersion(21)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        //manifestPlaceholders["mapsApiKey"] = "hehe"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(Libs.Kotlin.stdlib)
    implementation("androidx.core:core-ktx:1.2.0")
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.google.android.material:material:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    implementation("androidx.navigation:navigation-fragment-ktx:2.2.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.2.2")
}

open class MapsApiKeyProviderExtensionTest {
    /**
     * The name of the properties file containing the Maps API key.
     */
    var propertiesFile: String = defaultPropertiesFile

    /**
     * The name of the key in the properties file containing the Maps API key.
     */
    var propertyKey: String = "maps.apiKey"

    companion object {
        private const val defaultPropertiesFile = "local.properties"
    }
}

class MapsApiKeyProviderTest : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "mapsApiKeyProvider",
            MapsApiKeyProviderExtensionTest::class.java
        )

        project.afterEvaluate {
            // Try to load properties file
            val propertiesFileName = extension.propertiesFile
            val propertiesFile = project.rootProject.file(propertiesFileName)
            println("Properties file: ${propertiesFile.absolutePath}")
            if (!propertiesFile.exists()) {
                throw FileNotFoundException("The file '${propertiesFile.absolutePath}' cannot be found.")
            }

            val properties = Properties()
            properties.load(propertiesFile.inputStream())

            // Read API key
            println("Reading API Key")
            val apiKey = properties.getProperty(extension.propertyKey, "")
            val appExtension = project.extensions.findByType<com.android.build.gradle.AppExtension>()
            appExtension?.applicationVariants?.all {
                mergedFlavor.manifestPlaceholders["mapsApiKey"] = "hehe"
            }
        }
    }
}

apply<MapsApiKeyProviderTest>()
configure<MapsApiKeyProviderExtensionTest> {
    propertyKey = "MAPS_API_KEY"
    //propertiesFile = "locals.properties"
}
