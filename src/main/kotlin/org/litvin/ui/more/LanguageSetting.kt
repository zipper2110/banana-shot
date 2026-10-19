package org.litvin.ui.more

import org.litvin.i18n.AppLanguage

/** The language row of More > Settings (B-11 plan, section 4). `app` gives the implementation. */
interface LanguageSetting {
    /** The stored choice. Null follows the Windows display language. */
    val choice: AppLanguage?

    /** The language of the running app. */
    val running: AppLanguage

    /** The language that "Same as Windows" gives. */
    val systemLanguage: AppLanguage

    /** Saves the choice for the next start. Returns false when the preferences cannot save it. */
    fun save(choice: AppLanguage?): Boolean

    /**
     * Closes the app normally and starts it again. Null when this start cannot restart itself, for example a
     * start from the development build.
     */
    val restart: (() -> Unit)?
}
