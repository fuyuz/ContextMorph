package io.github.fuyuz.contextmorph.fir

import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import java.io.File

/**
 * FIR Extension that collects @Given declarations during the checking phase.
 *
 * This extension uses FirAdditionalCheckersExtension to intercept declarations
 * and register them with the GivenRegistry.
 */
class GivenDeclarationCheckerExtension(
    session: FirSession,
    private val registry: GivenRegistry
) : FirAdditionalCheckersExtension(session) {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [GivenChecker] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph GivenChecker] $message")
        }
    }

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val classCheckers: Set<FirClassChecker> = setOf(
            GivenClassChecker(registry)
        )

        override val propertyCheckers: Set<FirPropertyChecker> = setOf(
            GivenPropertyChecker(registry)
        )

        override val simpleFunctionCheckers: Set<FirSimpleFunctionChecker> = setOf(
            GivenFunctionChecker(registry)
        )
    }
}

/**
 * Checker for @Given annotated classes/objects.
 */
@OptIn(DeprecatedForRemovalCompilerApi::class)
class GivenClassChecker(
    private val registry: GivenRegistry
) : FirClassChecker(MppCheckerKind.Common) {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [GivenClassChecker] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration is FirRegularClass && registry.hasGivenAnnotation(declaration)) {
            log("Found @Given class: ${declaration.name}")
            registry.analyzeAndRegister(declaration)
        }
    }
}

/**
 * Checker for @Given annotated properties.
 */
@OptIn(DeprecatedForRemovalCompilerApi::class)
class GivenPropertyChecker(
    private val registry: GivenRegistry
) : FirPropertyChecker(MppCheckerKind.Common) {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [GivenPropertyChecker] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirProperty) {
        if (registry.hasGivenAnnotation(declaration)) {
            log("Found @Given property: ${declaration.name}")
            registry.analyzeAndRegister(declaration)
        }
    }
}

/**
 * Checker for @Given annotated functions (derived givens).
 */
@OptIn(DeprecatedForRemovalCompilerApi::class)
class GivenFunctionChecker(
    private val registry: GivenRegistry
) : FirSimpleFunctionChecker(MppCheckerKind.Common) {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [GivenFunctionChecker] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirSimpleFunction) {
        if (registry.hasGivenAnnotation(declaration)) {
            log("Found @Given function: ${declaration.name}")
            registry.analyzeAndRegister(declaration)
        }
    }
}
