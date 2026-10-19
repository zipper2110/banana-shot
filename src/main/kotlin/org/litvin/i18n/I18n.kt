package org.litvin.i18n

/**
 * The texts of the running app (B-11).
 *
 * The language does not change while the app runs: a change applies at the next start (plan, section 4). Thus one
 * global instance is sufficient. `main` calls [install] before it makes a window. Until then, and in the tests, the
 * app uses English.
 *
 * In this version the texts in the video use the app language. E5 adds a separate video language.
 */
object I18n {
    @Volatile
    private var current: Messages? = null

    /** The texts of the app interface. */
    val ui: Messages get() = current ?: Messages.english().also { current = it }

    /** The language of the app interface. */
    val language: AppLanguage get() = ui.language

    /** Sets the language of the app. Call it one time, before the app makes a window. */
    fun install(messages: Messages) {
        current = messages
    }

    private val all: Map<AppLanguage, Messages> by lazy { AppLanguage.entries.associateWith { Messages.of(it) } }

    /**
     * The text of [key] in each app language. Code that must know a saved default text uses it: a project from an
     * English start keeps "Player 1", and a project from a Spanish start keeps "Jugador 1" (B-11, "Saved default texts").
     */
    fun inAllLanguages(key: String, vararg args: Pair<String, Any?>): Set<String> =
        all.values.mapTo(LinkedHashSet()) { it.text(key, args.toMap()) } + ui.text(key, args.toMap())
}

/** The text of [key] in the app language, with the named [args]: `tr("export.points", "count" to 3)`. */
fun tr(key: String, vararg args: Pair<String, Any?>): String = I18n.ui.text(key, args.toMap())
