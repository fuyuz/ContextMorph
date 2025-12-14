package io.github.fuyuz.contextmorph.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey

/**
 * A shared key for ContextMorph-generated FIR declarations.
 *
 * FIR declarations generated with this key are expected to be recognized and
 * post-processed in the IR phase (e.g., call-site rewriting / implicit injection).
 */
object ContextMorphGeneratedDeclarationKey : GeneratedDeclarationKey()


