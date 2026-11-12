package org.litvin.shared.util

/**
 * The edition of the app: free or paid (docs/backlog-paid.md).
 *
 * This is a temporary switch until the paid version has a subscription check (P-2). The program argument [ARGUMENT]
 * or the VM option `-Dbananashot.paid=true` turns on the paid features. Without them, the app is the free version.
 * The argument sets the property, so all code and a restart after a language change read the same value.
 */
object Edition {
    const val ARGUMENT = "--paid"
    const val PROPERTY = "bananashot.paid"

    /** True when the paid features are on. */
    val isPaid: Boolean get() = System.getProperty(PROPERTY).toBoolean()

    /** Turns on the paid features when [args] has [ARGUMENT]. */
    fun readArguments(args: Array<String>) {
        if (args.contains(ARGUMENT)) System.setProperty(PROPERTY, "true")
    }
}
