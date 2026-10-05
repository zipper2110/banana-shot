package org.litvin.ui.tabs.points

import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.Timer

/**
 * Debounced autosave controller for the Points tab.
 *
 * - Schedules autosave with a debounce timer (no EDT blocking)
 * - Takes a [snapshot] of the state on the thread that starts the save (the EDT), so the
 *   background thread never reads the mutable editor state. A null snapshot skips the save.
 * - Executes [saver] with that snapshot off-EDT
 */
class AutosaveController<T : Any>(
    debounceMs: Int = 300,
    private val executor: ExecutorService,
    private val snapshot: () -> T?,
    private val saver: (T) -> Unit,
) : AutoCloseable {
    private val timer = Timer(debounceMs) { _ -> triggerSave() }.apply { isRepeats = false }
    private val closed = AtomicBoolean(false)
    private val saveLock = Any()
    private var inFlightSave: Future<*>? = null

    @Volatile
    private var _lastSavedAtMs: Long? = null

    fun schedule() {
        check(!closed.get()) { "Autosave controller is closed" }
        timer.restart()
    }

    fun autosaveNow() {
        if (timer.isRunning) timer.stop()
        triggerSave()
    }

    /**
     * Saves pending changes and waits for them to reach disk. Callers that hand the project over to
     * another view (a tab switch) need the file to be current before that view reads it.
     */
    fun flush() {
        if (closed.get()) return
        if (timer.isRunning) timer.stop()
        triggerSave()
        val pending = synchronized(saveLock) { inFlightSave }
        try {
            pending?.get()
        } catch (_: Exception) {
            // The saver reports its own failures; waiting for it must not fail the caller.
        }
    }

    fun isPending(): Boolean = try {
        timer.isRunning
    } catch (_: Throwable) {
        false
    }

    val lastSavedAtMs: Long?
        get() = _lastSavedAtMs

    private fun triggerSave() {
        synchronized(saveLock) {
            if (closed.get()) return
            submitSave()?.let { inFlightSave = it }
        }
    }

    /** Takes the snapshot on the calling thread and writes it off-EDT. */
    private fun submitSave(): Future<*>? {
        val state = snapshot() ?: return null
        return executor.submit {
            saver(state)
            _lastSavedAtMs = System.currentTimeMillis()
        }
    }

    fun flushAndClose() {
        if (!closed.compareAndSet(false, true)) return
        val hadPendingTimer = timer.isRunning
        timer.stop()
        val pending = synchronized(saveLock) {
            if (hadPendingTimer) {
                submitSave()?.also { inFlightSave = it } ?: inFlightSave
            } else {
                inFlightSave
            }
        }
        try {
            pending?.get()
        } finally {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                executor.shutdownNow()
            }
        }
    }

    override fun close() = flushAndClose()
}
