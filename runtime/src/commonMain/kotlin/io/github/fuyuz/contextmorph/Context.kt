package io.github.fuyuz.contextmorph

/**
 * Wraps all subsequent code in the current block inside the provided scope builder.
 *
 * This is a marker function that is transformed by the ContextMorph compiler plugin.
 * When the plugin is applied, all code following this call (until the end of the enclosing block)
 * is captured and injected into the builder lambda.
 *
 * ## Usage
 *
 * ```kotlin
 * useScope { content ->
 *     wrapper {
 *         content()  // Subsequent code is injected here
 *     }
 * }
 * statement1  // Automatically placed inside wrapper
 * statement2  // Automatically placed inside wrapper
 * ```
 *
 * This is particularly useful for flattening nested DSL structures, such as in Jetpack Compose:
 *
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     useScope { content ->
 *         CompositionLocalProvider(LocalTheme provides darkTheme) {
 *             content()
 *         }
 *     }
 *
 *     Text("Hello")   // Automatically inside CompositionLocalProvider
 *     Button { }      // Automatically inside CompositionLocalProvider
 * }
 * ```
 *
 * ## Scope Rules
 *
 * - Captures all statements from the call point to the end of the enclosing block
 * - The builder parameter (typically `it` or a named parameter) marks the injection point
 * - Nested `useScope` calls are supported
 *
 * ## Examples
 *
 * ### Flattening DSL nesting
 * ```kotlin
 * useScope { content ->
 *     html {
 *         body {
 *             content()
 *         }
 *     }
 * }
 * h1("Title")      // Inside html > body
 * p("Paragraph")   // Inside html > body
 * ```
 *
 * ### Resource management
 * ```kotlin
 * useScope { content ->
 *     transaction {
 *         try {
 *             content()
 *             commit()
 *         } catch (e: Exception) {
 *             rollback()
 *             throw e
 *         }
 *     }
 * }
 * insertRecord(data)   // Inside transaction
 * updateRecord(data)   // Inside transaction
 * ```
 *
 * @param builder A lambda that receives the subsequent code as a parameter and wraps it
 */
@Suppress("UNUSED_PARAMETER")
inline fun useScope(builder: (() -> Unit) -> Unit) {
    // Marker function - implementation is replaced by compiler plugin
    // When plugin is not applied, this is a no-op
}
