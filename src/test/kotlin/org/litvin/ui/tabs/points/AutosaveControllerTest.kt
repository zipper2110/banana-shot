package org.litvin.ui.tabs.points

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The autosave reads the editor state on the EDT and writes only that snapshot on the save thread. */
class AutosaveControllerTest {
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "test-autosave") }
    private val saved = CopyOnWriteArrayList<List<String>>()
    private val snapshotThreads = CopyOnWriteArrayList<Boolean>()
    private val state = mutableListOf("a")
    private var projectOpen = true

    private val autosave = AutosaveController(
        debounceMs = 60_000,
        executor = executor,
        snapshot = {
            snapshotThreads += SwingUtilities.isEventDispatchThread()
            state.toList().takeIf { projectOpen }
        },
        saver = { snapshot -> saved += snapshot },
    )

    @AfterTest
    fun tearDown() {
        SwingUtilities.invokeAndWait { autosave.flushAndClose() }
    }

    @Test
    fun flushTakesTheSnapshotOnTheEdtAndSavesIt() {
        SwingUtilities.invokeAndWait {
            autosave.schedule()
            autosave.flush()
            // A change after the flush does not go into the saved snapshot.
            state += "b"
        }
        assertEquals(listOf(listOf("a")), saved)
        assertEquals(listOf(true), snapshotThreads)
    }

    @Test
    fun closeSavesThePendingSnapshot() {
        SwingUtilities.invokeAndWait {
            state += "b"
            autosave.schedule()
            autosave.flushAndClose()
        }
        assertEquals(listOf(listOf("a", "b")), saved)
    }

    @Test
    fun aNullSnapshotSkipsTheSave() {
        projectOpen = false
        SwingUtilities.invokeAndWait {
            autosave.schedule()
            autosave.flush()
        }
        assertTrue(saved.isEmpty())
        assertEquals(null, autosave.lastSavedAtMs)
    }
}
