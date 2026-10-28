package org.litvin.ui.flow.harness

import org.litvin.ui.flow.driver.SwingUiDriver
import java.awt.Dialog
import java.awt.EventQueue
import java.awt.Window
import java.util.concurrent.FutureTask
import javax.swing.KeyStroke

/**
 * A driver that saves the new windows before each action (B-11 E4-T4). Before the action, the windows show the
 * result of the last action, and the test already waited for it.
 */
internal class ScreenshotSwingDriver(
    private val delegate: SwingUiDriver,
    private val screenshots: UiFlowScreenshots,
) : SwingUiDriver by delegate {
    override fun click(name: String) {
        screenshots.captureNewWindows()
        delegate.click(name)
    }

    override fun setText(name: String, value: String) {
        screenshots.captureNewWindows()
        delegate.setText(name, value)
    }

    override fun setSlider(name: String, value: Int) {
        screenshots.captureNewWindows()
        delegate.setSlider(name, value)
    }

    override fun setSelected(name: String, selected: Boolean) {
        screenshots.captureNewWindows()
        delegate.setSelected(name, selected)
    }

    override fun select(name: String, value: String) {
        screenshots.captureNewWindows()
        delegate.select(name, value)
    }

    override fun press(keyStroke: KeyStroke) {
        screenshots.captureNewWindows()
        delegate.press(keyStroke)
    }

    /** The dialog can open after the call starts. Wait for it first, so the screenshot has it. */
    override fun dismissDialog(title: String, buttonText: String) {
        runCatching { delegate.waitUntil("dialog '$title' to be showing") { dialogShowing(title) } }
        screenshots.captureNewWindows()
        delegate.dismissDialog(title, buttonText)
    }

    private fun dialogShowing(title: String): Boolean {
        val task = FutureTask { Window.getWindows().any { it is Dialog && it.isShowing && it.title == title } }
        if (EventQueue.isDispatchThread()) task.run() else EventQueue.invokeAndWait(task)
        return task.get()
    }
}
