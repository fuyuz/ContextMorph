@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkUsingInjectionTest {
    @Test
    fun `@Using parameter is automatically injected from @Given`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            sources = listOf(
                SourceFile.kotlin(
                    "UsingInjection.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given
                    import io.github.fuyuz.contextmorph.Using

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    @Given
                    val intTag: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int) = "A"
                    }

                    fun <T> tagLabel(value: T, @Using tag: Tag<T>): String =
                        tag.label(value)

                    val result: String = tagLabel(1)
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

        val ktClass = compilationResult.classLoader.loadClass("test.UsingInjectionKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("A", value)
    }
}


