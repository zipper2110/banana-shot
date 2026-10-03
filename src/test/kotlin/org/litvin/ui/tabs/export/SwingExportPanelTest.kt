package org.litvin.ui.tabs.export

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.litvin.ActiveQueueSnapshot
import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.RenderStatus
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.EncoderCapabilities
import org.litvin.export.RenderService
import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.PointV1
import org.litvin.projects.ManifestIO
import org.litvin.projects.ProjectManifestV1
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.UserDialogService
import java.awt.Component
import java.awt.Container
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.prefs.AbstractPreferences
import javax.swing.JButton
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwingExportPanelTest {
    @Test
    fun aQueuedExportKeepsTheAdjustmentsOfItsProjectWhenTheUserOpensAnotherProject(@TempDir root: File) {
        val scheduler = ScheduledThreadPoolExecutor(1)
        val adjustments = AdjustmentsSession(scheduler, saveDelayMs = 60_000L)
        val render = RecordingRenderService()
        val adjustmentsA = AdjustmentsV1(zoom = 1.5f, panX = 0.4f, brightness = 1.2f, whiteBalance = WhiteBalanceV1())
        val manifestA = createProject(root, "a", adjustmentsA)
        val manifestB = createProject(root, "b", AdjustmentsV1(zoom = 2.0f, panX = -0.6f, saturation = 0.3f))
        var panel: SwingExportPanel? = null
        try {
            SwingUtilities.invokeAndWait {
                adjustments.load(File(manifestA).parent)
                panel = SwingExportPanel(
                    ExportSettingsPreferences(MemoryPreferences()),
                    render,
                    adjustments,
                    EmptyCompletedRendersRepository,
                    FixedFilePicker(File(root, "a-export.mp4")),
                    AcceptingDialogs(),
                    CompletableFuture.completedFuture(EncoderCapabilities.NONE),
                )
                panel!!.setProjectManifest(manifestA)
                findButton(panel!!, "export-initialize").doClick()

                // The user changes the adjustments of A, then opens project B while the export waits in the queue.
                adjustments.set { it.copy(rotationDeg = 12.0f) }
                adjustments.load(File(manifestB).parent)
                panel!!.setProjectManifest(manifestB)
            }

            assertEquals(adjustmentsA, render.jobs.single().adjustments)
        } finally {
            SwingUtilities.invokeAndWait { panel?.close() }
            adjustments.close()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun withNoProjectTheLeftColumnShowsANoticeAndTheSettingsShowAfterAProjectOpens(@TempDir root: File) {
        val scheduler = ScheduledThreadPoolExecutor(1)
        val adjustments = AdjustmentsSession(scheduler, saveDelayMs = 60_000L)
        val manifest = createProject(root, "a", AdjustmentsV1())
        var panel: SwingExportPanel? = null
        try {
            SwingUtilities.invokeAndWait {
                panel = newPanel(RecordingRenderService(), adjustments, root)
                assertTrue(findNamed(panel!!, "export-no-project").isVisible)
                assertFalse(isShownInPanel(findButton(panel!!, "export-initialize"), panel!!))

                panel!!.setProjectManifest(manifest)
                assertFalse(findNamed(panel!!, "export-no-project").isVisible)
                assertTrue(isShownInPanel(findButton(panel!!, "export-initialize"), panel!!))

                panel!!.setProjectManifest(null)
                assertTrue(findNamed(panel!!, "export-no-project").isVisible)
            }
        } finally {
            SwingUtilities.invokeAndWait { panel?.close() }
            adjustments.close()
            scheduler.shutdownNow()
        }
    }

    @Test
    fun thePanelReportsTheNumberOfRunningAndQueuedExports(@TempDir root: File) {
        val scheduler = ScheduledThreadPoolExecutor(1)
        val adjustments = AdjustmentsSession(scheduler, saveDelayMs = 60_000L)
        val render = RecordingRenderService()
        val counts = mutableListOf<Int>()
        var panel: SwingExportPanel? = null
        try {
            SwingUtilities.invokeAndWait {
                panel = newPanel(render, adjustments, root)
                panel!!.onActiveExportCountChanged = { counts += it }
            }
            val running = job("running").apply { status = RenderStatus.RUNNING }
            render.push(ActiveQueueSnapshot(current = running, queued = listOf(job("q1"), job("q2"))))
            SwingUtilities.invokeAndWait { }
            render.push(ActiveQueueSnapshot(current = running.copy(status = RenderStatus.COMPLETED), queued = emptyList()))
            SwingUtilities.invokeAndWait { }

            assertEquals(listOf(0, 3, 0), counts)
            assertEquals(0, panel!!.activeExportCount)
        } finally {
            SwingUtilities.invokeAndWait { panel?.close() }
            adjustments.close()
            scheduler.shutdownNow()
        }
    }

    private fun newPanel(render: RenderService, adjustments: AdjustmentsSession, root: File) = SwingExportPanel(
        ExportSettingsPreferences(MemoryPreferences()),
        render,
        adjustments,
        EmptyCompletedRendersRepository,
        FixedFilePicker(File(root, "export.mp4")),
        AcceptingDialogs(),
        CompletableFuture.completedFuture(EncoderCapabilities.NONE),
    )

    private fun job(id: String) = RenderJob(
        id = id,
        sourcePath = "in.mp4",
        presetId = "p",
        outWidth = 1280,
        outHeight = 720,
        encoderLabel = "x264",
        idleTrim = true,
        outputPath = "$id.mp4",
    )

    /** True when [component] and all its parents up to [panel] are visible. A hidden card hides its children. */
    private fun isShownInPanel(component: Component, panel: Component): Boolean {
        var current: Component? = component
        while (current != null && current !== panel) {
            if (!current.isVisible) return false
            current = current.parent
        }
        return current === panel
    }

    private fun findNamed(root: Component, name: String): Component =
        checkNotNull(findNamedOrNull(root, name)) { "No component named $name" }

    private fun findNamedOrNull(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root !is Container) return null
        return root.components.firstNotNullOfOrNull { findNamedOrNull(it, name) }
    }

    private fun createProject(root: File, name: String, projectAdjustments: AdjustmentsV1): String {
        val dir = File(root, name).apply { mkdirs() }
        val video = File(root, "$name.mp4").apply { writeText("video") }
        val manifestPath = ManifestIO.manifestFilePath(dir.absolutePath)
        ManifestIO.write(
            manifestPath,
            ProjectManifestV1(
                id = name,
                name = name,
                createdAt = "2026-10-02T00:00:00Z",
                lastOpenedAt = "2026-10-02T00:00:00Z",
                sourceVideo = video.absolutePath,
            ),
        )
        EdlIO.writeForProjectDir(dir.absolutePath, EdlV1(points = listOf(PointV1(id = "p1", startMs = 0, endMs = 1_000))))
        AdjustmentsIO.writeForProjectDir(dir.absolutePath, projectAdjustments)
        return manifestPath
    }

    private fun findButton(root: Component, name: String): JButton =
        checkNotNull(findButtonOrNull(root, name)) { "No button named $name" }

    private fun findButtonOrNull(root: Component, name: String): JButton? {
        if (root is JButton && root.name == name) return root
        if (root !is Container) return null
        return root.components.firstNotNullOfOrNull { findButtonOrNull(it, name) }
    }

    private class RecordingRenderService : RenderService {
        val jobs = mutableListOf<RenderJob>()
        private val observers = mutableListOf<(ActiveQueueSnapshot) -> Unit>()
        fun push(snapshot: ActiveQueueSnapshot) = observers.forEach { it(snapshot) }
        override fun enqueue(job: RenderJob) { jobs += job }
        override fun cancelCurrent() = Unit
        override fun cancelQueued(jobId: String) = false
        override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable {
            observers += observer
            observer(ActiveQueueSnapshot(current = null, queued = emptyList()))
            return AutoCloseable { observers -= observer }
        }
        override fun close() = Unit
    }

    private object EmptyCompletedRendersRepository : CompletedRendersRepository {
        override fun loadAll() = emptyList<CompletedRender>()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }

    private class FixedFilePicker(private val destination: File) : FilePicker {
        override fun chooseSourceVideo(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?): File? = null
        override fun chooseExportDestination(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?): File =
            destination
    }

    private class AcceptingDialogs : UserDialogService {
        override fun showInfo(parent: Component?, message: String, title: String) = Unit
        override fun showError(parent: Component?, message: String, title: String) = error("$title: $message")
        override fun confirm(parent: Component?, message: String, title: String): Boolean = true
    }

    private class MemoryPreferences : AbstractPreferences(null, "") {
        private val values = mutableMapOf<String, String>()
        override fun putSpi(key: String, value: String) { values[key] = value }
        override fun getSpi(key: String): String? = values[key]
        override fun removeSpi(key: String) { values.remove(key) }
        override fun removeNodeSpi() = Unit
        override fun keysSpi(): Array<String> = values.keys.toTypedArray()
        override fun childrenNamesSpi(): Array<String> = emptyArray()
        override fun childSpi(name: String) = MemoryPreferences()
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }
}
