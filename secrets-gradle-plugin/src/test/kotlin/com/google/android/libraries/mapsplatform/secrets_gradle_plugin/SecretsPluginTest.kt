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

import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.Variant
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SecretsPluginTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    lateinit var root: Project
    lateinit var project: Project
    lateinit var variant: Variant

    @Before
    fun setUp() {
        root = ProjectBuilder.builder()
            .withProjectDir(tempFolder.root)
            .withName("root")
            .build()
        project = ProjectBuilder.builder()
            .withProjectDir(tempFolder.root.resolve("project"))
            .withName("project")
            .withParent(root)
            .build()
        variant = mock {
            on { buildConfigFields } doReturn
                    project.objects.mapProperty(String::class.java, BuildConfigField::class.java)
            on { manifestPlaceholders } doReturn
                    project.objects.mapProperty(String::class.java, String::class.java)
        }
        project.pluginManager.apply("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
    }

    // TODO: This exception is no longer thrown since migrating to use onVariants API. Need to
    // reenable this test
    @Ignore
    @Test(expected = ProjectConfigurationException::class)
    fun `missing default properties fails`() {
        project.extensions.configure(SecretsPluginExtension::class.java) {
            it.propertiesFileName = "test.properties"
        }
        (project as ProjectInternal).evaluate()
    }

    @Test
    fun `properties are correctly ignored`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            key="someValue"
            ignoreKey="sadf"
            sdk.dir="di"
            sdk.foo="di"
        """.trimIndent()
        )
        val properties = project.rootProject.loadPropertiesFile(fileName)
        val ignoreList = listOf("ignoreKey", "sdk*")

        variant.inject(properties = properties, ignore = ignoreList)

        check(
            Pair("key", "someValue")
        )
        checkKeysNotIn("ignoreKey", "sdk.dir", "sdk.foo")
    }

    @Test
    fun `properties are correctly added`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            key1="someValue1"
            key2="someValue2"
        """.trimIndent()
        )
        val properties = project.rootProject.loadPropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        check(
            Pair("key1", "someValue1"),
            Pair("key2", "someValue2"),
        )
    }

    @Test
    fun `invalid characters are removed`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            sdk.Dir="value"
            sdk.Foo!="value2"
        """.trimIndent()
        )
        val properties = project.rootProject.loadPropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        check(
            Pair("sdkDir", "value"),
            Pair("sdkFoo", "value2")
        )
    }

    @Test
    fun `parenthesis is appended if needed`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            key1="value"
            key2=value2
        """.trimIndent()
        )
        val properties = project.rootProject.loadPropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        check(
            Pair("key1", "value"),
            Pair("key2", "value2")
        )
    }

    private fun check(vararg keyValues: Pair<String, String>) {
        keyValues.forEach { (key, value) ->
            assertEquals(value, variant.manifestPlaceholders.getting(key).get())
            variant.buildConfigFields.getting(key).get().let {
                assertEquals("String", it.type)
                assertEquals(value.addParenthesisIfNeeded(), it.value)
                assertEquals(null, it.comment)
            }
        }
    }

    private fun checkKeysNotIn(vararg keys: String) {
        keys.forEach {
            assertTrue(variant.manifestPlaceholders.getting(it).orNull == null)
        }
    }
}
