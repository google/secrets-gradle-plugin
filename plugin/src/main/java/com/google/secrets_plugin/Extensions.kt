package com.google.secrets_plugin

import com.android.build.gradle.AppExtension
import org.gradle.api.Project
import java.io.FileNotFoundException
import java.util.Properties

fun Project.androidProject() : AppExtension? =
    extensions.findByType(AppExtension::class.java)

fun Project.loadPropertiesFile(fileName: String) : Properties {
    // Load file
    val propertiesFile = file(fileName)
    if (!propertiesFile.exists()) {
        throw FileNotFoundException(
            "The file '${propertiesFile.absolutePath}' could not be found"
        )
    }

    // Load contents into properties object
    val properties = Properties()
    properties.load(propertiesFile.inputStream())
    return properties
}

