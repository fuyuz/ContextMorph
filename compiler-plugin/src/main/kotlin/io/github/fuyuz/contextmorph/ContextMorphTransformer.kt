package io.github.fuyuz.contextmorph

import io.github.fuyuz.contextmorph.transform.BlockScopeTransformer
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

class ContextMorphTransformer(
    private val pluginContext: IrPluginContext,
    private val symbols: ContextMorphSymbols
) : IrElementTransformerVoidWithContext() {

    override fun visitBlockBody(body: IrBlockBody): IrBlockBody {
        try {
            val logFile = java.io.File("/tmp/contextmorph-plugin.log")
            logFile.appendText("[${java.time.LocalDateTime.now()}] visitBlockBody called with ${body.statements.size} statements\n")
            System.err.println("[ContextMorph IR] visitBlockBody called with ${body.statements.size} statements")
        } catch (e: Exception) {
            // Ignore
        }

        // First, transform children to handle nested blocks
        body.transformChildrenVoid(this)

        // Get the containing function from the context
        val containingFunction = currentFunction?.irElement as? IrFunction

        // Then apply our transformations
        val transformer = BlockScopeTransformer(pluginContext, symbols)
        return transformer.transform(body, containingFunction)
    }
}
