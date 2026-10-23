// Copyright 2026 Google LLC
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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end Gradle TestKit functional tests that execute real Android Gradle Plugin (AGP) tasks
 * (`generate*BuildConfig`, `compile*JavaWithJavac`, `process*MainManifest`,
 * `process*UnitTestManifest`, `process*AndroidTestManifest`) against real Gradle builds.
 */
class SecretsPluginFunctionalTest {

    @Rule
    @JvmField
    val testProjectDir = TemporaryFolder()

    private lateinit var testKitDir: File

    @Before
    fun setUp() {
        testKitDir = System.getProperty("testkit.dir")
            ?.let(::File)
            ?: testProjectDir.newFolder("testkit")

        writeLocalPropertiesWithSdkDir(testProjectDir.root)
        testProjectDir.newFile("gradle.properties").writeText(
            """
            org.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8
            android.useAndroidX=true
            kotlin.stdlib.default.dependency=false
            """.trimIndent() + "\n"
        )
    }

    @Test
    fun `end-to-end app BuildConfig compilation and main, unitTest, and androidTest manifest placeholder injection`() {
        testProjectDir.newFile("settings.gradle.kts").writeText(
            """
            rootProject.name = "functional-app-test"
            """.trimIndent()
        )
        testProjectDir.newFile("secrets.defaults.properties").writeText(
            """
            gmpApiKey=DEFAULT_KEY
            defaultOnlyKey=DEFAULT_ONLY_VALUE
            emptyUnquoted=
            emptyQuoted=""
            windowsPath=C\:\\Users\\dev\\Android\\Sdk
            keyToIgnore=SHOULD_BE_IGNORED
            ignorePrefixKey=SHOULD_ALSO_BE_IGNORED
            """.trimIndent()
        )
        testProjectDir.newFile("secrets.properties").writeText(
            """
            gmpApiKey="LIVE_MAPS_API_KEY"
            unquotedSecret=plain_secret_123
            """.trimIndent()
        )
        testProjectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }

