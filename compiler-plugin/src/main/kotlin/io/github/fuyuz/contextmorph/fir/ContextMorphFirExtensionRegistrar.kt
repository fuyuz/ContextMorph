package io.github.fuyuz.contextmorph.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionApiInternals
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

class ContextMorphFirExtensionRegistrar : FirExtensionRegistrar() {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = java.io.File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [FirRegistrar] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph] $message")
        }
    }

    init {
        log("FirExtensionRegistrar initialized!")
    }

    @OptIn(FirExtensionApiInternals::class)
    override fun ExtensionRegistrarContext.configurePlugin() {
        log("configurePlugin called!")

        // Register given declaration checker (collects @Given annotations)
        +{ session: FirSession ->
            val registry = GivenRegistry(session)
            GivenDeclarationCheckerExtension(session, registry)
        }
        log("GivenDeclarationCheckerExtension registered!")

        // Register top-level declaration generator (synthetic overloads for @Using/context parameters)
        +{ session: FirSession ->
            ContextMorphUsingOverloadGenerationExtension(session)
        }
        log("ContextMorphUsingOverloadGenerationExtension registered!")
    }
}
