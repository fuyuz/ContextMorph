package io.github.fuyuz.contextmorph.sample

import io.github.fuyuz.contextmorph.Given
import io.github.fuyuz.contextmorph.Using
import io.github.fuyuz.contextmorph.importGivens
import io.github.fuyuz.contextmorph.given
import io.github.fuyuz.contextmorph.summon

// ============================================
// Type Class Definitions
// ============================================

/**
 * Type class for ordering/comparison operations.
 */
interface Ord<T> {
    fun compare(a: T, b: T): Int
}

/**
 * Type class for converting values to strings.
 */
interface Show<T> {
    fun show(value: T): String
}

/**
 * Type class for monoid operations (identity + associative binary operation).
 */
interface Monoid<T> {
    val empty: T
    fun combine(a: T, b: T): T
}

/**
 * A tiny type class to demonstrate Scala 3-style "given imports".
 */
interface Tag<T> {
    val label: String
}

object TagGivensA {
    @Given
    val intTag: Tag<Int> = object : Tag<Int> {
        override val label: String = "A"
    }
}

object TagGivensB {
    @Given
    val intTag: Tag<Int> = object : Tag<Int> {
        override val label: String = "B"
    }
}

fun <T> tagLabel(value: T, @Using tag: Tag<T>): String = tag.label

// ============================================
// Given Instances
// ============================================

/**
 * Given instance for Int ordering.
 */
@Given
object IntOrd : Ord<Int> {
    override fun compare(a: Int, b: Int) = a.compareTo(b)
}

/**
 * Given instance for String ordering.
 */
@Given
object StringOrd : Ord<String> {
    override fun compare(a: String, b: String) = a.compareTo(b)
}

/**
 * Given instance for Int show.
 */
@Given
object IntShow : Show<Int> {
    override fun show(value: Int) = value.toString()
}

/**
 * Given instance for String show.
 */
@Given
object StringShow : Show<String> {
    override fun show(value: String) = "\"$value\""
}

/**
 * Given instance for Int addition monoid.
 */
@Given
object IntAddMonoid : Monoid<Int> {
    override val empty = 0
    override fun combine(a: Int, b: Int) = a + b
}

/**
 * Given instance for String concatenation monoid.
 */
@Given
object StringMonoid : Monoid<String> {
    override val empty = ""
    override fun combine(a: String, b: String) = a + b
}

// ============================================
// Derived Given Instances
// ============================================

/**
 * Derived given: List ordering from element ordering.
 */
@Given
fun <T> listOrd(@Using elementOrd: Ord<T>): Ord<List<T>> = object : Ord<List<T>> {
    override fun compare(a: List<T>, b: List<T>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val cmp = elementOrd.compare(a[i], b[i])
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }
}

/**
 * Derived given: Pair ordering from element orderings.
 */
@Given
fun <A, B> pairOrd(
    @Using firstOrd: Ord<A>,
    @Using secondOrd: Ord<B>
): Ord<Pair<A, B>> = object : Ord<Pair<A, B>> {
    override fun compare(a: Pair<A, B>, b: Pair<A, B>): Int {
        val cmp = firstOrd.compare(a.first, b.first)
        return if (cmp != 0) cmp else secondOrd.compare(a.second, b.second)
    }
}

/**
 * Derived given: List show from element show.
 */
@Given
fun <T> listShow(@Using elementShow: Show<T>): Show<List<T>> = object : Show<List<T>> {
    override fun show(value: List<T>) =
        value.joinToString(", ", "[", "]") { elementShow.show(it) }
}

/**
 * Derived given: List monoid.
 */
@Given
fun <T> listMonoid(): Monoid<List<T>> = object : Monoid<List<T>> {
    override val empty: List<T> = emptyList()
    override fun combine(a: List<T>, b: List<T>) = a + b
}

// ============================================
// Functions using @Using parameters
// ============================================

/**
 * Returns the maximum of two values using the given Ord instance.
 */
fun <T> max(a: T, b: T, @Using ord: Ord<T>): T =
    if (ord.compare(a, b) > 0) a else b

// ============================================
// Functions using Kotlin context parameters
// ============================================
//
// Kotlin 2.2+ context parameters allow implicit parameter passing.
// Currently, context arguments must be provided via with() blocks.
//
// Current state:
//   with(IntOrd) { maxCtx(1, 2) }  // Works - explicit context
//
// Future goal (not yet implemented):
//   maxCtx(1, 2)  // Auto-inject @Given IntOrd as context
//
// The auto-injection requires FIR-level integration to make
// @Given instances available as implicit values in scope.
// ============================================

