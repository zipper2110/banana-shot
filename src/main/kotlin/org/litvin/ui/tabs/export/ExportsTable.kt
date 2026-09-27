package org.litvin.ui.tabs.export

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.litvin.ActiveQueueSnapshot
import org.litvin.CompletedRender
import org.litvin.RenderJob
import org.litvin.RenderStatus
import org.litvin.export.CompletedRendersRepository
import org.litvin.export.ExportCardInfo
import org.litvin.export.RenderFormatting
import org.litvin.export.RenderService
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.applyDarkScrollbar
import org.litvin.ui.tabs.export.ExportUi.Weight
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The right side of the Export tab: one table with the active export, the export queue and the completed exports.
 * The column header and the group captions stay at the top while the table scrolls.
 */
internal class ExportsTable(
    private val renderService: RenderService,
    private val repository: CompletedRendersRepository,
    private val dialogs: UserDialogService,
    onCancelActive: () -> Unit,
) : JPanel(BorderLayout()) {
    private var activeOutputPath: String? = null
    private val activeRow = ActiveRow(
        onOpenFolder = { activeOutputPath?.let { openFolder(it, this, dialogs) } },
        onCancel = onCancelActive,
    ).apply { isVisible = false }
    private val activeEmpty = EmptyRow(Material2OutlinedAL.HOURGLASS_EMPTY, "No active exports")
    private val activeGroup = Group("Active", withCount = false)
    private val queueGroup = Group("Export queue", withCount = true)
    private val queueRows = Stack(0)
    private val completedGroup = Group("Completed", withCount = true) {
        GhostButton("Clear list", Material2OutlinedAL.DELETE_SWEEP).apply {
            toolTipText = "Remove all entries from this list. The exported files stay on the disk."
            addActionListener { clearCompleted() }
        }
    }
    private val completedRows = Stack(0)
    private val completedEmpty = EmptyRow(Material2OutlinedAL.CHECK_CIRCLE_OUTLINE, "No completed exports")

    private val runningStat = Stat("running")
    private val failedStat = Stat("failed", ExportUi.YELLOW)
    private val queueStat = Stat("in the queue")
    private val completedStat = Stat("completed")

    private val body = TableBody()
    private val sticky: StickyTable

    private var shownQueueIds: List<String>? = null
    private var shownCompleted: List<CompletedRender>? = null

    init {
        name = "export-exports"
        isOpaque = true
        background = ExportUi.BG
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)

        completedGroup.header.button?.name = "export-completed-clear"
        queueGroup.header.isVisible = false
        body.add(activeGroup.header)
        body.add(activeEmpty)
        body.add(activeRow)
        body.add(queueGroup.header)
        body.add(queueRows)
        body.add(completedGroup.header)
        body.add(completedRows)
        body.add(completedEmpty)
        sticky = StickyTable(body, HeadRow(), listOf(activeGroup, queueGroup, completedGroup))

        val card = RoundedCard()
        card.add(HeaderBar(listOf(runningStat, failedStat, queueStat, completedStat)), BorderLayout.NORTH)
        card.add(sticky, BorderLayout.CENTER)
        add(card, BorderLayout.CENTER)
        showSnapshot(null)
        showCompleted(emptyList())
    }

    /** The progress bar and the "Cancel export" button of the active export. */
    val progressBar: JProgressBar get() = activeRow.progress
    val cancelButton: JComponent get() = activeRow.cancelButton

    /** Shows the active export and the queue of a render queue snapshot. */
    fun showSnapshot(snapshot: ActiveQueueSnapshot?) {
        val current = snapshot?.current
        activeOutputPath = current?.outputPath
        activeEmpty.isVisible = current == null
        activeRow.isVisible = current != null
        if (current != null) activeRow.show(current)
        runningStat.show(if (current != null && (current.status == RenderStatus.RUNNING || current.status == RenderStatus.QUEUED)) 1 else 0)
        failedStat.show(if (current?.status == RenderStatus.FAILED) 1 else 0)

        // The queue sends a snapshot for each progress step of the current export. Build the rows only when the queue changes.
        val queued = snapshot?.queued.orEmpty()
        queueStat.show(queued.size)
        val ids = queued.map(RenderJob::id)
        if (ids != shownQueueIds) {
            shownQueueIds = ids
            queueRows.removeAll()
            queued.forEachIndexed { index, job -> queueRows.add(queuedRow(job, index)) }
            queueGroup.setCount(queued.size)
            queueGroup.header.isVisible = queued.isNotEmpty()
        }
        body.revalidate()
        body.repaint()
        sticky.updateSticky()
    }

    fun refreshCompletedFromStore() {
        val items = try {
            repository.loadAll()
        } catch (_: Throwable) {
            return
        }
        showCompleted(items)
    }

    private fun showCompleted(items: List<CompletedRender>) {
        if (items == shownCompleted) return
        shownCompleted = items
        completedRows.removeAll()
        items.forEachIndexed { index, item -> completedRows.add(completedRow(item, index)) }
        completedEmpty.isVisible = items.isEmpty()
        completedGroup.setCount(items.size)
        completedStat.show(items.size, always = true)
        body.revalidate()
        body.repaint()
        sticky.updateSticky()
    }

    private fun clearCompleted() {
        if (dialogs.confirm(this, "Clear the list of completed exports? The exported files stay on the disk.", "Confirm")) {
            try { repository.clear() } catch (_: Throwable) { }
            showCompleted(emptyList())
        }
    }

    private fun queuedRow(job: RenderJob, index: Int): DataRow {
        val info = ExportCardInfo.of(job)
        val prefix = "export-queued-${job.id}"
        return DataRow().apply {
            setCells(
                status = StatusPill("Queued · ${index + 1}", StatusPill.Kind.QUEUED),
                file = fileCell(info, "$prefix-path"),
                content = textCell(info.content, ExportUi.FG),
                video = textCell(info.video, ExportUi.FG_2),
                size = valueCell(job.expectedBytes?.let { "~" + RenderFormatting.formatSize(it) } ?: DASH, null),
                time = valueCell("Waiting", null, ExportUi.FG_3),
                actions = actions(
                    IconButton(Material2AL.FOLDER_OPEN, OPEN_FOLDER).apply {
                        name = "$prefix-open-folder"
                        addActionListener { openFolder(job.outputPath, this@ExportsTable, dialogs) }
                    },
                    IconButton(Material2OutlinedAL.CLOSE, CANCEL_EXPORT, danger = true).apply {
                        name = "$prefix-cancel"
                        addActionListener {
                            if (dialogs.confirm(this@ExportsTable, "Cancel the queued export?", "Confirm")) renderService.cancelQueued(job.id)
                        }
                    },
                ),
            )
        }
    }

    private fun completedRow(item: CompletedRender, index: Int): DataRow {
        val info = ExportCardInfo.of(item)
        val prefix = "export-completed-$index"
        val finished = item.createdAtEpochMs.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()) }
        return DataRow().apply {
            setCells(
                status = StatusPill("Done", StatusPill.Kind.DONE),
                file = fileCell(info, "$prefix-path"),
                content = textCell(info.content, ExportUi.FG),
                video = textCell(info.video, ExportUi.FG_2),
                size = valueCell(RenderFormatting.formatSize(item.bytesWritten), item.expectedBytes?.let { "of ~" + RenderFormatting.formatSize(it) }),
                time = valueCell(finished?.format(TIME_FORMAT) ?: DASH, finished?.format(DATE_FORMAT)),
                actions = actions(
                    IconButton(Material2AL.FOLDER_OPEN, OPEN_FOLDER).apply {
                        name = "$prefix-open-folder"
                        addActionListener { openFolder(item.outputPath, this@ExportsTable, dialogs) }
                    },
                ),
            )
        }
    }

    /** The active export. The table updates this row for each progress step, so it keeps its components. */
    private inner class ActiveRow(onOpenFolder: () -> Unit, onCancel: () -> Unit) : DataRow() {
        private val pill = StatusPill("Running", StatusPill.Kind.RUNNING)
        private val file = FileCell().apply { nameText.name = "export-active-path" }
        private val content = textCell("", ExportUi.FG).apply { name = "export-active-content" }
        private val video = textCell("", ExportUi.FG_2).apply { name = "export-active-video" }
        private val size = valueCell("", null).apply { name = "export-active-size" }
        private val time = valueCell("", null)
        val progress = ProgressLine().apply { name = "export-progress" }
        private val percent = WrapText("", ExportUi.font(12f, Weight.BOLD), ExportUi.FG, align = WrapText.Align.RIGHT)
        private val progressRow = ProgressRow(progress, percent)
        private val errorBox = ErrorBox()
        val cancelButton = IconButton(Material2OutlinedAL.CLOSE, CANCEL_EXPORT, danger = true).apply {
            name = "export-cancel"
            addActionListener { onCancel() }
        }

        init {
            setCells(
                status = pill,
                file = file,
                content = content,
                video = video,
                size = size,
                time = time,
                actions = actions(
                    IconButton(Material2AL.FOLDER_OPEN, OPEN_FOLDER).apply {
                        name = "export-active-open-folder"
                        addActionListener { onOpenFolder() }
                    },
                    cancelButton,
                ),
            )
            // The progress bar is in the component tree before the first export, so that the UI tests can find it.
            extra = progressRow
        }

        fun show(job: RenderJob) {
            val info = ExportCardInfo.of(job)
            file.show(info)
            content.setText(info.content, CELL_FONT, ExportUi.FG)
            video.setText(info.video, CELL_FONT, ExportUi.FG_2)
            size.show(info.size, null)
            val percentValue = (job.progress * 100).toInt().coerceIn(0, 100)
            progress.value = percentValue
            percent.setText("$percentValue%", ExportUi.font(12f, Weight.BOLD), ExportUi.FG)
            cancelButton.isEnabled = job.status == RenderStatus.RUNNING || job.status == RenderStatus.QUEUED
            when (job.status) {
                RenderStatus.QUEUED -> {
                    pill.set("Starting", StatusPill.Kind.RUNNING)
                    tone = Tone.LIVE
                    time.show(DASH, null)
                    extra = progressRow
                }
                RenderStatus.RUNNING -> {
                    pill.set("Running", StatusPill.Kind.RUNNING)
                    tone = Tone.LIVE
                    val eta = job.etaSeconds
                    if (eta != null) time.show(RenderFormatting.formatDuration(eta * 1000), "left") else time.show("calculating…", null, ExportUi.FG_2)
                    extra = progressRow
                }
                RenderStatus.COMPLETED -> {
                    pill.set("Done", StatusPill.Kind.DONE)
                    tone = Tone.NORMAL
                    time.show(DASH, null)
                    extra = progressRow
                }
                RenderStatus.FAILED -> {
                    pill.set("Failed", StatusPill.Kind.FAILED)
                    tone = Tone.FAILED
                    time.show(DASH, null)
                    errorBox.show(job.failureReason ?: "Unknown error")
                    extra = errorBox
                }
                RenderStatus.CANCELED -> {
                    pill.set("Canceled", StatusPill.Kind.QUEUED)
                    tone = Tone.NORMAL
                    time.show(DASH, null)
                    extra = null
                }
            }
            revalidate()
            repaint()
        }
    }

    /** The body of the table. A hover over a data row gives the row a lighter background. */
    private class TableBody : ScrollableStack(0) {
        private var hovered: DataRow? = null
        private val hoverListener = AWTEventListener { event ->
            val mouse = event as? MouseEvent ?: return@AWTEventListener
            val source = mouse.component ?: return@AWTEventListener
            val row = if (mouse.id != MouseEvent.MOUSE_EXITED && SwingUtilities.isDescendingFrom(source, this)) {
                val point = SwingUtilities.convertPoint(source, mouse.point, this)
                val deepest = SwingUtilities.getDeepestComponentAt(this, point.x, point.y)
                deepest as? DataRow ?: SwingUtilities.getAncestorOfClass(DataRow::class.java, deepest) as? DataRow
            } else {
                null
            }
            if (row !== hovered) {
                hovered?.hovered = false
                hovered = row
                row?.hovered = true
            }
        }

        override fun addNotify() {
            super.addNotify()
            Toolkit.getDefaultToolkit().addAWTEventListener(hoverListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
        }

        override fun removeNotify() {
            Toolkit.getDefaultToolkit().removeAWTEventListener(hoverListener)
            super.removeNotify()
        }
    }

    /**
     * The scroll pane of the table. The column header stays at the top. The caption of the group
     * that has scrolled past the top shows as a copy over the top of the rows, like a sticky header.
     */
    private class StickyTable(private val body: TableBody, head: HeadRow, private val groups: List<Group>) : JPanel(null) {
        private val scroll = JScrollPane(body).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            setColumnHeaderView(head)
            columnHeader.isOpaque = false
            setCorner(ScrollPaneConstants.UPPER_RIGHT_CORNER, HeadCorner())
            applyDarkScrollbar(this, ExportUi.CARD)
            viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
            viewport.addChangeListener { updateSticky() }
        }
        private var stuck: GroupHeader? = null

        init {
            isOpaque = false
            add(scroll)
            // A new row moves the group captions below it, so the sticky caption can change.
            body.addComponentListener(object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent) = updateSticky()
            })
            groups.forEach { group ->
                group.twin.addMouseWheelListener { event ->
                    scroll.dispatchEvent(SwingUtilities.convertMouseEvent(group.twin, event, scroll))
                }
            }
        }

        override fun isOptimizedDrawingEnabled() = false

        override fun doLayout() {
            scroll.setBounds(0, 0, width, height)
            scroll.doLayout()
            placeSticky()
        }

        fun updateSticky() {
            val top = scroll.viewport.viewPosition.y
            val header = groups.map { it.header }.lastOrNull { it.isVisible && it.parent === body && it.y < top }
            val twin = header?.let { h -> groups.first { it.header === h }.twin }
            if (twin !== stuck) {
                stuck?.let(::remove)
                stuck = twin
                twin?.let { add(it, 0) }
            }
            placeSticky()
            repaint()
        }

        private fun placeSticky() {
            val twin = stuck ?: return
            val viewport = SwingUtilities.convertRectangle(scroll, scroll.viewport.bounds, this)
            twin.setBounds(viewport.x, viewport.y, viewport.width, twin.heightForWidth(viewport.width))
            twin.validate()
        }
    }

    /** A group of rows: the caption in the table, and a copy of the caption for the sticky position. */
    private class Group(title: String, withCount: Boolean, button: (() -> JComponent)? = null) {
        val header = GroupHeader(title, withCount, button?.invoke())
        val twin = GroupHeader(title, withCount, button?.invoke())

        fun setCount(count: Int) {
            header.count = count
            twin.count = count
        }
    }

    private class GroupHeader(title: String, withCount: Boolean, val button: JComponent?) : JPanel(null), HeightForWidth {
        private val titleText = WrapText(title, ExportUi.font(12f, Weight.SEMIBOLD), ExportUi.FG_2)
        private val countPill = CountPill().apply { isVisible = withCount }

        var count: Int = 0
            set(value) {
                field = value
                countPill.text = value.toString()
            }

        init {
            isOpaque = false
            add(titleText)
            add(countPill)
            button?.let(::add)
        }

        override fun heightForWidth(width: Int): Int =
            PAD_TOP + maxOf(titleText.naturalHeight(), countPill.preferredSize.height, button?.preferredSize?.height ?: 0) + PAD_BOTTOM + 1

        override fun getPreferredSize() = Dimension(400, heightForWidth(400))

        override fun doLayout() {
            val inner = height - PAD_TOP - PAD_BOTTOM - 1
            var x = PAD_X
            val titleWidth = titleText.naturalWidth()
            val titleHeight = titleText.naturalHeight()
            titleText.setBounds(x, PAD_TOP + (inner - titleHeight) / 2, titleWidth, titleHeight)
            x += titleWidth + 8
            if (countPill.isVisible) {
                val size = countPill.preferredSize
                countPill.setBounds(x, PAD_TOP + (inner - size.height) / 2, size.width, size.height)
            }
            button?.let {
                val size = it.preferredSize
                it.setBounds(width - PAD_X - size.width, PAD_TOP + (inner - size.height) / 2, size.width, size.height)
            }
        }

        override fun paintComponent(g: Graphics) {
            g.color = GROUP_BG
            g.fillRect(0, 0, width, height)
            g.color = ExportUi.LINE
            g.fillRect(0, height - 1, width, 1)
        }

        private fun WrapText.naturalHeight() = heightForWidth(Int.MAX_VALUE)

        private companion object {
            const val PAD_X = 16
            const val PAD_TOP = 8
            const val PAD_BOTTOM = 6
            val GROUP_BG = Color(0x15, 0x15, 0x15)
        }
    }

    /** The number of rows in a group, in a small grey pill. */
    private class CountPill : JComponent() {
        var text: String = "0"
            set(value) {
                if (field == value) return
                field = value
                revalidate()
                repaint()
            }
        private val pillFont = ExportUi.font(11f)

        override fun getPreferredSize() = Dimension(ceil(ExportUi.textWidth(text, pillFont)).toInt() + 14, 16)

        override fun paintComponent(g: Graphics) {
            val g2 = ExportUi.smooth(g)
            try {
                ExportUi.paintBox(g2, 0, 0, width, height, height / 2, PILL_BG, null)
                val metrics = pillFont.getLineMetrics(text, ExportUi.frc)
                g2.font = pillFont
                g2.color = ExportUi.FG_2
                g2.drawString(text, (width - ExportUi.textWidth(text, pillFont)) / 2f, (height - metrics.ascent - metrics.descent) / 2f + metrics.ascent)
            } finally {
                g2.dispose()
            }
        }

        private companion object {
            val PILL_BG = Color(0x26, 0x26, 0x26)
        }
    }

    /** The column captions of the table. */
    private class HeadRow : JPanel(null) {
        private val captions = listOf("Status", "File", "Content", "Video", "Size", "Time", "").mapIndexed { index, caption ->
            WrapText(
                caption.uppercase(Locale.ROOT),
                ExportUi.trackedFont(11f, 0.06),
                ExportUi.FG_3,
                align = if (index == 4 || index == 5) WrapText.Align.RIGHT else WrapText.Align.LEFT,
            )
        }

        init {
            isOpaque = false
            captions.forEach(::add)
        }

        override fun getPreferredSize() = Dimension(ExportTableGrid.MIN_WIDTH, HEIGHT)

        override fun doLayout() {
            val columns = ExportTableGrid.columns(width)
            captions.forEachIndexed { index, caption ->
                val h = caption.heightForWidth(columns.width[index])
                caption.setBounds(columns.x[index], (height - 1 - h) / 2, columns.width[index], h)
            }
        }

        override fun paintComponent(g: Graphics) {
            g.color = HEAD_BG
            g.fillRect(0, 0, width, height)
            g.color = ExportUi.LINE
            g.fillRect(0, height - 1, width, 1)
        }

        companion object {
            const val HEIGHT = 32
            val HEAD_BG = Color(0x1C, 0x1C, 0x1C)
        }
    }

    /** The corner above the vertical scroll bar. It continues the column header. */
    private class HeadCorner : JComponent() {
        override fun paintComponent(g: Graphics) {
            g.color = HeadRow.HEAD_BG
            g.fillRect(0, 0, width, height)
            g.color = ExportUi.LINE
            g.fillRect(0, height - 1, width, 1)
        }
    }

    /** The title "Exports" and the counts of the exports on the right. */
    private class HeaderBar(stats: List<Stat>) : JPanel(null) {
        private val titleText = WrapText("Exports", ExportUi.font(14f, Weight.SEMIBOLD), ExportUi.FG)
        private val statsRow = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            stats.forEachIndexed { index, stat ->
                if (index > 0) add(stat.gap)
                add(stat.text)
            }
        }

        init {
            isOpaque = false
            add(titleText)
            add(statsRow)
        }

        override fun getPreferredSize() = Dimension(400, PAD_Y * 2 + max(titleText.preferredSize.height, statsRow.preferredSize.height) + 1)

        override fun doLayout() {
            val inner = height - PAD_Y * 2 - 1
            val titleSize = titleText.preferredSize
            titleText.setBounds(PAD_X, PAD_Y + (inner - titleSize.height) / 2, titleSize.width, titleSize.height)
            val statsSize = statsRow.preferredSize
            statsRow.setBounds(width - PAD_X - statsSize.width, PAD_Y + (inner - statsSize.height) / 2, statsSize.width, statsSize.height)
            statsRow.validate()
        }

        override fun paintComponent(g: Graphics) {
            g.color = ExportUi.LINE
            g.fillRect(0, height - 1, width, 1)
        }

        private companion object {
            const val PAD_X = 16
            const val PAD_Y = 12
        }
    }

    /** One count in the header, for example "7 completed". A count of 0 hides it, unless it must always show. */
    private class Stat(private val label: String, private val numberColor: Color = ExportUi.FG) {
        val text = WrapText(emptyList())
        val gap: Component = Box.createHorizontalStrut(14)

        fun show(count: Int, always: Boolean = false) {
            text.runs = listOf(
                TextRun(count.toString(), ExportUi.font(12f, Weight.SEMIBOLD), numberColor),
                TextRun(" $label", ExportUi.font(12f), ExportUi.FG_2),
            )
            val visible = always || count > 0
            text.isVisible = visible
            gap.isVisible = visible
            text.parent?.revalidate()
        }
    }

    /** The frame of the table: the card color, a thin border and round corners. The rows do not paint over the corners. */
    private class RoundedCard : JPanel(BorderLayout()) {
        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(1, 1, 1, 1)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = ExportUi.smooth(g)
            try {
                ExportUi.paintBox(g2, 0, 0, width, height, RADIUS, ExportUi.CARD, null)
            } finally {
                g2.dispose()
            }
        }

        override fun paintChildren(g: Graphics) {
            val g2 = g.create() as java.awt.Graphics2D
            try {
                g2.clip(RoundRectangle2D.Double(1.0, 1.0, width - 2.0, height - 2.0, RADIUS * 2.0 - 2, RADIUS * 2.0 - 2))
                super.paintChildren(g2)
            } finally {
                g2.dispose()
            }
        }

        override fun paintBorder(g: Graphics) {
            val g2 = ExportUi.smooth(g)
            try {
                ExportUi.paintBox(g2, 0, 0, width, height, RADIUS, null, ExportUi.LINE)
            } finally {
                g2.dispose()
            }
        }

        private companion object {
            const val RADIUS = 8
        }
    }

    companion object {
        private const val DASH = "—"
        private const val OPEN_FOLDER = "Open folder"
        private const val CANCEL_EXPORT = "Cancel export"
        private val CELL_FONT = ExportUi.font(12f)
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

        /** Opens the folder of [outputPath] in the file manager. */
        fun openFolder(outputPath: String, parent: Component, dialogs: UserDialogService) {
            try {
                val dir = File(outputPath).absoluteFile.parentFile ?: return
                try {
                    java.awt.Desktop.getDesktop().open(dir)
                } catch (_: Throwable) {
                    Runtime.getRuntime().exec(arrayOf("explorer.exe", dir.absolutePath))
                }
            } catch (t: Throwable) {
                dialogs.showError(parent, t.message ?: t.toString(), "Failed to open folder")
            }
        }

        private fun textCell(text: String, color: Color) = WrapText(text, CELL_FONT, color)

        private fun valueCell(value: String, sub: String?, color: Color = ExportUi.FG) = ValueCell().apply { show(value, sub, color) }

        private fun fileCell(info: ExportCardInfo, componentName: String) = FileCell().apply {
            nameText.name = componentName
            show(info)
        }

        private fun actions(vararg buttons: JComponent): JComponent = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
            isOpaque = false
            buttons.forEach(::add)
        }
    }
}

