package io.github.fuyuz.contextmorph

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

class ContextMorphSymbols(private val pluginContext: IrPluginContext) {
    private val contextMorphPackage = FqName("io.github.fuyuz.contextmorph")
    private val kotlinPackage = FqName("kotlin")

    // ========== Existing symbols ==========

    val useScopeFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                contextMorphPackage,
                Name.identifier("useScope")
            )
        ).singleOrNull() ?: error("useScope function not found")
    }

    val withFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                kotlinPackage,
                Name.identifier("with")
            )
        ).firstOrNull() ?: error("with function not found")
    }

    // ========== New symbols for given/using ==========

    val summonFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                contextMorphPackage,
                Name.identifier("summon")
            )
        ).singleOrNull() ?: error("summon function not found")
    }

    val givenFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                contextMorphPackage,
                Name.identifier("given")
            )
        ).singleOrNull() ?: error("given function not found")
    }

    val importGivensFunction: IrSimpleFunctionSymbol by lazy {
        pluginContext.referenceFunctions(
            CallableId(
                contextMorphPackage,
                Name.identifier("importGivens")
            )
        ).singleOrNull() ?: error("importGivens function not found")
    }

    val givenAnnotationClass: IrClassSymbol? by lazy {
        pluginContext.referenceClass(
            ClassId(
                contextMorphPackage,
                Name.identifier("Given")
            )
        )
    }

    val usingAnnotationClass: IrClassSymbol? by lazy {
        pluginContext.referenceClass(
            ClassId(
                contextMorphPackage,
                Name.identifier("Using")
            )
        )
    }

    // ========== Helper methods ==========

    fun isSummonCall(symbol: IrSimpleFunctionSymbol): Boolean {
        return try {
            symbol == summonFunction
        } catch (e: Exception) {
            false
        }
    }

    fun isGivenCall(symbol: IrSimpleFunctionSymbol): Boolean {
        return try {
            symbol == givenFunction
        } catch (e: Exception) {
            false
        }
    }

    fun isImportGivensCall(symbol: IrSimpleFunctionSymbol): Boolean {
        return try {
            symbol == importGivensFunction
        } catch (e: Exception) {
            false
        }
    }

    fun isUseScopeCall(symbol: IrSimpleFunctionSymbol): Boolean {
        return try {
            symbol == useScopeFunction
        } catch (e: Exception) {
            false
        }
    }
}
