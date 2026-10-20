package org.litvin.projects

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n

/**
 * The language of the texts in the video of a project (B-11 E5-T1).
 *
 * The manifest keeps the language code only after the user selects a language on the Export tab. A project without
 * a selection follows the app language. A code that this build does not have, or a broken value, also gives the app
 * language. The manifest keeps such a value until the user selects a language (plan, "Decisions", "Unknown language
 * value"), because each manifest change is a copy of the read manifest.
 */
object ProjectVideoLanguage {
    private val logger = KotlinLogging.logger {}

    /** The video language of [manifest]: the saved language, or the app language. */
    fun of(manifest: ProjectManifestV1?): AppLanguage = AppLanguage.ofCode(manifest?.videoLanguage) ?: I18n.language

    /** The video language of the project of [manifestPath]. A manifest that the app cannot read gives the app language. */
    fun read(manifestPath: String?): AppLanguage {
        val manifest = manifestPath?.let {
            try {
                ManifestIO.read(it)
            } catch (e: Exception) {
                logger.warn(e) { "Cannot read the video language of $it. The video uses the app language." }
                null
            }
        }
        return of(manifest)
    }

    /** Saves the language that the user selected. Throws when the manifest cannot be read or written. */
    fun save(manifestPath: String, language: AppLanguage) {
        val manifest = ManifestIO.read(manifestPath)
        if (manifest.videoLanguage == language.code) return
        ManifestIO.write(manifestPath, manifest.copy(videoLanguage = language.code))
    }
}
