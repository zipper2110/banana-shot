package org.litvin.export

import org.junit.jupiter.api.Test
import org.litvin.ActiveQueueSnapshot
import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.license.AllowNewWork
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class RenderServiceTest {
    @Test
    fun enqueueBindsEachJobToTheOriginatingServiceGraphsOwnedStateAndRepository() {
        val firstCompleted = RecordingCompletedRendersRepository()
        val secondCompleted = RecordingCompletedRendersRepository()
        val gateway = RecordingRenderQueueGateway()
        val firstService = ProductionRenderService(firstCompleted, AllowNewWork, gateway)
        val secondService = ProductionRenderService(secondCompleted, AllowNewWork, gateway)
        val firstJob = renderJob("first")
        val secondJob = renderJob("second")

        firstService.enqueue(firstJob)
        secondService.enqueue(secondJob)

        assertSame(firstCompleted, gateway.requests[0].completedRenders)
        assertSame(secondCompleted, gateway.requests[1].completedRenders)
        firstService.close()
        secondService.close()
    }

    @Test
    fun closingAServiceCancelsOnlyRequestsOwnedByThatServiceGraph() {
        val gateway = RecordingRenderQueueGateway()
        val firstService = ProductionRenderService(RecordingCompletedRendersRepository(), AllowNewWork, gateway)
        val secondService = ProductionRenderService(RecordingCompletedRendersRepository(), AllowNewWork, gateway)
        val firstJob = renderJob("first-owned")
        val secondJob = renderJob("second-owned")
        firstService.enqueue(firstJob)
        secondService.enqueue(secondJob)

        firstService.close()

        val firstOwner = gateway.requests[0].ownerId
        val secondOwner = gateway.requests[1].ownerId
        assertNotEquals(firstOwner, secondOwner)
        assertEquals(listOf(firstOwner to firstJob.id), gateway.cancelQueuedRequests)
        assertEquals(listOf(firstOwner), gateway.cancelCurrentOwners)
        secondService.close()
    }

    @Test
    fun observationHandleRemovesExactlyItsObserver() {
        val gateway = RecordingRenderQueueGateway()
        val service = ProductionRenderService(RecordingCompletedRendersRepository(), AllowNewWork, gateway)
        val first: (ActiveQueueSnapshot) -> Unit = { }
        val second: (ActiveQueueSnapshot) -> Unit = { }

        val firstHandle = service.observe(first)
        service.observe(second)
        firstHandle.close()
        firstHandle.close()

        assertEquals(listOf(second), gateway.observers)
        service.close()
    }

    @Test
    fun cancellationOperationsDelegateAndReturnTheGatewayResult() {
        val gateway = RecordingRenderQueueGateway().apply { cancelQueuedResult = true }
        val service = ProductionRenderService(RecordingCompletedRendersRepository(), AllowNewWork, gateway)
        try {
            service.cancelCurrent()

            assertEquals(true, service.cancelQueued("queued-job"))
            assertEquals(1, gateway.cancelCurrentCalls)
            assertEquals(listOf("queued-job"), gateway.cancelQueuedIds)
        } finally {
            service.close()
        }
    }

    @Test
    fun closeAttemptsEveryObserverRemovalAndCancellationBeforePropagatingFailures() {
        val gateway = FailingCleanupGateway()
        val service = ProductionRenderService(RecordingCompletedRendersRepository(), AllowNewWork, gateway)
        val first: (ActiveQueueSnapshot) -> Unit = { }
        val second: (ActiveQueueSnapshot) -> Unit = { }
        service.observe(first)
        service.observe(second)
        gateway.failRemovalFor = second
        gateway.failCancellation = true

        val failure = assertFailsWith<IllegalStateException> { service.close() }

        assertEquals(listOf(second, first), gateway.removalAttempts)
        assertEquals(1, gateway.cancelCalls)
        assertEquals(1, failure.suppressed.size)
    }

    @Test
    fun everyNaturalTerminalOutcomeRemovesHistoricalJobIdsExactlyOnce() {
        val gateway = RecordingRenderQueueGateway()
        val service = ProductionRenderService(RecordingCompletedRendersRepository(), AllowNewWork, gateway)
        val outcomes = RenderTerminalOutcome.entries
        repeat(90) { index ->
            service.enqueue(renderJob("terminal-$index"))
        }
        gateway.requests.forEachIndexed { index, request ->
            request.signalTerminal(outcomes[index % outcomes.size])
            request.signalTerminal(outcomes[index % outcomes.size])
        }

        service.close()

        assertEquals(emptyList(), gateway.cancelQueuedRequests)
    }

    @Test
    fun terminalSignalFollowedByExceptionalEnqueueDoesNotRetainTheJob() {
        val gateway = TerminalThenThrowingGateway()
        val service = ProductionRenderService(RecordingCompletedRendersRepository(), AllowNewWork, gateway)

        assertFailsWith<IllegalStateException> { service.enqueue(renderJob("exceptional-terminal")) }

        service.close()

        assertEquals(emptyList(), gateway.cancelQueuedIds)
    }

    private class RecordingRenderQueueGateway : RenderQueueGateway {
        val observers = mutableListOf<(ActiveQueueSnapshot) -> Unit>()
        val requests = mutableListOf<RenderQueueRequest>()
        var cancelCurrentCalls = 0
        val cancelQueuedIds = mutableListOf<String>()
        val cancelCurrentOwners = mutableListOf<String>()
        val cancelQueuedRequests = mutableListOf<Pair<String, String>>()
        var cancelQueuedResult = false

        override fun enqueue(request: RenderQueueRequest) {
            requests += request
        }
        override fun cancelCurrent(ownerId: String) {
            cancelCurrentCalls++
            cancelCurrentOwners += ownerId
        }
        override fun cancelQueued(ownerId: String, jobId: String): Boolean {
            cancelQueuedIds += jobId
            cancelQueuedRequests += ownerId to jobId
            return cancelQueuedResult
        }
        override fun closeOwner(ownerId: String) {
            cancelCurrentCalls++
            cancelCurrentOwners += ownerId
        }
        override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            observers += observer
        }
        override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            observers.remove(observer)
        }
    }

    private class RecordingCompletedRendersRepository : CompletedRendersRepository {
        override fun loadAll(): List<CompletedRender> = emptyList()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }

    private class FailingCleanupGateway : RenderQueueGateway {
        val removalAttempts = mutableListOf<(ActiveQueueSnapshot) -> Unit>()
        var failRemovalFor: ((ActiveQueueSnapshot) -> Unit)? = null
        var failCancellation = false
        var cancelCalls = 0

        override fun enqueue(request: RenderQueueRequest) = Unit
        override fun cancelCurrent(ownerId: String) {
            cancelCalls++
            if (failCancellation) throw IllegalArgumentException("cancel failed")
        }
        override fun cancelQueued(ownerId: String, jobId: String): Boolean = false
        override fun closeOwner(ownerId: String) {
            cancelCalls++
            if (failCancellation) throw IllegalArgumentException("cancel failed")
        }
        override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) = Unit
        override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            removalAttempts += observer
            if (observer === failRemovalFor) throw IllegalStateException("remove failed")
        }
    }

    private class TerminalThenThrowingGateway : RenderQueueGateway {
        val cancelQueuedIds = mutableListOf<String>()

        override fun enqueue(request: RenderQueueRequest) {
            request.signalTerminal(RenderTerminalOutcome.FAILED)
            throw IllegalStateException("enqueue failed after terminal signal")
        }
        override fun cancelCurrent(ownerId: String) = Unit
        override fun cancelQueued(ownerId: String, jobId: String): Boolean {
            cancelQueuedIds += jobId
            return false
        }
        override fun closeOwner(ownerId: String) = Unit
        override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) = Unit
        override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) = Unit
    }

    private fun renderJob(id: String) = RenderJob(
        id = id,
        sourcePath = "$id.mp4",
        presetId = "balanced",
        outWidth = 1920,
        outHeight = 1080,
        encoderLabel = "H.264 (libx264)",
        idleTrim = false,
        outputPath = "$id-output.mp4",
    )
}