/**
 * The columns of the exports table, like the CSS grid of the design:
 * 84px, minmax(170px, 1.3fr), minmax(160px, 1.2fr), minmax(140px, 1fr), 104px, 86px, 64px.
 * A narrow table makes the flexible columns narrower than their minimum, so that all columns stay visible.
 */
internal object ExportTableGrid {
    const val PAD = 16
    const val GAP = 12
    private val FIXED = mapOf(0 to 84, 4 to 104, 5 to 86, 6 to 64)
    private val FLEX = mapOf(1 to (170 to 1.3), 2 to (160 to 1.2), 3 to (140 to 1.0))
    private const val SMALLEST_FLEX = 48
    const val MIN_WIDTH = 84 + 170 + 160 + 140 + 104 + 86 + 64 + GAP * 6 + PAD * 2

    class Columns(val x: IntArray, val width: IntArray)

    fun columns(total: Int): Columns {
        val widths = IntArray(7)
        FIXED.forEach { (index, w) -> widths[index] = w }
        val free = total - PAD * 2 - GAP * 6 - FIXED.values.sum()
        val minimums = FLEX.values.sumOf { it.first }
        if (free <= minimums) {
            // Not enough space for the minimums: share the space in the ratio of the minimums.
            FLEX.forEach { (index, spec) ->
                widths[index] = max(SMALLEST_FLEX, (free.toDouble() * spec.first / minimums).toInt())
            }
        } else {
            val open = FLEX.keys.toMutableSet()
            var remaining = free.toDouble()
            var clamped = true
            while (clamped) {
                clamped = false
                val share = remaining / open.sumOf { FLEX.getValue(it).second }
                for (index in open.toList()) {
                    val (minimum, fraction) = FLEX.getValue(index)
                    if (share * fraction < minimum) {
                        widths[index] = minimum
                        remaining -= minimum
                        open -= index
                        clamped = true
                    }
                }
            }
            val share = remaining / open.sumOf { FLEX.getValue(it).second }.coerceAtLeast(0.001)
            open.forEach { widths[it] = (share * FLEX.getValue(it).second).toInt() }
        }
        val x = IntArray(7)
        var position = PAD
        for (index in 0 until 7) {
            x[index] = position
            position += widths[index] + GAP
        }
        return Columns(x, widths)
    }
}

