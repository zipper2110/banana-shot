package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2OutlinedMZ
import org.litvin.ui.UiStyles
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ToolTipManager
import kotlin.math.ceil
import org.litvin.ui.commons.Palette

/**
 * The "New project" block of the start panel: the workflow of the app in short steps, and the "Import new match" button.
 * The steps come from the Overview help page. Each step shows the tab where the user does it.
 */
internal class NewProjectBlock(contentWidth: Int, onImportNewMatch: () -> Unit) : JPanel() {
    val importButton: JButton = ProjectsButton("Import new match", Material2AL.ADD, ProjectsButton.Kind.LIME, 38, 13f).apply {
        name = "projects-import-match"
        toolTipText = "Select a match video and create a new project"
        stretch = true
        alignmentX = Component.LEFT_ALIGNMENT
        addActionListener { onImportNewMatch() }
    }

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        alignmentX = Component.LEFT_ALIGNMENT
        border = BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING)
        val innerWidth = contentWidth - 2 * PADDING
        add(WrapLabel("From a match video to a finished video:", ProjectsUi.font(12f), Palette.FG_2, innerWidth, 1.4f))
        add(Gap(0, 10).leftAligned())
        add(WorkflowSteps(innerWidth))
        add(Gap(0, 14).leftAligned())
        add(importButton)
    }

    override fun getMaximumSize(): Dimension = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            ProjectsUi.paintBox(g2, 0, 0, width, height, 8, Palette.INSET, Palette.LINE)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        // The 14 px padding of the design and the 1 px border.
        const val PADDING = 15
    }
}

/** One step of the workflow: the text and the tabs where the user does the step. */
private data class WorkflowStep(val text: String, val chips: List<Pair<String, Icon>>)

/** A tab name with the icon of its sidebar button, the `.chip` element of the design. */
private object Chips {
    const val HEIGHT = 20
    private const val ICON = 13
    private val chipFont = ProjectsUi.font(10.5f)

    fun width(label: String): Int = 6 + ICON + 3 + ceil(ProjectsUi.textWidth(label, chipFont)).toInt() + 7

    fun paint(g2: Graphics2D, c: Component, label: String, icon: Icon, x: Int, y: Int, highlighted: Boolean) {
        val w = width(label)
        ProjectsUi.paintBox(
            g2, x, y, w, HEIGHT, 4, Palette.OVERLAY,
            if (highlighted) Palette.LIME_LINE else Palette.TRACK,
        )
        icon.paintIcon(c, g2, x + 6, y + (HEIGHT - icon.iconHeight) / 2)
        ProjectsUi.drawText(
            g2, label, chipFont, if (highlighted) Palette.FG else Palette.FG_2,
            (x + 6 + ICON + 3).toFloat(), y.toFloat(), HEIGHT.toFloat(),
        )
    }
}

/**
 * The numbered steps with a line that joins the numbers. The first step is the step of this tab, so it is lime.
 * The last step is optional: it has a dashed circle and its tabs on a second line.
 */
private class WorkflowSteps(private val contentWidth: Int) : JComponent() {
    private val steps = listOf(
        WorkflowStep("Import a match video", listOf("Projects" to UiStyles.folderIcon(13))),
        WorkflowStep("Mark the points", listOf("Points" to UiStyles.pointsIcon(13))),
        WorkflowStep("Score each point", listOf("Scoring" to UiStyles.targetIcon(13))),
        WorkflowStep("Review the statistics", listOf("Stats" to UiStyles.statsIcon(13))),
        WorkflowStep("Export the video", listOf("Export" to UiStyles.exportIcon(13))),
    )
    private val optional = WorkflowStep(
        "Tune the image at any time",
        listOf("Colors" to UiStyles.colorsIcon(13), "Transform" to UiStyles.cropRotateIcon(13)),
    )
    private val stepFont = ProjectsUi.font(12.5f)
    private val numberFont = ProjectsUi.font(11f, ProjectsUi.Weight.BOLD)
    private val scheduleIcon = ProjectsUi.icon(Material2OutlinedMZ.SCHEDULE, 13, Palette.FG_3)

