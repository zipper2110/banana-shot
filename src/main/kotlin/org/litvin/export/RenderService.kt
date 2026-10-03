package org.litvin.export

import io.github.oshai.kotlinlogging.KotlinLogging
import org.litvin.ActiveQueueSnapshot
import org.litvin.RenderJob
import org.litvin.RenderQueueManager
import org.litvin.license.ExpiredVersionException
import org.litvin.license.NewWorkGate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

interface RenderService : AutoCloseable {
    fun enqueue(job: RenderJob)
    fun cancelCurrent()
    fun cancelQueued(jobId: String): Boolean
    fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable
}

internal const val LEGACY_RENDER_OWNER_ID = "legacy-render-queue"

internal enum class RenderTerminalOutcome { COMPLETED, FAILED, CANCELED }

internal class RenderQueueRequest(
    val ownerId: String,
    val job: RenderJob,
    val completedRenders: CompletedRendersRepository,
    private val onTerminal: (RenderTerminalOutcome) -> Unit = { },
) {
    private val terminalSignaled = AtomicBoolean(false)

    fun signalTerminal(outcome: RenderTerminalOutcome) {
        if (terminalSignaled.compareAndSet(false, true)) onTerminal(outcome)
    }
}

internal interface RenderQueueGateway {
    fun enqueue(request: RenderQueueRequest)
    fun cancelCurrent(ownerId: String)
    fun cancelQueued(ownerId: String, jobId: String): Boolean
    fun closeOwner(ownerId: String)
    fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit)
    fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit)
}

private object GlobalRenderQueueGateway : RenderQueueGateway {
    override fun enqueue(request: RenderQueueRequest) = RenderQueueManager.enqueue(request)
    override fun cancelCurrent(ownerId: String) = RenderQueueManager.cancelCurrent(ownerId)
    override fun cancelQueued(ownerId: String, jobId: String): Boolean = RenderQueueManager.cancelQueued(ownerId, jobId)
    override fun closeOwner(ownerId: String) = RenderQueueManager.closeOwner(ownerId)
    override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) =
        RenderQueueManager.addObserver(ownerId, observer)
    override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) =
        RenderQueueManager.removeObserver(ownerId, observer)
}

