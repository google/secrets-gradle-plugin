package com.google.maps.android.api_key_provider

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.lang.IllegalStateException

/**
 * Plugin that reads the Maps API key from a properties file and injects a build variable in the
 * Android manifest file.
 */
class GmpApiKeyProvider : Plugin<Project> {

    private val extensionName = "gmpApiKeyProvider"
    private val manifestPlaceholderVarName = "gmpApiKey"

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            extensionName,
            GmpApiKeyProviderExtension::class.java
        )

        project.afterEvaluate {
            val properties = project.rootProject.loadPropertiesFile(extension.propertiesFileName)

            if (!properties.containsKey(extension.propertyKey)) {
                throw IllegalStateException(
                    "'${extension.propertyKey}' is not defined in '${extension.propertiesFileName}'"
                )
            }

            val mapsApiKey = properties.getProperty(extension.propertyKey)
            project.androidProject()?.applicationVariants?.all {
                // Inject manifest build variable
                it.mergedFlavor.manifestPlaceholders[manifestPlaceholderVarName] = mapsApiKey

                // Conditionally create a build config field
                if (extension.generateBuildConfigField) {
                    it.buildConfigField("String", extension.buildConfigFieldName, mapsApiKey)
                }
            }
        }
    }
}