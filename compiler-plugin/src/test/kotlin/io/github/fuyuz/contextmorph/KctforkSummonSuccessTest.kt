@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkSummonSuccessTest {
    @Test
    fun `summon resolves a given instance`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            sources = listOf(
                SourceFile.kotlin(
                    "SummonSuccess.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given
                    import io.github.fuyuz.contextmorph.summon

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    @Given
                    object IntTag : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }

                    fun test(): String = summon<Tag<Int>>().label(1)
                    """.trimIndent(),
                )
            )
            compilerPluginRegistrars = listOf(ContextMorphCompilerPluginRegistrar())
            commandLineProcessors = listOf(ContextMorphCommandLineProcessor())
            verbose = false
        }.compile()

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            compilationResult.exitCode,
            compilationResult.messages,
        )

        val ktClass = compilationResult.classLoader.loadClass("test.SummonSuccessKt")
        val method = ktClass.getDeclaredMethod("test")
        val value = method.invoke(null)
        assertEquals("A", value)
    }
}


