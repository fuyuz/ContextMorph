package io.github.fuyuz.contextmorph.transform

import io.github.fuyuz.contextmorph.ContextMorphSymbols
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrGetObjectValue
import org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.isSubtypeOfClass
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.io.File

/**
 * Registry for tracking @Given instances during IR transformation.
 *
 * This registry collects @Given annotated declarations from the IR module
 * and provides lookup functionality to resolve summon<T>() calls.
 */
class IrGivenRegistry(
    private val pluginContext: IrPluginContext,
    private val symbols: ContextMorphSymbols
) {
    companion object {
        private val GIVEN_ANNOTATION_FQ_NAME = FqName("io.github.fuyuz.contextmorph.Given")
        private val USING_ANNOTATION_FQ_NAME = FqName("io.github.fuyuz.contextmorph.Using")

        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [IrGivenRegistry] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph IR] $message")
        }
    }

    /**
     * Represents a discovered given instance in IR.
     */
    data class IrGivenEntry(
        val providedType: IrType,       // The type this given provides (e.g., Ord<Int>)
        val declaration: IrDeclaration,  // The declaration (object, property, or function)
        val priority: Int = 0
    )

    // Type signature -> List of given entries
    private val givenInstances = mutableMapOf<String, MutableList<IrGivenEntry>>()

    /**
     * Scan an IR module and collect all @Given declarations.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun collectGivens(module: IrModuleFragment) {
        log("Scanning module for @Given declarations...")

        module.acceptChildrenVoid(object : IrVisitorVoid() {
            override fun visitElement(element: IrElement) {
                element.acceptChildrenVoid(this)
            }

            override fun visitClass(declaration: IrClass) {
                if (hasGivenAnnotation(declaration)) {
                    registerGivenClass(declaration)
                }
                declaration.acceptChildrenVoid(this)
            }

            override fun visitProperty(declaration: IrProperty) {
                if (hasGivenAnnotation(declaration)) {
                    registerGivenProperty(declaration)
                }
                declaration.acceptChildrenVoid(this)
            }

            override fun visitSimpleFunction(declaration: IrSimpleFunction) {
                if (hasGivenAnnotation(declaration)) {
                    registerGivenFunction(declaration)
                }
                declaration.acceptChildrenVoid(this)
            }
        })

        log("Found ${givenInstances.values.flatten().size} given instances")
    }

    /**
     * Check if a declaration has @Given annotation.
     */
    private fun hasGivenAnnotation(declaration: IrDeclaration): Boolean {
        return declaration.annotations.any { annotation ->
            val annotationType = annotation.type
            annotationType.classOrNull?.owner?.name?.asString() == "Given"
        }
    }

    /**
     * Register a @Given class/object.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun registerGivenClass(declaration: IrClass) {
        log("Registering @Given class: ${declaration.name}")

        // For objects, register for each implemented interface
        declaration.superTypes.forEach { superType ->
            if (!superType.isAny()) {
                registerEntry(superType, declaration)
            }
        }
    }

    /**
     * Register a @Given property.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun registerGivenProperty(declaration: IrProperty) {
        log("Registering @Given property: ${declaration.name}")

        val propertyType = declaration.getter?.returnType ?: return
        registerEntry(propertyType, declaration)
    }

    /**
     * Register a @Given function (derived given).
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun registerGivenFunction(declaration: IrSimpleFunction) {
        log("Registering @Given function: ${declaration.name}")

        val returnType = declaration.returnType
        registerEntry(returnType, declaration)
    }

    private fun registerEntry(type: IrType, declaration: IrDeclaration) {
        val typeKey = getTypeKey(type)
        val entry = IrGivenEntry(type, declaration, priority = extractGivenPriority(declaration))
        givenInstances.getOrPut(typeKey) { mutableListOf() }.add(entry)
        log("  Registered: $typeKey -> ${declaration}")
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class, DeprecatedForRemovalCompilerApi::class)
    private fun extractGivenPriority(declaration: IrDeclaration): Int {
        val givenAnnotation = declaration.annotations.firstOrNull { ann ->
            ann.type.classOrNull?.owner?.name?.asString() == "Given"
        } ?: return 0

        val arg0 = givenAnnotation.getValueArgument(0) as? IrConst
        return (arg0?.value as? Int) ?: 0
    }

    /**
     * Get a unique key for a type (for lookup).
     * Uses render() to get a human-readable string including type arguments.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun getTypeKey(type: IrType): String {
        return type.render()
    }

    /**
     * Check if a type is Any.
     */
    private fun IrType.isAny(): Boolean {
        return this.classOrNull?.owner?.name?.asString() == "Any"
    }

    /**
     * Find a given instance for a type.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun findGiven(type: IrType): IrGivenEntry? {
        val typeKey = getTypeKey(type)

        // Direct match
        val directMatch = givenInstances[typeKey]?.firstOrNull()
        if (directMatch != null) {
            log("Found direct match for $typeKey: ${directMatch.declaration}")
            return directMatch
        }

        // Try subtype matching
        for ((key, entries) in givenInstances) {
            for (entry in entries) {
                if (isSubtypeMatch(type, entry.providedType)) {
                    log("Found subtype match for $typeKey: ${entry.declaration}")
                    return entry
                }
            }
        }

        log("No given found for $typeKey")
        return null
    }

    /**
     * Check if requestedType matches providedType.
     * This compares both the class and type arguments.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun isSubtypeMatch(requestedType: IrType, providedType: IrType): Boolean {
        // Use rendered strings for comparison to handle type arguments
        val requestedKey = requestedType.render()
        val providedKey = providedType.render()
        return requestedKey == providedKey
    }

    /**
     * Create an expression to get the given instance.
     */
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    fun createGivenAccessExpression(
        entry: IrGivenEntry,
        startOffset: Int,
        endOffset: Int
    ): IrExpression? {
        return when (val declaration = entry.declaration) {
            is IrClass -> {
                // For objects, use IrGetObjectValue
                if (declaration.isObject) {
                    IrGetObjectValueImpl(
                        startOffset,
                        endOffset,
                        declaration.defaultType,
                        declaration.symbol
                    )
                } else {
                    log("Cannot create access for non-object class: ${declaration.name}")
                    null
                }
            }
            is IrProperty -> {
                // For properties, we need to call the getter
                // This is more complex and requires building a call expression
                log("Property access not yet implemented for: ${declaration.name}")
                null
            }
            is IrSimpleFunction -> {
                // For functions (derived givens), we need to call the function
                // with its @Using parameters resolved
                log("Function call not yet implemented for: ${declaration.name}")
                null
            }
            else -> {
                log("Unknown declaration type: ${declaration::class.simpleName}")
                null
            }
        }
    }

    /**
     * Get all registered givens (for debugging).
     */
    fun getAllGivens(): Map<String, List<IrGivenEntry>> = givenInstances.toMap()

    fun getAllEntries(): List<IrGivenEntry> = givenInstances.values.flatten()
}
