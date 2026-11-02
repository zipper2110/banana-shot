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
    /** "Update and restart" shows. "Manually download update" always shows. */
    val showsUpdateAndRestart: Boolean,
    /** The installer that "Update and restart" downloads and starts: the setup EXE, or the .pkg on macOS. */
    val installerUrl: String,
    /** The page that "Manually download update" opens in the browser. The app does not quit. */
    val downloadPageUrl: String,
) {
    companion object {
        /** The setup EXE has this name in each release (B-24). `Build-VelopackRelease.ps1` makes it. */
        val SETUP_EXE_NAME = "${AppInfo.NAME}-win-Setup.exe"

        /** The macOS installer has this name in each release (B-13). `build-velopack-release.sh` makes it. */
        val MAC_SETUP_PKG_NAME = "${AppInfo.NAME}-osx-Setup.pkg"

        /** The page that "Manually download update" opens with no valid `latest`. */
        const val RELEASES_PAGE_URL = "https://github.com/zipper2110/banana-shot/releases/latest"

        /** The setup EXE of the latest release, for a file with no `latest.installerUrl`. */
        val STABLE_INSTALLER_URL = "https://github.com/zipper2110/banana-shot/releases/latest/download/$SETUP_EXE_NAME"

        /** The macOS installer of the latest release, for a file with no `latest.macInstallerUrl`. */
        val STABLE_MAC_INSTALLER_URL = "https://github.com/zipper2110/banana-shot/releases/latest/download/$MAC_SETUP_PKG_NAME"

        private fun currentOsName(): String = System.getProperty("os.name").orEmpty()

        private fun isMac(osName: String): Boolean = osName.startsWith("Mac", ignoreCase = true)

        /** The file name of the installer: the setup EXE, or the .pkg on macOS. */
        fun setupFileName(osName: String = currentOsName()): String =
            if (isMac(osName)) MAC_SETUP_PKG_NAME else SETUP_EXE_NAME

        /** The stable URL of the installer of this system, for a file with no installer URL. */
        fun stableInstallerUrl(osName: String = currentOsName()): String =
            if (isMac(osName)) STABLE_MAC_INSTALLER_URL else STABLE_INSTALLER_URL

        /**
         * The options for [appVersion] with the rules file [rules] (the new file or the saved file).
         * With no file, or a file with no valid `latest`, both buttons show, with the stable URL and
         * the releases page.
         */
        fun of(appVersion: String, rules: VersionRules?, osName: String = currentOsName()): UpdateOptions =
            of(appVersion, rules?.latest, osName)

        /** On macOS, the installer is `latest.macInstallerUrl`. The other systems use `latest.installerUrl`. */
        fun of(appVersion: String, latest: LatestRelease?, osName: String = currentOsName()): UpdateOptions = UpdateOptions(
            // The setup would install the same version again when latest.version is the app version or older.
            showsUpdateAndRestart = latest == null || Version.parse(latest.version) > Version.parse(appVersion),
            installerUrl = (if (isMac(osName)) latest?.macInstallerUrl else latest?.installerUrl)
                ?: stableInstallerUrl(osName),
            downloadPageUrl = latest?.downloadUrl ?: RELEASES_PAGE_URL,
        )
    }
}
