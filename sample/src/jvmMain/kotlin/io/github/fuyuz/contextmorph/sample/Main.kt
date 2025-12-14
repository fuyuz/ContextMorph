package io.github.fuyuz.contextmorph.sample

import io.github.fuyuz.contextmorph.useScope
import io.github.fuyuz.contextmorph.summon
import io.github.fuyuz.contextmorph.given
import io.github.fuyuz.contextmorph.sample.max
import io.github.fuyuz.contextmorph.sample.min
import io.github.fuyuz.contextmorph.sample.maxCtx
import io.github.fuyuz.contextmorph.sample.sortedCtx
import io.github.fuyuz.contextmorph.sample.sortedWith
import io.github.fuyuz.contextmorph.sample.fold
import io.github.fuyuz.contextmorph.sample.show
import io.github.fuyuz.contextmorph.sample.Ord
import io.github.fuyuz.contextmorph.sample.Monoid

fun useScopeTest(): String {
    val result = mutableListOf<String>()
    useScope { content ->
        result.add("Before content")
        content()
        result.add("After content")
    }
    result.add("This is captured")
    result.add("And this too")
    return result.joinToString(", ")
}

fun main() {
    println("===== useScope Tests =====")
    println()
    println("useScope: " + useScopeTest())
    println()

    println("--- Basic useScope ---")
    println("basicUseScopeExample: " + basicUseScopeExample())
    println()

    println("--- DSL Builder ---")
    println("dslBuilderExample: " + dslBuilderExample())
    println()

    println("--- Resource Management ---")
    println("resourceManagementExample: " + resourceManagementExample())

    println()
    println("===== Given/Using Inline Examples =====")
    println()

    // 1. Basic @Using: auto-injection of type class instances
    println("--- Auto-injection Example ---")
    val maxResult = max(10, 5)  // IntOrd auto-injected
    println("max(10, 5) = $maxResult")

    val minResult = min("apple", "banana")  // StringOrd auto-injected
    println("min(\"apple\", \"banana\") = $minResult")
    println()

    // 2. summon<T>(): explicitly retrieve a @Given instance
    println("--- summon<T>() Example ---")
    val intOrd: Ord<Int> = summon()
    println("summon<Ord<Int>>().compare(3, 7) = ${intOrd.compare(3, 7)}")

    val intMonoid: Monoid<Int> = summon()
    println("summon<Monoid<Int>>().combine(10, 20) = ${intMonoid.combine(10, 20)}")
    println()

    // 3. Derived instances: Ord<List<T>> from Ord<T>
    println("--- Derived Instance Example ---")
    val list1 = listOf(1, 2, 3)
    val list2 = listOf(1, 2, 4)
    val maxList = max(list1, list2)  // listOrd(IntOrd) auto-derived
    println("max([1,2,3], [1,2,4]) = $maxList")
    println()

    // 4. Context parameters with auto-injection
    println("--- Context Parameters Example ---")
    val ctxMax = maxCtx(100, 200)  // context(Ord<Int>) auto-injected
    println("maxCtx(100, 200) = $ctxMax")

    val sorted = listOf(5, 2, 8, 1).sortedCtx()  // context(Ord<Int>) auto-injected
    println("[5, 2, 8, 1].sortedCtx() = $sorted")
    println()

    // 5. Block-scoped given: override default instance locally
    println("--- Block-scoped given Example ---")
    val defaultSorted = listOf(3, 1, 2).sortedWith()
    println("Default sortedWith() = $defaultSorted")

    given<Ord<Int>> {
        object : Ord<Int> {
            override fun compare(a: Int, b: Int) = b.compareTo(a)  // Reverse order
        }
    }
    val reverseSorted = listOf(3, 1, 2).sortedWith()
    println("Reverse sortedWith() = $reverseSorted")
    println()

    // 6. Pair ordering (derived from two Ord instances)
    println("--- Pair Ord Example ---")
    val pair1 = 1 to "z"
    val pair2 = 1 to "a"
    val maxPair = max(pair1, pair2)  // pairOrd(IntOrd, StringOrd) auto-derived
    println("max((1, \"z\"), (1, \"a\")) = $maxPair")
    println()

    // 7. Monoid fold
    println("--- Monoid Fold Example ---")
    val numbers = listOf(1, 2, 3, 4, 5)
    val sum = numbers.fold()  // IntAddMonoid auto-injected
    println("[1, 2, 3, 4, 5].fold() = $sum")

    val words = listOf("Hello", " ", "World")
    val concat = words.fold()  // StringMonoid auto-injected
    println("[\"Hello\", \" \", \"World\"].fold() = $concat")
    println()

    // 8. Show type class
    println("--- Show Example ---")
    val intStr = 42.show()  // IntShow auto-injected
    println("42.show() = $intStr")

    val listStr = listOf(1, 2, 3).show()  // listShow(IntShow) auto-derived
    println("[1, 2, 3].show() = $listStr")

    println()
    println()

    // Run Given/Using examples from GivenUsageExamples
    GivenUsageExamples.runAll()
}
