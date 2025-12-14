@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkDerivedGivenRecursiveTest {
    @Test
    fun `derived givens can be composed recursively`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            sources = listOf(
                SourceFile.kotlin(
                    "DerivedGivenRecursive.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given
                    import io.github.fuyuz.contextmorph.Using
                    import io.github.fuyuz.contextmorph.summon

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    @Given
                    object IntTag : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }

                    @Given
                    fun <T> listTag(@Using elementTag: Tag<T>): Tag<List<T>> =
                        object : Tag<List<T>> {
                            override fun label(value: List<T>): String =
                                value.joinToString(separator = "") { elementTag.label(it) }
                        }

                    val result: String =
                        summon<Tag<List<List<Int>>>>().label(listOf(listOf(1, 2), listOf(3)))
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

        val ktClass = compilationResult.classLoader.loadClass("test.DerivedGivenRecursiveKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("AAA", value)
    }
}


