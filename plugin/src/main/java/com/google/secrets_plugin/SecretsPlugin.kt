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

package com.google.secrets_plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin that reads secrets from a properties file and injects manifest build and BuildConfig
 * variables into an Android project. Since property keys are turned into Java variables,
 * invalid variable characters from the property key are removed.
 *
 * e.g.
 * A key defined as "sdk.dir" in the properties file will be converted to "sdkdir".
 */
class SecretsPlugin : Plugin<Project> {

    private val extensionName = "secrets"
    private val javaVarRegexp = Regex(pattern = "((?![a-zA-Z_\$0-9]).)")

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            extensionName,
            SecretsPluginExtension::class.java
        )

        project.afterEvaluate {
            project.androidProject()?.applicationVariants?.all { variant ->

                val properties = project.rootProject.loadPropertiesFile(
                    extension.propertiesFileName
                )

                val ignoreRegexs = extension.ignoreList.map { Regex(pattern = it) }

                properties.keys.map { key ->
                    key as String
                }.filter { key ->
                    key.isNotEmpty() && !ignoreRegexs.any { it.containsMatchIn(key) }
                }.forEach { key ->
                    val value = properties.getProperty(key)
                    val translatedKey = key.replace(javaVarRegexp, "")
                    variant.mergedFlavor.manifestPlaceholders[translatedKey] = value
                    variant.buildConfigField("String", translatedKey, value)
                }
            }
        }
    }
}