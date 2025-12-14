plugins {
    kotlin("multiplatform") version "2.2.20" apply false
    kotlin("jvm") version "2.2.20" apply false
    id("com.gradle.plugin-publish") version "1.3.0" apply false
}

allprojects {
    group = "io.github.fuyuz.contextmorph"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }

    // In this mono-repo, ensure the compiler plugin used by the Gradle subplugin
    // is always the one built from this source tree, not a stale mavenLocal snapshot.
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("io.github.fuyuz.contextmorph:compiler-plugin"))
                .using(project(":compiler-plugin"))
        }
    }
}
