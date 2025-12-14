pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        kotlin("jvm") version "2.2.0"
        id("com.gradle.plugin-publish") version "1.3.0"
    }
}

rootProject.name = "gradle-plugin"
