package io.github.fuyuz.contextmorph

/**
 * Retrieves the given instance for type [T] from the current scope.
 *
 * This function is transformed by the ContextMorph compiler plugin to
 * return the appropriate given instance. It's similar to Scala 3's `summon`.
 *
 * ## Usage
 *
 * ```kotlin
 * @Given
 * object IntOrd : Ord<Int> {
 *     override fun compare(a: Int, b: Int) = a.compareTo(b)
 * }
 *
 * fun example() {
 *     val ord = summon<Ord<Int>>()  // Returns IntOrd
 *     println(ord.compare(1, 2))    // -1
 * }
 * ```
 *
 * ## Derived Types
 *
 * summon can resolve derived given instances automatically:
 *
 * ```kotlin
 * @Given
 * fun <T> listOrd(@Using elementOrd: Ord<T>): Ord<List<T>> = /* ... */
 *
 * fun example() {
 *     // Automatically constructs listOrd(IntOrd)
 *     val listOrd = summon<Ord<List<Int>>>()
 * }
 * ```
 *
 * ## Compilation Errors
 *
 * - If no given instance is found for [T], a compilation error occurs
 * - If multiple instances match with the same priority, an ambiguity error occurs
 *
 * @param T The type of the given instance to retrieve
 * @return The given instance for type [T]
 * @throws NotImplementedError if called without the compiler plugin
 *
 * @see Given
 * @see Using
 */
inline fun <reified T> summon(): T {
    throw NotImplementedError(
        "summon<${T::class.simpleName}>() requires ContextMorph compiler plugin. " +
        "Make sure the plugin is applied in your build.gradle.kts."
    )
}

/**
 * Defines a block-scoped given instance for type [T].
 *
 * All code following this call within the same block will have access to
 * the provided given instance. This is useful for temporarily overriding
 * a given instance or providing a context-specific value.
 *
 * ## Usage
 *
 * ```kotlin
 * fun example() {
 *     // Default ascending order
 *     val sorted1 = listOf(3, 1, 2).sortedBy()  // [1, 2, 3]
 *
 *     // Override with descending order
 *     given<Ord<Int>> {
 *         object : Ord<Int> {
 *             override fun compare(a: Int, b: Int) = b.compareTo(a)
 *         }
 *     }
 *
 *     // Now uses descending order
 *     val sorted2 = listOf(3, 1, 2).sortedBy()  // [3, 2, 1]
 * }
 * ```
 *
 * ## Scope Rules
 *
 * - The given instance is available from the call point to the end of the enclosing block
 * - Block-scoped givens take highest priority over all other scopes
 * - Multiple `given` calls for the same type override each other (latest wins)
 *
 * ## Nesting
 *
 * ```kotlin
 * fun nested() {
 *     given<Ord<Int>> { AscendingOrd }
 *
 *     {
 *         given<Ord<Int>> { DescendingOrd }
 *         // Uses DescendingOrd here
 *     }
 *
 *     // Back to AscendingOrd
 * }
 * ```
 *
 * @param T The type of the given instance
 * @param provider Lambda that returns the given instance
 * @throws NotImplementedError if called without the compiler plugin
 *
 * @see Given
 * @see summon
 */
inline fun <reified T> given(noinline provider: () -> T) {
    throw NotImplementedError(
        "given<${T::class.simpleName}> { } requires ContextMorph compiler plugin. " +
        "Make sure the plugin is applied in your build.gradle.kts."
    )
}
