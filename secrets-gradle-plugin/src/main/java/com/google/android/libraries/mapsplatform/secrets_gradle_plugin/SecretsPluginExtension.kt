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

package com.google.android.libraries.mapsplatform.secrets_gradle_plugin

/**
 * Gradle DSL configuration extension for [SecretsPlugin].
 *
 * Consumers configure this block inside their module's `build.gradle` or `build.gradle.kts`:
 * ```kotlin
 * secrets {
 *     propertiesFileName = "secrets.properties"
 *     defaultPropertiesFileName = "secrets.defaults.properties"
 *     ignoreList.add("keyToIgnore")
 * }
 * ```
 */
open class SecretsPluginExtension {
    /**
     * The name (or relative path) of the properties file containing secrets.
     * Defaults to [defaultPropertiesFile] (`"local.properties"`).
     */
    var propertiesFileName: String = defaultPropertiesFile

    /**
     * A list of regular-expression patterns for keys that this plugin should ignore and not inject.
     *
     * Initialized as a defensive copy of [defaultIgnoreList] so mutations in one Gradle module
     * never leak into other modules sharing the same Gradle daemon JVM.
     */
    var ignoreList: MutableList<String> = defaultIgnoreList.toMutableList()

    /**
     * The optional name (or relative path) of a fallback properties file containing safe default
     * values (suitable for checking into version control for CI/CD builds).
     */
    var defaultPropertiesFileName: String? = null

    companion object {
        const val defaultPropertiesFile = "local.properties"
        val defaultIgnoreList = mutableListOf("sdk.dir")
    }
}
