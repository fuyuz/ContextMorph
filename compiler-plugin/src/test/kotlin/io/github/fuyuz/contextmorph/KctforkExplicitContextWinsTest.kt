@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkExplicitContextWinsTest {
    @Test
    fun `explicit context wins over auto-injection`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            kotlincArguments = listOf("-Xcontext-parameters")
            sources = listOf(
                SourceFile.kotlin(
                    "ExplicitContextWins.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    // Available for auto-injection, but should NOT be used when explicit context is provided.
                    @Given
                    val tagA: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }

                    val tagB: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "B"
                    }

                    context(tag: Tag<Int>)
                    fun tagOnce(value: Int): String = tag.label(value)

                    val result: String = with(tagB) { tagOnce(1) }
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

        val ktClass = compilationResult.classLoader.loadClass("test.ExplicitContextWinsKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("B", value)
    }
}