/** The background of a data row: normal, a running export, or a failed export. */
internal enum class Tone { NORMAL, LIVE, FAILED }

/**
 * One row of the exports table. The cells are in the grid columns and are centered vertically.
 * The [extra] line, for example the progress bar, is under the cells from the second column to the end.
 */
internal open class DataRow : JPanel(null), HeightForWidth {
    private val cells = arrayOfNulls<JComponent>(7)
    var extra: JComponent? = null
        set(value) {
            if (field === value) return
            field?.let(::remove)
            field = value
            value?.let(::add)
            revalidate()
            repaint()
        }
    var tone = Tone.NORMAL
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }
    var hovered = false
        set(value) {
            if (field == value) return
            field = value
            repaint()
        }

    init {
        isOpaque = false
    }

    fun setCells(status: JComponent, file: JComponent, content: JComponent, video: JComponent, size: JComponent, time: JComponent, actions: JComponent) {
        cells.forEach { it?.let(::remove) }
        listOf(status, file, content, video, size, time, actions).forEachIndexed { index, cell ->
            cells[index] = cell
            add(cell)
        }
        revalidate()
    }

    private fun mainHeight(columns: ExportTableGrid.Columns) =
        cells.withIndex().maxOf { (index, cell) -> cell?.heightAt(columns.width[index]) ?: 0 }

    private fun extraWidth(columns: ExportTableGrid.Columns) = columns.x[6] + columns.width[6] - columns.x[1]

    override fun heightForWidth(width: Int): Int {
        val columns = ExportTableGrid.columns(width)
        val extraHeight = extra?.let { EXTRA_GAP + it.heightAt(extraWidth(columns)) } ?: 0
        return PAD_Y * 2 + mainHeight(columns) + extraHeight + 1
    }

    override fun getPreferredSize() = Dimension(ExportTableGrid.MIN_WIDTH, heightForWidth(if (width > 0) width else ExportTableGrid.MIN_WIDTH))

    override fun doLayout() {
        val columns = ExportTableGrid.columns(width)
        val main = mainHeight(columns)
        cells.forEachIndexed { index, cell ->
            cell ?: return@forEachIndexed
            val w = columns.width[index]
            val h = cell.heightAt(w)
            val cellWidth = if (cell is StatusPill) minOf(w, cell.preferredSize.width) else w
            cell.setBounds(columns.x[index], PAD_Y + (main - h) / 2, cellWidth, h)
        }
        extra?.let {
            val w = extraWidth(columns)
            it.setBounds(columns.x[1], PAD_Y + main + EXTRA_GAP, w, it.heightAt(w))
        }
    }

    override fun paintComponent(g: Graphics) {
        g.color = when (tone) {
            Tone.LIVE -> if (hovered) LIVE_HOVER_BG else LIVE_BG
            Tone.FAILED -> FAILED_BG
            Tone.NORMAL -> if (hovered) HOVER_BG else null
        } ?: ExportUi.CARD
        if (tone != Tone.NORMAL || hovered) g.fillRect(0, 0, width, height)
        g.color = ExportUi.LINE
        g.fillRect(0, height - 1, width, 1)
    }

    private companion object {
        const val PAD_Y = 9
        const val EXTRA_GAP = 8
        val HOVER_BG = Color(0x1E, 0x1E, 0x1E)
        val LIVE_BG = Color(0x1D, 0x21, 0x16)
        val LIVE_HOVER_BG = Color(0x20, 0x25, 0x1A)
        val FAILED_BG = Color(0x22, 0x16, 0x13)
    }
}

