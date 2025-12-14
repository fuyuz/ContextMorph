package io.github.fuyuz.contextmorph.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class ContextMorphKotlinPlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: org.gradle.api.Project) {
        println("[ContextMorph Gradle] Plugin apply() called on project: ${target.name}")
        super.apply(target)
    }

    override fun getCompilerPluginId(): String {
        println("[ContextMorph Gradle] getCompilerPluginId() called")
        return "io.github.fuyuz.contextmorph"
    }

    override fun getPluginArtifact(): SubpluginArtifact {
        println("[ContextMorph Gradle] getPluginArtifact() called")
        return SubpluginArtifact(
            groupId = "io.github.fuyuz.contextmorph",
            artifactId = "compiler-plugin",
            version = "0.1.0-SNAPSHOT"
        )
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        println("[ContextMorph Gradle] isApplicable() called for compilation: ${kotlinCompilation.name}")
        return true
    }

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        println("[ContextMorph Gradle] applyToCompilation() called for compilation: ${kotlinCompilation.name}")
        val project = kotlinCompilation.target.project
        val extension = project.extensions.findByType(ContextMorphExtension::class.java)
            ?: project.extensions.create("contextMorph", ContextMorphExtension::class.java)

        return project.provider {
            val options = listOf(
                SubpluginOption(
                    key = "enabled",
                    value = extension.enabled.getOrElse(true).toString()
                )
            )
            println("[ContextMorph Gradle] Subplugin options: $options")
            options
        }
    }
}
