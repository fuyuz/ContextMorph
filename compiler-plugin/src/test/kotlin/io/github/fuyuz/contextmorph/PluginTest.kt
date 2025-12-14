package io.github.fuyuz.contextmorph

import org.junit.Test
import kotlin.test.assertNotNull

class PluginTest {
    @Test
    fun `plugin components are accessible`() {
        // Basic sanity test to verify plugin compiles
        val registrar = ContextMorphCompilerPluginRegistrar()
        assertNotNull(registrar)
    }
}
