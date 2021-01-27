# Secrets Gradle Plugin for Android
![Tests](https://github.com/google/secrets-gradle-plugin/workflows/Tests/badge.svg)
![Apache-2.0](https://img.shields.io/badge/license-Apache-blue)

A Gradle plugin for providing your secrets securely to your Android project.

This Gradle plugin reads secrets from a properties file **not checked into version control**,
such as `local.properties`, and expose those properties as variables in the Gradle-generated `BuildConfig`
class and in the Android manifest file.

## Requirements
* Gradle-based Android project
* Android Gradle plugin 4.1.x or newer

## Installation

In your app-level `build.gradle` file:

Groovy:
```groovy
plugins {
    id 'com.google.secrets_gradle_plugin' version '0.3'
}
```

Kotlin:
```groovy
plugins {
    id("com.google.secrets_gradle_plugin") version "0.3"
}
```

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
  <meta-data android:value=${apiKey} />
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

## Contributing

Contributions to this library are always welcome and highly encouraged!

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for more 
information on how to get started.

## License
Apache 2.0. See [LICENSE](LICENSE) for more information.
