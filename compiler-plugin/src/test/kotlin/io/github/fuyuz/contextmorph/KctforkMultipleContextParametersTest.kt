@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkMultipleContextParametersTest {
    @Test
    fun `multiple context parameters are injected`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            kotlincArguments = listOf("-Xcontext-parameters")
            sources = listOf(
                SourceFile.kotlin(
                    "MultipleContextParameters.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given

                    interface Add<T> {
                        fun add(a: T, b: T): T
                    }

                    interface Mul<T> {
                        fun mul(a: T, b: T): T
                    }

                    @Given
                    val intAdd: Add<Int> = object : Add<Int> {
                        override fun add(a: Int, b: Int): Int = a + b
                    }

                    @Given
                    val intMul: Mul<Int> = object : Mul<Int> {
                        override fun mul(a: Int, b: Int): Int = a * b
                    }

                    context(add: Add<Int>, mul: Mul<Int>)
                    fun calc(x: Int, y: Int): Int =
                        mul.mul(add.add(x, y), 2)

                    val result: Int = calc(1, 2)
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

        val ktClass = compilationResult.classLoader.loadClass("test.MultipleContextParametersKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals(6, value)
    }
}


