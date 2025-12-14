@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkExplicitUsingArgumentWinsTest {
    @Test
    fun `explicit @Using argument wins over auto-injection`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            sources = listOf(
                SourceFile.kotlin(
                    "ExplicitUsingWins.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given
                    import io.github.fuyuz.contextmorph.Using

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    @Given
                    val tagA: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }

                    val tagB: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "B"
                    }

                    fun tagLabel(value: Int, @Using tag: Tag<Int>): String =
                        tag.label(value)

                    val result: String = tagLabel(1, tagB)
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

        val ktClass = compilationResult.classLoader.loadClass("test.ExplicitUsingWinsKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("B", value)
    }
}


