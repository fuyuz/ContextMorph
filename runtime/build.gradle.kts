plugins {
    kotlin("multiplatform")
    `maven-publish`
}

kotlin {
    jvm()

    js(IR) {
        browser()
        nodejs()
    }

    // Native targets
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

publishing {
    publications.withType<MavenPublication> {
        pom {
            name.set("ContextMorph Runtime")
            description.set("Runtime library for ContextMorph Kotlin compiler plugin")
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
