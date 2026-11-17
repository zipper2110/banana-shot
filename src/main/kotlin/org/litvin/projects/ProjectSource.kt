package org.litvin.projects

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.media.SourceFile
import org.litvin.media.SourceVideo
import java.io.File

/**
 * The source video of a project (B-49): one video file, or several files of one match that play one after the other.
 *
 * The tabs play and export [mediaPath]. For a match of several files, this is the EDL file in the project folder
 * ([SourceVideo.EDL_FILE_NAME]). The manifest keeps the file list, and the EDL file comes from it.
 */
object ProjectSource {
    private val logger = KotlinLogging.logger {}

    /** True when the project has several video files. */
    fun isJoined(manifest: ProjectManifestV1): Boolean = (manifest.sourceVideos?.size ?: 0) > 1

    /** All video files of the project, in play order. Empty when the project has no video. */
    fun files(manifest: ProjectManifestV1): List<String> =
        manifest.sourceVideos?.takeIf { it.size > 1 }?.map { it.path }
            ?: listOfNotNull(manifest.sourceVideo?.takeIf { it.isNotBlank() })

    /** The video files of the project that are not on the disk. */
    fun missingFiles(manifest: ProjectManifestV1): List<String> = files(manifest).filterNot { File(it).isFile }

    /**
     * The path that the tabs play and export: the video file, or the EDL file of a match of several files.
     * The EDL file is written again when it is missing or old. Null when the project has no video.
     */
    fun mediaPath(manifestPath: String, manifest: ProjectManifestV1): String? {
        val files = manifest.sourceVideos?.takeIf { it.size > 1 } ?: return manifest.sourceVideo?.takeIf { it.isNotBlank() }
        val edl = edlFile(manifestPath)
        try {
            SourceVideo.write(edl, files)
        } catch (e: Exception) {
            logger.warn(e) { "Cannot write ${edl.absolutePath}" }
        }
        return edl.absolutePath
    }

    /** The media path of the project of the manifest file [manifestPath]. */
    fun mediaPath(manifestPath: String): String? = mediaPath(manifestPath, ManifestIO.read(manifestPath))

    /** The EDL file of a match of several files. */
    fun edlFile(manifestPath: String): File = File(File(manifestPath).absoluteFile.parentFile, SourceVideo.EDL_FILE_NAME)

    /** A manifest with the files [files]: one file gives [ProjectManifestV1.sourceVideo] only. */
    fun withFiles(manifest: ProjectManifestV1, files: List<SourceFile>): ProjectManifestV1 = manifest.copy(
        sourceVideo = files.firstOrNull()?.path,
        sourceVideos = files.takeIf { it.size > 1 },
    )

    /**
     * The files after the user located [selected] for a project with missing files. Each missing file that has a
     * file with the same name in the folder of [selected] gets that file. When no missing file has the name of
     * [selected], [selected] replaces the first missing file. The files that are on the disk do not change.
     */
    fun relink(files: List<SourceFile>, selected: String): List<SourceFile> {
        val folder = File(selected).absoluteFile.parentFile
        val selectedName = File(selected).name
        val missing = files.filterNot { File(it.path).isFile }
        val replaceFirst = missing.none { File(it.path).name.equals(selectedName, ignoreCase = true) }
        var first = true
        return files.map { file ->
            if (File(file.path).isFile) return@map file
            if (replaceFirst && first) {
                first = false
                return@map file.copy(path = File(selected).absolutePath)
            }
            val candidate = File(folder, File(file.path).name)
            if (candidate.isFile) file.copy(path = candidate.absolutePath) else file
        }
    }
}
