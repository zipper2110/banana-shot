package org.litvin.app

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.AppInfo
import org.litvin.license.Version
import java.io.File
import java.util.prefs.Preferences

/**
 * The old copy of the app that an update can leave on macOS (B-13, step 11).
 *
 * The `.pkg` of Velopack is not relocatable: the macOS Installer always puts the app into the Applications folder
 * (`/Applications`, or `~/Applications` for "Install for me only"). When the user moved the app to another folder,
 * an update makes a new copy in the Applications folder, and the old copy stays. If the user starts the old copy
 * again, the old version starts and shows the update notice again.
 *
 * Before the update, [rememberBeforeUpdate] saves the bundle of this process. At the first start after the update,
 * [leftOver] gives that bundle when it is another copy of the app with an older version. The app then offers to
 * move it to the Trash. On Windows, the process has no `.app` bundle, so this class does nothing.
 */
class OldAppCopy(
    private val preferences: Preferences,
    /** The `.app` bundle of this process, or null for a start that is not from a bundle. */
    private val currentBundle: File? = bundleOf(System.getProperty("jpackage.app-path")),
    private val currentVersion: Version = Version.parse(AppInfo.version),
    private val home: File = File(System.getProperty("user.home").orEmpty()),
) {
    private val logger = KotlinLogging.logger {}

    /** The old copy and its version. */
    data class LeftOver(val bundle: File, val version: Version)

    /** True when this process runs from a bundle outside the folders where the installer puts the app. */
    val isOutsideApplications: Boolean
        get() {
            val parent = currentBundle?.absoluteFile?.parentFile ?: return false
            return parent != File("/Applications").absoluteFile && parent != File(home, "Applications").absoluteFile
        }

    /** Saves the bundle of this process. Call it before the installer starts. */
    fun rememberBeforeUpdate() {
        val bundle = currentBundle ?: return
        preferences.put(KEY, bundle.absolutePath)
        runCatching { preferences.flush() }.onFailure { logger.warn(it) { "Cannot save the path of the app before the update." } }
    }

    /**
     * The bundle that [rememberBeforeUpdate] saved, when it is another copy of the app with an older version, or
     * null. The saved path is used only one time: the next start does not ask again.
     */
    fun leftOver(): LeftOver? {
        val saved = preferences.get(KEY, null) ?: return null
        preferences.remove(KEY)
        runCatching { preferences.flush() }
        val current = currentBundle ?: return null
        val old = File(saved)
        if (old.absoluteFile.normalize() == current.absoluteFile.normalize() || !old.isDirectory) return null
        val plist = File(old, "Contents/Info.plist").takeIf { it.isFile } ?: return null
        val text = runCatching { plist.readText() }.getOrNull() ?: return null
        if (plistString(text, "CFBundleIdentifier") != BUNDLE_ID) return null
        val version = plistString(text, "CFBundleShortVersionString")?.let(Version::parse) ?: return null
        if (version >= currentVersion) return null
        logger.info { "An old copy of the app ($version) is at $old. This copy ($currentVersion) is at $current." }
        return LeftOver(old, version)
    }

    companion object {
        internal const val KEY = "update-previous-bundle"

        /** The bundle ID of the app. `distribution/macos/build-app-image.sh` sets it. */
        internal const val BUNDLE_ID = "app.banana-shot-editor.bananashot"

        /** The `.app` bundle of the launcher [appPath] (`<bundle>/Contents/MacOS/<launcher>`), or null. */
        internal fun bundleOf(appPath: String?): File? {
            val macOs = appPath?.let(::File)?.absoluteFile?.parentFile ?: return null
            val contents = macOs.parentFile ?: return null
            val bundle = contents.parentFile ?: return null
            return bundle.takeIf { macOs.name == "MacOS" && contents.name == "Contents" && it.name.endsWith(".app") }
        }

        /** The text of [key] in an XML property list, or null. */
        internal fun plistString(plist: String, key: String): String? =
            Regex("<key>\\s*${Regex.escape(key)}\\s*</key>\\s*<string>([^<]*)</string>").find(plist)?.groupValues?.get(1)?.trim()
    }
}
