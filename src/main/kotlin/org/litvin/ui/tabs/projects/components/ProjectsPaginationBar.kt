package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2AL
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/** The pager next to the "Recent projects" title: the previous page button, "Page 1 / 3", and the next page button. */
class ProjectsPaginationBar(
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
) : JPanel() {
    private val prevButton = ProjectsIconButton(Material2AL.CHEVRON_LEFT, "Previous page", bordered = true, iconSize = 18).apply {
        name = "projects-page-previous"
    }
    private val nextButton = ProjectsIconButton(Material2AL.CHEVRON_RIGHT, "Next page", bordered = true, iconSize = 18).apply {
        name = "projects-page-next"
    }
    private val pageLabel = JLabel("Page 1 / 1", SwingConstants.CENTER).apply {
        name = "projects-page-label"
        font = ProjectsUi.font(12f)
        foreground = ProjectsUi.FG_2
    }

    init {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        prevButton.addActionListener { onPrevious() }
        nextButton.addActionListener { onNext() }
        add(prevButton)
        add(Box.createHorizontalStrut(6))
        add(pageLabel)
        add(Box.createHorizontalStrut(6))
        add(nextButton)
        isVisible = false
    }

    override fun getMaximumSize(): Dimension = preferredSize

    fun render(currentPage: Int, totalPages: Int, canGoPrevious: Boolean, canGoNext: Boolean, isVisible: Boolean) {
        pageLabel.text = "Page $currentPage / $totalPages"
        // The label keeps a minimum width, so the buttons do not move when the page number changes.
        val labelWidth = maxOf(LABEL_MIN_WIDTH, pageLabel.getFontMetrics(pageLabel.font).stringWidth(pageLabel.text) + 8)
        pageLabel.preferredSize = Dimension(labelWidth, 26)
        pageLabel.maximumSize = pageLabel.preferredSize
        prevButton.isEnabled = canGoPrevious
        nextButton.isEnabled = canGoNext
        this.isVisible = isVisible
        revalidate()
        repaint()
    }

    private companion object {
        const val LABEL_MIN_WIDTH = 78
    }
}