/** A row with an icon and a grey text, for example "No active exports". */
internal class EmptyRow(ikon: org.kordamp.ikonli.Ikon, text: String) : JPanel(null) {
    private val icon: Icon = ExportUi.icon(ikon, 18, ExportUi.FG_3)
    private val label = WrapText(text, ExportUi.font(12.5f), ExportUi.FG_3)

    init {
        isOpaque = false
        add(label)
    }

    override fun getPreferredSize() = Dimension(400, PAD_Y * 2 + max(icon.iconHeight, label.preferredSize.height) + 1)

    override fun doLayout() {
        val size = label.preferredSize
        label.setBounds(PAD_X + icon.iconWidth + 8, (height - 1 - size.height) / 2, size.width, size.height)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            icon.paintIcon(this, g2, PAD_X, (height - 1 - icon.iconHeight) / 2)
            g2.color = ExportUi.LINE
            g2.fillRect(0, height - 1, width, 1)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 16
        const val PAD_Y = 12
    }
}

/** A status in a round pill with a dot, for example "Running" or "Queued · 1". */
internal class StatusPill(text: String, kind: Kind) : JComponent() {
    enum class Kind(val foreground: Color, val background: Color) {
        RUNNING(ExportUi.LIME, Color(161, 254, 0, 26)),
        QUEUED(ExportUi.FG_2, Color(0x23, 0x23, 0x23)),
        FAILED(ExportUi.RED, ExportUi.RED_TINT),
        DONE(ExportUi.SAGE, Color(163, 197, 134, 26)),
    }

