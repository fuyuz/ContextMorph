package io.github.fuyuz.contextmorph

/**
 * Marks a declaration as a given instance for implicit resolution.
 *
 * Given instances are canonical values for specific types that can be
 * automatically resolved by the compiler when needed. This is similar
 * to Scala 3's `given` keyword.
 *
 * ## Usage
 *
 * ### Object given (singleton instance)
 * ```kotlin
 * @Given
 * object IntOrd : Ord<Int> {
 *     override fun compare(a: Int, b: Int) = a.compareTo(b)
 * }
 * ```
 *
 * ### Property given
 * ```kotlin
 * @Given
 * val intShow: Show<Int> = object : Show<Int> {
 *     override fun show(value: Int) = value.toString()
 * }
 * ```
 *
 * ### Derived given (depends on other givens)
 * ```kotlin
 * @Given
 * fun <T> listOrd(@Using elementOrd: Ord<T>): Ord<List<T>> = object : Ord<List<T>> {
 *     override fun compare(a: List<T>, b: List<T>): Int {
 *         // comparison logic using elementOrd
 *     }
 * }
 * ```
 *
 * ## Priority
 *
 * When multiple given instances exist for the same type, priority determines
 * which one is selected. Higher priority values take precedence.
 *
 * ```kotlin
 * @Given(priority = 10)  // Higher priority, selected first
 * object HighPriorityOrd : Ord<Int> { ... }
 *
 * @Given(priority = 0)   // Default priority
 * object DefaultOrd : Ord<Int> { ... }
 * ```
 *
 * ## Scope
 *
 * Given instances follow scope hierarchy:
 * 1. Block scope (local `given { }` calls) - highest priority
 * 2. Function parameter scope (@Using parameters)
 * 3. Class scope
 * 4. File scope
 * 5. Module/package scope - lowest priority
 *
 * @property priority Priority for conflict resolution (higher = more preferred)
 *
 * @see Using
 * @see summon
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FUNCTION
)
@Retention(AnnotationRetention.BINARY)
annotation class Given(
    val priority: Int = 0
)
