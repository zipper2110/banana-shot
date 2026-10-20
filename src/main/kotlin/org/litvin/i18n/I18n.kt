package org.litvin.i18n

/**
 * The texts of the running app (B-11).
 *
 * The language does not change while the app runs: a change applies at the next start (plan, section 4). Thus one
 * global instance is sufficient. `main` calls [install] before it makes a window. Until then, and in the tests, the
 * app uses English.
 *
 * The texts in the video can have a different language (E5). Code that makes video texts runs in [inVideo]. In this
 * block, [tr], [DisplayFormats.upper], and the number formats use the video language. The block is bound to the
 * thread, so a preview on the EDT and an export on a worker thread do not change each other.
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

    private val videoScope = ThreadLocal<Messages?>()

    /** The texts that [tr] gives on this thread: the video texts in [inVideo], else the texts of the app interface. */
    val active: Messages get() = videoScope.get() ?: ui

    /** The video language of the [inVideo] block of this thread, or null outside of a block. */
    val videoLanguage: AppLanguage? get() = videoScope.get()?.language

    /**
     * Runs [block] with the video texts of [language] (B-11 E5). The video language of the app interface uses the
     * texts of the app interface, so the test language `qps` also shows in the video.
     */
    fun <T> inVideo(language: AppLanguage, block: () -> T): T {
        val previous = videoScope.get()
        videoScope.set(if (language == ui.language) ui else all.getValue(language))
        try {
            return block()
        } finally {
            if (previous == null) videoScope.remove() else videoScope.set(previous)
        }
    }

    /**
     * The text of [key] in each app language. Code that must know a saved default text uses it: a project from an
     * English start keeps "Player 1", and a project from a Spanish start keeps "Jugador 1" (B-11, "Saved default texts").
     */
    fun inAllLanguages(key: String, vararg args: Pair<String, Any?>): Set<String> =
        all.values.mapTo(LinkedHashSet()) { it.text(key, args.toMap()) } + ui.text(key, args.toMap())
}

/**
 * The text of [key] in the app language, with the named [args]: `tr("export.points", "count" to 3)`. In
 * [I18n.inVideo], the text is in the video language.
 */
fun tr(key: String, vararg args: Pair<String, Any?>): String = I18n.active.text(key, args.toMap())
