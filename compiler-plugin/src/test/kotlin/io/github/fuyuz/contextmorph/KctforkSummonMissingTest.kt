@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KctforkSummonMissingTest {
    @Test
    fun `summon fails when no given exists`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            sources = listOf(
                SourceFile.kotlin(
                    "SummonMissing.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.summon

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    fun test(): String = summon<Tag<Int>>().label(1)
                    """.trimIndent(),
                )
            )
            compilerPluginRegistrars = listOf(ContextMorphCompilerPluginRegistrar())
            commandLineProcessors = listOf(ContextMorphCommandLineProcessor())
            verbose = false
        }.compile()

        assertNotEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertTrue(
            compilationResult.messages.contains("ContextMorph: No given instance found for summon<"),
            compilationResult.messages,
        )
    }
}


