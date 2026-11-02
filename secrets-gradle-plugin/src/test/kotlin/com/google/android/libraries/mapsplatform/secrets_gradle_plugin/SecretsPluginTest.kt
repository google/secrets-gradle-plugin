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
import com.android.build.api.component.UnitTest as ComponentUnitTest
import com.android.build.api.dsl.BuildFeatures
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.AndroidTest
import com.android.build.api.variant.ApplicationVariant
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.HasAndroidTest
import com.android.build.api.variant.HasUnitTest
import com.android.build.api.variant.TestComponent
import com.android.build.api.variant.UnitTest
import com.android.build.api.variant.Variant
import com.android.build.api.variant.VariantSelector
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
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
    fun `module local properties file merges over root properties file when both exist`() {
        tempFolder.newFile("local.properties").writeText(
            """
            sdk.dir=/android/sdk
            SHARED_KEY=fromRoot
            ROOT_ONLY_KEY=rootValue
            """.trimIndent()
        )
        project.projectDir.resolve("local.properties").writeText(
            """
            SHARED_KEY=fromModule
            MODULE_ONLY_KEY=moduleValue
            """.trimIndent()
        )

        val properties = project.loadRootOrModulePropertiesFile("local.properties")
        assertEquals("rootValue", properties.getProperty("ROOT_ONLY_KEY"))
        assertEquals("fromModule", properties.getProperty("SHARED_KEY"))
        assertEquals("moduleValue", properties.getProperty("MODULE_ONLY_KEY"))
    }

    @Test(expected = FileNotFoundException::class)
    fun `directory with same name as properties file is ignored and throws FileNotFoundException`() {
        tempFolder.newFolder("directory.properties")
        project.loadRootOrModulePropertiesFile("directory.properties")
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
    fun `isBuildConfigEnabled respects DSL, gradle property on AGP 8, and version defaults on AGP 7, 8, and 9`() {
        val agp7 = mock<AndroidComponentsExtension<*, *, *>> {
            on { pluginVersion } doReturn AndroidPluginVersion(7, 4)
        }
        val agp8 = mock<AndroidComponentsExtension<*, *, *>> {
            on { pluginVersion } doReturn AndroidPluginVersion(8, 2)
        }
        val agp9 = mock<AndroidComponentsExtension<*, *, *>> {
            on { pluginVersion } doReturn AndroidPluginVersion(9, 0)
        }

        // 1. Default when neither DSL nor gradle.properties is set: true on AGP 7.x, false on AGP 8.x/9.x
        assertTrue(project.isBuildConfigEnabled(agp7))
        assertFalse(project.isBuildConfigEnabled(agp8))
        assertFalse(project.isBuildConfigEnabled(agp9))

        // 2. Explicit DSL setting overrides version defaults
        val buildFeatures = mock<BuildFeatures> {
            on { buildConfig } doReturn true
        }
        val commonExt = mock<CommonExtension<*, *, *, *, *, *>> {
            on { this.buildFeatures } doReturn buildFeatures
        }
        project.extensions.add(CommonExtension::class.java, "android", commonExt)
        assertTrue(project.isBuildConfigEnabled(agp8))
        assertTrue(project.isBuildConfigEnabled(agp9))

        whenever(buildFeatures.buildConfig).thenReturn(false)
        assertFalse(project.isBuildConfigEnabled(agp7))
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
    fun `invalid characters are removed and leading digit keys are excluded from BuildConfig only`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            sdk.Dir="value"
            sdk.Foo!="value2"
            123numericKey="numericValue"
            """.trimIndent()
        )
        val properties = project.loadRootOrModulePropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        check(
            Pair("sdkDir", "value"),
            Pair("sdkFoo", "value2")
        )
        assertEquals("numericValue", variant.manifestPlaceholders.getting("123numericKey").get())
        assertNull(variant.buildConfigFields!!.getting("123numericKey").orNull)
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
    fun `windows paths and strings with internal quotes are escaped for BuildConfig`() {
        val fileName = "local.properties"
        val propertiesFile = tempFolder.newFile(fileName)
        propertiesFile.writeText(
            """
            flutter.sdk=C\:\\Users\\dev\\flutter
            quoted.sdk="C\:\\Users\\dev\\flutter"
            json.config={"env":"prod","debug":false}
            """.trimIndent()
        )
        val properties = project.loadRootOrModulePropertiesFile(fileName)

        variant.inject(properties = properties, ignore = emptyList())

        assertEquals("C:\\Users\\dev\\flutter", variant.manifestPlaceholders.getting("fluttersdk").get())
        assertEquals(
            "\"C:\\\\Users\\\\dev\\\\flutter\"",
            variant.buildConfigFields!!.getting("fluttersdk").get().value
        )
        assertEquals("C:\\Users\\dev\\flutter", variant.manifestPlaceholders.getting("quotedsdk").get())
        assertEquals(
            "\"C:\\\\Users\\\\dev\\\\flutter\"",
            variant.buildConfigFields!!.getting("quotedsdk").get().value
        )
        assertEquals("{\"env\":\"prod\",\"debug\":false}", variant.manifestPlaceholders.getting("jsonconfig").get())
        assertEquals(
            "\"{\\\"env\\\":\\\"prod\\\",\\\"debug\\\":false}\"",
            variant.buildConfigFields!!.getting("jsonconfig").get().value
        )
        // Also verify direct invocation on a quoted string containing unescaped backslashes
        assertEquals(
            "\"C:\\\\Users\\\\dev\"",
            "\"C:\\Users\\dev\"".addParenthesisIfNeeded()
        )
    }

    @Test
    fun `plugin applied before Android plugin configures multi-dimension flavors and falls back when nestedComponents throws NoSuchMethodError`() {
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
            kDimensionTier=fromDebug
            """.trimIndent()
        )
        tempFolder.newFile("demo.properties").writeText(
            """
            kDimensionTier=fromDemo
            kDimensionEnv=fromDemo
            """.trimIndent()
        )
        tempFolder.newFile("staging.properties").writeText(
            """
            kDimensionEnv=fromStaging
            kCombinedFlavor=fromStaging
            """.trimIndent()
        )
        tempFolder.newFile("demoStaging.properties").writeText(
            """
            kCombinedFlavor=fromDemoStaging
            kVariant=fromDemoStaging
            """.trimIndent()
        )
        tempFolder.newFile("demoStagingDebug.properties").writeText(
            """
            kVariant=fromDemoStagingDebug
            """.trimIndent()
        )

        val extension = project.extensions.getByType(SecretsPluginExtension::class.java)
        extension.defaultPropertiesFileName = "defaults.properties"

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

        SecretsPlugin().configureVariants(project, extension, androidComponents)

        val callbackCaptor = argumentCaptor<(Variant) -> Unit>()
        verify(androidComponents).onVariants(anyOrNull(), callbackCaptor.capture())

        @Suppress("DEPRECATION")
        val unitTestComponent = mock<ComponentUnitTest> {
            on { manifestPlaceholders } doReturn
                project.objects.mapProperty(String::class.java, String::class.java)
        }
        val androidTestComponent = mock<AndroidTest> {
            on { manifestPlaceholders } doReturn
                project.objects.mapProperty(String::class.java, String::class.java)
        }

        // Simulate an AGP 8.0-8.2 Variant where getNestedComponents() does not exist and throws
        // NoSuchMethodError (a LinkageError), verifying fallback to HasUnitTest / HasAndroidTest.
        val legacyVariant = mock<ApplicationVariant> {
            on { name } doReturn "demoStagingDebug"
            on { buildType } doReturn "debug"
            on { productFlavors } doReturn listOf("tier" to "demo", "env" to "staging")
            on { flavorName } doReturn "demoStaging"
            on { buildConfigFields } doReturn
                project.objects.mapProperty(String::class.java, BuildConfigField::class.java)
            on { manifestPlaceholders } doReturn
                project.objects.mapProperty(String::class.java, String::class.java)
            on { nestedComponents } doAnswer {
                throw NoSuchMethodError("com.android.build.api.variant.Variant.getNestedComponents()")
            }
            on { androidTest } doReturn androidTestComponent
        }
        whenever((legacyVariant as HasUnitTest).unitTest).thenReturn(unitTestComponent)

        callbackCaptor.firstValue.invoke(legacyVariant)

        // Verify precedence: defaults < local < buildType < productFlavors (tier, env) < combined flavorName < variantName
        assertEquals("fromDefault", legacyVariant.manifestPlaceholders.getting("kDefault").get())
        assertEquals("fromLocal", legacyVariant.manifestPlaceholders.getting("kOverride").get())
        assertEquals("fromDebug", legacyVariant.manifestPlaceholders.getting("kBuildType").get())
        assertEquals("fromDemo", legacyVariant.manifestPlaceholders.getting("kDimensionTier").get())
        assertEquals("fromStaging", legacyVariant.manifestPlaceholders.getting("kDimensionEnv").get())
        assertEquals("fromDemoStaging", legacyVariant.manifestPlaceholders.getting("kCombinedFlavor").get())
        assertEquals("fromDemoStagingDebug", legacyVariant.manifestPlaceholders.getting("kVariant").get())

        // Verify both unitTest and androidTest components received manifest placeholders via HasUnitTest/HasAndroidTest fallback
        assertEquals("fromDemoStagingDebug", unitTestComponent.manifestPlaceholders.getting("kVariant").get())
        assertEquals("fromDemoStagingDebug", androidTestComponent.manifestPlaceholders.getting("kVariant").get())
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
