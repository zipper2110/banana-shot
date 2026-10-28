package org.litvin.license

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import java.time.LocalDate

/** The valid content of the version rules file (`release/version-policy.json`). */
data class VersionRules(
    val latest: LatestRelease?,
    val rules: List<VersionRule>,
) {
    /**
     * The rules that stop [appVersion]. A rule that matches `latest.version` is ignored, because it
     * also stops the version that the user must download.
     */
    fun matchingRules(appVersion: String): List<VersionRule> {
        val version = Version.parse(appVersion)
        val latestVersion = latest?.let { Version.parse(it.version) }
        return rules.filter { rule ->
            rule.matches(version) && (latestVersion == null || !rule.matches(latestVersion))
        }
    }
}

data class LatestRelease(
    val version: String,
    val downloadUrl: String,
    val installerUrl: String? = null,
    val notes: String? = null,
    /** The Markdown page of the "What's new" popup (B-54). With no page, no popup shows. */
    val whatsNewUrl: String? = null,
    /** The extended page about the release on the site (B-54). The popup and the update notice link to it. */
    val pageUrl: String? = null,
    /** The [notes] in other languages (B-11 E6). */
    val notesByLanguage: Map<AppLanguage, String> = emptyMap(),
    /** The [whatsNewUrl] in other languages (B-11 E6). */
    val whatsNewUrlByLanguage: Map<AppLanguage, String> = emptyMap(),
) {
    /** The notes in [language], or the English [notes]. */
    fun notesIn(language: AppLanguage = I18n.language): String? = textIn(notesByLanguage, language) ?: notes

    /** The "What's new" page in [language], or null when the file has no page for it. Then use [whatsNewUrl]. */
    fun whatsNewUrlOnlyIn(language: AppLanguage = I18n.language): String? = textIn(whatsNewUrlByLanguage, language)
}

data class VersionRule(
    val id: String,
    val fromVersion: String?,
    val toVersion: String,
    val stopsOn: LocalDate,
    val message: String? = null,
    /** The [message] in other languages (B-11 E6). */
    val messageByLanguage: Map<AppLanguage, String> = emptyMap(),
) {
    /** The message in [language], or the English [message]. */
    fun messageIn(language: AppLanguage = I18n.language): String? = textIn(messageByLanguage, language) ?: message

    /** Both ends are included. With no [fromVersion], the rule matches all versions up to [toVersion]. */
    fun matches(version: Version): Boolean {
        val from = fromVersion?.let(Version::parse) ?: Version.ZERO
        return version >= from && version <= Version.parse(toVersion)
    }
}

/**
 * The text of [language] from a "ByLanguage" field of the rules file (B-11 E6), or null. A variant uses the text of
 * its main language when it has no own text: "es-419" uses "es". English and the test language `qps` (its language
 * is English) use the English field.
 */
private fun textIn(texts: Map<AppLanguage, String>, language: AppLanguage): String? =
    language.chain.firstNotNullOfOrNull { texts[it] }