            android {
                namespace = "com.example.functional"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.functional"
                    minSdk = 24
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                }
                buildFeatures {
                    buildConfig = true
                }
                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }

            secrets {
                propertiesFileName = "secrets.properties"
                defaultPropertiesFileName = "secrets.defaults.properties"
                ignoreList.add("keyToIgnore")
                ignoreList.add("ignorePrefix*")
            }
            """.trimIndent()
        )
        writeMainManifest(
            testProjectDir.root,
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:label="FunctionalTest">
                    <meta-data
                        android:name="com.google.android.geo.API_KEY"
                        android:value="$dollar{gmpApiKey}" />
                    <meta-data
                        android:name="com.google.android.geo.DEFAULT_KEY"
                        android:value="$dollar{defaultOnlyKey}" />
                </application>
            </manifest>
            """.trimIndent()
        )

        val result = createRunner(
            "generateDebugBuildConfig",
            "compileDebugJavaWithJavac",
            "processDebugMainManifest",
            "processDebugUnitTestManifest",
            "processDebugAndroidTestManifest"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDebugBuildConfig")?.outcome)
        // Compiling the generated BuildConfig.java with javac proves that empty strings, quotes,
        // and Windows backslashes produce valid Java string literals (#72, #81, #99).
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileDebugJavaWithJavac")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugMainManifest")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugUnitTestManifest")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugAndroidTestManifest")?.outcome)

        val buildConfigText = findGeneratedBuildConfig("debug").readText()
        assertTrue(buildConfigText.contains("""public static final String gmpApiKey = "LIVE_MAPS_API_KEY";"""))
        assertTrue(buildConfigText.contains("""public static final String defaultOnlyKey = "DEFAULT_ONLY_VALUE";"""))
        assertTrue(buildConfigText.contains("""public static final String unquotedSecret = "plain_secret_123";"""))
        assertTrue(buildConfigText.contains("""public static final String emptyUnquoted = "";"""))
        assertTrue(buildConfigText.contains("""public static final String emptyQuoted = "";"""))
        assertTrue(buildConfigText.contains("""public static final String windowsPath = "C:\\Users\\dev\\Android\\Sdk";"""))
        assertFalse(buildConfigText.contains("keyToIgnore"))
        assertFalse(buildConfigText.contains("ignorePrefixKey"))

        val mainManifestText = findMergedManifest("processDebugMainManifest").readText()
        assertTrue(mainManifestText.contains("""android:value="LIVE_MAPS_API_KEY""""))
        assertTrue(mainManifestText.contains("""android:value="DEFAULT_ONLY_VALUE""""))

        // Verify unit test manifest also had ${gmpApiKey} and ${defaultOnlyKey} resolved (#41, #89, #96, #104, PR #107).
        val unitTestManifestText = findMergedManifest("processDebugUnitTestManifest").readText()
        assertTrue(unitTestManifestText.contains("""android:value="LIVE_MAPS_API_KEY""""))
        assertTrue(unitTestManifestText.contains("""android:value="DEFAULT_ONLY_VALUE""""))
    }

    @Test
    fun `plugin applied before com_android_application in plugins block still injects BuildConfig and manifest placeholders`() {
        testProjectDir.newFile("settings.gradle.kts").writeText(
            """
            rootProject.name = "plugin-order-test"
            """.trimIndent()
        )
        testProjectDir.newFile("secrets.properties").writeText(
            """
            orderedKey=ORDERED_SECRET_VALUE
            """.trimIndent()
        )
        // Declare secrets-gradle-plugin BEFORE com.android.application (Issue #62).
        testProjectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
                id("com.android.application")
            }

            secrets {
                propertiesFileName = "secrets.properties"
            }

            android {
                namespace = "com.example.ordering"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.ordering"
                    minSdk = 24
                    targetSdk = 36
                }
                buildFeatures {
                    buildConfig = true
                }
            }
            """.trimIndent()
        )
        writeMainManifest(
            testProjectDir.root,
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <meta-data
                        android:name="ordered.key"
                        android:value="$dollar{orderedKey}" />
                </application>
            </manifest>
            """.trimIndent()
        )

        val result = createRunner("generateDebugBuildConfig", "processDebugMainManifest").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDebugBuildConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugMainManifest")?.outcome)

        val buildConfigText = findGeneratedBuildConfig("debug").readText()
        assertTrue(buildConfigText.contains("""public static final String orderedKey = "ORDERED_SECRET_VALUE";"""))

        val mainManifestText = findMergedManifest("processDebugMainManifest").readText()
        assertTrue(mainManifestText.contains("""android:value="ORDERED_SECRET_VALUE""""))
    }

    @Test
    fun `buildConfig disabled does not emit AGP warning and still injects manifest placeholders`() {
        testProjectDir.newFile("settings.gradle.kts").writeText(
            """
            rootProject.name = "no-buildconfig-test"
            """.trimIndent()
        )
        testProjectDir.newFile("secrets.properties").writeText(
            """
            manifestOnlyKey=MANIFEST_ONLY_SECRET
            """.trimIndent()
        )
        testProjectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }

            android {
                namespace = "com.example.nobuildconfig"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.nobuildconfig"
                    minSdk = 24
                    targetSdk = 36
                }
                buildFeatures {
                    buildConfig = false
                }
                testOptions {
                    unitTests {
                        isIncludeAndroidResources = true
                    }
                }
            }

            secrets {
                propertiesFileName = "secrets.properties"
            }
            """.trimIndent()
        )
        writeMainManifest(
            testProjectDir.root,
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <meta-data
                        android:name="manifest.only.key"
                        android:value="$dollar{manifestOnlyKey}" />
                </application>
            </manifest>
            """.trimIndent()
        )

        val result = createRunner("processDebugMainManifest", "processDebugUnitTestManifest").build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugMainManifest")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":processDebugUnitTestManifest")?.outcome)

        // Ensure AGP's warning for accessing variant.buildConfigFields when buildConfig is disabled
        // is completely absent (Issue #87).
        assertFalse(
            "Expected no buildConfigFields warning in build output, but got:\n${result.output}",
            result.output.contains("buildConfigFields") ||
                result.output.contains("buildFeatures.buildConfig")
        )

        val mainManifestText = findMergedManifest("processDebugMainManifest").readText()
        assertTrue(mainManifestText.contains("""android:value="MANIFEST_ONLY_SECRET""""))
        val unitTestManifestText = findMergedManifest("processDebugUnitTestManifest").readText()
        assertTrue(unitTestManifestText.contains("""android:value="MANIFEST_ONLY_SECRET""""))
    }

    @Test
    fun `five-level property precedence across defaults, main, buildType, productFlavor, and combined variantName`() {
        testProjectDir.newFile("settings.gradle.kts").writeText(
            """
            rootProject.name = "precedence-test"
            """.trimIndent()
        )
        testProjectDir.newFile("secrets.defaults.properties").writeText(
            """
            kDefault=fromDefault
            kMain=fromDefault
            kBuildType=fromDefault
            kFlavor=fromDefault
            kVariant=fromDefault
            """.trimIndent()
        )
        testProjectDir.newFile("secrets.properties").writeText(
            """
            kMain=fromMain
            kBuildType=fromMain
            kFlavor=fromMain
            kVariant=fromMain
            """.trimIndent()
        )
        testProjectDir.newFile("debug.properties").writeText(
            """
            kBuildType=fromDebug
            kFlavor=fromDebug
            kVariant=fromDebug
            """.trimIndent()
        )
        testProjectDir.newFile("demo.properties").writeText(
            """
            kFlavor=fromDemo
            kVariant=fromDemo
            """.trimIndent()
        )
        testProjectDir.newFile("demoDebug.properties").writeText(
            """
            kVariant=fromDemoDebug
            """.trimIndent()
        )
        testProjectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }

            android {
                namespace = "com.example.precedence"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.precedence"
                    minSdk = 24
                    targetSdk = 36
                }
                flavorDimensions += "tier"
                productFlavors {
                    create("demo") { dimension = "tier" }
                    create("full") { dimension = "tier" }
                }
                buildFeatures {
                    buildConfig = true
                }
            }

            secrets {
                defaultPropertiesFileName = "secrets.defaults.properties"
                propertiesFileName = "secrets.properties"
            }
            """.trimIndent()
        )
        writeMainManifest(testProjectDir.root, """<manifest><application/></manifest>""")

        val result = createRunner(
            "generateDemoDebugBuildConfig",
            "generateFullDebugBuildConfig"
        ).build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateDemoDebugBuildConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateFullDebugBuildConfig")?.outcome)

        val demoDebugBuildConfig = findGeneratedBuildConfig("demo/debug").readText()
        assertTrue(demoDebugBuildConfig.contains("""public static final String kDefault = "fromDefault";"""))
        assertTrue(demoDebugBuildConfig.contains("""public static final String kMain = "fromMain";"""))
        assertTrue(demoDebugBuildConfig.contains("""public static final String kBuildType = "fromDebug";"""))
        assertTrue(demoDebugBuildConfig.contains("""public static final String kFlavor = "fromDemo";"""))
        assertTrue(demoDebugBuildConfig.contains("""public static final String kVariant = "fromDemoDebug";"""))

        val fullDebugBuildConfig = findGeneratedBuildConfig("full/debug").readText()
        assertTrue(fullDebugBuildConfig.contains("""public static final String kDefault = "fromDefault";"""))
        assertTrue(fullDebugBuildConfig.contains("""public static final String kMain = "fromMain";"""))
        assertTrue(fullDebugBuildConfig.contains("""public static final String kBuildType = "fromDebug";"""))
        assertTrue(fullDebugBuildConfig.contains("""public static final String kFlavor = "fromDebug";"""))
        assertTrue(fullDebugBuildConfig.contains("""public static final String kVariant = "fromDebug";"""))
    }

    @Test
    fun `library and com_android_test modules with module-local properties fallback inject secrets cleanly`() {
        testProjectDir.newFile("settings.gradle.kts").writeText(
            """
            rootProject.name = "multi-module-test"
            include(":app", ":lib", ":benchmark")
            """.trimIndent()
        )
        testProjectDir.newFile("secrets.properties").writeText(
            """
            rootKey=ROOT_SECRET_VALUE
            """.trimIndent()
        )

        val appDir = testProjectDir.newFolder("app")
        appDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }
            android {
                namespace = "com.example.multimodule.app"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.multimodule.app"
                    minSdk = 24
                    targetSdk = 36
                }
            }
            secrets {
                propertiesFileName = "secrets.properties"
            }
            """.trimIndent()
        )
        writeMainManifest(appDir, """<manifest><application/></manifest>""")

        val libDir = testProjectDir.newFolder("lib")
        // Place module-specific properties file inside :lib directory (not root).
        libDir.resolve("lib.secrets.properties").writeText(
            """
            libKey=MODULE_LOCAL_LIB_SECRET
            """.trimIndent()
        )
        libDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library")
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }
            android {
                namespace = "com.example.multimodule.lib"
                compileSdk = 36
                defaultConfig {
                    minSdk = 24
                }
                buildFeatures {
                    buildConfig = true
                }
            }
            secrets {
                propertiesFileName = "lib.secrets.properties"
            }
            """.trimIndent()
        )
        writeMainManifest(
            libDir,
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <meta-data android:name="lib.key" android:value="$dollar{libKey}" />
                </application>
            </manifest>
            """.trimIndent()
        )

        val benchmarkDir = testProjectDir.newFolder("benchmark")
        benchmarkDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.test")
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }
            android {
                namespace = "com.example.multimodule.benchmark"
                compileSdk = 36
                targetProjectPath = ":app"
                defaultConfig {
                    minSdk = 24
                    targetSdk = 36
                }
            }
            secrets {
                propertiesFileName = "secrets.properties"
            }
            """.trimIndent()
        )
        writeMainManifest(
            benchmarkDir,
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application>
                    <meta-data android:name="benchmark.key" android:value="$dollar{rootKey}" />
                </application>
            </manifest>
            """.trimIndent()
        )

        val result = createRunner(
            ":lib:generateDebugBuildConfig",
            ":lib:processDebugManifest",
            ":benchmark:processDebugManifest"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:generateDebugBuildConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":lib:processDebugManifest")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":benchmark:processDebugManifest")?.outcome)

        val libBuildConfig = libDir.resolve("build/generated/source/buildConfig/debug")
            .walkTopDown()
            .first { it.name == "BuildConfig.java" }
            .readText()
        assertTrue(libBuildConfig.contains("""public static final String libKey = "MODULE_LOCAL_LIB_SECRET";"""))

        val libManifest = findMergedManifest("processDebugManifest", libDir).readText()
        assertTrue(libManifest.contains("""android:value="MODULE_LOCAL_LIB_SECRET""""))

        val benchmarkManifest = findMergedManifest("processDebugManifest", benchmarkDir).readText()
        assertTrue(benchmarkManifest.contains("""android:value="ROOT_SECRET_VALUE""""))
    }

    @Test
    fun `configuration cache is reused when properties are unchanged and invalidated when secret value changes`() {
        testProjectDir.newFile("settings.gradle.kts").writeText(
            """
            rootProject.name = "config-cache-test"
            """.trimIndent()
        )
        val secretsFile = testProjectDir.newFile("secrets.properties").apply {
            writeText("testProp=INITIAL_VALUE\n")
        }
        testProjectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application")
                id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
            }
            android {
                namespace = "com.example.configcache"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.example.configcache"
                    minSdk = 24
                    targetSdk = 36
                }
                buildFeatures {
                    buildConfig = true
                }
            }
            secrets {
                propertiesFileName = "secrets.properties"
            }
            """.trimIndent()
        )
        writeMainManifest(testProjectDir.root, """<manifest><application/></manifest>""")

        // 1. First run stores the configuration cache entry.
        val firstRun = createRunner("--configuration-cache", "generateDebugBuildConfig").build()
        assertEquals(TaskOutcome.SUCCESS, firstRun.task(":generateDebugBuildConfig")?.outcome)
        assertTrue(
            findGeneratedBuildConfig("debug").readText()
                .contains("""public static final String testProp = "INITIAL_VALUE";""")
        )

        // 2. Second run with unchanged secrets.properties reuses the configuration cache.
        val secondRun = createRunner("--configuration-cache", "generateDebugBuildConfig").build()
        assertTrue(
            "Expected configuration cache reuse, but output was:\n${secondRun.output}",
            secondRun.output.contains("Reusing configuration cache.")
        )

        // 3. Rotating the value in secrets.properties invalidates the configuration cache
        // (tracked via project.providers.fileContents) and updates BuildConfig.java.
        secretsFile.writeText("testProp=ROTATED_VALUE\n")
        val thirdRun = createRunner("--configuration-cache", "generateDebugBuildConfig").build()
        assertEquals(TaskOutcome.SUCCESS, thirdRun.task(":generateDebugBuildConfig")?.outcome)
        assertTrue(
            findGeneratedBuildConfig("debug").readText()
                .contains("""public static final String testProp = "ROTATED_VALUE";""")
        )
    }

    private fun createRunner(vararg args: String): GradleRunner {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withTestKitDir(testKitDir)
            .withPluginClasspath()
            .withArguments(*args, "--offline", "--stacktrace")
    }

    private fun writeLocalPropertiesWithSdkDir(dir: File) {
        val sdkDir = System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: "/usr/local/google/home/dkhawk/Android/Sdk"
        dir.resolve("local.properties").writeText("sdk.dir=${sdkDir.replace("\\", "\\\\")}\n")
    }

    private fun writeMainManifest(moduleDir: File, content: String) {
        val manifestFile = moduleDir.resolve("src/main/AndroidManifest.xml")
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText(content)
    }

    private fun findGeneratedBuildConfig(variantPath: String): File {
        return testProjectDir.root.resolve("build/generated/source/buildConfig/$variantPath")
            .walkTopDown()
            .first { it.name == "BuildConfig.java" }
    }

    private fun findMergedManifest(taskName: String, moduleDir: File = testProjectDir.root): File {
        val intermediatesDir = moduleDir.resolve("build/intermediates")
        return intermediatesDir.walkTopDown()
            .firstOrNull { it.name == "AndroidManifest.xml" && it.path.contains(taskName) }
            ?: intermediatesDir.walkTopDown().first { it.name == "AndroidManifest.xml" }
    }

    companion object {
        private const val dollar = "$"
    }
}
