package org.litvin.export

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.litvin.ActiveQueueSnapshot
import org.litvin.CommentOverlaySpan
import org.litvin.CompletedRender
import org.litvin.OverlaySpan
import org.litvin.RenderJob
import org.litvin.RenderStatus
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.WhiteBalanceV1
import org.litvin.export.scoreboard.Corners
import org.litvin.export.scoreboard.SceneItem
import org.litvin.export.scoreboard.ScenePoint
import org.litvin.export.scoreboard.ScoreboardScene
import org.litvin.export.scoreboard.TextAnchor
import org.litvin.points.PointV1
import org.litvin.scoring.ScoreboardPosition
import org.litvin.scoring.ScoreboardSettingsV1
import org.litvin.scoring.ScoreboardStyleId
import org.litvin.stats.SetSummaryCard
import org.litvin.stats.StatsCardVideo
import java.io.File
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedRenderQueueTest {
    @TempDir
    lateinit var dir: File

    private val queueFile get() = dir.resolve("render-queue.json")
    private val lockFile get() = dir.resolve("render-queue.lock")

    @Test
    fun `a saved job loads with all its fields`() {
        val job = fullJob()
        val queue = SavedRenderQueue(queueFile)

        queue.save(listOf(job))

        assertEquals(listOf(job), queue.load())
    }

    @Test
    fun `the file contains each field of the job`() {
        SavedRenderQueue(queueFile).save(listOf(fullJob()))

        val saved = SavedRenderQueue.mapper.readTree(queueFile).get("jobs").get(0)
        val missing = RenderJob::class.primaryConstructor!!.parameters.map { it.name!! }.filterNot { saved.has(it) }
        assertEquals(emptyList(), missing, "Each field of RenderJob must be in the queue file. Give the field a value in fullJob().")
    }

    @Test
    fun `the write leaves no temporary file`() {
        val queue = SavedRenderQueue(queueFile)

        queue.save(listOf(job("a"), job("b")))
        queue.save(listOf(job("b")))

        assertEquals(listOf(queueFile.name), dir.list()!!.toList())
        assertEquals(listOf("b"), queue.load().map { it.id })
    }

    @Test
    fun `no file gives an empty queue`() {
        assertEquals(emptyList(), SavedRenderQueue(queueFile).load())
    }

    @Test
    fun `a damaged file gives an empty queue`() {
        queueFile.writeText("{ not json")

        assertEquals(emptyList(), SavedRenderQueue(queueFile).load())
    }

    @Test
    fun `a file with an unknown format gives an empty queue`() {
        queueFile.writeText("""{ "format": 2, "jobs": [] }""")

        assertEquals(emptyList(), SavedRenderQueue(queueFile).load())
    }

    @Test
    fun `a job that the app cannot read is ignored and the other jobs load`() {
        val queue = SavedRenderQueue(queueFile)
        queue.save(listOf(job("a"), job("b")))
        val root = SavedRenderQueue.mapper.readTree(queueFile) as com.fasterxml.jackson.databind.node.ObjectNode
        (root.get("jobs").get(0) as com.fasterxml.jackson.databind.node.ObjectNode).remove("sourcePath")
        SavedRenderQueue.mapper.writeValue(queueFile, root)

        assertEquals(listOf("b"), queue.load().map { it.id })
    }

    @Test
    fun `only the first process owns the file`() {
        val first = assertNotNull(SavedRenderQueue.claim(queueFile, lockFile))

        assertNull(SavedRenderQueue.claim(queueFile, lockFile))

        first.close()
        SavedRenderQueue.claim(queueFile, lockFile)!!.close()
    }

    @Test
    fun `the restore deletes the partial output files of the job`() {
        val output = dir.resolve("out.mp4")
        val partials = listOf("out.mp4.part", "out.mp4.part.chunk0.ts", "out.mp4.part.concat.txt", "out.mp4.part.ass")
            .map { dir.resolve(it).apply { writeText("x") } }
        val other = dir.resolve("other.mp4.part").apply { writeText("x") }

        SavedRenderQueue.deletePartialOutput(job("a").copy(outputPath = output.path))

        partials.forEach { assertFalse(it.exists(), "$it") }
        assertTrue(other.exists())
    }

    @Test
    fun `the service writes the file when a job is added, starts, completes, fails, or is canceled`() {
        val gateway = FakeGateway()
        val service = ProductionRenderService(NoCompletedRenders, gateway, SavedRenderQueue(queueFile))
        val queue = SavedRenderQueue(queueFile)

        service.enqueue(job("a"))
        service.enqueue(job("b"))
        service.enqueue(job("c"))
        service.enqueue(job("d"))
        assertEquals(listOf("a", "b", "c", "d"), queue.load().map { it.id })

        gateway.start("a")
        assertEquals(RenderStatus.RUNNING, queue.load().first().status)

        gateway.finish("a", RenderTerminalOutcome.COMPLETED)
        assertEquals(listOf("b", "c", "d"), queue.load().map { it.id })

        gateway.start("b")
        gateway.finish("b", RenderTerminalOutcome.FAILED)
        assertEquals(listOf("c", "d"), queue.load().map { it.id })

        gateway.cancelQueuedResult = true
        assertTrue(service.cancelQueued("d"))
        assertEquals(listOf("c"), queue.load().map { it.id })

        gateway.start("c")
        service.cancelCurrent()
        gateway.finish("c", RenderTerminalOutcome.CANCELED)
        assertEquals(emptyList(), queue.load())
        service.close()
    }

    @Test
    fun `three exports continue after the app closes during the first export`() {
        val output = dir.resolve("a.mp4")
        val firstGateway = FakeGateway()
        val first = ProductionRenderService(NoCompletedRenders, firstGateway, SavedRenderQueue.claim(queueFile, lockFile))
        first.enqueue(job("a").copy(outputPath = output.path))
        first.enqueue(job("b"))
        first.enqueue(job("c"))
        firstGateway.start("a")
        val partial = dir.resolve("a.mp4.part").apply { writeText("half") }

        // The close stops the running export and cancels the queue, but the file keeps the jobs.
        first.close()
        firstGateway.requests.forEach { it.signalTerminal(RenderTerminalOutcome.CANCELED) }

        val secondGateway = FakeGateway()
        val second = ProductionRenderService(NoCompletedRenders, secondGateway, SavedRenderQueue.claim(queueFile, lockFile))
        second.restoreSavedQueue()

        assertEquals(listOf("a", "b", "c"), secondGateway.requests.map { it.job.id })
        assertTrue(secondGateway.requests.all { it.job.status == RenderStatus.QUEUED && it.job.progress == 0.0 })
        assertFalse(partial.exists(), "The running export starts again from the beginning.")
        second.close()
    }

    @Test
    fun `a canceled export does not come back`() {
        val firstGateway = FakeGateway().apply { cancelQueuedResult = true }
        val first = ProductionRenderService(NoCompletedRenders, firstGateway, SavedRenderQueue.claim(queueFile, lockFile))
        first.enqueue(job("a"))
        first.enqueue(job("b"))
        first.cancelQueued("b")
        first.close()

        val secondGateway = FakeGateway()
        val second = ProductionRenderService(NoCompletedRenders, secondGateway, SavedRenderQueue.claim(queueFile, lockFile))
        second.restoreSavedQueue()

        assertEquals(listOf("a"), secondGateway.requests.map { it.job.id })
        second.close()
    }

    @Test
    fun `a service with no queue file keeps the queue in memory only`() {
        val service = ProductionRenderService(NoCompletedRenders, FakeGateway(), null)

        service.enqueue(job("a"))
        service.restoreSavedQueue()
        service.close()

        assertFalse(queueFile.exists())
    }

    @Test
    fun `a restored job with no source file fails with the reason Source file missing`() {
        val missingSource = dir.resolve("missing.mp4")
        SavedRenderQueue(queueFile).save(
            listOf(job("missing-source").copy(sourcePath = missingSource.path, outputPath = dir.resolve("out.mp4").path)),
        )
        val service = ProductionRenderService(NoCompletedRenders, SavedRenderQueue(queueFile))
        val failed = CountDownLatch(1)
        var reason: String? = null
        service.observe { snapshot ->
            snapshot.current?.takeIf { it.status == RenderStatus.FAILED }?.let {
                reason = it.failureReason
                failed.countDown()
            }
        }

        service.restoreSavedQueue()

        assertTrue(failed.await(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS))
        assertTrue(reason!!.startsWith("Source file missing"), reason)
        waitUntil { SavedRenderQueue(queueFile).load().isEmpty() }
        service.close()
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!condition()) {
            check(System.nanoTime() < end) { "Timeout" }
            Thread.sleep(20)
        }
    }

    /** The queue of the real manager: one worker, the jobs in order. The test starts and ends the jobs. */
    private class FakeGateway : RenderQueueGateway {
        val requests = mutableListOf<RenderQueueRequest>()
        private val observers = mutableListOf<(ActiveQueueSnapshot) -> Unit>()
        private var current: RenderQueueRequest? = null
        var cancelQueuedResult = false

        fun start(jobId: String) {
            val request = requests.single { it.job.id == jobId }
            request.job.status = RenderStatus.RUNNING
            current = request
            notifyObservers()
        }

        fun finish(jobId: String, outcome: RenderTerminalOutcome) {
            val request = requests.single { it.job.id == jobId }
            current = null
            notifyObservers()
            request.signalTerminal(outcome)
        }

        private fun notifyObservers() {
            val snapshot = ActiveQueueSnapshot(current?.job, emptyList())
            observers.toList().forEach { it(snapshot) }
        }

        override fun enqueue(request: RenderQueueRequest) {
            requests += request
        }
        override fun cancelCurrent(ownerId: String) = Unit
        override fun cancelQueued(ownerId: String, jobId: String): Boolean = cancelQueuedResult
        override fun closeOwner(ownerId: String) = Unit
        override fun addObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            observers += observer
            observer(ActiveQueueSnapshot(current?.job, emptyList()))
        }
        override fun removeObserver(ownerId: String, observer: (ActiveQueueSnapshot) -> Unit) {
            observers.remove(observer)
        }
    }

    private object NoCompletedRenders : CompletedRendersRepository {
        override fun loadAll(): List<CompletedRender> = emptyList()
        override fun append(job: RenderJob) = Unit
        override fun clear() = Unit
    }

    private fun job(id: String) = RenderJob(
        id = id,
        sourcePath = "$id.mp4",
        presetId = "balanced",
        outWidth = 1920,
        outHeight = 1080,
        encoderLabel = "H.264 (libx264)",
        idleTrim = false,
        outputPath = dir.resolve("$id-output.mp4").path,
    )

    /** A job with a value that is not the default in each field. */
    private fun fullJob(): RenderJob {
        val page = ScoreboardScene(
            width = 1920.0,
            height = 1080.0,
            items = listOf(
                SceneItem.Polygon(listOf(ScenePoint(0.0, 0.0), ScenePoint(1.0, 2.0)), rgb = 0x112233, opacity = 0.5),
                SceneItem.Polyline(listOf(ScenePoint(3.0, 4.0)), width = 2.0, rgb = 0x445566),
                SceneItem.Box(1.0, 2.0, 3.0, 4.0, rgb = 0x778899, corners = Corners.all(4.0)),
                SceneItem.Label(5.0, 6.0, "Aces", "Segoe UI", 20.0, rgb = 0xFFFFFF, anchor = TextAnchor.CENTER, outline = 1.0),
            ),
        )
        return RenderJob(
            id = "full",
            projectId = "project-1",
            projectName = "Final",
            sourcePath = "C:/videos/match.mp4",
            edlSnapshot = listOf(PointV1("p1", 1_000, 5_000, "label", "notes", favorite = true)),
            presetId = "high",
            outWidth = 3840,
            outHeight = 2160,
            outputFrameRate = "60",
            videoBitrateK = 40_000,
            expectedBytes = 123_456_789L,
            encoderLabel = "HEVC (NVENC)",
            idleTrim = true,
            favoriteOnly = true,
            includeScoreboard = true,
            overlayTimeline = listOf(
                OverlaySpan(
                    startMs = 0, endMs = 4_000, text = "15-0", p1Name = "A", p2Name = "B",
                    p1ColorHex = "#FF0000", p2ColorHex = "#00FF00", isTiebreak = true, tbP1 = 3, tbP2 = 2,
                    p1Pts = 1, p2Pts = 0, gamesP1 = 2, gamesP2 = 1, setsP1 = 1, setsP2 = 0,
                    completedSets = listOf(6 to 4), server = 2,
                ),
            ),
            scoreboardSettings = ScoreboardSettingsV1(
                style = ScoreboardStyleId.RETRO,
                title = "Club",
                showTitle = false,
                showAppCredit = false,
                showPlayerColors = false,
                showServe = false,
                position = ScoreboardPosition.BOTTOM_RIGHT,
                sizePercent = 150,
                backgroundOpacityPercent = 40,
                accentColorHex = "#123456",
            ),
            outputPath = "C:/videos/out.mp4",
            includeComments = true,
            commentOverlayTimeline = listOf(CommentOverlaySpan(7, 100, 900, "Nice", "#FFFF00")),
            statsCard = StatsCardVideo(listOf(page), pageDurationMs = 5_000),
            setSummaries = listOf(SetSummaryCard(1, setOf("p1"), StatsCardVideo(listOf(page)))),
            adjustments = AdjustmentsV1(
                brightness = 1.1f, contrast = 1.2f, saturation = 1.3f, shadows = 0.1f, highlights = -0.1f,
                whiteBalance = WhiteBalanceV1(0.2f), zoom = 1.5f, panX = 0.3f, panY = -0.3f, rotationDeg = 2f,
            ),
            status = RenderStatus.RUNNING,
            progress = 0.4,
            etaSeconds = 30,
            bytesWritten = 1_000,
            failureReason = "reason",
            stderrTail = "tail",
            createdAtEpochMs = 1_700_000_000_000L,
            updatedAtEpochMs = 1_700_000_001_000L,
        )
    }
}
