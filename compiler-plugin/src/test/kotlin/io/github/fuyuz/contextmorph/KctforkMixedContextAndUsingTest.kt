@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkMixedContextAndUsingTest {
    @Test
    fun `context parameters and @Using parameters are both injected`() {
        val compilationResult = KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            kotlincArguments = listOf("-Xcontext-parameters")
            sources = listOf(
                SourceFile.kotlin(
                    "MixedContextAndUsing.kt",
                    """
                    package test

                    import io.github.fuyuz.contextmorph.Given
                    import io.github.fuyuz.contextmorph.Using

                    interface Add<T> {
                        fun add(a: T, b: T): T
                    }

                    interface Mul<T> {
                        fun mul(a: T, b: T): T
                    }

                    interface Tag<T> {
                        fun label(value: T): String
                    }

                    @Given
                    val intAdd: Add<Int> = object : Add<Int> {
                        override fun add(a: Int, b: Int): Int = a + b
                    }

                    @Given
                    val intMul: Mul<Int> = object : Mul<Int> {
                        override fun mul(a: Int, b: Int): Int = a * b
                    }

                    @Given
                    val intTag: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = value.toString()
                    }

                    context(add: Add<Int>)
                    fun calc(x: Int, y: Int, @Using mul: Mul<Int>, @Using tag: Tag<Int>): String {
                        val n = mul.mul(add.add(x, y), 2)
                        return tag.label(n)
                    }

                    val result: String = calc(1, 2)
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

        val ktClass = compilationResult.classLoader.loadClass("test.MixedContextAndUsingKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("6", value)
    }
}


