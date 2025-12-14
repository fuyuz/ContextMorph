plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = false
        freeCompilerArgs.addAll(
            "-Xsuppress-version-warnings",
            "-Xskip-prerelease-check",
            "-Xcontext-parameters",
            "-Xsuppress-deprecated-jvm-target-warning"
        )
    }

    // Suppress deprecation warnings for tests as well
    target.compilations.all {
        compileTaskProvider.configure {
            compilerOptions {
                allWarningsAsErrors.set(false)
                freeCompilerArgs.addAll(
                    "-Xsuppress-version-warnings",
                    "-Xskip-prerelease-check",
                    "-Xcontext-parameters",
                )
            }
        }
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    compileOnly(project(":runtime"))

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    testImplementation(project(":runtime"))
    testImplementation("dev.zacsweers.kctfork:core:0.11.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("ContextMorph Compiler Plugin")
                description.set("Kotlin compiler plugin for ContextMorph")
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
