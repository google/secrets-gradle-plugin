package com.google.maps.android.api_key_provider

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin that reads the Maps API key from a properties file and injects a build variable in the
 * Android manifest file.
 */
class GmpApiKeyProvider : Plugin<Project> {

    private val extensionName = "mapsApiKeyProvider"

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            extensionName,
            GmpApiKeyProviderExtension::class.java
        )

        project.afterEvaluate {
            val properties = project.loadPropertiesFile(extension.propertiesFileName)
            val mapsApiKey = properties.getProperty(extension.propertyKey, "")
            project.androidProject()?.applicationVariants?.all {
                it.mergedFlavor.manifestPlaceholders["gmpApiKey"] = mapsApiKey
            }
        }
    }
}