/**
 * Returns the maximum of two values using context parameter.
 *
 * This uses Kotlin 2.2+'s context parameters syntax.
 * The ord parameter is implicitly passed from the calling context.
 */
context(ord: Ord<T>)
fun <T> maxCtx(a: T, b: T): T =
    if (ord.compare(a, b) > 0) a else b

/**
 * Returns the minimum of two values using context parameter.
 */
context(ord: Ord<T>)
fun <T> minCtx(a: T, b: T): T =
    if (ord.compare(a, b) < 0) a else b

/**
 * Sorts a list using context parameter.
 */
context(ord: Ord<T>)
fun <T> List<T>.sortedCtx(): List<T> =
    sortedWith { a, b -> ord.compare(a, b) }

/**
 * Shows a value using context parameter.
 */
context(s: Show<T>)
fun <T> T.showCtx(): String = s.show(this)

/**
 * Folds a list using context parameter.
 */
context(m: Monoid<T>)
fun <T> List<T>.foldCtx(): T =
    fold(m.empty) { acc, x -> m.combine(acc, x) }

/**
 * Returns the minimum of two values using the given Ord instance.
 */
fun <T> min(a: T, b: T, @Using ord: Ord<T>): T =
    if (ord.compare(a, b) < 0) a else b

/**
 * Sorts a list using the given Ord instance.
 */
fun <T> List<T>.sortedWith(@Using ord: Ord<T>): List<T> =
    sortedWith { a, b -> ord.compare(a, b) }

/**
 * Converts a value to string using the given Show instance.
 */
fun <T> T.show(@Using s: Show<T>): String = s.show(this)

/**
 * Folds a list using the given Monoid instance.
 */
fun <T> List<T>.fold(@Using m: Monoid<T>): T =
    fold(m.empty) { acc, x -> m.combine(acc, x) }

/**
 * Combines two values using the given Monoid instance.
 */
fun <T> T.combine(other: T, @Using m: Monoid<T>): T =
    m.combine(this, other)

// ============================================
// Example usage (for future testing)
// ============================================

/**
 * Demonstrates given/using functionality.
 * Note: This requires compiler plugin support to work.
 * Currently shows the expected API design.
 */
object GivenUsageExamples {

    /**
     * Example: Basic type class usage
     *
     * Functions with @Using parameters can be called with explicit instances,
     * or you can use summon<T>() to get instances automatically.
     */
    fun basicExample() {
        // With explicit instance
        val maxInt = max(1, 2, IntOrd)
        val minStr = min("a", "b", StringOrd)

        println("max(1, 2) = $maxInt")
        println("min(\"a\", \"b\") = $minStr")

        // Using summon<T>() to get the instance
        val maxIntViaSummon = max(1, 2, summon<Ord<Int>>())
        println("max(1, 2, summon<Ord<Int>>()) = $maxIntViaSummon")
    }

    /**
     * Example: Derived instances
     *
     * Expected behavior when compiler plugin is implemented:
     * ```
     * val listOrd = summon<Ord<List<Int>>>()  // Auto-constructs listOrd(IntOrd)
     * max(listOf(1, 2), listOf(1, 3))  // Returns [1, 3]
     * ```
     */
    fun derivedExample() {
        // With explicit instances (works now)
        val derivedListOrd = listOrd(IntOrd)
        val maxList = max(listOf(1, 2), listOf(1, 3), derivedListOrd)

        println("max([1,2], [1,3]) = $maxList")

        // With @Using auto-resolution (requires compiler plugin)
        val autoMaxList = max(listOf(1, 2), listOf(1, 3))
        println("max([1,2], [1,3]) via auto-injected @Using = $autoMaxList")

        // Derived summon (requires compiler plugin)
        val autoListOrd = summon<Ord<List<Int>>>()
        println("summon<Ord<List<Int>>>() = $autoListOrd")
    }

    /**
     * Example: Monoid fold
     *
     * Use summon<Monoid<Int>>() to automatically get the monoid instance.
     */
    fun monoidExample() {
        // With explicit instances
        val sumInt = listOf(1, 2, 3, 4, 5).fold(IntAddMonoid)
        val concatStr = listOf("a", "b", "c").fold(StringMonoid)

        println("fold([1,2,3,4,5]) = $sumInt")
        println("fold([\"a\",\"b\",\"c\"]) = $concatStr")

        // Using summon to get instances
        val sumViaSummon = listOf(10, 20, 30).fold(summon<Monoid<Int>>())
        println("fold([10,20,30]) via summon = $sumViaSummon")
    }

