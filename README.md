# Secrets Gradle Plugin for Android
![Tests](https://github.com/google/secrets-gradle-plugin/workflows/Tests/badge.svg)
![Apache-2.0](https://img.shields.io/badge/license-Apache-blue)

A Gradle plugin for providing your secrets securely to your Android project.

This Gradle plugin reads secrets from a properties file **not checked into version control**,
such as `local.properties`, and expose those properties as variables in the Gradle-generated `BuildConfig`
class and in the Android manifest file.

**DISCLAIMER:** This plugin is primarily for hiding your keys from version control. Since your key is part of the static binary, your API keys are still recoverable by decompiling an APK. So, securing your key using other measures like adding restrictions (if possible) are recommended.

## Requirements
* Gradle-based Android project
* Android Gradle plugin 7.0.2

## Installation

**NOTE**: Starting from v1.1.0, the plugin ID was changed to "com.google.android.libraries.mapsplatform.secrets-gradle-plugin" and the plugin is now being distributed via Google Maven (gMaven).  You can still download previous versions of the plugin from Gradle's plugin portal, but new versions will now only be distributed through gMaven.

1. In your project's root `build.gradle` file:

Groovy:
```groovy
buildscript {
    dependencies {
        classpath "com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1"
    }
}
```

Kotlin:
```kotlin
buildscript {
    dependencies {
        classpath("com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1")
    }
}
```

2. In your app-level `build.gradle` file:

Groovy:
```groovy
plugins {
    id 'com.google.android.libraries.mapsplatform.secrets-gradle-plugin'
}
```

Kotlin:
```groovy
plugins {
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}
```

This plugin also supports library module type (`com.android.library`). Just install the plugin in your library-level `build.gradle` file and keys will be visible inside that module as well.

### Snapshot Releases

Snapshot releases, which are distributed via [GitHub Packages](https://github.com/orgs/google/packages?repo_name=secrets-gradle-plugin), are also available for latest fixes. To use a snapshot release, add the following repository to your project-level `build.gradle` file:

Groovy:
```groovy
buildscript {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/google/secrets-gradle-plugin")
            credentials {
                username = project.findProperty("GITHUB_USER") ?: System.getenv("GITHUB_USER")
                password = project.findProperty("GITHUB_TOKEN") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    dependencies {
        classpath "com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:<version>-SNAPSHOT"
    }
}
```

Also, see [Authenticating to GitHub Packages](https://docs.github.com/en/packages/learn-github-packages/introduction-to-github-packages#authenticating-to-github-packages).


## Example Usage

Example contents of `local.properties` under your root project:
```
apiKey=YOUR_API_KEY
```

After applying the plugin and building your project, the API key then becomes accessible in two ways.

  1. As a `BuildConfig` value:
  ```kotlin
  val apiKey = BuildConfig.apiKey
  ```
  2. As a variable accessible in your `AndroidManifest.xml` file:
  ```xml
  <meta-data android:value="${apiKey}" />
  ```

## CI/CD Systems

For CI/CD systems, consider creating and checking in version control a default properties file with all
the same keys required by your app but with _safe_ default values. To do this, create a properties file
and set the `defaultPropertiesFileName` value to that file name. For example:

```groovy
secrets {
    defaultPropertiesFileName = 'local.defaults.properties'
}
```

## Configuration Options

The plugin can optionally be configured:

```groovy
secrets {
    // Change the properties file from the default "local.properties" in your root project
    // to another properties file in your root project.
    propertiesFileName 'secrets.properties'

    // A properties file containing default secret values. This file can be checked in version
    // control.
    defaultPropertiesFileName = 'secrets.defaults.properties'

    // Configure which keys should be ignored by the plugin by providing regular expressions.
    // "sdk.dir" is ignored by default.
    ignoreList.add("keyToIgnore") // Ignore the key "keyToIgnore"
    ignoreList.add("sdk.*")       // Ignore all keys matching the regexp "sdk.*"
}
```

### Build-Variant Specific Properties

To set build-variant specific properties (build type or flavor), create a properties file at the
root directory of the project with the same name as the variant. For example, to set keys specific
for the `release` build type, create a new file called `release.properties` containing
release-specific keys.

## Contributing

Contributions to this library are always welcome and highly encouraged!

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for more 
information on how to get started.

## License
Apache 2.0. See [LICENSE](LICENSE) for more information.