    private var text = text
    private var kind = kind
    private val pillFont = ExportUi.font(11f, Weight.SEMIBOLD)

    fun set(text: String, kind: Kind) {
        if (this.text == text && this.kind == kind) return
        this.text = text
        this.kind = kind
        revalidate()
        repaint()
    }

    override fun getPreferredSize() = Dimension(PAD + DOT + GAP + ceil(ExportUi.textWidth(text, pillFont)).toInt() + PAD + 1, HEIGHT)

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val w = preferredSize.width.coerceAtMost(width)
            val y = (height - HEIGHT) / 2
            ExportUi.paintBox(g2, 0, y, w, HEIGHT, HEIGHT / 2, kind.background, null)
            g2.color = kind.foreground
            g2.fill(java.awt.geom.Ellipse2D.Double(PAD.toDouble(), y + (HEIGHT - DOT) / 2.0, DOT.toDouble(), DOT.toDouble()))
            val metrics = pillFont.getLineMetrics(text, ExportUi.frc)
            g2.font = pillFont
            g2.drawString(text, (PAD + DOT + GAP).toFloat(), y + (HEIGHT - metrics.ascent - metrics.descent) / 2f + metrics.ascent)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val HEIGHT = 20
        const val PAD = 8
        const val DOT = 6
        const val GAP = 5
    }
}

