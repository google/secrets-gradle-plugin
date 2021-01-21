package com.google.maps.android.api_key_provider

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.lang.IllegalStateException
import java.nio.charset.CharacterCodingException

/**
 * Plugin that reads the Maps API key from a properties file and injects a build variable in the
 * Android manifest file.
 */
class GmpApiKeyProvider : Plugin<Project> {

    private val extensionName = "gmpApiKeyProvider"
    private val javaVarRegexp = Regex(pattern = "((?![a-zA-Z_\$0-9]).)")

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            extensionName,
            GmpApiKeyProviderExtension::class.java
        )

        project.afterEvaluate {
            project.androidProject()?.applicationVariants?.all { variant ->

                val properties = project.rootProject.loadPropertiesFile(
                    extension.propertiesFileName
                )

                properties.keys.map { key ->
                    key as String
                }.filter { key ->
                    key.isNotEmpty() && !extension.ignoreList.contains(key)
                }.forEach { key ->
                    val value = properties.getProperty(key)
                    val translatedKey = key.replace(javaVarRegexp, "")
                    println("Key: '$key 'translatedKey: '$translatedKey'")
                    variant.mergedFlavor.manifestPlaceholders[translatedKey] = value
                    variant.buildConfigField("String", translatedKey, value)
                }
            }
        }
    }
}