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

    companion object {
        private const val defaultPropertiesFile = "local.properties"
        private const val defaultPropertyKey = "gmp.apiKey"
    }
}