class ProductionRenderService internal constructor(
    private val completedRenders: CompletedRendersRepository,
    // The expiry check of the function that adds a new export (E7-S2). The restore of the saved queue does not use it.
    private val newWork: NewWorkGate,
    private val gateway: RenderQueueGateway = GlobalRenderQueueGateway,
    // The queue file of B-18. Null keeps the queue in memory only.
    private val savedQueue: SavedRenderQueue? = null,
) : RenderService {
    constructor(completedRenders: CompletedRendersRepository, newWork: NewWorkGate, savedQueue: SavedRenderQueue?) :
        this(completedRenders, newWork, GlobalRenderQueueGateway, savedQueue)

    private companion object {
        private val ownerSequence = AtomicLong(0L)
        private val logger = KotlinLogging.logger {}
    }

    private val ownerId = "render-service-${ownerSequence.incrementAndGet()}"
    private val subscriptions = CopyOnWriteArrayList<AutoCloseable>()
    private val ownedJobIds = ConcurrentHashMap.newKeySet<String>()
    private val closed = AtomicBoolean(false)

    // The jobs that are not terminal, in the queue order. The queue file contains these jobs.
    private val pendingJobs = LinkedHashMap<String, RenderJob>()
    private var savedRunningJobId: String? = null
    private var saving = savedQueue != null
    private val savedQueueObserver: (ActiveQueueSnapshot) -> Unit = { snapshot -> onQueueSnapshot(snapshot) }

    init {
        if (savedQueue != null) gateway.addObserver(ownerId, savedQueueObserver)
    }

    /** A new export of the user. It refuses in expired mode. The worker of the queue does not check the expiry. */
    override fun enqueue(job: RenderJob) {
        if (!newWork.allowsNewWork()) throw ExpiredVersionException()
        add(job)
    }

    /**
     * Puts the jobs of the saved queue back in the queue, in the same order, and starts them (B-18).
     * This is a separate entry from [enqueue]: the restore must not use the expiry check of a new
     * export (`build-expiry-spec.md`, "Expiry check"). The restored jobs continue also in expired mode.
     */
    fun restoreSavedQueue() {
        val queue = savedQueue ?: return
        val jobs = try {
            queue.load()
        } catch (failure: Throwable) {
            logger.warn(failure) { "Cannot restore the saved export queue." }
            return
        }
        if (jobs.isEmpty()) return
        logger.info { "Restoring ${jobs.size} export(s) from the saved queue." }
        jobs.distinctBy { it.id }.forEach { saved ->
            val job = SavedRenderQueue.restoredCopy(saved)
            try {
                // A job that ran when the app closed starts again from the beginning.
                SavedRenderQueue.deletePartialOutput(job)
                add(job)
            } catch (failure: Throwable) {
                logger.warn(failure) { "Cannot restore the export ${job.id} (${job.outputPath})." }
            }
        }
    }

    private fun add(job: RenderJob) {
        synchronized(subscriptions) {
            check(!closed.get()) { "Render service is closed" }
            ownedJobIds += job.id
            updateSavedQueue { pendingJobs[job.id] = job; true }
            try {
                gateway.enqueue(
                    RenderQueueRequest(ownerId, job, completedRenders) {
                        ownedJobIds.remove(job.id)
                        updateSavedQueue { pendingJobs.remove(job.id) != null }
                    },
                )
            } catch (failure: Throwable) {
                ownedJobIds.remove(job.id)
                updateSavedQueue { pendingJobs.remove(job.id) != null }
                throw failure
            }
        }
    }

    override fun cancelCurrent() = gateway.cancelCurrent(ownerId)

    override fun cancelQueued(jobId: String): Boolean = gateway.cancelQueued(ownerId, jobId).also { removed ->
        if (removed) {
            ownedJobIds.remove(jobId)
            updateSavedQueue { pendingJobs.remove(jobId) != null }
        }
    }

    /** Writes the queue file again when a job starts. */
    private fun onQueueSnapshot(snapshot: ActiveQueueSnapshot) {
        val runningId = snapshot.current?.id
        updateSavedQueue {
            val changed = runningId != savedRunningJobId
            savedRunningJobId = runningId
            changed
        }
    }

    /** Runs [change] and writes the queue file when [change] gives true. */
    private fun updateSavedQueue(change: () -> Boolean) {
        val queue = savedQueue ?: return
        synchronized(pendingJobs) {
            if (!saving) return
            if (change()) queue.save(pendingJobs.values.toList())
        }
    }

    override fun observe(observer: (ActiveQueueSnapshot) -> Unit): AutoCloseable {
        synchronized(subscriptions) {
            check(!closed.get()) { "Render service is closed" }
            val subscriptionClosed = AtomicBoolean(false)
            val handle = AutoCloseable {
                if (subscriptionClosed.compareAndSet(false, true)) {
                    gateway.removeObserver(ownerId, observer)
                }
            }
            subscriptions += handle
            try {
                gateway.addObserver(ownerId, observer)
            } catch (failure: Throwable) {
                subscriptions.remove(handle)
                handle.close()
                throw failure
            }
            return handle
        }
    }

    override fun close() {
        val (ownedSubscriptions, queuedJobIds) = synchronized(subscriptions) {
            if (!closed.compareAndSet(false, true)) return
            val observers = subscriptions.toList().asReversed().also { subscriptions.clear() }
            observers to ownedJobIds.toList()
        }
        // The close stops the jobs, but they must stay in the queue file for the next start (B-18).
        if (savedQueue != null) synchronized(pendingJobs) { saving = false }
        var failure: Throwable? = null
        if (savedQueue != null) {
            failure = attemptCleanup(failure) { gateway.removeObserver(ownerId, savedQueueObserver) }
        }
        ownedSubscriptions.forEach { subscription ->
            failure = attemptCleanup(failure) { subscription.close() }
        }
        queuedJobIds.forEach { jobId ->
            failure = attemptCleanup(failure) { cancelQueued(jobId) }
        }
        failure = attemptCleanup(failure) { gateway.closeOwner(ownerId) }
        failure = attemptCleanup(failure) { savedQueue?.close() }
        failure?.let { throw it }
    }

    private fun attemptCleanup(previous: Throwable?, action: () -> Unit): Throwable? = try {
        action()
        previous
    } catch (failure: Throwable) {
        if (previous == null) failure else previous.apply { addSuppressed(failure) }
    }
}
