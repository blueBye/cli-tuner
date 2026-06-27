pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        maven { url = uri("https://maven.myket.ir/") }
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven.myket.ir/") }
    }
}

rootProject.name="guitarish"
