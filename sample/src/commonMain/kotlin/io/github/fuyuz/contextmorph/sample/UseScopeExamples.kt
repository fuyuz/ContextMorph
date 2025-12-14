package io.github.fuyuz.contextmorph.sample

import io.github.fuyuz.contextmorph.useScope

fun basicUseScopeExample(): String {
    val log = mutableListOf<String>()

    run {
        useScope { content ->
            log.add("Before")
            content()
            log.add("After")
        }

        log.add("Content")
    }

    return log.joinToString(", ")
}

fun dslBuilderExample(): String {
    val output = StringBuilder()

    fun html(block: () -> Unit) {
        output.append("<html>")
        block()
        output.append("</html>")
    }

    fun body(block: () -> Unit) {
        output.append("<body>")
        block()
        output.append("</body>")
    }

    fun text(value: String) {
        output.append(value)
    }

    run {
        useScope { content ->
            html {
                body {
                    content()
                }
            }
        }

        text("Hello World")
    }

    return output.toString()
}

fun resourceManagementExample(): List<String> {
    val operations = mutableListOf<String>()

    fun transaction(block: () -> Unit) {
        operations.add("BEGIN")
        try {
            block()
            operations.add("COMMIT")
        } catch (e: Exception) {
            operations.add("ROLLBACK")
            throw e
        }
    }

    run {
        useScope { content ->
            transaction {
                content()
            }
        }

        operations.add("INSERT record 1")
        operations.add("UPDATE record 2")
    }

    return operations
}
