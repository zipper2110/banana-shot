package org.litvin.adjustments

import org.junit.jupiter.api.Test
import org.litvin.app.TrackedExecutorProvider
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdjustmentsSessionTest {
    @Test
    fun closeFlushesPendingStateAndAReplacementSessionStartsCleanlyFromDisk() {
        val projectDir = kotlin.io.path.createTempDirectory("adjustments-session-").toFile()
        val executors = TrackedExecutorProvider("adjustments-session-test", Duration.ofSeconds(5))
        try {
            val first = AdjustmentsSession(executors.createScheduledExecutor("first"), 60_000)
            first.load(projectDir.absolutePath)
            first.set { it.copy(brightness = 1.4f) }

            first.close()
            first.close()

            val replacement = AdjustmentsSession(executors.createScheduledExecutor("replacement"), 60_000)
            replacement.load(projectDir.absolutePath)
            assertEquals(1.4f, replacement.get().brightness)
            replacement.close()
        } finally {
            executors.close()
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun aSavedZoomBelowOneLoadsAsOne() {
        val projectDir = kotlin.io.path.createTempDirectory("adjustments-session-").toFile()
        val executors = TrackedExecutorProvider("adjustments-session-test", Duration.ofSeconds(5))
        try {
            AdjustmentsIO.write(AdjustmentsIO.adjustmentsFilePath(projectDir.absolutePath), AdjustmentsV1(zoom = 0.4f))
            val session = AdjustmentsSession(executors.createScheduledExecutor("zoom"), 60_000)
            session.load(projectDir.absolutePath)
            assertEquals(AdjustmentsV1.MIN_ZOOM, session.get().zoom)
            session.set { it.copy(zoom = 0.2f) }
            assertEquals(AdjustmentsV1.MIN_ZOOM, session.get().zoom)
            session.close()
        } finally {
            executors.close()
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun closingOneSubscriptionRemovesOnlyThatListener() {
        val executors = TrackedExecutorProvider("adjustments-listener-test", Duration.ofSeconds(5))
        try {
            val session = AdjustmentsSession(executors.createScheduledExecutor("session"))
            var firstCalls = 0
            var secondCalls = 0
            val first = session.subscribe { firstCalls++ }
            session.subscribe { secondCalls++ }

            first()
            first()
            session.set { it.copy(contrast = 1.2f) }
            javax.swing.SwingUtilities.invokeAndWait { }

            assertEquals(0, firstCalls)
            assertEquals(1, secondCalls)
            session.close()
        } finally {
            executors.close()
        }
    }

    @Test
    fun aFailedSaveTellsTheSubscriberOneTimeUntilASaveSucceeds() {
        val projectDir = kotlin.io.path.createTempDirectory("adjustments-save-error-").toFile()
        val executors = TrackedExecutorProvider("adjustments-save-error-test", Duration.ofSeconds(5))
        try {
            val session = AdjustmentsSession(executors.createScheduledExecutor("session"), 60_000)
            session.load(projectDir.absolutePath)
            val errors = mutableListOf<Exception>()
            session.subscribeSaveErrors { errors += it }
            // A folder with a file in it at the path of adjustments.json: the save cannot replace it.
            val blocker = File(AdjustmentsIO.adjustmentsFilePath(projectDir.absolutePath))
            File(blocker, "keep").apply { parentFile.mkdirs(); writeText("x") }

            session.set { it.copy(brightness = 1.2f) }
            session.flush()
            session.set { it.copy(brightness = 1.3f) }
            session.flush()
            javax.swing.SwingUtilities.invokeAndWait { }
            assertEquals(1, errors.size, "a series of failed saves tells the subscriber one time")

            blocker.deleteRecursively()
            session.flush()
            assertEquals(1.3f, AdjustmentsIO.readForProjectDir(projectDir.absolutePath).brightness)

            blocker.delete()
            File(blocker, "keep").apply { parentFile.mkdirs(); writeText("x") }
            session.set { it.copy(brightness = 1.4f) }
            session.flush()
            javax.swing.SwingUtilities.invokeAndWait { }
            assertEquals(2, errors.size, "after a successful save, the next failed save tells the subscriber again")
            session.close()
        } finally {
            executors.close()
            projectDir.deleteRecursively()
        }
    }

    @Test
    fun aSaveErrorSubscriptionStopsAfterUnsubscribe() {
        val projectDir = kotlin.io.path.createTempDirectory("adjustments-save-error-").toFile()
        val executors = TrackedExecutorProvider("adjustments-save-error-test", Duration.ofSeconds(5))
        try {
            val session = AdjustmentsSession(executors.createScheduledExecutor("session"), 60_000)
            session.load(projectDir.absolutePath)
            var calls = 0
            val unsubscribe = session.subscribeSaveErrors { calls++ }
            unsubscribe()
            File(File(AdjustmentsIO.adjustmentsFilePath(projectDir.absolutePath)), "keep")
                .apply { parentFile.mkdirs(); writeText("x") }

            session.set { it.copy(contrast = 1.2f) }
            session.flush()
            javax.swing.SwingUtilities.invokeAndWait { }

            assertEquals(0, calls)
            assertTrue(session.get().contrast == 1.2f, "the state keeps the change after a failed save")
            session.close()
        } finally {
            executors.close()
            projectDir.deleteRecursively()
        }
    }
}
