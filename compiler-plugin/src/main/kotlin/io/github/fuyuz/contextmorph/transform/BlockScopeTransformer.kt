package io.github.fuyuz.contextmorph.transform

import io.github.fuyuz.contextmorph.ContextMorphSymbols
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall

class BlockScopeTransformer(
    private val pluginContext: IrPluginContext,
    private val symbols: ContextMorphSymbols
) {
    fun transform(body: IrBlockBody, containingFunction: IrFunction?): IrBlockBody {
        val statements = body.statements

        try {
            val logFile = java.io.File("/tmp/contextmorph-plugin.log")
            logFile.appendText("[${java.time.LocalDateTime.now()}] BlockScopeTransformer checking ${statements.size} statements\n")
            statements.forEachIndexed { index, stmt ->
                logFile.appendText("[${java.time.LocalDateTime.now()}]   [$index] ${stmt::class.simpleName}\n")
                if (stmt is IrCall) {
                    logFile.appendText("[${java.time.LocalDateTime.now()}]       Call to: ${stmt.symbol.owner.name}\n")
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        System.err.println("[ContextMorph IR] BlockScopeTransformer checking ${statements.size} statements")
        statements.forEachIndexed { index, stmt ->
            System.err.println("[ContextMorph IR]   [$index] ${stmt::class.simpleName}")
            if (stmt is IrCall) {
                System.err.println("[ContextMorph IR]       Call to: ${stmt.symbol.owner.name}")
            }
        }

        // Check for useScope first (takes precedence)
        val useScopeIndex = findLastUseScopeIndex(statements)
        if (useScopeIndex != -1) {
            System.err.println("[ContextMorph IR] Found useScope at index $useScopeIndex")
            val useScopeCall = statements[useScopeIndex] as IrCall
            val transformer = UseScopeTransformer(pluginContext, symbols)

            val transformedStatements = transformer.transform(body, useScopeCall, useScopeIndex, containingFunction)
            body.statements.clear()
            body.statements.addAll(transformedStatements)
            return body
        }

        System.err.println("[ContextMorph IR] No useScope found")
        // No transformations needed
        return body
    }

    private fun findLastUseScopeIndex(statements: List<IrStatement>): Int {
        var lastIndex = -1

        for ((index, statement) in statements.withIndex()) {
            if (statement is IrCall) {
                // Match by function name
                if (statement.symbol.owner.name.asString() == "useScope") {
                    lastIndex = index
                }
            }
        }

        return lastIndex
    }
}
