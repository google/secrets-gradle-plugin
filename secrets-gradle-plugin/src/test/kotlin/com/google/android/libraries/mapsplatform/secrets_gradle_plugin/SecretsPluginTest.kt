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

import com.android.build.api.AndroidPluginVersion
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.UnitTest
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantSelector
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.FileNotFoundException

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
        val moduleDir = tempFolder.root.resolve("project").apply { mkdirs() }
        project = ProjectBuilder.builder()
            .withProjectDir(moduleDir)
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

    @Test(expected = FileNotFoundException::class)
    fun `missing properties file throws FileNotFoundException`() {
        project.loadRootOrModulePropertiesFile("nonexistent.properties")
    }

    @Test
    fun `module local properties file is loaded as fallback when root file is absent`() {
        val moduleFile = project.projectDir.resolve("module-secrets.properties")
        moduleFile.writeText("MODULE_KEY=moduleSecretValue\n")

        val properties = project.loadRootOrModulePropertiesFile("module-secrets.properties")
        assertEquals("moduleSecretValue", properties.getProperty("MODULE_KEY"))
    }

    @Test
    fun `extension ignoreList mutations do not leak across plugin instances`() {
        val ext1 = SecretsPluginExtension()
        ext1.ignoreList.add("customIgnore")

        val ext2 = SecretsPluginExtension()
        assertFalse(ext2.ignoreList.contains("customIgnore"))
        assertEquals(listOf("sdk.dir"), SecretsPluginExtension.defaultIgnoreList)
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
        val properties = project.loadRootOrModulePropertiesFile(fileName)
        val ignoreList = listOf("ignoreKey", "sdk*")

        variant.inject(properties = properties, ignore = ignoreList)

        check(
            Pair("key", "someValue")
        )
        checkKeysNotIn("ignoreKey", "sdk.dir", "sdk.foo")
    }

    @Test
    fun `test component receives manifest placeholders`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            MAPS_API_KEY="someValue"
            ignoreKey="sadf"
            """.trimIndent()
        )
        val properties = project.loadRootOrModulePropertiesFile(fileName)
        val testPlaceholders = project.objects.mapProperty(String::class.java, String::class.java)
        val testComponent = mock<TestComponent> {
            on { manifestPlaceholders } doReturn testPlaceholders
        }

        testComponent.inject(properties = properties, ignore = listOf("ignoreKey"))

        assertEquals("someValue", testPlaceholders.get()["MAPS_API_KEY"])
        assertFalse(testPlaceholders.get().containsKey("ignoreKey"))
    }

    @Test
    fun `buildConfigFields are not accessed when injectBuildConfig is false`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText("MAPS_API_KEY=manifestOnlyValue\n")
        val properties = project.loadRootOrModulePropertiesFile(fileName)

        val manifestMap = project.objects.mapProperty(String::class.java, String::class.java)
        val manifestOnlyVariant = mock<Variant> {
            on { manifestPlaceholders } doReturn manifestMap
        }

        manifestOnlyVariant.inject(
            properties = properties,
            ignore = emptyList(),
            injectBuildConfig = false
        )

        assertEquals("manifestOnlyValue", manifestMap.getting("MAPS_API_KEY").get())
        verify(manifestOnlyVariant, never()).buildConfigFields
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
        val properties = project.loadRootOrModulePropertiesFile(fileName)

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
        val properties = project.loadRootOrModulePropertiesFile(fileName)

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
        val properties = project.loadRootOrModulePropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        check(
            Pair("key1", "value"),
            Pair("key2", "value2")
        )
    }

    @Test
    fun `empty property value produces valid empty Java string literal`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            emptyUnquoted=
            emptyQuoted=""
            """.trimIndent()
        )
        val properties = project.loadRootOrModulePropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        assertEquals("", variant.manifestPlaceholders.getting("emptyUnquoted").get())
        assertEquals("\"\"", variant.buildConfigFields!!.getting("emptyUnquoted").get().value)
        assertEquals("", variant.manifestPlaceholders.getting("emptyQuoted").get())
        assertEquals("\"\"", variant.buildConfigFields!!.getting("emptyQuoted").get().value)
    }

    @Test
    fun `windows paths with backslashes are escaped for BuildConfig`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            flutter.sdk=C\:\\Users\\dev\\flutter
            """.trimIndent()
        )
        val properties = project.loadRootOrModulePropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        assertEquals("C:\\Users\\dev\\flutter", variant.manifestPlaceholders.getting("fluttersdk").get())
        assertEquals(
            "\"C:\\\\Users\\\\dev\\\\flutter\"",
            variant.buildConfigFields!!.getting("fluttersdk").get().value
        )
    }

    @Test
    fun `plugin applied before Android plugin still configures variants and test components`() {
        tempFolder.newFile("defaults.properties").writeText(
            """
            kDefault=fromDefault
            kOverride=fromDefault
            """.trimIndent()
        )
        tempFolder.newFile("local.properties").writeText(
            """
            kOverride=fromLocal
            kBuildType=fromLocal
            """.trimIndent()
        )
        tempFolder.newFile("debug.properties").writeText(
            """
            kBuildType=fromDebug
            kFlavor=fromDebug
            """.trimIndent()
        )
        tempFolder.newFile("demo.properties").writeText(
            """
            kFlavor=fromDemo
            kVariant=fromDemo
            """.trimIndent()
        )
        tempFolder.newFile("demoDebug.properties").writeText(
            """
            kVariant=fromDemoDebug
            """.trimIndent()
        )

        // Apply SecretsPlugin BEFORE applying an Android plugin (Issue #62).
        project.pluginManager.apply(SecretsPlugin::class.java)
        val extension = project.extensions.getByType(SecretsPluginExtension::class.java)
        extension.defaultPropertiesFileName = "defaults.properties"

        // Register mock androidComponents and android extensions and apply dummy com.android.test plugin (#91).
        val selector = mock<VariantSelector>()
        whenever(selector.all()).thenReturn(selector)
        val androidComponents = mock<AndroidComponentsExtension<*, *, Variant>> {
            on { selector() } doReturn selector
            on { pluginVersion } doReturn AndroidPluginVersion(9, 0)
        }
        val buildFeaturesMock = mock<BuildFeatures> {
            on { buildConfig } doReturn true
        }
        val commonExtension = mock<CommonExtension<*, *, *, *, *, *>> {
            on { buildFeatures } doReturn buildFeaturesMock
        }
        project.extensions.add(AndroidComponentsExtension::class.java, "androidComponents", androidComponents)
        project.extensions.add(CommonExtension::class.java, "android", commonExtension)

        // Configure variants via SecretsPlugin (real plugin ordering and com.android.test are verified in SecretsPluginFunctionalTest).
        SecretsPlugin().configureVariants(project, extension, androidComponents)

        val callbackCaptor = argumentCaptor<(Variant) -> Unit>()
        verify(androidComponents).onVariants(anyOrNull(), callbackCaptor.capture())

        val unitTestComponent = mock<UnitTest> {
            on { manifestPlaceholders } doReturn
                project.objects.mapProperty(String::class.java, String::class.java)
        }
        val androidTestComponent = mock<AndroidTest> {
            on { manifestPlaceholders } doReturn
                project.objects.mapProperty(String::class.java, String::class.java)
        }
        val fullVariant = mock<ApplicationVariant> {
            on { name } doReturn "demoDebug"
            on { buildType } doReturn "debug"
            on { flavorName } doReturn "demo"
            on { buildConfigFields } doReturn
                project.objects.mapProperty(String::class.java, BuildConfigField::class.java)
            on { manifestPlaceholders } doReturn
                project.objects.mapProperty(String::class.java, String::class.java)
            on { nestedComponents } doReturn listOf(unitTestComponent, androidTestComponent)
            on { androidTest } doReturn androidTestComponent
        }

        callbackCaptor.firstValue.invoke(fullVariant)

        // Verify precedence: defaults < local < buildType < flavor < variantName (#59)
        assertEquals("fromDefault", fullVariant.manifestPlaceholders.getting("kDefault").get())
        assertEquals("fromLocal", fullVariant.manifestPlaceholders.getting("kOverride").get())
        assertEquals("fromDebug", fullVariant.manifestPlaceholders.getting("kBuildType").get())
        assertEquals("fromDemo", fullVariant.manifestPlaceholders.getting("kFlavor").get())
        assertEquals("fromDemoDebug", fullVariant.manifestPlaceholders.getting("kVariant").get())

        // Verify both unitTest and androidTest components received manifest placeholders (#41, #89, #96, #104)
        assertEquals("fromDemoDebug", unitTestComponent.manifestPlaceholders.getting("kVariant").get())
        assertEquals("fromDemoDebug", androidTestComponent.manifestPlaceholders.getting("kVariant").get())
    }

    private fun check(vararg keyValues: Pair<String, String>) {
        keyValues.forEach { (key, value) ->
            assertEquals(value, variant.manifestPlaceholders.getting(key).get())
            variant.buildConfigFields!!.getting(key).get().let {
                assertEquals("String", it.type)
                assertEquals(value.addParenthesisIfNeeded(), it.value)
                assertNull(it.comment)
            }
        }
    }

    private fun checkKeysNotIn(vararg keys: String) {
        keys.forEach {
            assertTrue(variant.manifestPlaceholders.getting(it).orNull == null)
        }
    }
}
