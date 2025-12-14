pluginManagement {
    includeBuild("gradle-plugin")
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "contextmorph"

include(":runtime")
include(":compiler-plugin")
include(":sample")
