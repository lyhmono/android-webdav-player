pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libVLC（full 风味）所需仓库
        maven { url = uri("https://maven.videolan.org") }
    }
}

rootProject.name = "WebDavPlayer"
include(":app")
