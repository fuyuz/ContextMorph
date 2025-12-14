package io.github.fuyuz.contextmorph

import kotlin.test.Test
import kotlin.test.assertNotNull

class ContextTest {
    @Test
    fun useScope_existsAsFunction() {
        // Without the compiler plugin, these are no-ops
        // This test just verifies the functions exist and can be called
        useScope { it() }
        assertNotNull(Unit)
    }
}
