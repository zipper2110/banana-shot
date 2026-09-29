package org.litvin.adjustments

import org.junit.jupiter.api.Test
import org.litvin.app.TrackedExecutorProvider
import java.time.Duration
import kotlin.test.assertEquals

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
}
