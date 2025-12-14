@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KctforkAmbiguousContextParameterTest {
    private fun compile(fileName: String, source: String) =
        KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            kotlincArguments = listOf("-Xcontext-parameters")
            sources = listOf(SourceFile.kotlin(fileName, source.trimIndent()))
            compilerPluginRegistrars = listOf(ContextMorphCompilerPluginRegistrar())
            commandLineProcessors = listOf(ContextMorphCommandLineProcessor())
            verbose = false
        }.compile()

    @Test
    fun `context parameter injection is ambiguous when multiple givens exist`() {
        val compilationResult = compile(
            "AmbiguousContext.kt",
            """
            package test

            import io.github.fuyuz.contextmorph.Given

            interface Tag<T> {
                fun label(value: T): String
            }

            @Given
            val tagA: Tag<Int> = object : Tag<Int> {
                override fun label(value: Int): String = "A"
            }

            @Given
            val tagB: Tag<Int> = object : Tag<Int> {
                override fun label(value: Int): String = "B"
            }

            context(tag: Tag<Int>)
            fun tagOnce(value: Int): String = tag.label(value)

            fun test(): String = tagOnce(1)
            """,
        )

        assertNotEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertTrue(
            compilationResult.messages.contains("ContextMorph: Ambiguous given"),
            compilationResult.messages,
        )
    }

    @Test
    fun `importGivens can disambiguate context parameter injection`() {
        val compilationResult = compile(
            "DisambiguatedContext.kt",
            """
            package test

            import io.github.fuyuz.contextmorph.Given
            import io.github.fuyuz.contextmorph.importGivens

            interface Tag<T> {
                fun label(value: T): String
            }

            object TagGivensA {
                @Given
                val tagA: Tag<Int> = object : Tag<Int> {
                    override fun label(value: Int): String = "A"
                }
            }

            object TagGivensB {
                @Given
                val tagB: Tag<Int> = object : Tag<Int> {
                    override fun label(value: Int): String = "B"
                }
            }

            context(tag: Tag<Int>)
            fun tagOnce(value: Int): String = tag.label(value)

            fun test(): String {
                importGivens(TagGivensA)
                return tagOnce(1)
            }
            """,
        )

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            compilationResult.exitCode,
            compilationResult.messages,
        )

        val ktClass = compilationResult.classLoader.loadClass("test.DisambiguatedContextKt")
        val method = ktClass.getDeclaredMethod("test")
        val value = method.invoke(null)
        assertEquals("A", value)
    }
}


