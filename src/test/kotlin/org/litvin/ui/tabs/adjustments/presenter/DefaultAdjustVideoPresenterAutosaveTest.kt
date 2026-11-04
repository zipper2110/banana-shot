package org.litvin.ui.tabs.adjustments.presenter

import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsSession
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.i18n.tr
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A failed save of `adjustments.json` shows "Autosave failed" on the Colors & Transform tab (B-38 T6). */
class DefaultAdjustVideoPresenterAutosaveTest {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val adjustments = AdjustmentsSession(scheduler, 60_000)
    private val presenter = DefaultAdjustVideoPresenter(adjustments)
    private val projectDir: File = Files.createTempDirectory("autosave-test").toFile()
    private val view = object : AdjustVideoView {
        val effects = mutableListOf<AdjustVideoViewEffect>()
        override fun render(state: AdjustVideoViewState) {}
        override fun renderEffect(effect: AdjustVideoViewEffect) {
            effects += effect
        }
    }

    private fun onEdt(body: () -> Unit) = SwingUtilities.invokeAndWait(body)

    /** Opens the project. The project has no manifest, so no source video. */
    private fun open() {
        presenter.attach(view)
        presenter.onIntent(AdjustVideoIntent.LoadProject(File(projectDir, "manifest.json").absolutePath))
        presenter.onActivated()
        view.effects.clear()
    }

    /** A folder with a file in it at the path of adjustments.json: a save cannot replace it. */
    private fun blockSaves() {
        File(File(AdjustmentsIO.adjustmentsFilePath(projectDir.absolutePath)), "keep")
            .apply { parentFile.mkdirs(); writeText("x") }
    }

    @AfterTest
    fun tearDown() {
        adjustments.close()
        scheduler.shutdownNow()
        projectDir.deleteRecursively()
    }

    @Test
    fun `a failed save shows Autosave failed with the reason`() = onEdt {
        open()
        blockSaves()
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.3f)))
        presenter.onDeactivated()

        val effect = assertIs<AdjustVideoViewEffect.AutosaveFailed>(view.effects.single())
        assertTrue(effect.message.isNotBlank())
        assertEquals("Autosave failed", tr("adjust.error.autosave"))
    }

    @Test
    fun `a successful save shows nothing`() = onEdt {
        open()
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.3f)))
        presenter.onDeactivated()

        assertEquals(emptyList(), view.effects)
        assertEquals(1.3f, AdjustmentsIO.readForProjectDir(projectDir.absolutePath).brightness)
    }

    @Test
    fun `a detached view gets no Autosave failed`() = onEdt {
        open()
        blockSaves()
        presenter.onIntent(AdjustVideoIntent.ChangeColors(AdjustmentsV1(brightness = 1.3f)))
        presenter.detach()
        adjustments.flush()

        assertEquals(emptyList(), view.effects)
    }
}
