@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KctforkImportGivensTest {
    private fun compile(fileName: String, source: String) =
        KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            sources = listOf(SourceFile.kotlin(fileName, source.trimIndent()))
            compilerPluginRegistrars = listOf(ContextMorphCompilerPluginRegistrar())
            commandLineProcessors = listOf(ContextMorphCommandLineProcessor())
            verbose = false
        }.compile()

    @Test
    fun `without importGivens compilation fails with ambiguity`() {
        val compilationResult = compile(
            "Ambiguous.kt",
            """
            package test

            import io.github.fuyuz.contextmorph.Given
            import io.github.fuyuz.contextmorph.Using

            interface Tag<T> {
                fun label(value: T): String
            }

            @Given
            val tagA: Tag<Int> = object : Tag<Int> {
                override fun label(value: Int) = "A"
            }

            @Given
            val tagB: Tag<Int> = object : Tag<Int> {
                override fun label(value: Int) = "B"
            }

            fun <T> tagLabel(value: T, @Using tag: Tag<T>): String =
                tag.label(value)

            fun test(): String = tagLabel(1)
            """,
        )

        assertNotEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertTrue(
            compilationResult.messages.contains("ContextMorph: Ambiguous given"),
            compilationResult.messages,
        )
    }

    @Test
    fun `importGivens disambiguates and injection succeeds`() {
        val compilationResult = compile(
            "ImportGivens.kt",
            """
            package test

            import io.github.fuyuz.contextmorph.Given
            import io.github.fuyuz.contextmorph.Using
            import io.github.fuyuz.contextmorph.importGivens

            interface Tag<T> {
                fun label(value: T): String
            }

            object TagGivensA {
                @Given
                val tagA: Tag<Int> = object : Tag<Int> {
                    override fun label(value: Int) = "A"
                }
            }

            object TagGivensB {
                @Given
                val tagB: Tag<Int> = object : Tag<Int> {
                    override fun label(value: Int) = "B"
                }
            }

            fun <T> tagLabel(value: T, @Using tag: Tag<T>): String =
                tag.label(value)

            fun test(): String {
                importGivens(TagGivensA)
                return tagLabel(1)
            }
            """,
        )

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            compilationResult.exitCode,
            compilationResult.messages,
        )

        val ktClass = compilationResult.classLoader.loadClass("test.ImportGivensKt")
        val method = ktClass.getDeclaredMethod("test")
        val value = method.invoke(null)
        assertEquals("A", value)
    }
}


