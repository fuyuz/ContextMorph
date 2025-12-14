@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkDuplicateContextTypeTest {
    @Test
    fun `two context parameters of the same type are injected`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            kotlincArguments = listOf("-Xcontext-parameters")
            sources = listOf(
                SourceFile.kotlin(
                    "DuplicateContextType.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    @Given
                    val intTag: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }

                    context(a: Tag<Int>, b: Tag<Int>)
                    fun tagTwice(value: Int): String =
                        a.label(value) + b.label(value)

                    val result: String = tagTwice(1)
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

        val ktClass = compilationResult.classLoader.loadClass("test.DuplicateContextTypeKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("AA", value)
    }
}


