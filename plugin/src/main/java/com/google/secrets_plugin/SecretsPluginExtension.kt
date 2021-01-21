package com.google.secrets_plugin

/**
 * Configuration object for customizing where the Maps API Key is retrieved from and how it should
 * be provided.
 */
open class SecretsPluginExtension {
    /**
     * The name of the properties file containing secrets. Defaults to "$defaultPropertiesFile"
     */
    var propertiesFileName: String = defaultPropertiesFile

    /**
     * A list of keys this plugin should ignore and not inject. Defaults to $defaultIgnoreList
     */
    var ignoreList: MutableList<String> = defaultIgnoreList

    companion object {
        const val defaultPropertiesFile = "local.properties"
        val defaultIgnoreList = mutableListOf("sdk.dir")
    }
}