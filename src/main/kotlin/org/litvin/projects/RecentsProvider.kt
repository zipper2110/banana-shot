package org.litvin.projects

import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import java.io.File
import java.text.Collator
import java.time.Instant

/**
 * Provides Most-Recently-Used (MRU) projects list by scanning manifests on disk.
 * - Scans one repository-owned projects root
 * - Reads the .trproj manifest in each subfolder
 * - Keeps only valid manifests; sorts by lastOpenedAt desc
 * - Supports simple in-memory remove-from-view
 */
class RecentsProvider(private val projectsRoot: File) {
    data class RecentEntry(
        val path: String,
        val name: String,
        val lastOpenedAt: Instant?,
    )

    private var cache: List<RecentEntry> = emptyList()

    private fun scan(): List<RecentEntry> {
        val items = mutableListOf<RecentEntry>()
        if (projectsRoot.exists() && projectsRoot.isDirectory) {
            projectsRoot.listFiles { f -> f.isDirectory }?.forEach { dir ->
                val manifests = dir.listFiles { f ->
                    f.isFile && f.name.endsWith(".trproj", ignoreCase = true)
                }?.toList() ?: emptyList()
                val manifestFile = manifests.firstOrNull() ?: return@forEach
                try {
                    val mf = ManifestIO.read(manifestFile.absolutePath)
                    val ts = try {
                        Instant.parse(mf.lastOpenedAt)
                    } catch (_: Throwable) {
                        null
                    }
                    items.add(RecentEntry(manifestFile.absolutePath, mf.name.ifBlank { manifestFile.nameWithoutExtension }, ts))
                } catch (_: Throwable) {
                    // Ignore invalid manifest files while building the recents list.
                }
            }
        }
        cache = items.sortedWith(order(I18n.language))
        return cache
    }

    fun refresh(): List<RecentEntry> = scan()

    fun current(): List<RecentEntry> = cache.ifEmpty { scan() }

    companion object {
        /**
         * The newest open first. Projects with the same time (for example, never opened) sort by name in the order of
         * the app language (B-11 E3-T8): in Spanish "Ángel" comes before "Bruno". Names that are equal for the collator
         * ("Angel" and "angel") then sort by their characters and by their path, so the order does not change.
         */
        internal fun order(language: AppLanguage): Comparator<RecentEntry> {
            val collator = Collator.getInstance(language.locale).apply { strength = Collator.SECONDARY }
            return compareByDescending<RecentEntry> { it.lastOpenedAt ?: Instant.EPOCH }
                .then { a, b -> collator.compare(a.name, b.name) }
                .thenBy { it.name }
                .thenBy { it.path }
        }
    }
}
