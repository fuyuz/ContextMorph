@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package io.github.fuyuz.contextmorph

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class KctforkVisibilityMemberPrivateTest {
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
    fun `private member given is visible inside its object`() {
        val compilationResult = compile(
            SourceFile.kotlin(
                "InsideObject.kt",
                """
                package test

                import io.github.fuyuz.contextmorph.Given
                import io.github.fuyuz.contextmorph.summon

                interface Tag<T> {
                    fun label(value: T): String
                }

                object Scope {
                    @Given
                    private val tagA: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }

                    fun inside(): String = summon<Tag<Int>>().label(1)
                }

                val result: String = Scope.inside()
                """.trimIndent(),
            ),
        )

        assertEquals(
            KotlinCompilation.ExitCode.OK,
            compilationResult.exitCode,
            compilationResult.messages,
        )

        val ktClass = compilationResult.classLoader.loadClass("test.InsideObjectKt")
        val getter = ktClass.getDeclaredMethod("getResult")
        val value = getter.invoke(null)
        assertEquals("A", value)
    }

    @Test
    fun `private member given is not visible outside its object`() {
        val compilationResult = compile(
            SourceFile.kotlin(
                "OutsideObject.kt",
                """
                package test

                import io.github.fuyuz.contextmorph.Given
                import io.github.fuyuz.contextmorph.summon

                interface Tag<T> {
                    fun label(value: T): String
                }

                object Scope {
                    @Given
                    private val tagA: Tag<Int> = object : Tag<Int> {
                        override fun label(value: Int): String = "A"
                    }
                }

                val result: String = summon<Tag<Int>>().label(1)
                """.trimIndent(),
            ),
        )

        assertNotEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)
        assertTrue(
            compilationResult.messages.contains("ContextMorph: No given instance found for summon<"),
            compilationResult.messages,
        )
    }
}


