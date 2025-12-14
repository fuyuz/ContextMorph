plugins {
    kotlin("multiplatform")
    id("io.github.fuyuz.contextmorph")
}

kotlin {
    jvm {
        mainRun {
            mainClass.set("io.github.fuyuz.contextmorph.sample.MainKt")
        }
    }

    js(IR) {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":runtime"))
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

    // Enable context parameters language feature
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xcontext-parameters")
                }
            }
        }
    }
}
