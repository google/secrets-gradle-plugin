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

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.Variant
import org.gradle.api.Project
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.util.Properties

/**
 * Looks up the generic [AndroidComponentsExtension] registered by any Android Gradle Plugin
 * module type (`com.android.application`, `com.android.library`, `com.android.test`, or
 * `com.android.dynamic-feature`).
 */
fun Project.androidComponents(): AndroidComponentsExtension<*, *, *>? =
    extensions.findByType(AndroidComponentsExtension::class.java)

/**
 * Retained for binary and source backward compatibility with callers inspecting application
 * components directly.
 */
fun Project.androidAppComponent(): ApplicationAndroidComponentsExtension? =
    extensions.findByType(ApplicationAndroidComponentsExtension::class.java)

/**
 * Retained for binary and source backward compatibility with callers inspecting library
 * components directly.
 */
fun Project.androidLibraryComponent(): LibraryAndroidComponentsExtension? =
    extensions.findByType(LibraryAndroidComponentsExtension::class.java)

/**
 * Determines whether `BuildConfig` generation is enabled for this Android module without
 * triggering AGP's `warnAboutAccessingVariantApiValueForDisabledFeature` warning.
 *
 * Resolution precedence:
 * 1. Explicit module DSL setting (`android.buildFeatures.buildConfig`).
 * 2. Project-wide Gradle property (`android.defaults.buildfeatures.buildconfig`).
 * 3. AGP version default (`true` on AGP < 8.0, `false` on AGP 8.0+).
 */
internal fun Project.isBuildConfigEnabled(
    androidComponents: AndroidComponentsExtension<*, *, *>
): Boolean {
    val dslSetting = extensions.findByType(CommonExtension::class.java)
        ?.buildFeatures
        ?.buildConfig
    if (dslSetting != null) {
        return dslSetting
    }
    val gradleProperty = providers.gradleProperty("android.defaults.buildfeatures.buildconfig")
        .orNull
        ?.toBooleanStrictOrNull()
    if (gradleProperty != null) {
        return gradleProperty
    }
    return androidComponents.pluginVersion < AndroidPluginVersion(8, 0)
}

/**
 * Loads a Java [Properties] file resolved relative to this [Project]'s directory.
 *
 * @throws FileNotFoundException if the file does not exist.
 */
fun Project.loadPropertiesFile(fileName: String): Properties {
    val propertiesFile = file(fileName)
    if (!propertiesFile.exists()) {
        throw FileNotFoundException(
            "The file '${propertiesFile.absolutePath}' could not be found"
        )
    }
    return readPropertiesFile(propertiesFile)
}

/**
 * Loads a Java [Properties] file in a **Gradle Isolated Projects** and **Configuration Cache**
 * compatible manner.
 *
 * Rather than calling `project.rootProject.file(fileName)`—which crosses mutable project boundaries
 * and violates Gradle Isolated Projects (`-Dorg.gradle.unsafe.isolated-projects=true`)—this method
 * resolves the path against [Project.getRootDir] first, falling back to [Project.getProjectDir] so
 * module-local secret files are also supported.
 *
 * @throws FileNotFoundException if the file does not exist in either the root project directory
 *   or the module project directory.
 */
internal fun Project.loadRootOrModulePropertiesFile(fileName: String): Properties {
    val rootFile = rootDir.resolve(fileName)
    readOptionalPropertiesFile(rootFile)?.let { return it }

    val moduleFile = projectDir.resolve(fileName)
    if (moduleFile != rootFile) {
        readOptionalPropertiesFile(moduleFile)?.let { return it }
    }

    throw FileNotFoundException(
        "The file '${rootFile.absolutePath}' could not be found"
    )
}

private fun Project.readOptionalPropertiesFile(targetFile: java.io.File): Properties? {
    // Query ProviderFactory.fileContents first so Gradle's Configuration Cache tracks both
    // file content when present and file absence when missing.
    val bytes = providers.fileContents(layout.file(provider { targetFile })).asBytes.orNull
    if (bytes != null) {
        return Properties().apply {
            ByteArrayInputStream(bytes).use(::load)
        }
    }
    if (!targetFile.exists()) {
        return null
    }
    return Properties().apply {
        targetFile.inputStream().use(::load)
    }
}

private fun Project.readPropertiesFile(targetFile: java.io.File): Properties {
    return readOptionalPropertiesFile(targetFile)
        ?: throw FileNotFoundException(
            "The file '${targetFile.absolutePath}' could not be found"
        )
}

private val javaVarRegexp = Regex(pattern = "((?![a-zA-Z_\$0-9]).)")

private inline fun forEachTranslatedProperty(
    properties: Properties,
    ignore: List<String>,
    action: (translatedKey: String, value: String) -> Unit
) {
    val ignoreRegexs = ignore.map { Regex(pattern = it) }
    properties.keys.map { key ->
        key as String
    }.filter { key ->
        key.isNotEmpty() && !ignoreRegexs.any { it.containsMatchIn(key) }
    }.forEach { key ->
        val value = properties.getProperty(key).removeSurrounding("\"")
        val translatedKey = key.replace(javaVarRegexp, "")
        if (translatedKey.isNotEmpty()) {
            action(translatedKey, value)
        }
    }
}

/**
 * Injects non-ignored entries from [properties] into this [Variant]'s `manifestPlaceholders`
 * and (when [injectBuildConfig] is `true`) `buildConfigFields`.
 */
@JvmOverloads
fun Variant.inject(
    properties: Properties,
    ignore: List<String>,
    injectBuildConfig: Boolean = true
) {
    forEachTranslatedProperty(properties, ignore) { translatedKey, value ->
        if (injectBuildConfig && !translatedKey[0].isDigit()) {
            buildConfigFields?.put(
                translatedKey,
                BuildConfigField("String", value.addParenthesisIfNeeded(), null)
            )
        }
        manifestPlaceholders.put(translatedKey, value)
    }
}

/**
 * Injects non-ignored entries from [properties] into a [TestComponent]'s `manifestPlaceholders`.
 *
 * Test components (`UnitTest` and `AndroidTest`) merge the same `AndroidManifest.xml` as the main
 * variant (which may reference `${MAPS_API_KEY}`), and AGP 9.1+ strictly validates that all
 * placeholders in merged test manifests are resolved. Test components do not expose `buildConfigFields`.
 */
fun TestComponent.inject(properties: Properties, ignore: List<String>) {
    forEachTranslatedProperty(properties, ignore) { translatedKey, value ->
        manifestPlaceholders.put(translatedKey, value)
    }
}

/**
 * Formats a property string value as a valid Java `String` literal for `BuildConfig.java`.
 *
 * - An empty string (`""`) becomes `"\"\""` so `BuildConfig.java` compiles to `= "";` instead
 *   of the invalid `= ;`.
 * - A string already enclosed in double quotes is preserved as-is.
 * - Unquoted strings have backslashes, double quotes, and control characters (`\n`, `\r`, `\t`)
 *   escaped before wrapping in `"..."`, preventing Windows file paths (such as `C:\Users\...`)
 *   or multi-line values from causing compiler errors in `BuildConfig.java`.
 */
fun String.addParenthesisIfNeeded(): String {
    if (isEmpty()) {
        return "\"\""
    }
    val charArray = this.toCharArray()
    if (length > 1 && charArray[0] == '"' && charArray[charArray.size - 1] == '"') {
        return this
    }
    val escaped = this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}
