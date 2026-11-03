package org.litvin.app

import org.litvin.license.Version
import java.io.File
import java.nio.file.Files
import java.util.prefs.AbstractPreferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-13, step 11: the old copy of the app that an update leaves on macOS. */
class OldAppCopyTest {
    private val root = Files.createTempDirectory("old-app-copy").toFile()
    private val home = File(root, "home")
    private val preferences = MemoryPreferences()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun bundle(path: String, version: String, id: String = OldAppCopy.BUNDLE_ID): File {
        val bundle = File(root, path)
        File(bundle, "Contents/MacOS").mkdirs()
        File(bundle, "Contents/Info.plist").writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <plist version="1.0">
            <dict>
                <key>CFBundleIdentifier</key>
                <string>$id</string>
                <key>CFBundleShortVersionString</key>
                <string>$version</string>
            </dict>
            </plist>
            """.trimIndent(),
        )
        return bundle
    }

    private fun copy(current: File?, version: String = "1.4.0") =
        OldAppCopy(preferences, currentBundle = current, currentVersion = Version.parse(version), home = home)

    @Test
    fun `an update from a moved copy finds the old copy one time`() {
        val moved = bundle("home/Desktop/BananaShot.app", "1.3.99")
        val installed = bundle("home/Applications/BananaShot.app", "1.4.0")
        copy(moved, "1.3.99").rememberBeforeUpdate()

        val after = copy(installed)
        assertEquals(OldAppCopy.LeftOver(moved, Version(1, 3, 99)), after.leftOver())
        // The next start does not ask again.
        assertNull(after.leftOver())
    }

    @Test
    fun `an update in place finds no old copy`() {
        val installed = bundle("home/Applications/BananaShot.app", "1.4.0")
        copy(installed, "1.3.99").rememberBeforeUpdate()
        assertNull(copy(installed).leftOver())
    }

    @Test
    fun `a copy that is gone, newer, or another app is not offered`() {
        val installed = bundle("home/Applications/BananaShot.app", "1.4.0")

        preferences.put(OldAppCopy.KEY, File(root, "home/Desktop/Gone.app").path)
        assertNull(copy(installed).leftOver())

        preferences.put(OldAppCopy.KEY, bundle("home/Desktop/Newer.app", "1.5.0").path)
        assertNull(copy(installed).leftOver())

        preferences.put(OldAppCopy.KEY, bundle("home/Desktop/Other.app", "1.0.0", id = "com.example.other").path)
        assertNull(copy(installed).leftOver())
    }

    @Test
    fun `a start that is not from a bundle saves nothing`() {
        copy(null).rememberBeforeUpdate()
        assertNull(preferences.get(OldAppCopy.KEY, null))
        assertFalse(copy(null).isOutsideApplications)
    }

    @Test
    fun `the Applications folders are the folders of the installer`() {
        assertFalse(copy(File(home, "Applications/BananaShot.app")).isOutsideApplications)
        assertFalse(copy(File("/Applications/BananaShot.app")).isOutsideApplications)
        assertTrue(copy(File(home, "Desktop/BananaShot.app")).isOutsideApplications)
        assertTrue(copy(File("/Applications/Sports/BananaShot.app")).isOutsideApplications)
    }

    @Test
    fun `the bundle of the launcher`() {
        assertEquals(File("/Applications/BananaShot.app").absoluteFile, OldAppCopy.bundleOf("/Applications/BananaShot.app/Contents/MacOS/BananaShot"))
        assertNull(OldAppCopy.bundleOf("C:\\Users\\a\\AppData\\Local\\BananaShot\\current\\BananaShot.exe"))
        assertNull(OldAppCopy.bundleOf(null))
    }

    /** Preferences in memory. */
    private class MemoryPreferences : AbstractPreferences(null, "") {
        private val values = mutableMapOf<String, String>()
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun removeNodeSpi() = Unit
        override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi(): Array<String> = emptyArray()
        override fun childSpi(name: String): AbstractPreferences = MemoryPreferences()
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }
}