/** The file name in the accent color and, under it, the folder and the project. Both lines end with "…" when they are too long. */
internal class FileCell : Stack(0) {
    val nameText = WrapText("", ExportUi.font(12.5f, Weight.SEMIBOLD), ExportUi.SAGE, ellipsis = true)
    private val subText = WrapText("", ExportUi.font(11.5f), ExportUi.FG_3, ellipsis = true)

    init {
        add(nameText)
        add(subText)
    }

    fun show(info: ExportCardInfo) {
        val fileName = File(info.outputPath).name
        val folder = info.outputPath.removeSuffix(fileName)
        nameText.setText(fileName, ExportUi.font(12.5f, Weight.SEMIBOLD), ExportUi.SAGE)
        subText.setText(listOfNotNull(folder.ifEmpty { null }, info.projectName).joinToString(" · "), ExportUi.font(11.5f), ExportUi.FG_3)
        if (toolTipText != info.outputPath) toolTipText = info.outputPath
    }
}

/** A right-aligned value and an optional grey line under it, for example the size and "of ~7.55 GB". */
internal class ValueCell : Stack(0) {
    private val valueText = WrapText("", ExportUi.font(12f), ExportUi.FG, align = WrapText.Align.RIGHT)
    private val subText = WrapText("", ExportUi.font(11.5f), ExportUi.FG_3, align = WrapText.Align.RIGHT, ellipsis = true)

