# Secrets Gradle Plugin for Android
![Tests](https://github.com/google/secrets-gradle-plugin/workflows/Tests/badge.svg)
![Apache-2.0](https://img.shields.io/badge/license-Apache-blue)

A Gradle plugin for providing your secrets securely to your Android project.

This Gradle plugin reads secrets from a properties file **not checked into version control**,
such as `local.properties` or `secrets.properties`, and exposes those properties as variables
in the Gradle-generated `BuildConfig` class and in your `AndroidManifest.xml` file.

**DISCLAIMER:** This plugin is primarily for hiding your keys from version control. Since your key is part of the static binary, your API keys are still recoverable by decompiling an APK. Securing your key using other measures—such as adding [API key restrictions](https://developers.google.com/maps/api-security-best-practices) in the Google Cloud Console—is strongly recommended.

## Requirements
* Gradle-based Android project (Gradle 8.0+ recommended)
* Android Gradle Plugin (AGP) 7.4.0 or higher (tested through AGP 9.x)
* JDK 17 or higher

## Installation

**NOTE**: Starting from v1.1.0, the plugin ID is `"com.google.android.libraries.mapsplatform.secrets-gradle-plugin"` and artifacts are distributed via Google Maven (`gMaven`). Ensure `google()` is included in your `settings.gradle` / `settings.gradle.kts` `pluginManagement.repositories` block:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

### Option A: Using Version Catalogs (`gradle/libs.versions.toml`) (Recommended)

1. In your `gradle/libs.versions.toml` file:

```toml
[versions]
secretsGradlePlugin = "2.0.1"

[plugins]
secrets-gradle-plugin = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "secretsGradlePlugin" }
```

2. In your root-level `build.gradle.kts` (or `build.gradle`) file:

```kotlin
plugins {
    alias(libs.plugins.secrets.gradle.plugin) apply false
}
```

3. In your module-level `build.gradle.kts` (or `build.gradle`) file:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.secrets.gradle.plugin)
}
```

### Option B: Using the `plugins` DSL

1. In your root-level `build.gradle` / `build.gradle.kts` file:

Groovy:
```groovy
plugins {
    id 'com.google.android.libraries.mapsplatform.secrets-gradle-plugin' version '2.0.1' apply false
}
```

Kotlin:
```kotlin
plugins {
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}
```

2. In your module-level `build.gradle` / `build.gradle.kts` file:

Groovy:
```groovy
plugins {
    id 'com.android.application'
    id 'com.google.android.libraries.mapsplatform.secrets-gradle-plugin'
}
```

Kotlin:
```kotlin
plugins {
    id("com.android.application")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}
```

### Option C: Using Legacy `buildscript` Classpath

In your root-level `build.gradle` / `build.gradle.kts` file:

```kotlin
buildscript {
    dependencies {
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
    }
}
```

Supported Android module types include:
* `com.android.application`
* `com.android.library`
* `com.android.test` (including Baseline Profile generator modules)
* `com.android.dynamic-feature`

## Example Usage

Example contents of `local.properties` (or `secrets.properties`) under your root project (or module directory):
```properties
MAPS_API_KEY=YOUR_API_KEY
```

After applying the plugin and building your project, the secret becomes accessible in two ways:

1. **In your `AndroidManifest.xml` file** (injected into the main variant as well as unit-test and instrumented-test manifests):
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

2. **As a `BuildConfig` field**:
```kotlin
val apiKey = BuildConfig.MAPS_API_KEY
```

> **Important (AGP 8.0+):** Starting in Android Gradle Plugin 8.0, `BuildConfig` generation is disabled by default. If you want secrets injected into `BuildConfig` (in addition to `AndroidManifest.xml`), enable `buildConfig` in your module's `build.gradle.kts` / `build.gradle`:
> ```kotlin
> android {
>     buildFeatures {
>         buildConfig = true
>     }
> }
> ```
> If `buildConfig` is disabled, the plugin automatically skips `BuildConfig` field injection and only injects `AndroidManifest.xml` placeholders without emitting AGP warnings.

## CI/CD Systems

For CI/CD systems, consider checking in a default properties file containing all required keys with safe stub values, and setting `defaultPropertiesFileName`:

```kotlin
secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "secrets.defaults.properties"
}
```

## Configuration Options

The plugin can optionally be configured inside your module's `build.gradle` / `build.gradle.kts`:

```kotlin
secrets {
    // Change the properties file from the default "local.properties" in your root project
    // (or module directory) to another properties file.
    propertiesFileName = "secrets.properties"

    // A properties file containing default secret values. This file can be checked into
    // version control for CI/CD or fresh checkouts.
    defaultPropertiesFileName = "secrets.defaults.properties"

    // Configure which keys should be ignored by the plugin by providing regular expressions.
    // "sdk.dir" is ignored by default.
    ignoreList.add("keyToIgnore") // Ignore the key "keyToIgnore"
    ignoreList.add("sdk.*")       // Ignore all keys matching the regexp "sdk.*"
}
```

### Build-Type, Flavor, and Variant-Specific Properties

You can override properties per build type, product flavor, or combined variant by placing matching `.properties` files in your root project (or module) directory. Files are evaluated in the following order (later files override earlier ones):

1. `defaultPropertiesFileName` (e.g., `secrets.defaults.properties`)
2. `propertiesFileName` (e.g., `local.properties` or `secrets.properties`)
3. `<buildType>.properties` (e.g., `debug.properties`, `release.properties`)
4. `<flavorName>.properties` (e.g., `free.properties`, `paid.properties`)
5. `<variantName>.properties` (e.g., `freeDebug.properties`, `paidRelease.properties`)

## Contributing

Contributions to this library are always welcome and highly encouraged!

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for more
information on how to get started.

## License
Apache 2.0. See [LICENSE](LICENSE) for more information.
