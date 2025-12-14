package io.github.fuyuz.contextmorph.transform

import io.github.fuyuz.contextmorph.ContextMorphSymbols
import io.github.fuyuz.contextmorph.fir.ContextMorphGeneratedDeclarationKey
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.expressions.IrBody
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.getClass
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.makeTypeProjection
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.kotlin.ir.types.IrTypeSubstitutor
import java.io.File

/**
 * IR transformer for ContextMorph's given/using/context-parameters features.
 *
 * Responsibilities:
 * - Rewrites calls to synthetic overloads (generated in FIR) into calls to the original declaration,
 *   injecting required @Using parameters and context parameters from available givens.
 * - Rewrites summon<T>() into the resolved given instance.
 * - Rewrites given<T> { ... } statement into a local value declaration and registers it for subsequent resolution.
 */
@OptIn(DeprecatedForRemovalCompilerApi::class)
class GivenUsingTransformer(
    private val moduleFragment: IrModuleFragment,
    private val pluginContext: IrPluginContext,
    private val symbols: ContextMorphSymbols,
    private val givenRegistry: IrGivenRegistry,
) : IrElementTransformerVoidWithContext() {

    companion object {
        private val USING_ANNOTATION_FQ_NAME = FqName("io.github.fuyuz.contextmorph.Using")

        private fun log(message: String) {
            try {
                val logFile = File("/tmp/contextmorph-plugin.log")
                logFile.appendText("[${java.time.LocalDateTime.now()}] [GivenUsingTransformer] $message\n")
            } catch (_: Exception) {
                // Ignore
            }
            System.err.println("[ContextMorph GivenUsingTransformer] $message")
        }
    }

    private data class TopLevelKey(val packageFqName: String, val name: String)

    private val overloadToOriginal = mutableMapOf<IrSimpleFunction, IrSimpleFunction>()

    // Stack of block-scoped givens: typeKey -> variable symbol (latest wins within a scope).
    private val blockGivenStack = ArrayDeque<MutableMap<String, org.jetbrains.kotlin.ir.symbols.IrVariableSymbol>>()

    // Stack of imported givens (Scala 3 style "given imports"): declarations that are in local scope.
    private val importedGivenStack = ArrayDeque<MutableList<IrGivenRegistry.IrGivenEntry>>()

    private val todoFunctionSymbol by lazy {
        val callableId = CallableId(FqName("kotlin"), Name.identifier("TODO"))
        val candidates = pluginContext.referenceFunctions(callableId)
        candidates.singleOrNull { it.owner.valueParameters.size == 1 }
            ?: candidates.firstOrNull()
            ?: error("ContextMorph: kotlin.TODO not found")
    }

    init {
        buildSyntheticOverloadIndex()
    }

    override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
        val result = super.visitSimpleFunction(declaration)

        // FIR-generated overloads are created without bodies and must be given a body in IR,
        // otherwise JVM backend fails with "Function has no body".
        if (isContextMorphGenerated(declaration.origin) && declaration.body == null) {
            // Use UNDEFINED_OFFSET to avoid line number mapping assertions for synthetic files (fileEntry may not map offsets).
            val builder = DeclarationIrBuilder(pluginContext, declaration.symbol, UNDEFINED_OFFSET, UNDEFINED_OFFSET)
            val todoCall = builder.irCall(todoFunctionSymbol).apply {
                if (todoFunctionSymbol.owner.valueParameters.size == 1) {
                    putValueArgument(0, builder.irString("ContextMorph synthetic overload stub; call should be rewritten in IR"))
                }
            }
            declaration.body = builder.irBlockBody {
                +irReturn(todoCall)
            }
        }

        return result
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        // Transform children first to ensure nested blocks are processed.
        // We still need sequential processing for statements in this block for block-scoped givens.
        blockGivenStack.addLast(mutableMapOf())
        importedGivenStack.addLast(mutableListOf())
        val newStatements = mutableListOf<IrStatement>()
        for (stmt in expression.statements) {
            val transformed = stmt.transform(this, null) as IrStatement
            if (handleImportGivensStatementIfNeeded(transformed)) continue
            val handled = handleGivenStatementIfNeeded(transformed, newStatements)
            if (!handled) newStatements += transformed
        }
        expression.statements.clear()
        expression.statements.addAll(newStatements)
        blockGivenStack.removeLast()
        importedGivenStack.removeLast()
        return expression
    }

    override fun visitBlockBody(body: IrBlockBody): IrBody {
        blockGivenStack.addLast(mutableMapOf())
        importedGivenStack.addLast(mutableListOf())
        val newStatements = mutableListOf<IrStatement>()
        for (stmt in body.statements) {
            val transformed = stmt.transform(this, null) as IrStatement
            if (handleImportGivensStatementIfNeeded(transformed)) continue
            val handled = handleGivenStatementIfNeeded(transformed, newStatements)
            if (!handled) newStatements += transformed
        }
        body.statements.clear()
        body.statements.addAll(newStatements)
        blockGivenStack.removeLast()
        importedGivenStack.removeLast()
        return body
    }

    override fun visitCall(expression: IrCall): IrExpression {
        val transformed = super.visitCall(expression) as IrCall

        // 1) summon<T>()
        if (symbols.isSummonCall(transformed.symbol)) {
            val requested = transformed.getTypeArgument(0)
            if (requested == null) return transformed
            val resolved = resolveGivenExpression(requested, transformed.startOffset, transformed.endOffset)
            if (resolved != null) {
                log("Transformed summon<$requested>() at ${transformed.startOffset}")
                return resolved
            }
            throw IllegalStateException("ContextMorph: No given instance found for summon<${requested.render()}>()")
        }

        // 2) Synthetic overload call (generated in FIR)
        val callee = transformed.symbol.owner as? IrSimpleFunction
        if (callee != null && isContextMorphGenerated(callee.origin)) {
            val original = overloadToOriginal.getOrPut(callee) { findOriginalForSyntheticOverload(callee) ?: callee }
            if (original !== callee) {
                val rewritten = rewriteSyntheticOverloadCall(transformed, callee, original)
                if (rewritten != null) return rewritten
            }
        }

        return transformed
    }

    private fun buildSyntheticOverloadIndex() {
        overloadToOriginal.clear()

        val byKey = mutableMapOf<TopLevelKey, MutableList<IrSimpleFunction>>()

        for (file in moduleFragment.files) {
            val pkg = file.packageFqName.asString()
            for (decl in file.declarations) {
                val fn = decl as? IrSimpleFunction ?: continue
                byKey.getOrPut(TopLevelKey(pkg, fn.name.asString())) { mutableListOf() }.add(fn)
            }
        }

        var mapped = 0
        for ((key, functions) in byKey) {
            val synthetic = functions.filter { isContextMorphGenerated(it.origin) }
            if (synthetic.isEmpty()) continue

            val nonSynthetic = functions.filterNot { isContextMorphGenerated(it.origin) }
            for (syn in synthetic) {
                val original = nonSynthetic.singleOrNull { strippedSignatureEquals(it, syn) }
                if (original != null) {
                    overloadToOriginal[syn] = original
                    mapped++
                } else {
                    // Leave unmapped; we will try again lazily.
                    log("WARN: Could not uniquely map synthetic overload ${key.packageFqName}.${key.name}(${syn.parameters.size} params)")
                }
            }
        }

        log("Synthetic overload index: mapped $mapped overload(s)")
    }

    private fun strippedSignatureEquals(original: IrSimpleFunction, overload: IrSimpleFunction): Boolean {
        val origStripped = original.parameters.filterNot { it.kind == org.jetbrains.kotlin.ir.declarations.IrParameterKind.Context || it.isUsingParameter() }
        if (origStripped.size != overload.parameters.size) return false

        return origStripped.zip(overload.parameters).all { (a, b) ->
            a.kind == b.kind && a.type.render() == b.type.render()
        }
    }

    private fun IrValueParameter.isUsingParameter(): Boolean {
        if (kind != org.jetbrains.kotlin.ir.declarations.IrParameterKind.Regular) return false
        return hasAnnotation(USING_ANNOTATION_FQ_NAME)
    }

    private fun isContextMorphGenerated(origin: IrDeclarationOrigin): Boolean {
        val pluginOrigin = origin as? IrDeclarationOrigin.GeneratedByPlugin ?: return false
        val key: GeneratedDeclarationKey = pluginOrigin.pluginKey ?: return false
        return key == ContextMorphGeneratedDeclarationKey
    }

    private fun findOriginalForSyntheticOverload(overload: IrSimpleFunction): IrSimpleFunction? {
        // Fallback: search within the same package across the whole module.
        // NOTE: FIR-generated declarations may be placed into synthetic files, so `overload.parent` is not reliable.
        val pkg = overload.getPackageFragment()?.packageFqName?.asString() ?: return null
        val key = TopLevelKey(pkg, overload.name.asString())

        val candidates = mutableListOf<IrSimpleFunction>()
        for (file in moduleFragment.files) {
            if (file.packageFqName.asString() != key.packageFqName) continue
            for (decl in file.declarations) {
                val fn = decl as? IrSimpleFunction ?: continue
                if (fn.name.asString() != key.name) continue
                if (isContextMorphGenerated(fn.origin)) continue
                candidates += fn
            }
        }

        return candidates.singleOrNull { strippedSignatureEquals(it, overload) }
    }

    private fun rewriteSyntheticOverloadCall(call: IrCall, overload: IrSimpleFunction, original: IrSimpleFunction): IrExpression? {
        val currentScopeSymbol = currentScope?.scope?.scopeOwnerSymbol ?: return null
        val builder = DeclarationIrBuilder(pluginContext, currentScopeSymbol, call.startOffset, call.endOffset)

        val newCall = builder.irCall(original.symbol).apply {
            origin = call.origin
            type = call.type
            superQualifierSymbol = call.superQualifierSymbol
        }

        // Copy type arguments by index.
        val originalTypeParams = original.typeParameters
        for (i in originalTypeParams.indices) {
            val t = call.getTypeArgument(i) ?: originalTypeParams[i].defaultType
            newCall.putTypeArgument(i, t)
        }

        // Collect explicit regular arguments from the overload call (in order).
        val explicitRegularArgs = mutableListOf<IrExpression?>()
        for (p in overload.parameters) {
            when (p.kind) {
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.DispatchReceiver -> {
                    newCall.dispatchReceiver = call.dispatchReceiver
                }
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.ExtensionReceiver -> {
                    newCall.extensionReceiver = call.extensionReceiver
                }
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.Context -> {
                    // Synthetic overload should not have context parameters.
                }
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.Regular -> {
                    explicitRegularArgs += call.arguments[p.indexInParameters]
                }
            }
        }

        val substitutor = buildTypeSubstitutor(original.typeParameters, newCall)

        var nextExplicit = 0
        for (p in original.parameters) {
            when (p.kind) {
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.DispatchReceiver -> {
                    // already handled
                }
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.ExtensionReceiver -> {
                    // already handled
                }
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.Context -> {
                    val injectedType = substitutor?.substitute(p.type) ?: p.type
                    val injected = resolveGivenExpression(injectedType, call.startOffset, call.endOffset)
                        ?: throw IllegalStateException("ContextMorph: No given instance found for context parameter ${injectedType.render()}")
                    newCall.arguments[p.indexInParameters] = injected
                }
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.Regular -> {
                    if (p.isUsingParameter()) {
                        val injectedType = substitutor?.substitute(p.type) ?: p.type
                        val injected = resolveGivenExpression(injectedType, call.startOffset, call.endOffset)
                            ?: throw IllegalStateException("ContextMorph: No given instance found for @Using parameter ${injectedType.render()}")
                        newCall.arguments[p.indexInParameters] = injected
                    } else {
                        newCall.arguments[p.indexInParameters] = explicitRegularArgs.getOrNull(nextExplicit)
                        nextExplicit++
                    }
                }
            }
        }

        log("Rewrote synthetic overload call ${overload.name} -> original ${original.name}")
        return newCall
    }

    private fun buildTypeSubstitutor(typeParameters: List<IrTypeParameter>, call: IrCall): org.jetbrains.kotlin.ir.types.AbstractIrTypeSubstitutor? {
        if (typeParameters.isEmpty()) return null
        val typeArgs = typeParameters.indices.map { idx ->
            val t = call.getTypeArgument(idx) ?: typeParameters[idx].defaultType
            makeTypeProjection(t, Variance.INVARIANT)
        }
        return IrTypeSubstitutor(typeParameters.map { it.symbol }, typeArgs, allowEmptySubstitution = true)
    }

    private fun resolveGivenExpression(requestedType: IrType, startOffset: Int, endOffset: Int): IrExpression? =
        resolveGivenExpression(requestedType, startOffset, endOffset, mutableSetOf())

    private fun resolveGivenExpression(
        requestedType: IrType,
        startOffset: Int,
        endOffset: Int,
        visiting: MutableSet<String>,
    ): IrExpression? {
        // 1) Block-scoped givens (latest wins, inner scope first)
        val typeKey = requestedType.render()
        for (scope in blockGivenStack.reversed()) {
            val symbol = scope[typeKey]
            if (symbol != null) {
                return IrGetValueImpl(startOffset, endOffset, requestedType, symbol)
            }
        }

        // 2) Prevent infinite recursion for derived givens.
        if (!visiting.add(typeKey)) return null

        try {
            // 3) Imported givens (Scala 3 style local scope).
            val imported = resolveImportedGivenExpression(requestedType, startOffset, endOffset, visiting)
            if (imported != null) return imported

            // 4) Global @Given instances (IR-collected)
            return resolveGlobalGivenExpression(requestedType, startOffset, endOffset, visiting)
        } finally {
            // Keep stack-like semantics
            visiting.remove(typeKey)
        }
    }

    private fun resolveImportedGivenExpression(
        requestedType: IrType,
        startOffset: Int,
        endOffset: Int,
        visiting: MutableSet<String>,
    ): IrExpression? {
        // Search inner scope first (latest wins by scope nesting, but still ambiguous within same scope).
        val callSiteFile = currentFile
        val callSiteClass = currentClass?.irElement as? org.jetbrains.kotlin.ir.declarations.IrClass

        for (scope in importedGivenStack.reversed()) {
            if (scope.isEmpty()) continue

            val candidates = mutableListOf<GlobalCandidate>()
            for (entry in scope) {
                if (!isGivenVisibleFromHere(entry.declaration, callSiteFile, callSiteClass)) continue
                when (val decl = entry.declaration) {
                    is org.jetbrains.kotlin.ir.declarations.IrClass -> {
                        if (decl.isObject && typesEqualByRender(entry.providedType, requestedType)) {
                            candidates += GlobalCandidate(entry, kindRank = 2)
                        }
                    }
                    is org.jetbrains.kotlin.ir.declarations.IrProperty -> {
                        if (typesEqualByRender(entry.providedType, requestedType)) {
                            candidates += GlobalCandidate(entry, kindRank = 2)
                        }
                    }
                    is org.jetbrains.kotlin.ir.declarations.IrSimpleFunction -> {
                        val substitution = unifyFunctionReturnType(decl, requestedType) ?: continue
                        candidates += GlobalCandidate(entry, kindRank = 1, typeArgSubstitution = substitution)
                    }
                    else -> Unit
                }
            }

            if (candidates.isEmpty()) continue
            if (candidates.size != 1) {
                val rendered = candidates.joinToString(separator = ", ") { it.entry.declaration.render() }
                throw IllegalStateException("ContextMorph: Ambiguous imported given for ${requestedType.render()}: $rendered")
            }
            return createGlobalGivenExpression(candidates.single(), requestedType, startOffset, endOffset, visiting)
        }

        return null
    }

    private data class GlobalCandidate(
        val entry: IrGivenRegistry.IrGivenEntry,
        val kindRank: Int, // object/property > function
        val typeArgSubstitution: Map<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType> = emptyMap(),
    )

    private fun resolveGlobalGivenExpression(
        requestedType: IrType,
        startOffset: Int,
        endOffset: Int,
        visiting: MutableSet<String>,
    ): IrExpression? {
        val candidates = mutableListOf<GlobalCandidate>()

        val callSiteFile = currentFile
        val callSiteClass = currentClass?.irElement as? org.jetbrains.kotlin.ir.declarations.IrClass

        for (entry in givenRegistry.getAllEntries()) {
            if (!isGivenVisibleFromHere(entry.declaration, callSiteFile, callSiteClass)) continue
            when (val decl = entry.declaration) {
                is org.jetbrains.kotlin.ir.declarations.IrClass -> {
                    if (decl.isObject && typesEqualByRender(entry.providedType, requestedType)) {
                        candidates += GlobalCandidate(entry, kindRank = 2)
                    }
                }
                is org.jetbrains.kotlin.ir.declarations.IrProperty -> {
                    if (typesEqualByRender(entry.providedType, requestedType)) {
                        candidates += GlobalCandidate(entry, kindRank = 2)
                    }
                }
                is org.jetbrains.kotlin.ir.declarations.IrSimpleFunction -> {
                    val substitution = unifyFunctionReturnType(decl, requestedType) ?: continue
                    candidates += GlobalCandidate(entry, kindRank = 1, typeArgSubstitution = substitution)
                }
                else -> Unit
            }
        }

        if (candidates.isEmpty()) return null

        // Scala 3 style: if multiple candidates are applicable and none is strictly "more specific",
        // report ambiguity instead of using an ad-hoc numeric priority.
        if (candidates.size != 1) {
            val rendered = candidates.joinToString(separator = ", ") { it.entry.declaration.render() }
            throw IllegalStateException(
                "ContextMorph: Ambiguous given for ${requestedType.render()}: $rendered"
            )
        }

        return createGlobalGivenExpression(candidates.single(), requestedType, startOffset, endOffset, visiting)
    }

    private fun handleImportGivensStatementIfNeeded(statement: IrStatement): Boolean {
        val call = statement as? IrCall ?: return false
        if (!symbols.isImportGivensCall(call.symbol)) return false

        val scopeArg = call.getValueArgument(0) ?: return true // drop statement
        val scopeClass = scopeArg.type.getClass()
        if (scopeClass == null || !scopeClass.isObject) {
            throw IllegalStateException("ContextMorph: importGivens(...) requires an object expression")
        }

        val imported = mutableListOf<IrGivenRegistry.IrGivenEntry>()
        for (entry in givenRegistry.getAllEntries()) {
            // Only entries declared inside the scope object.
            val ownerClass = entry.declaration.parents.filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrClass>().firstOrNull() ?: continue
            if (ownerClass != scopeClass) continue
            imported += entry
        }

        importedGivenStack.lastOrNull()?.addAll(imported)
        log("Imported ${imported.size} givens from ${scopeClass.name.asString()}")
        return true // remove marker statement
    }

    private fun isGivenVisibleFromHere(
        declaration: org.jetbrains.kotlin.ir.declarations.IrDeclaration,
        callSiteFile: IrFile,
        callSiteClass: org.jetbrains.kotlin.ir.declarations.IrClass?,
    ): Boolean {
        val declWithVis = declaration as? org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility ?: return true
        val vis = declWithVis.visibility

        // Public is always visible.
        if (vis == org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PUBLIC) return true

        // Internal: we only see current module declarations anyway; treat as visible here.
        if (vis == org.jetbrains.kotlin.descriptors.DescriptorVisibilities.INTERNAL) return true

        // File-private / class-private:
        if (vis == org.jetbrains.kotlin.descriptors.DescriptorVisibilities.PRIVATE) {
            // If this is a class/object member, private means "visible only within that class/object (and nested)".
            val parent = declaration.parent
            if (parent is org.jetbrains.kotlin.ir.declarations.IrClass) {
                val ownerClass = parent
                val csClass = callSiteClass ?: return false
                return csClass == ownerClass ||
                    csClass.parents.filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrClass>().any { it == ownerClass }
            }

            // Otherwise, treat as file-private: visible only within the same file.
            val declFile = (parent as? org.jetbrains.kotlin.ir.declarations.IrFile)
                ?: declaration.parents.filterIsInstance<org.jetbrains.kotlin.ir.declarations.IrFile>().firstOrNull()
                ?: return false
            return declFile == callSiteFile
        }

        // Protected/Local/etc: conservatively treat as visible within current module for now.
        return true
    }

    private fun createGlobalGivenExpression(
        candidate: GlobalCandidate,
        requestedType: IrType,
        startOffset: Int,
        endOffset: Int,
        visiting: MutableSet<String>,
    ): IrExpression? {
        return when (val decl = candidate.entry.declaration) {
            is org.jetbrains.kotlin.ir.declarations.IrClass -> {
                givenRegistry.createGivenAccessExpression(candidate.entry, startOffset, endOffset)
            }
            is org.jetbrains.kotlin.ir.declarations.IrProperty -> {
                createPropertyAccessExpression(decl, startOffset, endOffset)
            }
            is org.jetbrains.kotlin.ir.declarations.IrSimpleFunction -> {
                createDerivedGivenCallExpression(
                    decl,
                    requestedType,
                    candidate.typeArgSubstitution,
                    startOffset,
                    endOffset,
                    visiting,
                )
            }
            else -> null
        }
    }

    private fun createPropertyAccessExpression(
        property: org.jetbrains.kotlin.ir.declarations.IrProperty,
        startOffset: Int,
        endOffset: Int,
    ): IrExpression? {
        val getter = property.getter ?: return null
        val currentScopeSymbol = currentScope?.scope?.scopeOwnerSymbol ?: return null
        val builder = DeclarationIrBuilder(pluginContext, currentScopeSymbol, startOffset, endOffset)

        val call = builder.irCall(getter.symbol).apply {
            origin = IrStatementOrigin.GET_PROPERTY
        }

        val dispatchParam = getter.dispatchReceiverParameter
        if (dispatchParam != null) {
            val parentClass = property.parent as? org.jetbrains.kotlin.ir.declarations.IrClass
            if (parentClass != null && parentClass.isObject) {
                call.dispatchReceiver = org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl(
                    startOffset,
                    endOffset,
                    parentClass.defaultType,
                    parentClass.symbol
                )
            } else {
                // We only support properties on objects for now.
                return null
            }
        }

        return call
    }

    private fun createDerivedGivenCallExpression(
        function: org.jetbrains.kotlin.ir.declarations.IrSimpleFunction,
        requestedType: IrType,
        typeArgSubstitution: Map<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>,
        startOffset: Int,
        endOffset: Int,
        visiting: MutableSet<String>,
    ): IrExpression? {
        val currentScopeSymbol = currentScope?.scope?.scopeOwnerSymbol ?: return null
        val builder = DeclarationIrBuilder(pluginContext, currentScopeSymbol, startOffset, endOffset)

        val call = builder.irCall(function.symbol).apply {
            origin = null
            type = requestedType
        }

        // Type arguments
        for ((idx, tp) in function.typeParameters.withIndex()) {
            val argType = typeArgSubstitution[tp.symbol] ?: return null
            call.putTypeArgument(idx, argType)
        }

        val substitutor = buildTypeSubstitutor(function.typeParameters, call)

        // Dispatch receiver if needed (only support objects for now)
        function.dispatchReceiverParameter?.let {
            val parentClass = function.parent as? org.jetbrains.kotlin.ir.declarations.IrClass
            if (parentClass != null && parentClass.isObject) {
                call.dispatchReceiver = org.jetbrains.kotlin.ir.expressions.impl.IrGetObjectValueImpl(
                    startOffset,
                    endOffset,
                    parentClass.defaultType,
                    parentClass.symbol
                )
            } else {
                return null
            }
        }

        // Fill context + @Using parameters as dependencies
        for (p in function.parameters) {
            when (p.kind) {
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.DispatchReceiver,
                org.jetbrains.kotlin.ir.declarations.IrParameterKind.ExtensionReceiver,
                -> {
                    // receivers handled separately / not supported for derived givens yet
                }

                org.jetbrains.kotlin.ir.declarations.IrParameterKind.Context -> {
                    val needType = substitutor?.substitute(p.type) ?: p.type
                    val arg = resolveGivenExpression(needType, startOffset, endOffset, visiting) ?: return null
                    call.arguments[p.indexInParameters] = arg
                }

                org.jetbrains.kotlin.ir.declarations.IrParameterKind.Regular -> {
                    if (p.isUsingParameter()) {
                        val needType = substitutor?.substitute(p.type) ?: p.type
                        val arg = resolveGivenExpression(needType, startOffset, endOffset, visiting) ?: return null
                        call.arguments[p.indexInParameters] = arg
                    } else {
                        // Non-@Using parameter makes this function unusable as a derived given (for now),
                        // unless it has a default value.
                        if (p.defaultValue == null) return null
                    }
                }
            }
        }

        return call
    }

    private fun typesEqualByRender(a: IrType, b: IrType): Boolean = a.render() == b.render()

    private fun unifyFunctionReturnType(
        function: org.jetbrains.kotlin.ir.declarations.IrSimpleFunction,
        requestedType: IrType,
    ): Map<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>? {
        val mapping = linkedMapOf<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>()
        if (!unifyTypes(function.returnType, requestedType, mapping)) return null

        // Ensure all function type parameters are bound (otherwise we can't call it deterministically).
        for (tp in function.typeParameters) {
            if (mapping[tp.symbol] == null) return null
        }
        return mapping
    }

    private fun unifyTypes(
        template: IrType,
        target: IrType,
        mapping: MutableMap<org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol, IrType>,
    ): Boolean {
        val templateSimple = template as? org.jetbrains.kotlin.ir.types.IrSimpleType
        val targetSimple = target as? org.jetbrains.kotlin.ir.types.IrSimpleType

        // Type parameter: bind
        val templateClassifier = templateSimple?.classifier
        if (templateClassifier is org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol) {
            val existing = mapping[templateClassifier]
            if (existing == null) {
                mapping[templateClassifier] = target
                return true
            }
            return existing.render() == target.render()
        }

        // Must be simple class types for now
        if (templateSimple == null || targetSimple == null) return false

        if (templateSimple.classifier != targetSimple.classifier) return false
        if (templateSimple.arguments.size != targetSimple.arguments.size) return false

        return templateSimple.arguments.zip(targetSimple.arguments).all { (a, b) ->
            when {
                a is org.jetbrains.kotlin.ir.types.IrStarProjection && b is org.jetbrains.kotlin.ir.types.IrStarProjection -> true
                a is org.jetbrains.kotlin.ir.types.IrTypeProjection && b is org.jetbrains.kotlin.ir.types.IrTypeProjection ->
                    unifyTypes(a.type, b.type, mapping)
                else -> false
            }
        }
    }

    private fun handleGivenStatementIfNeeded(statement: IrStatement, out: MutableList<IrStatement>): Boolean {
        val call = statement as? IrCall ?: return false
        if (!symbols.isGivenCall(call.symbol)) return false

        val typeArg = call.getTypeArgument(0) ?: return true // drop statement
        val provider = call.getValueArgument(0) ?: return true

        val currentScopeSymbol = currentScope?.scope?.scopeOwnerSymbol ?: return true
        val builder = DeclarationIrBuilder(pluginContext, currentScopeSymbol, call.startOffset, call.endOffset)

        val invokeCall = buildInvokeCall(builder, provider) ?: return true

        val variable = builder.scope.createTemporaryVariableDeclaration(
            irType = typeArg,
            nameHint = "__contextmorph_given_${out.size}",
            isMutable = false,
            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
            startOffset = call.startOffset,
            endOffset = call.endOffset,
        ).apply {
            initializer = invokeCall
        }

        // Register into current block scope (latest wins).
        blockGivenStack.lastOrNull()?.put(typeArg.render(), variable.symbol)

        out += variable
        log("Registered block-scoped given for $typeArg")
        return true
    }

    private fun buildInvokeCall(builder: DeclarationIrBuilder, functionExpr: IrExpression): IrCall? {
        val functionClass = functionExpr.type.getClass() ?: return null
        val invokeFun = functionClass.declarations
            .filterIsInstance<IrSimpleFunction>()
            .firstOrNull { it.name.asString() == "invoke" } ?: return null

        return builder.irCall(invokeFun.symbol).apply {
            dispatchReceiver = functionExpr
        }
    }
}


