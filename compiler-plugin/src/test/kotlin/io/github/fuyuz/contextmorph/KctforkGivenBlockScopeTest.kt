@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkGivenBlockScopeTest {
    @Test
    fun `given block overrides within scope and does not leak`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            sources = listOf(
                SourceFile.kotlin(
                    "GivenBlockScope.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given
                    import io.github.fuyuz.contextmorph.Using
                    import io.github.fuyuz.contextmorph.given

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    @Given
                    val intTagA: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }

                    fun <T> tagLabel(value: T, @Using tag: Tag<T>): String =
                        tag.label(value)

                    fun test(): String {
                        val before = tagLabel(1) // A
                        val inside = run {
                            given<Tag<Int>> {
                                object : Tag<Int> {
                                    override fun label(value: Int): String = "B"
                                }
                            }
                            tagLabel(1) // B
                        }
                        val after = tagLabel(1) // A
                        return before + inside + after
                    }

                    val result: String = test()
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

        val ktClass = compilationResult.classLoader.loadClass("test.GivenBlockScopeKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("ABA", value)
    }
}


