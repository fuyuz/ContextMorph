package io.github.fuyuz.contextmorph

import io.github.fuyuz.contextmorph.fir.ContextMorphFirExtensionRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension

@OptIn(ExperimentalCompilerApi::class)
class ContextMorphCompilerPluginRegistrar : CompilerPluginRegistrar() {
    companion object {
        const val PLUGIN_ID = "io.github.fuyuz.contextmorph"
    }

    init {
        try {
            val logFile = java.io.File("/tmp/contextmorph-plugin.log")
            logFile.appendText("[${java.time.LocalDateTime.now()}] CompilerPluginRegistrar initialized!\n")
        } catch (e: Exception) {
            // Ignore file logging errors
        }
        System.err.println("[ContextMorph] CompilerPluginRegistrar initialized!")
    }

    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        try {
            val logFile = java.io.File("/tmp/contextmorph-plugin.log")
            logFile.appendText("[${java.time.LocalDateTime.now()}] registerExtensions called!\n")
        } catch (e: Exception) {
            // Ignore file logging errors
        }
        System.err.println("[ContextMorph] registerExtensions called!")

        // Register FIR extension for type checking
        System.err.println("[ContextMorph] Registering FirExtensionRegistrarAdapter...")
        FirExtensionRegistrarAdapter.registerExtension(ContextMorphFirExtensionRegistrar())

        // Register IR extension for transformation
        System.err.println("[ContextMorph] Registering IrGenerationExtension...")
        IrGenerationExtension.registerExtension(ContextMorphIrGenerationExtension())
        try {
            val logFile = java.io.File("/tmp/contextmorph-plugin.log")
            logFile.appendText("[${java.time.LocalDateTime.now()}] IR extension registered!\n")
        } catch (e: Exception) {
            // Ignore file logging errors
        }
        System.err.println("[ContextMorph] Extensions registered!")
    }
}
