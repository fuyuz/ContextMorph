package io.github.fuyuz.contextmorph.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirEmptyArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildAnnotationCall
import org.jetbrains.kotlin.fir.plugin.copyFirFunctionWithResolvePhase
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.resolve.substitution.substitutorByMap
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol

/**
 * Generates synthetic overloads to make:
 * - `@Using` parameters optional at call sites (they will be injected later in IR)
 * - `context(...)` parameters optional at call sites (they will be injected later in IR)
 *
 * The generated overloads are intentionally body-less and are expected to be rewritten in IR.
 */
@OptIn(
    ExperimentalTopLevelDeclarationsGenerationApi::class,
    SymbolInternals::class,
    DirectDeclarationsAccess::class,
)
class ContextMorphUsingOverloadGenerationExtension(
    session: FirSession,
) : FirDeclarationGenerationExtension(session) {

    private val usingAnnotationClassId =
        ClassId(FqName("io.github.fuyuz.contextmorph"), Name.identifier("Using"))

    private val givenAnnotationClassId =
        ClassId(FqName("io.github.fuyuz.contextmorph"), Name.identifier("Given"))

    private val lowPriorityAnnotationClassId =
        ClassId(FqName("kotlin.internal"), Name.identifier("LowPriorityInOverloadResolution"))

    override fun getTopLevelCallableIds(): Set<CallableId> {
        val sourceProvider = session.firProvider.symbolProvider
        val namesProvider = sourceProvider.symbolNamesProvider

        val packageNames = namesProvider.getPackageNamesWithTopLevelCallables().orEmpty()
        val result = linkedSetOf<CallableId>()

        for (pkgName in packageNames) {
            val pkgFqName = FqName(pkgName)
            val callables = namesProvider.getTopLevelCallableNamesInPackage(pkgFqName).orEmpty()
            for (callableName in callables) {
                val callableId = CallableId(pkgFqName, callableName)
                val functions = sourceProvider.getTopLevelFunctionSymbols(pkgFqName, callableName)
                if (functions.any { needsSyntheticOverload(it.fir) }) {
                    result += callableId
                }
            }
        }

        return result
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol> {
        // Only top-level generation for now.
        if (context != null) return emptyList()

        val sourceProvider = session.firProvider.symbolProvider
        val originals = sourceProvider.getTopLevelFunctionSymbols(callableId.packageName, callableId.callableName)
            .map { it.fir }
            .filter { needsSyntheticOverload(it) }

        if (originals.isEmpty()) return emptyList()

        return originals.map { original ->
            val copy = copyFirFunctionWithResolvePhase(
                original = original,
                callableId = callableId,
                key = ContextMorphGeneratedDeclarationKey,
                firResolvePhase = FirResolvePhase.BODY_RESOLVE,
            ) {
                // 1) Drop context parameters for the synthetic overload.
                contextParameters.clear()

                // 2) Drop @Using parameters for the synthetic overload.
                val newValueParams = valueParameters.filterNot { it.hasUsingAnnotation() }
                valueParameters.clear()
                valueParameters += newValueParams

                // 3) Remove the body. IR phase is expected to rewrite call sites.
                // NOTE: Keeping the original body would reference dropped parameters and fail FIR validation.
                body = null

                // 4) Avoid ambiguity: prefer the original when it is applicable.
                buildLowPriorityInOverloadResolutionAnnotation()?.let { annotations += it }

                // 5) Fix generic signatures:
                // copyFirFunctionWithResolvePhase creates fresh type parameter symbols, but the copied parameter/return types
                // may still reference the original symbols. Remap them to the newly created type parameters.
                remapCopiedTypeParameters(original)
            }

            copy.symbol
        }
    }

    private fun org.jetbrains.kotlin.fir.declarations.builder.FirSimpleFunctionBuilder.remapCopiedTypeParameters(original: FirSimpleFunction) {
        if (original.typeParameters.isEmpty()) return

        val substitution = original.typeParameters.zip(typeParameters).associate { (oldTp, newTp) ->
            oldTp.symbol to newTp.symbol.defaultType
        }

        val substitutor = substitutorByMap(substitution, useSiteSession = session, allowIdenticalSubstitution = true)

        fun substituteTypeRef(typeRef: FirTypeRef): FirTypeRef {
            val oldType = typeRef.coneType
            val newType = substitutor.substituteOrSelf(oldType)
            if (newType == oldType) return typeRef
            return buildResolvedTypeRef { coneType = newType }
        }

        returnTypeRef = substituteTypeRef(returnTypeRef)

        receiverParameter?.let { receiver ->
            receiver.replaceTypeRef(substituteTypeRef(receiver.typeRef))
        }

        // We clear context parameters above, but keep the substitution logic here in case we extend generation later.
        for (p in contextParameters) {
            p.replaceReturnTypeRef(substituteTypeRef(p.returnTypeRef))
        }
        for (p in valueParameters) {
            p.replaceReturnTypeRef(substituteTypeRef(p.returnTypeRef))
        }
        for (tp in typeParameters) {
            tp.replaceBounds(tp.bounds.map(::substituteTypeRef))
        }
    }

    private fun needsSyntheticOverload(function: FirSimpleFunction): Boolean {
        // Never generate overloads for @Given declarations.
        // Doing so would duplicate givens (original + overload) and lead to ambiguity during resolution.
        if (function.annotations.any { it.isClassId(givenAnnotationClassId) }) return false

        val hasUsing = function.valueParameters.any { it.hasUsingAnnotation() }
        val hasContext = function.contextParameters.isNotEmpty()
        return hasUsing || hasContext
    }

    private fun FirValueParameter.hasUsingAnnotation(): Boolean {
        return annotations.any { ann ->
            val coneType = ann.annotationTypeRef.coneType
            (coneType as? ConeClassLikeType)?.lookupTag?.classId == usingAnnotationClassId
        }
    }

    private fun org.jetbrains.kotlin.fir.expressions.FirAnnotation.isClassId(classId: ClassId): Boolean {
        val coneType = this.annotationTypeRef.coneType
        return (coneType as? ConeClassLikeType)?.lookupTag?.classId == classId
    }

    private fun org.jetbrains.kotlin.fir.declarations.builder.FirSimpleFunctionBuilder.buildLowPriorityInOverloadResolutionAnnotation(): FirAnnotation? {
        val annoClassSymbol = session.symbolProvider.getClassLikeSymbolByClassId(lowPriorityAnnotationClassId) as? FirRegularClassSymbol
            ?: return null
        val ctor = annoClassSymbol.declarationSymbols.filterIsInstance<FirConstructorSymbol>().firstOrNull() ?: return null

        return buildAnnotationCall {
            argumentList = FirEmptyArgumentList
            annotationTypeRef = buildResolvedTypeRef {
                coneType = annoClassSymbol.defaultType()
            }
            calleeReference = buildResolvedNamedReference {
                name = annoClassSymbol.name
                resolvedSymbol = ctor
            }
            containingDeclarationSymbol = this@buildLowPriorityInOverloadResolutionAnnotation.symbol
        }
    }
}


