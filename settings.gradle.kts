pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"

    id("dev.kikugie.loom-back-compat") version "0.4.2"

    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        fun match(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        match("1.19.2", "fabric")
        match("1.19.3", "fabric")
        match("1.19.4", "fabric")
        match("1.20.1", "fabric")
        match("1.20.2", "fabric")
        match("1.20.4", "fabric")
        match("1.20.6", "fabric")
        match("1.21.1", "fabric")
        match("1.21.3", "fabric")
        match("1.21.4", "fabric")
        match("1.21.5", "fabric")
        match("1.21.8", "fabric")
        match("1.21.10", "fabric")
        match("1.21.11", "fabric")

        vcsVersion = "1.20.1-fabric"
    }
}

rootProject.name = "synchronize"
