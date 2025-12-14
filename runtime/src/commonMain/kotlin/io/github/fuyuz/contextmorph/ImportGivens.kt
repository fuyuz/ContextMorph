package io.github.fuyuz.contextmorph

/**
 * Marker function to import givens from a scope object for subsequent resolution.
 *
 * This is inspired by Scala 3 "given imports" (bringing givens into local scope).
 * The function itself is a no-op at runtime; its semantics are implemented by the compiler plugin.
 *
 * Example:
 *
 * ```kotlin
 * object MyGivens {
 *   @Given val ordInt: Ord<Int> = ...
 * }
 *
 * fun f() {
 *   importGivens(MyGivens)
 *   max(1, 2) // can use ordInt
 * }
 * ```
 */
@Suppress("UNUSED_PARAMETER")
inline fun importGivens(scope: Any) {
    // Marker function - implementation replaced by compiler plugin
}


