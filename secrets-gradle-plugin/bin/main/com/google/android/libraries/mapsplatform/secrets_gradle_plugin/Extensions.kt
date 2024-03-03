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

@file:Suppress("UnstableApiUsage")

package com.google.android.libraries.mapsplatform.secrets_gradle_plugin

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.build.gradle.AppExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.core.InternalBaseVariant
import org.gradle.api.Project
import java.io.FileNotFoundException
import java.util.Properties

fun Project.androidAppComponent(): ApplicationAndroidComponentsExtension? =
    extensions.findByType(ApplicationAndroidComponentsExtension::class.java)

fun Project.androidLibraryComponent(): LibraryAndroidComponentsExtension? =
    extensions.findByType(LibraryAndroidComponentsExtension::class.java)

fun Project.androidProject(): AppExtension? =
    extensions.findByType(AppExtension::class.java)

fun Project.libraryProject(): LibraryExtension? =
    extensions.findByType(LibraryExtension::class.java)

fun Project.loadPropertiesFile(fileName: String): Properties {
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

private val javaVarRegexp = Regex(pattern = "((?![a-zA-Z_\$0-9]).)")

fun Variant.inject(properties: Properties, ignore: List<String>) {
    val ignoreRegexs = ignore.map { Regex(pattern = it) }
    properties.keys.map { key ->
        key as String
    }.filter { key ->
        key.isNotEmpty() && !ignoreRegexs.any { it.containsMatchIn(key) }
    }.forEach { key ->
        val value = properties.getProperty(key).removeSurrounding("\"")
        val translatedKey = key.replace(javaVarRegexp, "")
        buildConfigFields.put(
            translatedKey,
            BuildConfigField("String", value.addParenthesisIfNeeded(), null)
        )
        manifestPlaceholders.put(translatedKey, value)
    }
}

fun InternalBaseVariant.inject(properties: Properties, ignore: List<String>) {
    val ignoreRegexs = ignore.map { Regex(pattern = it) }
    properties.keys.map { key ->
        key as String
    }.filter { key ->
        key.isNotEmpty() && !ignoreRegexs.any { it.containsMatchIn(key) }
    }.forEach { key ->
        val value = properties.getProperty(key).removeSurrounding("\"")
        val translatedKey = key.replace(javaVarRegexp, "")
        buildConfigField("String", translatedKey, value.addParenthesisIfNeeded())
        mergedFlavor.manifestPlaceholders[translatedKey] = value
    }
}

fun String.addParenthesisIfNeeded(): String {
    if (isEmpty()) {
        return this
    }
    val charArray = this.toCharArray()
    if (length > 1 && charArray[0] == '"' && charArray[charArray.size - 1] == '"') {
        return this
    }
    return "\"$this\""
}
