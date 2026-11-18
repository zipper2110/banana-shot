package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.i18n.tr
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Html
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Window
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Shows all video files of a project (B-49). Tests replace the dialog with a fake. */
fun interface ProjectVideosViewer {
    fun show(parent: Component, projectName: String, videoPaths: List<String>)
}

/** The text "4 videos" for a project of several video files. */
internal fun videoCountText(count: Int): String = tr("projects.videos.count", "count" to count)

/** A tooltip with each path on its own line. */
internal fun videoPathsTooltip(paths: List<String>): String =
    paths.joinToString("<br>", "<html>", "</html>") { Html.escapeHtml(it) }

/**
 * The modal dialog with all video files of a project in play order (B-49). It shows the full paths.
 * A file that is not on the disk shows in red.
 */
class ProjectVideosDialog private constructor(
    owner: Window?,
    projectName: String,
    videoPaths: List<String>,
) : JDialog(owner, tr("projects.videos.title"), Dialog.ModalityType.APPLICATION_MODAL) {

    companion object : ProjectVideosViewer {
        override fun show(parent: Component, projectName: String, videoPaths: List<String>) {
            val dialog = ProjectVideosDialog(SwingUtilities.getWindowAncestor(parent), projectName, videoPaths)
            dialog.setLocationRelativeTo(parent)
            DialogKit.keepOnScreen(dialog)
            dialog.isVisible = true
        }
    }

    init {
        name = "project-videos-dialog"
        defaultCloseOperation = DISPOSE_ON_CLOSE
        val closeButton = UiButton(tr("common.close"), kind = UiButton.Kind.LIME).apply {
            name = "project-videos-close"
            addActionListener { dispose() }
        }
        contentPane = DialogKit.content(
            DialogKit.WIDE,
            DialogKit.head(projectName, tr("projects.videos.order", "count" to videoPaths.size), DialogKit.WIDE),
            DialogKit.form(ProjectVideosList(videoPaths, visibleRows = 10)),
            DialogKit.footer(right = listOf(closeButton)),
        )
        rootPane.defaultButton = closeButton
        DialogKit.onEscape(this) { dispose() }
        pack()
        minimumSize = size
    }
}

/**
 * The read-only list of the video files of a project, in play order (B-49). Each row has the number and the full path.
 * A long path loses the start of the folder, so the file name stays visible. The tooltip shows the full path.
 * A file that is not on the disk starts with "Video not found" in red. [isMissing] tells if a file is not on the disk.
 */
internal class ProjectVideosList(
    private val paths: List<String>,
    private val visibleRows: Int = 6,
    isMissing: (String) -> Boolean = { !File(it).isFile },
) : JPanel(BorderLayout()) {
    private val rows = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(4, 10, 4, 10)
    }

    init {
        name = "project-videos-list"
        isOpaque = false
        paths.forEachIndexed { index, path ->
            rows.add(PathRow(index, path, isMissing(path), numbered = paths.size > 1).apply { name = "project-videos-file-$index" })
        }
        add(DialogKit.scrollBody(rows, Palette.INSET).apply { viewport.isOpaque = false }, BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.INSET, Palette.LINE_2)
        } finally {
            g2.dispose()
        }
    }

    /** At most [visibleRows] rows show without a scroll. The width follows the dialog. */
    override fun getPreferredSize(): Dimension {
        val content = rows.preferredSize.height
        return Dimension(0, minOf(content, visibleRows * ROW_HEIGHT + 8) + 2)
    }

    /** One painted row: the number and the path. */
    private class PathRow(index: Int, private val path: String, private val missing: Boolean, numbered: Boolean) : JComponent() {
        private val number = if (numbered) "${index + 1}." else ""
        private val pathFont = ProjectsUi.font(12.5f)
        private val missFont = ProjectsUi.font(12.5f, ProjectsUi.Weight.SEMIBOLD)

        init {
            toolTipText = if (missing) "<html>${Html.escapeHtml(tr("projects.row.videoMissing"))}<br>${Html.escapeHtml(path)}</html>" else path
        }

        override fun getPreferredSize() = Dimension(0, ROW_HEIGHT)
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, ROW_HEIGHT)

        override fun paintComponent(g: Graphics) {
            val g2 = ProjectsUi.smooth(g)
            try {
                val h = height.toFloat()
                var x = 0f
                if (number.isNotEmpty()) {
                    ProjectsUi.drawText(g2, number, pathFont, Palette.FG_3, x, 0f, h)
                    x += NUMBER_WIDTH
                }
                if (missing) {
                    val text = tr("projects.row.videoMissing")
                    ProjectsUi.drawText(g2, text, missFont, Palette.RED, x, 0f, h)
                    x += ProjectsUi.textWidth(text, missFont) + 6f
                }
                val color: Color = if (missing) Palette.FG_3 else Palette.FG
                ProjectsUi.drawText(g2, ProjectsUi.ellipsizeStart(path, pathFont, width - x - 1f), pathFont, color, x, 0f, h)
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val ROW_HEIGHT = 26
        const val NUMBER_WIDTH = 26f
    }
}

/**
 * "4 videos" and a small list button, for a project of several video files (B-49). The button opens the list of
 * the files. The tooltip of the text shows all paths. [missingMessage] adds "Video not found" in red before the text.
 * [iconColor] gives the color of the button at rest, for example a brighter color while the pointer is over the row.
 */
internal class VideoCountCell(
    paths: List<String>,
    missingMessage: String?,
    font: java.awt.Font,
    buttonName: String,
    iconColor: () -> Color = { Palette.FG_3 },
    onShow: () -> Unit,
) : JPanel(GridBagLayout()) {
    val countLabel = JLabel(videoCountText(paths.size)).apply {
        this.font = font
        foreground = Palette.FG_2
        toolTipText = videoPathsTooltip(paths)
    }
    val showButton = ProjectsIconButton(Material2AL.FORMAT_LIST_BULLETED, tr("projects.videos.show"), side = 22, iconSize = 15, restColor = iconColor)
        .apply {
            name = buttonName
            addActionListener { onShow() }
        }

    init {
        isOpaque = false
        val c = GridBagConstraints().apply { anchor = GridBagConstraints.WEST }
        if (missingMessage != null) {
            add(JLabel(tr("projects.row.videoMissing")).apply {
                this.font = ProjectsUi.font(font.size2D, ProjectsUi.Weight.SEMIBOLD)
                foreground = Palette.RED
                toolTipText = missingMessage
            }, c.apply { insets = Insets(0, 0, 0, 6) })
            countLabel.foreground = Palette.FG_3
        }
        add(countLabel, c.apply { insets = Insets(0, 0, 0, 4) })
        add(showButton, c.apply { insets = Insets(0, 0, 0, 0) })
        add(JPanel().apply { isOpaque = false }, c.apply { weightx = 1.0 })
    }
}