    init {
        add(valueText)
        add(subText)
    }

    fun show(value: String, sub: String?, color: Color = ExportUi.FG) {
        valueText.setText(value, ExportUi.font(12f), color)
        subText.isVisible = sub != null
        subText.setText(sub.orEmpty(), ExportUi.font(11.5f), ExportUi.FG_3)
    }
}

/** The thin progress bar of the active export: a lime gradient on a dark track. */
internal class ProgressLine : JProgressBar(0, 100) {
    init {
        isOpaque = false
        isBorderPainted = false
        border = BorderFactory.createEmptyBorder()
    }

    override fun getPreferredSize() = Dimension(100, 6)
    override fun getMinimumSize() = Dimension(10, 6)

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            val y = (height - 6) / 2
            ExportUi.paintBox(g2, 0, y, width, 6, 3, TRACK, null)
            val fill = (width * percentComplete).roundToInt()
            if (fill > 0) {
                g2.paint = GradientPaint(0f, 0f, FILL_START, width.toFloat(), 0f, ExportUi.LIME)
                g2.fill(RoundRectangle2D.Double(0.0, y.toDouble(), fill.toDouble(), 6.0, 6.0, 6.0))
            }
        } finally {
            g2.dispose()
        }
    }

    override fun paintBorder(g: Graphics) = Unit

    private companion object {
        val TRACK = Color(0x2C, 0x2C, 0x2C)
        val FILL_START = Color(0x7F, 0xCC, 0x00)
    }
}

