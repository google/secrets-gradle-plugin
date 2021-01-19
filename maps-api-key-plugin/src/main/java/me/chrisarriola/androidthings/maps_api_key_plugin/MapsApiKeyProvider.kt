package me.chrisarriola.androidthings.maps_api_key_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.FileNotFoundException
import java.util.*

/**
 * Plugin that reads the Maps API key from a properties file and injects a build variable in the
 * Android manifest file.
 */
class MapsApiKeyProvider : Plugin<Project> {

    private val extensionName = "mapsApiKeyProvider"

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            extensionName,
            MapsApiKeyProviderExtension::class.java
        )

        project.afterEvaluate {
            val properties = project.loadPropertiesFile(extension.propertiesFileName)
            val mapsApiKey = properties.getProperty(extension.propertyKey, "")
            project.androidProject()
                ?.defaultConfig
                ?.manifestPlaceholders
                ?.put(extension.manifestBuildVariable, mapsApiKey)
        }
    }
}