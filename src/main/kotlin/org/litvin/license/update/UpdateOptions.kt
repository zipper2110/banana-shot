package org.litvin.license.update

import org.litvin.AppInfo
import org.litvin.license.LatestRelease
import org.litvin.license.Version
import org.litvin.license.VersionRules

/**
 * The buttons and the URLs of "Update and restart" (B-30, build-expiry-spec.md "Update and restart").
 * The update notice, the expiry warning, the banner, and the dialog of expired mode use the same values.
 */
data class UpdateOptions(
    /** "Update and restart" shows. "Download update" always shows. */
    val showsUpdateAndRestart: Boolean,
    /** The setup EXE that "Update and restart" downloads and starts. */
    val installerUrl: String,
    /** The page that "Download update" opens in the browser. The app does not quit. */
    val downloadPageUrl: String,
) {
    companion object {
        /** The setup EXE has this name in each release (B-24). `Build-VelopackRelease.ps1` makes it. */
        val SETUP_EXE_NAME = "${AppInfo.NAME}-win-Setup.exe"

        /** The page that "Download update" opens with no valid `latest`. */
        const val RELEASES_PAGE_URL = "https://github.com/zipper2110/tennis-record/releases/latest"

        /** The setup EXE of the latest release, for a file with no `latest.installerUrl`. */
        val STABLE_INSTALLER_URL = "https://github.com/zipper2110/tennis-record/releases/latest/download/$SETUP_EXE_NAME"

        /**
         * The options for [appVersion] with the rules file [rules] (the new file or the saved file).
         * With no file, or a file with no valid `latest`, both buttons show, with the stable URL and
         * the releases page.
         */
        fun of(appVersion: String, rules: VersionRules?): UpdateOptions = of(appVersion, rules?.latest)

        fun of(appVersion: String, latest: LatestRelease?): UpdateOptions = UpdateOptions(
            // The setup would install the same version again when latest.version is the app version or older.
            showsUpdateAndRestart = latest == null || Version.parse(latest.version) > Version.parse(appVersion),
            installerUrl = latest?.installerUrl ?: STABLE_INSTALLER_URL,
            downloadPageUrl = latest?.downloadUrl ?: RELEASES_PAGE_URL,
        )
    }
}
