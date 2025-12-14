package io.github.fuyuz.contextmorph.transform

import io.github.fuyuz.contextmorph.ContextMorphSymbols
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols

/**
 * Transforms useScope { wrapper -> ... } calls to inject subsequent code.
 */
@OptIn(DeprecatedForRemovalCompilerApi::class)
class UseScopeTransformer(
    private val pluginContext: IrPluginContext,
    private val symbols: ContextMorphSymbols
) {
    fun transform(
        body: IrBlockBody,
        useScopeCall: IrCall,
        useScopeIndex: Int,
        containingFunction: org.jetbrains.kotlin.ir.declarations.IrFunction?
    ): List<IrStatement> {
        System.err.println("[ContextMorph IR] useScope transformation starting")

        // Extract the wrapper lambda from useScope(wrapper) call
        val wrapperLambda = useScopeCall.getValueArgument(0) as? IrFunctionExpression
        if (wrapperLambda == null) {
            System.err.println("[ContextMorph IR] ERROR: Could not extract wrapper lambda")
            return body.statements
        }

        val beforeStatements = body.statements.subList(0, useScopeIndex)
        val allAfterStatements = if (useScopeIndex + 1 < body.statements.size) {
            body.statements.subList(useScopeIndex + 1, body.statements.size)
        } else {
            emptyList()
        }

        // Separate return statements from other statements
        // Return statements should NOT be captured in the content lambda
        val returnStatement = allAfterStatements.filterIsInstance<IrReturn>().firstOrNull()
        val returnIndex = if (returnStatement != null) allAfterStatements.indexOf(returnStatement) else -1

        val contentStatements = if (returnIndex >= 0) {
            allAfterStatements.subList(0, returnIndex)
        } else {
            allAfterStatements
        }

        val afterWrapperStatements = if (returnIndex >= 0) {
            allAfterStatements.subList(returnIndex, allAfterStatements.size)
        } else {
            emptyList()
        }

        if (contentStatements.isEmpty()) {
            System.err.println("[ContextMorph IR] No statements to inject into content lambda")
            return beforeStatements + afterWrapperStatements
        }

        System.err.println("[ContextMorph IR] Creating content lambda with ${contentStatements.size} statements")

        // Create the content lambda that captures subsequent statements (excluding return)
        val contentLambda = pluginContext.irFactory.buildFun {
            name = org.jetbrains.kotlin.name.Name.special("<anonymous>")
            this.returnType = pluginContext.irBuiltIns.unitType
            visibility = org.jetbrains.kotlin.descriptors.DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }

        contentLambda.parent = containingFunction ?: useScopeCall.symbol.owner

        // Set the content lambda body
        contentLambda.body = DeclarationIrBuilder(pluginContext, contentLambda.symbol, UNDEFINED_OFFSET, UNDEFINED_OFFSET)
            .irBlockBody(UNDEFINED_OFFSET, UNDEFINED_OFFSET) {
                contentStatements.forEach { +it }
            }

        // Create the lambda type: () -> Unit
        val contentLambdaType = pluginContext.irBuiltIns.functionN(0).typeWith(pluginContext.irBuiltIns.unitType)

        val contentLambdaExpression = IrFunctionExpressionImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = contentLambdaType,
            function = contentLambda,
            origin = IrStatementOrigin.LAMBDA
        )

        System.err.println("[ContextMorph IR] Calling wrapper lambda with content lambda")

        // Call the wrapper lambda's invoke() method with the content lambda
        // The wrapper lambda type is ((() -> Unit) -> Unit), we need to invoke it
        val functionClass = wrapperLambda.type.getClass()
        if (functionClass == null) {
            System.err.println("[ContextMorph IR] ERROR: Could not get function class")
            return body.statements
        }

        val invokeFunction = functionClass.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { it.name.asString() == "invoke" }

        if (invokeFunction == null) {
            System.err.println("[ContextMorph IR] ERROR: Could not find invoke method")
            return body.statements
        }

        val builder = DeclarationIrBuilder(pluginContext, invokeFunction.symbol, UNDEFINED_OFFSET, UNDEFINED_OFFSET)

        val wrapperCall = builder.irCall(invokeFunction.symbol).apply {
            dispatchReceiver = wrapperLambda
            putValueArgument(0, contentLambdaExpression)
        }

        System.err.println("[ContextMorph IR] useScope transformation complete")

        return beforeStatements + wrapperCall + afterWrapperStatements
    }
}
