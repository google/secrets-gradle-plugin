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

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.Variant
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.FileNotFoundException
import java.util.Properties

/**
 * Gradle plugin that reads secrets from `.properties` files not checked into version control
 * and injects them as `AndroidManifest.xml` placeholders and `BuildConfig` fields across
 * Android variants.
 *
 * Since property keys are turned into Java identifiers in `BuildConfig`, characters that are not
 * valid in Java variable names are stripped (for example, `"sdk.dir"` becomes `"sdkdir"`).
 */
class SecretsPlugin : Plugin<Project> {

    private val extensionName = "secrets"

    private val supportedPlugins = listOf(
        "com.android.application",
        "com.android.library",
        "com.android.test",
        "com.android.dynamic-feature"
    )

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            extensionName,
            SecretsPluginExtension::class.java
        )
        // Use pluginManager.withPlugin so that plugin declaration order in plugins {} never
        // causes silent failures (Issue #62), and support all Android module types including
        // baselineprofile / com.android.test modules (Issue #91).
        supportedPlugins.forEach { pluginId ->
            project.pluginManager.withPlugin(pluginId) {
                project.androidComponents()?.let { component ->
                    configureVariants(project, extension, component)
                }
            }
        }
    }

    internal fun configureVariants(
        project: Project,
        extension: SecretsPluginExtension,
        component: AndroidComponentsExtension<*, *, *>
    ) {
        component.onVariants { variant ->
            val defaultProperties = extension.defaultPropertiesFileName?.let {
                project.loadRootOrModulePropertiesFile(it)
            }

            val properties: Properties? = try {
                project.loadRootOrModulePropertiesFile(
                    extension.propertiesFileName
                )
            } catch (e: FileNotFoundException) {
                defaultProperties ?: throw e
            }
            val injectBuildConfig = project.isBuildConfigEnabled(component)
            generateConfigKey(
                project = project,
                extension = extension,
                defaultProperties = defaultProperties,
                properties = properties,
                variant = variant,
                injectBuildConfig = injectBuildConfig
            )
        }
    }

    private fun generateConfigKey(
        project: Project,
        extension: SecretsPluginExtension,
        defaultProperties: Properties?,
        properties: Properties?,
        variant: Variant,
        injectBuildConfig: Boolean
    ) {
        // 1. Inject defaults first (lowest precedence)
        defaultProperties?.let {
            variant.injectWithTestComponents(it, extension.ignoreList, injectBuildConfig)
        }

        // 2. Inject main properties file (overrides defaults)
        properties?.let {
            variant.injectWithTestComponents(it, extension.ignoreList, injectBuildConfig)
        }

        // 3. Inject build-type specific properties (e.g., debug.properties, release.properties)
        val buildType = variant.buildType
        if (!buildType.isNullOrEmpty()) {
            val buildTypeFileName = "$buildType.properties"
            val buildTypeProperties = try {
                project.loadRootOrModulePropertiesFile(buildTypeFileName)
            } catch (e: FileNotFoundException) {
                null
            }
            buildTypeProperties?.let {
                variant.injectWithTestComponents(it, extension.ignoreList, injectBuildConfig)
            }
        }

        // 4. Inject product-flavor specific properties (e.g., free.properties, paid.properties)
        val flavorName = variant.flavorName
        if (!flavorName.isNullOrEmpty()) {
            val flavorFileName = "$flavorName.properties"
            val flavorProperties = try {
                project.loadRootOrModulePropertiesFile(flavorFileName)
            } catch (e: FileNotFoundException) {
                null
            }
            flavorProperties?.let {
                variant.injectWithTestComponents(it, extension.ignoreList, injectBuildConfig)
            }
        }

        // 5. Inject combined variant-specific properties (e.g., freeDebug.properties) when the
        // variant name is distinct from both buildType and flavorName (Issue #59).
        val variantName = variant.name
        if (variantName.isNotEmpty() && variantName != buildType && variantName != flavorName) {
            val variantFileName = "$variantName.properties"
            val variantProperties = try {
                project.loadRootOrModulePropertiesFile(variantFileName)
            } catch (e: FileNotFoundException) {
                null
            }
            variantProperties?.let {
                variant.injectWithTestComponents(it, extension.ignoreList, injectBuildConfig)
            }
        }
    }

    /**
     * Injects secrets into both the main [Variant] and its nested [TestComponent]s (`UnitTest`
     * and `AndroidTest`) so test manifest merging resolves all `${...}` placeholders (Issue #41,
     * PR #107).
     */
    private fun Variant.injectWithTestComponents(
        properties: Properties,
        ignore: List<String>,
        injectBuildConfig: Boolean
    ) {
        inject(properties, ignore, injectBuildConfig)
        val testComponents = buildSet {
            addAll(nestedComponents.filterIsInstance<TestComponent>())
            (this@injectWithTestComponents as? HasUnitTest)?.unitTest?.let(::add)
            (this@injectWithTestComponents as? HasAndroidTest)?.androidTest?.let(::add)
        }
        testComponents.forEach { testComponent ->
            testComponent.inject(properties, ignore)
        }
    }
}
