include(":plugin", ":sample-app")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven(url = "./plugin/build/repository")
    }
}
