plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    id("com.gradle.plugin-publish")
}

group = "io.github.fuyuz.contextmorph"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(kotlin("gradle-plugin-api"))
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

gradlePlugin {
    website.set("https://github.com/fuyuz/ContextMorph")
    vcsUrl.set("https://github.com/fuyuz/ContextMorph.git")

    plugins {
        create("contextMorphPlugin") {
            id = "io.github.fuyuz.contextmorph"
            displayName = "ContextMorph Kotlin Compiler Plugin"
            description = "Provides Scala 3-style contextual abstractions (given/using/summon) for Kotlin"
            tags.set(listOf("kotlin", "compiler-plugin", "context", "dsl"))
            implementationClass = "io.github.fuyuz.contextmorph.gradle.ContextMorphGradlePlugin"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("ContextMorph Gradle Plugin")
                description.set("Gradle plugin for ContextMorph Kotlin compiler plugin")
                url.set("https://github.com/fuyuz/ContextMorph")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
            }
        }
    }
}
