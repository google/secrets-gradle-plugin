package me.chrisarriola.androidthings.maps_api_key_plugin

/**
 * Configuration object for customizing where the Maps API Key is retrieved from and how it should
 * be provided.
 */
open class MapsApiKeyProviderExtension {
    /**
     * The name of the properties file containing the Maps API Key.
     * Defaults to "$defaultPropertiesFile"
     */
    var propertiesFileName: String = defaultPropertiesFile

    /**
     * The name of the key in the properties file containing the Maps API key.
     */
    var propertyKey: String = defaultPropertyKey

    /**
     * The name of the build variable injected into the manifest file.
     */
    var manifestBuildVariable: String = defaultManifestBuildVariable

    companion object {
        private const val defaultPropertiesFile = "local.properties"
        private const val defaultPropertyKey = "maps.apiKey"
        private const val defaultManifestBuildVariable = "mapsApiKey"
    }
}