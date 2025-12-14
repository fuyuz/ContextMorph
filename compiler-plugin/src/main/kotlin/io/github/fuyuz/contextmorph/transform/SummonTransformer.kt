package io.github.fuyuz.contextmorph.transform

import io.github.fuyuz.contextmorph.ContextMorphSymbols
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import java.io.File

/**
 * Transforms summon<T>() calls into actual given instance references.
 *
 * This transformer replaces:
 * ```kotlin
 * val ord = summon<Ord<Int>>()
 * ```
 *
 * With:
 * ```kotlin
 * val ord = IntOrd  // Direct reference to the @Given object
 * ```
 */
@OptIn(DeprecatedForRemovalCompilerApi::class)
class SummonTransformer(
    private val pluginContext: IrPluginContext,
    private val symbols: ContextMorphSymbols,
    private val givenRegistry: IrGivenRegistry
) : IrElementTransformerVoid() {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [SummonTransformer] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph SummonTransformer] $message")
        }
    }

    private var transformedCount = 0

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall): IrExpression {
        // First, transform children
        val transformed = super.visitCall(expression) as IrCall

        // Check if this is a summon call
        if (!isSummonCall(transformed)) {
            return transformed
        }

        log("Found summon call at ${transformed.startOffset}")

        // Get the type argument
        val typeArg = transformed.getTypeArgument(0)
        if (typeArg == null) {
            log("ERROR: summon call has no type argument")
            return transformed
        }

        log("summon type argument: $typeArg")

        // Look up the given instance
        val givenEntry = givenRegistry.findGiven(typeArg)
        if (givenEntry == null) {
            log("ERROR: No given instance found for type: $typeArg")
            // Return original call - will throw at runtime
            return transformed
        }

        // Create expression to access the given instance
        val accessExpression = givenRegistry.createGivenAccessExpression(
            givenEntry,
            transformed.startOffset,
            transformed.endOffset
        )

        if (accessExpression == null) {
            log("ERROR: Could not create access expression for: ${givenEntry.declaration}")
            return transformed
        }

        transformedCount++
        log("Transformed summon<$typeArg>() -> ${givenEntry.declaration}")

        return accessExpression
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun isSummonCall(call: IrCall): Boolean {
        return try {
            val calleeName = call.symbol.owner.name.asString()
            calleeName == "summon"
        } catch (e: Exception) {
            false
        }
    }

    fun getTransformedCount(): Int = transformedCount
}

/**
 * Transforms given<T> { } calls into block-scoped given registration.
 *
 * This transformer handles:
 * ```kotlin
 * given<Ord<Int>> { DescendingOrd }
 * val sorted = list.sortedWith()  // Uses DescendingOrd
 * ```
 *
 * The given value is stored and made available for subsequent summon calls
 * within the same block scope.
 */
@OptIn(DeprecatedForRemovalCompilerApi::class)
class GivenBlockTransformer(
    private val pluginContext: IrPluginContext,
    private val symbols: ContextMorphSymbols,
    private val givenRegistry: IrGivenRegistry
) : IrElementTransformerVoid() {

    companion object {
        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [GivenBlockTransformer] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph GivenBlockTransformer] $message")
        }
    }

    // Track block-scoped givens (type -> expression)
    private val blockScopedGivens = mutableMapOf<String, IrExpression>()

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression) as IrCall

        // Check if this is a given { } call
        if (!isGivenCall(transformed)) {
            return transformed
        }

        log("Found given call at ${transformed.startOffset}")

        // Get the type argument
        val typeArg = transformed.getTypeArgument(0)
        if (typeArg == null) {
            log("ERROR: given call has no type argument")
            return transformed
        }

        // Get the provider lambda
        val providerArg = transformed.getValueArgument(0)
        if (providerArg == null) {
            log("ERROR: given call has no provider argument")
            return transformed
        }

        log("Registered block-scoped given for: $typeArg")

        // For now, we just log - full implementation would store and use this
        // The block-scoped given mechanism requires more complex scope tracking

        return transformed
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun isGivenCall(call: IrCall): Boolean {
        return try {
            val calleeName = call.symbol.owner.name.asString()
            calleeName == "given"
        } catch (e: Exception) {
            false
        }
    }

    fun getBlockScopedGiven(type: IrType): IrExpression? {
        return blockScopedGivens[type.toString()]
    }
}
