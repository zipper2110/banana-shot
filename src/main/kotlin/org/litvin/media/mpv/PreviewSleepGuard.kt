package org.litvin.media.mpv

import com.sun.jna.Platform
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.Desktop
import java.awt.desktop.ScreenSleepEvent
import java.awt.desktop.ScreenSleepListener
import java.awt.desktop.SystemSleepEvent
import java.awt.desktop.SystemSleepListener
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.SwingUtilities

/** A preview that closes its native player before a sleep and starts it again after the wake. */
internal interface SleepAwarePreview {
    /** Closes the native player. Called on the event dispatch thread. */
    fun suspendForSleep()

    /** Starts the native player again when the preview needs it. Called on the event dispatch thread. */
    fun resumeAfterSleep()
}

/**
 * Closes the mpv previews before the Mac or its display sleeps, and starts them again after the wake (B-13).
 *
 * Each mpv video window runs a CoreVideo display link until mpv closes, also while the video is paused. A display
 * link that runs while the displays go away at sleep can crash the process inside CoreVideo. Without mpv, the
 * preview also has no audio output and no Vulkan device during the sleep.
 *
 * The guard is for macOS only. On Windows, mpv draws into a child window of the app and has no display link.
 */
internal class PreviewSleepGuard {
    private companion object {
        val logger = KotlinLogging.logger {}
    }

    private val previews = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap<SleepAwarePreview, Boolean>()))

    fun register(preview: SleepAwarePreview) {
        previews += preview
    }

    fun unregister(preview: SleepAwarePreview) {
        previews -= preview
    }

    /** Closes the native players. Call it on the event dispatch thread. */
    fun onSleep(reason: String) {
        val current = snapshot()
        logger.info { "Closing ${current.size} preview player(s) before the $reason sleeps." }
        current.forEach { preview -> runSafely("suspend") { preview.suspendForSleep() } }
    }

    /** Starts the native players again. Call it on the event dispatch thread. */
    fun onWake(reason: String) {
        val current = snapshot()
        logger.info { "Starting the preview players again after the $reason woke." }
        current.forEach { preview -> runSafely("resume") { preview.resumeAfterSleep() } }
    }

    private fun snapshot(): List<SleepAwarePreview> = synchronized(previews) { previews.toList() }

    private inline fun runSafely(action: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            logger.warn(t) { "A preview failed to $action for a sleep." }
        }
    }
}

/** The one [PreviewSleepGuard] of the app. It listens to the sleep events of macOS. */
internal object MacPreviewSleep {
    private val logger = KotlinLogging.logger {}

    val guard = PreviewSleepGuard()

    @Volatile private var installed = false

    /** Adds [preview] to the guard. On the first call on macOS, it also adds the sleep listener of the app. */
    fun register(preview: SleepAwarePreview) {
        if (!Platform.isMac()) return
        install()
        guard.register(preview)
    }

    fun unregister(preview: SleepAwarePreview) {
        guard.unregister(preview)
    }

    @Synchronized
    private fun install() {
        if (installed) return
        installed = true
        val desktop = runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop() else null }.getOrNull()
        if (desktop == null || !desktop.isSupported(Desktop.Action.APP_EVENT_SYSTEM_SLEEP)) {
            logger.warn { "The app gets no sleep events. The preview players stay open during a sleep." }
            return
        }
        // The system sleep and the display sleep each close the players. Each wake starts them again. A second
        // close or start does nothing.
        desktop.addAppEventListener(object : SystemSleepListener, ScreenSleepListener {
            override fun systemAboutToSleep(e: SystemSleepEvent) = onEdt { guard.onSleep("Mac") }
            override fun systemAwoke(e: SystemSleepEvent) = onEdt { guard.onWake("Mac") }
            override fun screenAboutToSleep(e: ScreenSleepEvent) = onEdt { guard.onSleep("display") }
            override fun screenAwoke(e: ScreenSleepEvent) = onEdt { guard.onWake("display") }
        })
        logger.info { "The preview players close before a sleep." }
    }

    private fun onEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}
