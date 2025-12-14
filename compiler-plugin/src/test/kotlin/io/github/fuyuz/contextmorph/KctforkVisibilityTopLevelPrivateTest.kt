@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals

class KctforkVisibilityTopLevelPrivateTest {
    private fun compile(vararg sources: SourceFile) =
        KotlinCompilation().apply {
            inheritClassPath = true
            supportsK2 = true
            jvmTarget = "21"
            this.sources = sources.toList()
            compilerPluginRegistrars = listOf(ContextMorphCompilerPluginRegistrar())
            commandLineProcessors = listOf(ContextMorphCommandLineProcessor())
            verbose = false
        }.compile()

    @Test
    fun `private top-level given is visible in the same file`() {
        val compilationResult = compile(
            SourceFile.kotlin(
                "SameFile.kt",
                """
                package test

                import io.github.fuyuz.contextmorph.Given
                import io.github.fuyuz.contextmorph.summon

                interface Tag<T> {
                    fun label(value: T): String
                }

                @Given
                private val tagA: Tag<Int> = object : Tag<Int> {
                    override fun label(value: Int): String = "A"
                }

                val result: String = summon<Tag<Int>>().label(1)
                """.trimIndent(),
            )
        )

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            compilationResult.exitCode,
            compilationResult.messages,
        )

        val ktClass = compilationResult.classLoader.loadClass("test.SameFileKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("A", value)
    }

    @Test
    fun `private top-level given is not visible from another file`() {
        val compilationResult = compile(
            SourceFile.kotlin(
                "FileA.kt",
                """
                package test

                import io.github.fuyuz.contextmorph.Given

                interface Tag<T> {
                    fun label(value: T): String
                }

                @Given
                private val tagA: Tag<Int> = object : Tag<Int> {
                    override fun label(value: Int): String = "A"
                }
                """.trimIndent(),
            ),
            SourceFile.kotlin(
                "FileB.kt",
                """
                package test

                import io.github.fuyuz.contextmorph.Given
                import io.github.fuyuz.contextmorph.summon

                @Given
                val tagB: Tag<Int> = object : Tag<Int> {
                    override fun label(value: Int): String = "B"
                }

                val result: String = summon<Tag<Int>>().label(1)
                """.trimIndent(),
            ),
        )

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            compilationResult.exitCode,
            compilationResult.messages,
        )

        val ktClass = compilationResult.classLoader.loadClass("test.FileBKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("B", value)
    }
}


