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

