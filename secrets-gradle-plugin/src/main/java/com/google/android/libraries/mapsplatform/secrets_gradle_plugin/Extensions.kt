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
import java.io.File
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
 * 2. Project-wide Gradle property (`android.defaults.buildfeatures.buildconfig`) on AGP < 9.0
 *    (AGP 9.0+ removed the global default flag).
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
    if (androidComponents.pluginVersion < AndroidPluginVersion(9, 0)) {
        val gradleProperty = providers.gradleProperty("android.defaults.buildfeatures.buildconfig")
            .orNull
            ?.trim()
            ?.lowercase()
            ?.toBooleanStrictOrNull()
        if (gradleProperty != null) {
            return gradleProperty
        }
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
    return readPropertiesFile(propertiesFile)
}

/**
 * Loads a Java [Properties] file in a **Gradle Isolated Projects** and **Configuration Cache**
 * compatible manner.
 *
 * Rather than calling `project.rootProject.file(fileName)`—which crosses mutable project boundaries
 * and violates Gradle Isolated Projects (`-Dorg.gradle.unsafe.isolated-projects=true`)—this method
 * resolves the path against [Project.getRootDir] and [Project.getProjectDir]. If the file exists in
 * both the root project directory and the module directory (for example, when root `local.properties`
 * holds `sdk.dir` and module `local.properties` holds module-specific secrets), module-level entries
 * are merged on top of root-level entries.
 *
 * @throws FileNotFoundException if the file does not exist in either the root project directory
 *   or the module project directory.
 */
internal fun Project.loadRootOrModulePropertiesFile(fileName: String): Properties {
    val rootFile = rootDir.resolve(fileName)
    val rootProperties = readOptionalPropertiesFile(rootFile)

    val moduleFile = projectDir.resolve(fileName)
    val moduleProperties = if (moduleFile != rootFile) {
        readOptionalPropertiesFile(moduleFile)
    } else {
        null
    }

    if (rootProperties == null && moduleProperties == null) {
        throw FileNotFoundException(
            "The file '${rootFile.absolutePath}' could not be found"
        )
    }

    return Properties().apply {
        rootProperties?.let(::putAll)
        moduleProperties?.let(::putAll)
    }
}

private fun Project.readOptionalPropertiesFile(targetFile: File): Properties? {
    // Query ProviderFactory.fileContents so Gradle's Configuration Cache tracks both
    // file content when present and file absence when missing.
    val bytes = providers.fileContents(layout.file(provider { targetFile })).asBytes.orNull
        ?: return null
    return Properties().apply {
        ByteArrayInputStream(bytes).use(::load)
    }
}

private fun Project.readPropertiesFile(targetFile: File): Properties {
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
    }.sorted().filter { key ->
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
 * - An empty string (`""`) or empty quoted string (`"\"\""`) becomes `"\"\""` so `BuildConfig.java`
 *   compiles to `= "";` instead of the invalid `= ;`.
 * - Outer surrounding quotes (`"..."`) are stripped before escaping so that both quoted and unquoted
 *   inputs containing backslashes, internal double quotes, or control characters (`\n`, `\r`, `\t`)
 *   are consistently escaped into valid Java string literals.
 */
fun String.addParenthesisIfNeeded(): String {
    val raw = removeSurrounding("\"")
    if (raw.isEmpty()) {
        return "\"\""
    }
    val escaped = raw
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    return "\"$escaped\""
}
