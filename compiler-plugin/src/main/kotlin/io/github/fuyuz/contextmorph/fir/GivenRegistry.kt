package io.github.fuyuz.contextmorph.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.*
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import java.io.File

/**
 * Registry for tracking @Given instances discovered during FIR analysis.
 *
 * This registry maintains a mapping from types to their given instances,
 * supporting scope-based resolution and priority ordering.
 */
class GivenRegistry(private val session: FirSession) {

    companion object {
        val GIVEN_ANNOTATION_CLASS_ID = ClassId(FqName("io.github.fuyuz.contextmorph"), org.jetbrains.kotlin.name.Name.identifier("Given"))
        val USING_ANNOTATION_CLASS_ID = ClassId(FqName("io.github.fuyuz.contextmorph"), org.jetbrains.kotlin.name.Name.identifier("Using"))

        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [GivenRegistry] $message\n")
            } catch (e: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph GivenRegistry] $message")
        }
    }

    /**
     * Represents a discovered given instance.
     */
    data class GivenEntry(
        val type: ConeKotlinType,
        val symbol: FirBasedSymbol<*>,
        val priority: Int,
        val scope: GivenScope,
        val isDerived: Boolean = false,
        val dependencies: List<ConeKotlinType> = emptyList()
    )

    /**
     * Scope levels for given resolution.
     */
    enum class GivenScope(val level: Int) {
        BLOCK(0),      // Highest priority - local given { } blocks
        PARAMETER(1),  // Function parameters with @Using
        CLASS(2),      // Class members
        FILE(3),       // File-level declarations
        MODULE(4)      // Module/package level
    }

    // Type -> List of GivenEntries (may have multiple with different priorities)
    private val givenInstances = mutableMapOf<String, MutableList<GivenEntry>>()

    /**
     * Register a given instance.
     */
    fun registerGiven(
        type: ConeKotlinType,
        symbol: FirBasedSymbol<*>,
        priority: Int = 0,
        scope: GivenScope = GivenScope.MODULE,
        isDerived: Boolean = false,
        dependencies: List<ConeKotlinType> = emptyList()
    ) {
        val typeKey = type.renderForDebugging()
        val entry = GivenEntry(type, symbol, priority, scope, isDerived, dependencies)

        givenInstances.getOrPut(typeKey) { mutableListOf() }.add(entry)
        log("Registered given: $typeKey (priority=$priority, scope=$scope, isDerived=$isDerived)")
    }

    /**
     * Find the best matching given instance for a type.
     * Returns null if not found or if there's an ambiguity.
     */
    fun findGiven(type: ConeKotlinType, fromScope: GivenScope = GivenScope.MODULE): GivenEntry? {
        val typeKey = type.renderForDebugging()
        val candidates = givenInstances[typeKey] ?: return null

        // Filter by scope visibility
        val visibleCandidates = candidates.filter { it.scope.level >= fromScope.level }

        if (visibleCandidates.isEmpty()) {
            log("No visible given found for: $typeKey")
            return null
        }

        // Sort by: scope (lower level = higher priority), then by priority (higher = better)
        val sorted = visibleCandidates.sortedWith(
            compareBy<GivenEntry> { it.scope.level }
                .thenByDescending { it.priority }
        )

        val best = sorted.first()

        // Check for ambiguity (multiple with same scope and priority)
        val ambiguous = sorted.filter {
            it.scope.level == best.scope.level && it.priority == best.priority
        }

        if (ambiguous.size > 1) {
            log("Ambiguous given for: $typeKey (${ambiguous.size} candidates with same priority)")
            // TODO: Report error to FIR
            return null
        }

        log("Found given for: $typeKey -> ${best.symbol}")
        return best
    }

    /**
     * Check if a declaration has @Given annotation.
     */
    fun hasGivenAnnotation(declaration: FirDeclaration): Boolean {
        return declaration.annotations.any { annotation ->
            isGivenAnnotation(annotation)
        }
    }

    /**
     * Check if an annotation is @Given.
     */
    fun isGivenAnnotation(annotation: FirAnnotation): Boolean {
        val annotationType = annotation.annotationTypeRef.coneType
        if (annotationType is ConeClassLikeType) {
            return annotationType.lookupTag.classId == GIVEN_ANNOTATION_CLASS_ID
        }
        return false
    }

    /**
     * Check if a value parameter has @Using annotation.
     */
    fun hasUsingAnnotation(parameter: FirValueParameter): Boolean {
        return parameter.annotations.any { annotation ->
            isUsingAnnotation(annotation)
        }
    }

    /**
     * Check if an annotation is @Using.
     */
    fun isUsingAnnotation(annotation: FirAnnotation): Boolean {
        val annotationType = annotation.annotationTypeRef.coneType
        if (annotationType is ConeClassLikeType) {
            return annotationType.lookupTag.classId == USING_ANNOTATION_CLASS_ID
        }
        return false
    }

    /**
     * Get priority from @Given annotation.
     */
    fun getGivenPriority(declaration: FirDeclaration): Int {
        // TODO: Extract priority from annotation arguments
        // For now, return default priority
        return 0
    }

    /**
     * Analyze a declaration and register it if it has @Given annotation.
     */
    fun analyzeAndRegister(declaration: FirDeclaration) {
        if (!hasGivenAnnotation(declaration)) return

        log("Analyzing @Given declaration: ${declaration.symbol}")

        when (declaration) {
            is FirRegularClass -> {
                // @Given object/class
                val classSymbol = declaration.symbol
                val classType = classSymbol.constructType(emptyArray(), false)

                // Find implemented interfaces/supertypes
                declaration.superTypeRefs.forEach { superTypeRef ->
                    val superType = superTypeRef.coneType
                    if (!superType.isAny && !superType.isNullableAny) {
                        registerGiven(
                            type = superType,
                            symbol = classSymbol,
                            priority = getGivenPriority(declaration),
                            scope = GivenScope.MODULE
                        )
                    }
                }
            }

            is FirProperty -> {
                // @Given val
                val propertyType = declaration.returnTypeRef.coneType
                registerGiven(
                    type = propertyType,
                    symbol = declaration.symbol,
                    priority = getGivenPriority(declaration),
                    scope = GivenScope.MODULE
                )
            }

            is FirSimpleFunction -> {
                // @Given fun (derived given)
                val returnType = declaration.returnTypeRef.coneType

                // Collect @Using parameter types as dependencies
                val dependencies = declaration.valueParameters
                    .filter { hasUsingAnnotation(it) }
                    .map { it.returnTypeRef.coneType }

                registerGiven(
                    type = returnType,
                    symbol = declaration.symbol,
                    priority = getGivenPriority(declaration),
                    scope = GivenScope.MODULE,
                    isDerived = dependencies.isNotEmpty(),
                    dependencies = dependencies
                )
            }

            else -> {
                log("Unsupported @Given declaration type: ${declaration::class.simpleName}")
            }
        }
    }

    /**
     * Get all registered givens (for debugging).
     */
    fun getAllGivens(): Map<String, List<GivenEntry>> = givenInstances.toMap()

    /**
     * Clear the registry (useful for tests).
     */
    fun clear() {
        givenInstances.clear()
        log("Registry cleared")
    }
}
