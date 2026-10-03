package org.litvin.license

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
)

data class VersionRule(
    val id: String,
    val fromVersion: String?,
    val toVersion: String,
    val stopsOn: LocalDate,
    val message: String? = null,
) {
    /** Both ends are included. With no [fromVersion], the rule matches all versions up to [toVersion]. */
    fun matches(version: Version): Boolean {
        val from = fromVersion?.let(Version::parse) ?: Version.ZERO
        return version >= from && version <= Version.parse(toVersion)
    }
}