    private val optionalTop get() = steps.size * ROW + 4

    init {
        alignmentX = Component.LEFT_ALIGNMENT
        name = "projects-workflow"
        ToolTipManager.sharedInstance().registerComponent(this)
    }

    override fun getPreferredSize() = Dimension(contentWidth, optionalTop + ROW + 5 + Chips.HEIGHT)
    override fun getMinimumSize() = preferredSize
    override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)

    override fun getToolTipText(event: MouseEvent): String? =
        if (event.y >= optionalTop) "Optionally tune the color and frame the video with Transform. You can do this at any time." else null

    override fun paintComponent(g: Graphics) {
        val g2 = ProjectsUi.smooth(g)
        try {
            val textX = CIRCLE + 10f
            // The line that joins the step numbers. The circles paint over it.
            g2.color = Palette.TRACK
            g2.fillRect(CIRCLE / 2 - 1, ROW / 2, 1, optionalTop)
            steps.forEachIndexed { index, step ->
                val top = index * ROW
                val here = index == 0
                val circleY = top + (ROW - CIRCLE) / 2
                g2.color = if (here) Palette.LIME else Palette.LINE
                g2.fillOval(0, circleY, CIRCLE, CIRCLE)
                val number = (index + 1).toString()
                val numberX = (CIRCLE - ProjectsUi.textWidth(number, numberFont)) / 2f
                ProjectsUi.drawText(
                    g2, number, numberFont, if (here) Palette.ON_LIME else Palette.FG,
                    numberX, circleY.toFloat(), CIRCLE.toFloat(),
                )
                val chipsWidth = chipsWidth(step)
                val chipY = top + (ROW - Chips.HEIGHT) / 2
                paintChips(g2, step, width - chipsWidth, chipY, here)
                val text = ProjectsUi.ellipsize(step.text, stepFont, width - textX - chipsWidth - 10)
                ProjectsUi.drawText(g2, text, stepFont, Palette.FG, textX, top.toFloat(), ROW.toFloat())
            }
            // The optional step: a dashed circle with a clock, the text, and its tabs on a second line.
            val top = optionalTop
            val circleY = top + (ROW - CIRCLE) / 2
            g2.color = Palette.INSET
            g2.fillOval(0, circleY, CIRCLE, CIRCLE)
            g2.color = Palette.HOVER_LINE
            g2.stroke = BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(2.5f, 2f), 0f)
            g2.drawOval(0, circleY, CIRCLE - 1, CIRCLE - 1)
            scheduleIcon.paintIcon(this, g2, (CIRCLE - scheduleIcon.iconWidth) / 2, circleY + (CIRCLE - scheduleIcon.iconHeight) / 2)
            ProjectsUi.drawText(g2, optional.text, stepFont, Palette.FG_2, textX, top.toFloat(), ROW.toFloat())
            val note = " (optional)"
            val noteX = textX + ProjectsUi.textWidth(optional.text, stepFont)
            ProjectsUi.drawText(g2, note, stepFont, Palette.FG_3, noteX, top.toFloat(), ROW.toFloat())
            paintChips(g2, optional, textX.toInt(), top + ROW + 5, false)
        } finally {
            g2.dispose()
        }
    }

    private fun chipsWidth(step: WorkflowStep): Int =
        step.chips.sumOf { Chips.width(it.first) } + (step.chips.size - 1) * CHIP_GAP

    private fun paintChips(g2: Graphics2D, step: WorkflowStep, startX: Int, y: Int, highlighted: Boolean) {
        var x = startX
        step.chips.forEach { (label, icon) ->
            Chips.paint(g2, this, label, icon, x, y, highlighted)
            x += Chips.width(label) + CHIP_GAP
        }
    }

    private companion object {
        const val ROW = 30
        const val CIRCLE = 22
        const val CHIP_GAP = 4
    }
}
