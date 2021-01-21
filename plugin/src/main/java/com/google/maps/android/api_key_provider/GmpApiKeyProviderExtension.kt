package com.google.maps.android.api_key_provider

/**
 * Configuration object for customizing where the Maps API Key is retrieved from and how it should
 * be provided.
 */
open class GmpApiKeyProviderExtension {
    /**
     * The name of the properties file containing the Maps API Key.
     * Defaults to "$defaultPropertiesFile"
     */
    var propertiesFileName: String = defaultPropertiesFile

    /**
     * The name of the key in the properties file containing the GMP API key.
     */
    var propertyKey: String = defaultPropertyKey

    /**
     * Determines if a build config field containing the GMP API key should be set.
     */
    var generateBuildConfigField: Boolean = false

    /**
     * If "$generateBuildConfigField" is set to `true`, the name of this field will be used
     * for the generated build config variable name.
     */
    var buildConfigFieldName: String = defaultBuildConfigName

    companion object {
        private const val defaultPropertiesFile = "local.properties"
        private const val defaultPropertyKey = "gmp.apiKey"
        private const val defaultBuildConfigName = "GMP_API_KEY"
    }
}