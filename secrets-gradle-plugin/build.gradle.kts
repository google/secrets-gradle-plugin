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

import org.gradle.api.attributes.plugin.GradlePluginApiVersion
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `java-gradle-plugin`
    `maven-publish`
    alias(libs.plugins.kotlin.jvm)
}

group = PluginInfo.group
version = PluginInfo.version

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
    }
}

val agpTestRuntime: Configuration = configurations.create("agpTestRuntime")

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(agpTestRuntime)
}

// Declare compatibility with Gradle 8.0+ in published Gradle Module Metadata so that
// consumers on Gradle 8.x are not forced to upgrade to Gradle 9.x to resolve the plugin.
configurations.matching { it.name == "apiElements" || it.name == "runtimeElements" }.configureEach {
    attributes {
        attribute(
            GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE,
            objects.named(GradlePluginApiVersion::class.java, "8.0")
        )
    }
}

dependencies {
    compileOnly(libs.agp.api)
    compileOnly(libs.kotlin.stdlib)
    agpTestRuntime(libs.android.gradle.plugin)
    testImplementation(libs.agp.api)
    testImplementation(libs.kotlin.stdlib)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.byte.buddy)
    testImplementation(libs.byte.buddy.agent)
}

gradlePlugin {
    plugins {
        create(PluginInfo.name) {
            id = PluginInfo.group
            implementationClass = PluginInfo.implementationClass
            displayName = PluginInfo.displayName
            description = PluginInfo.description
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(PluginInfo.displayName)
            description.set(PluginInfo.description)
            url.set(PluginInfo.url)

            scm {
                connection.set("scm:git@github.com:google/secrets-gradle-plugin.git")
                developerConnection.set("scm:git@github.com:google/secrets-gradle-plugin.git")
                url.set(PluginInfo.url)
            }

            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }

            organization {
                name.set("Google Inc.")
                url.set("https://developers.google.com/maps")
            }

            developers {
                developer {
                    name.set("Google Inc.")
                }
            }
        }
    }
    repositories {
        maven {
            name = "localPluginRepository"
            url = uri(layout.buildDirectory.dir("repository"))
        }
        mavenLocal()
    }
}

object PluginInfo {
    const val artifactId =
        "com.google.android.libraries.mapsplatform.secrets-gradle-plugin.gradle.plugin"
    const val description = "A Gradle plugin for providing secrets securely to an Android project."
    const val displayName = "Secrets Gradle Plugin for Android"
    const val group = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin"
    const val implementationClass =
        "com.google.android.libraries.mapsplatform.secrets_gradle_plugin.SecretsPlugin"
    const val name = "secretsGradlePlugin"
    const val url = "https://github.com/google/secrets-gradle-plugin"
    const val version = "2.0.2"
}
