package io.github.fuyuz.contextmorph.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class ContextMorphGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create extension for user configuration
        project.extensions.create(
            "contextMorph",
            ContextMorphExtension::class.java
        )

        // Apply the Kotlin compiler plugin support
        project.plugins.apply(ContextMorphKotlinPlugin::class.java)
    }
}