/** The progress bar and the percent after it. */
internal class ProgressRow(private val bar: ProgressLine, private val percent: WrapText) : JPanel(null) {
    init {
        isOpaque = false
        add(bar)
        add(percent)
    }

    override fun getPreferredSize() = Dimension(200, max(6, percent.preferredSize.height))

    override fun doLayout() {
        val percentHeight = percent.preferredSize.height
        percent.setBounds(width - PERCENT_WIDTH, (height - percentHeight) / 2, PERCENT_WIDTH, percentHeight)
        bar.setBounds(0, (height - 6) / 2, (width - PERCENT_WIDTH - 12).coerceAtLeast(0), 6)
    }

    private companion object {
        const val PERCENT_WIDTH = 38
    }
}

/** The error of a failed export in a red box. */
internal class ErrorBox : JPanel(null), HeightForWidth {
    private val icon: Icon = ExportUi.icon(Material2OutlinedAL.ERROR_OUTLINE, 18, ExportUi.RED)
    private val message = WrapText(emptyList())

    init {
        isOpaque = false
        name = "export-active-error"
        add(message)
    }

    fun show(reason: String) {
        message.runs = listOf(
            TextRun("Error:", ExportUi.font(12f, Weight.BOLD), TEXT),
            TextRun(" $reason", ExportUi.font(12f), TEXT),
        )
    }

    private fun messageWidth(width: Int) = (width - PAD_X * 2 - icon.iconWidth - 12).coerceAtLeast(0)

    override fun heightForWidth(width: Int): Int = PAD_Y * 2 + max(icon.iconHeight, message.heightForWidth(messageWidth(width)))

    override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 600))

    override fun doLayout() {
        val w = messageWidth(width)
        val h = message.heightForWidth(w)
        message.setBounds(PAD_X + icon.iconWidth + 12, (height - h) / 2, w, h)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = ExportUi.smooth(g)
        try {
            ExportUi.paintBox(g2, 0, 0, width, height, 4, ExportUi.RED_TINT, BORDER)
            icon.paintIcon(this, g2, PAD_X, (height - icon.iconHeight) / 2)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val PAD_X = 11
        const val PAD_Y = 7
        val TEXT = Color(0xFF, 0xC2, 0xB3)
        val BORDER = Color(255, 115, 81, 89)
    }
}