    /**
     * Example: Show type class
     *
     * Use summon<Show<Int>>() to automatically get the show instance.
     */
    fun showExample() {
        // With explicit instances
        val intStr = 42.show(IntShow)
        val listStr = listOf(1, 2, 3).show(listShow(IntShow))

        println("42.show() = $intStr")
        println("[1,2,3].show() = $listStr")

        // Using summon to get instance
        val strViaSummon = 100.show(summon<Show<Int>>())
        println("100.show() via summon = $strViaSummon")
    }

    /**
     * Example: summon() - explicit given retrieval
     *
     * This tests the compiler plugin's ability to transform
     * summon<T>() calls into direct references to @Given instances.
     */
    fun summonExample() {
        try {
            // summon<Ord<Int>>() should be transformed to IntOrd
            val ord: Ord<Int> = summon<Ord<Int>>()
            println("summon<Ord<Int>>() = $ord")
            println("ord.compare(1, 2) = ${ord.compare(1, 2)}")
        } catch (e: NotImplementedError) {
            println("summon<Ord<Int>>() - transformation not yet active")
        }

        try {
            // summon<Show<Int>>() should be transformed to IntShow
            val show: Show<Int> = summon<Show<Int>>()
            println("summon<Show<Int>>() = $show")
            println("show.show(42) = ${show.show(42)}")
        } catch (e: NotImplementedError) {
            println("summon<Show<Int>>() - transformation not yet active")
        }

        try {
            // summon<Monoid<Int>>() should be transformed to IntAddMonoid
            val monoid: Monoid<Int> = summon<Monoid<Int>>()
            println("summon<Monoid<Int>>() = $monoid")
            println("monoid.combine(3, 4) = ${monoid.combine(3, 4)}")
        } catch (e: NotImplementedError) {
            println("summon<Monoid<Int>>() - transformation not yet active")
        }
    }

    /**
     * Example: Kotlin context parameters
     *
     * This demonstrates using Kotlin 2.2+'s context parameters syntax.
     * Currently, context arguments must be provided explicitly with `with(...)`.
     *
     * Future goal: Auto-inject @Given instances as context arguments.
     */
    fun contextParametersExample() {
        // Explicit context passing with with()
        with(IntOrd) {
            val maxVal = maxCtx(5, 3)
            val minVal = minCtx(5, 3)
            val sorted = listOf(3, 1, 4, 1, 5).sortedCtx()
            println("with(IntOrd) { maxCtx(5, 3) } = $maxVal")
            println("with(IntOrd) { minCtx(5, 3) } = $minVal")
            println("with(IntOrd) { [3,1,4,1,5].sortedCtx() } = $sorted")
        }

        with(StringOrd) {
            val maxStr = maxCtx("hello", "world")
            println("with(StringOrd) { maxCtx(\"hello\", \"world\") } = $maxStr")
        }

        with(IntShow) {
            val shown = 42.showCtx()
            println("with(IntShow) { 42.showCtx() } = $shown")
        }

        with(IntAddMonoid) {
            val folded = listOf(1, 2, 3, 4, 5).foldCtx()
            println("with(IntAddMonoid) { [1,2,3,4,5].foldCtx() } = $folded")
        }

        // Auto-injection from @Given (requires compiler plugin support)
        val maxInt = maxCtx(1, 2)  // Auto-resolve IntOrd
        println("maxCtx(1, 2) via auto-injected context parameter = $maxInt")
    }

    fun blockScopedGivenExample() {
        val xs = listOf(3, 1, 2)
        println("sortedWith() default = ${xs.sortedWith()}")

        given<Ord<Int>> {
            object : Ord<Int> {
                override fun compare(a: Int, b: Int): Int = b.compareTo(a)
            }
        }

        println("sortedWith() with block-scoped given = ${xs.sortedWith()}")
    }

    fun givenImportExample() {
        // There are two Tag<Int> givens (TagGivensA.intTag and TagGivensB.intTag).
        // We disambiguate by importing givens from TagGivensA into local scope.
        importGivens(TagGivensA)
        println("tagLabel(1) with imported givens = ${tagLabel(1)}")
    }

    fun runAll() {
        println("=== Given/Using Examples ===")
        println()

        println("--- Basic Example ---")
        basicExample()
        println()

        println("--- Derived Example ---")
        derivedExample()
        println()

        println("--- Monoid Example ---")
        monoidExample()
        println()

        println("--- Show Example ---")
        showExample()
        println()

        println("--- Summon Example ---")
        summonExample()
        println()

        println("--- Context Parameters Example ---")
        contextParametersExample()
        println()

        println("--- Block-scoped given Example ---")
        blockScopedGivenExample()
        println()

        println("--- Given import Example ---")
        givenImportExample()
        println()

        println("=== All examples completed ===")
    }
}
