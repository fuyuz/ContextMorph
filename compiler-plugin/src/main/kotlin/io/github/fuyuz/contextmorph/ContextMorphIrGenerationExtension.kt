package io.github.fuyuz.contextmorph

import io.github.fuyuz.contextmorph.transform.IrGivenRegistry
import io.github.fuyuz.contextmorph.transform.GivenUsingTransformer
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import java.io.File

class ContextMorphIrGenerationExtension : IrGenerationExtension {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [IrExtension] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph IR] $message")
        }
    }

    init {
        log("IrGenerationExtension instance created!")
    }

    override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
        log("generate() called for module: ${moduleFragment.name}")

        // Phase 1: Create symbols
        log("Creating symbols...")
        val symbols = ContextMorphSymbols(pluginContext)
        log("Symbols initialized")

        // Phase 2: Create given registry and collect @Given declarations
        log("Creating given registry...")
        val givenRegistry = IrGivenRegistry(pluginContext, symbols)
        givenRegistry.collectGivens(moduleFragment)
        log("Given registry populated with ${givenRegistry.getAllGivens().size} type entries")

        // Phase 3: Transform given/using/context-parameters + summon
        log("Transforming given/using/context + summon calls...")
        val givenUsingTransformer = GivenUsingTransformer(moduleFragment, pluginContext, symbols, givenRegistry)
        moduleFragment.transformChildrenVoid(givenUsingTransformer)
        log("Given/Using transformation completed")

        // Phase 4: Transform useScope calls
        log("Creating useScope transformer...")
        val contextTransformer = ContextMorphTransformer(pluginContext, symbols)
        log("Transformer created, starting transformation...")

        val fileCount = moduleFragment.files.size
        log("Module has $fileCount files")

        moduleFragment.transformChildrenVoid(contextTransformer)
        log("Transformation completed successfully")
    }
}
