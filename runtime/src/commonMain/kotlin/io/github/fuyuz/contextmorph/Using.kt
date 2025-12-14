package io.github.fuyuz.contextmorph

/**
 * Marks a function parameter as implicitly resolved from given instances.
 *
 * Parameters annotated with @Using are automatically filled by the compiler
 * from available given instances in scope. This is similar to Scala 3's
 * `using` parameter clause.
 *
 * ## Usage
 *
 * ### Basic usage
 * ```kotlin
 * fun <T> max(a: T, b: T, @Using ord: Ord<T>): T =
 *     if (ord.compare(a, b) > 0) a else b
 *
 * // Call site - ord is automatically resolved
 * max(1, 2)  // Uses IntOrd given instance
 * ```
 *
 * ### Multiple using parameters
 * ```kotlin
 * fun <T> format(value: T, @Using show: Show<T>, @Using ord: Ord<T>): String =
 *     "Value: ${show.show(value)}"
 * ```
 *
 * ### Derived given with using
 * ```kotlin
 * @Given
 * fun <T> listOrd(@Using elementOrd: Ord<T>): Ord<List<T>> = /* ... */
 * ```
 *
 * ## Explicit Override
 *
 * You can always explicitly provide a value for @Using parameters:
 *
 * ```kotlin
 * // Automatic resolution
 * max(1, 2)
 *
 * // Explicit override
 * max(1, 2, customOrd)
 * ```
 *
 * ## Resolution Rules
 *
 * The compiler searches for given instances in the following order:
 * 1. Local scope (block-level `given { }` calls)
 * 2. Enclosing function parameters
 * 3. Class members
 * 4. File-level declarations
 * 5. Imported declarations
 * 6. Package-level declarations
 *
 * If no matching given is found, a compilation error occurs.
 * If multiple givens match with the same priority, an ambiguity error occurs.
 *
 * @see Given
 * @see summon
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class Using
