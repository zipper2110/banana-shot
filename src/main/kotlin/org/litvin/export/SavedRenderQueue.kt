package org.litvin.export

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.JsonFileIO
import org.litvin.RenderJob
import org.litvin.RenderStatus
import org.litvin.app.InstanceLock
import org.litvin.export.scoreboard.SceneItem
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException

/**
 * The export queue in the data folder (B-18). The file keeps the running job and the queued jobs in
 * the queue order. Thus, the queue continues after the app closes, after a crash, and after the setup
 * of an update kills the app.
 *
 * Only one process can own the file. [claim] gives `null` when another process owns it. Then that
 * process keeps its queue in memory only.
 *
 * A failure to read or write the file shows no error to the user. It goes to the log only.
 */
class SavedRenderQueue internal constructor(
    val file: File,
    private val ownership: AutoCloseable? = null,
) : AutoCloseable {
    private val logger = KotlinLogging.logger {}

    /**
     * Reads the saved jobs in the queue order. A job that the app cannot read is ignored and goes to
     * the log. A damaged file gives an empty list.
     */
    @Synchronized
    fun load(): List<RenderJob> {
        if (!file.isFile) return emptyList()
        val root = try {
            JsonFileIO.read(mapper, file.path, JsonNode::class.java)
        } catch (e: IOException) {
            logger.warn(e) { "Cannot read the saved export queue $file. The queue starts empty." }
            return emptyList()
        }
        val format = root?.get("format")?.asInt(-1) ?: -1
        val jobs = root?.get("jobs")
        if (format != FORMAT || jobs !is ArrayNode) {
            logger.warn { "The saved export queue $file has an unknown format. The queue starts empty." }
            return emptyList()
        }
        return jobs.mapNotNull { node ->
            try {
                mapper.treeToValue(node, RenderJob::class.java)
            } catch (e: Exception) {
                logger.warn(e) { "Cannot read a job of the saved export queue (id=${node.get("id")?.asText()}). The job is ignored." }
                null
            }
        }
    }

    /**
     * Replaces the file with [jobs]. The write uses a temporary file and a move. Windows refuses the
     * move while another program (for example a virus scanner) has the file open, so the write tries
     * again some times.
     */
    @Synchronized
    fun save(jobs: List<RenderJob>) {
        val document = mapOf("format" to FORMAT, "jobs" to jobs)
        var attempt = 1
        while (true) {
            try {
                JsonFileIO.writeAtomically(mapper, file.path, document)
                return
            } catch (e: AccessDeniedException) {
                if (attempt >= WRITE_ATTEMPTS) {
                    logger.warn(e) { "Cannot save the export queue $file" }
                    return
                }
                attempt++
                Thread.sleep(WRITE_RETRY_DELAY_MS)
            } catch (e: Exception) {
                logger.warn(e) { "Cannot save the export queue $file" }
                return
            }
        }
    }

    /** Releases the ownership of the file. The file stays. */
    override fun close() {
        ownership?.close()
    }

    companion object {
        private val staticLogger = KotlinLogging.logger {}

        /** The version of the file format. A file with a different value is ignored. */
        const val FORMAT = 1
        private const val WRITE_ATTEMPTS = 5
        private const val WRITE_RETRY_DELAY_MS = 50L

        internal val mapper: ObjectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .addMixIn(SceneItem::class.java, SceneItemTypes::class.java)

        /**
         * Takes the ownership of [file] with a lock on [lockFile]. The process that starts first owns the
         * file. Gives `null` when another process has the lock, or when the lock fails. Then the caller
         * keeps its queue in memory only.
         */
        fun claim(file: File, lockFile: File): SavedRenderQueue? =
            when (val result = InstanceLock.acquire(lockFile)) {
                is InstanceLock.Result.Acquired -> SavedRenderQueue(file, result.lock)
                InstanceLock.Result.HeldByOtherInstance -> {
                    staticLogger.info { "Another process owns the export queue $file. This process keeps its queue in memory only." }
                    null
                }
                is InstanceLock.Result.Failed -> {
                    staticLogger.warn(result.cause) { "Cannot lock $lockFile. This process keeps its export queue in memory only." }
                    null
                }
            }

        /**
         * Deletes the partial output files of [job]: the `.part` file, and the chunk, concat and subtitle
         * files next to it. A job that was stopped during its export starts again from the beginning.
         */
        fun deletePartialOutput(job: RenderJob) {
            val output = File(job.outputPath)
            val folder = output.absoluteFile.parentFile ?: return
            val partName = output.name + ".part"
            val partials = folder.listFiles { candidate ->
                candidate.isFile && (candidate.name == partName || candidate.name.startsWith("$partName."))
            } ?: return
            partials.forEach { partial ->
                if (!partial.delete()) staticLogger.warn { "Cannot delete the partial export file $partial" }
            }
        }

        /** The saved job, ready for the queue again: the runtime fields have their start values. */
        internal fun restoredCopy(job: RenderJob): RenderJob = job.copy(
            status = RenderStatus.QUEUED,
            progress = 0.0,
            etaSeconds = null,
            bytesWritten = 0,
            failureReason = null,
            stderrTail = null,
        )
    }

    /** The statistics card pages contain the subtypes of [SceneItem]. The file names the subtype of each item. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
        JsonSubTypes.Type(value = SceneItem.Polygon::class, name = "polygon"),
        JsonSubTypes.Type(value = SceneItem.Polyline::class, name = "polyline"),
        JsonSubTypes.Type(value = SceneItem.Box::class, name = "box"),
        JsonSubTypes.Type(value = SceneItem.Label::class, name = "label"),
    )
    private interface SceneItemTypes
}
