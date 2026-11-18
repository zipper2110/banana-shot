package org.litvin.ui.tabs.projects.components

import org.kordamp.ikonli.material2.Material2AL
import org.litvin.i18n.tr
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.RowIconButton
import org.litvin.ui.commons.UiKit
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.io.File
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * The video files of a match of several files in the "New project" dialog (B-49), in play order.
 * Each row has the file name and the buttons that move the file up or down and remove it.
 * When the files are in more than one folder, each row also shows the folder in gray, because two files can have
 * the same name. [onChange] gets the new list after a change by the user.
 */
internal class VideoFilesList(private val onChange: (List<String>) -> Unit) : JPanel(BorderLayout()) {
    private val rows = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(4, 10, 4, 4)
    }
    private val scroll = DialogKit.scrollBody(rows, Palette.INSET).apply { viewport.isOpaque = false }

    /** The files in play order. */
    var paths: List<String> = emptyList()
        set(value) {
            field = value
            rebuild()
        }

    init {
        name = "new-project-files"
        isOpaque = false
        add(scroll, BorderLayout.CENTER)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = UiKit.smooth(g)
        try {
            UiKit.paintBox(g2, 0, 0, width, height, 4, Palette.INSET, Palette.LINE_2)
        } finally {
            g2.dispose()
        }
    }

    /** At most [VISIBLE_ROWS] rows show without a scroll. */
    override fun getPreferredSize(): Dimension {
        val content = rows.preferredSize
        val maxHeight = VISIBLE_ROWS * ROW_HEIGHT + 8
        return Dimension(content.width, minOf(content.height, maxHeight) + 2)
    }

    private fun rebuild() {
        rows.removeAll()
        val showFolders = paths.map { File(it).absoluteFile.parent?.lowercase() }.distinct().size > 1
        paths.forEachIndexed { index, path -> rows.add(row(index, path, showFolders)) }
        rows.revalidate()
        revalidate()
        repaint()
    }

    private fun row(index: Int, path: String, showFolder: Boolean): JComponent = JPanel(BorderLayout(8, 0)).apply {
        name = "new-project-file-$index"
        isOpaque = false
        val size = Dimension(Int.MAX_VALUE, ROW_HEIGHT)
        maximumSize = size
        val nameLabel = JLabel("${index + 1}.  ${File(path).name}").apply {
            name = "new-project-file-name-$index"
            toolTipText = path
            font = UiKit.font(12.5f)
            foreground = Palette.FG
        }
        add(if (showFolder) JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(nameLabel, BorderLayout.WEST)
            add(FolderLabel(File(path).absoluteFile.parent.orEmpty()).apply {
                name = "new-project-file-folder-$index"
                toolTipText = path
            }, BorderLayout.CENTER)
        } else nameLabel, BorderLayout.CENTER)
        add(JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply {
            isOpaque = false
            add(button(Material2AL.ARROW_UPWARD, tr("projects.new.files.up"), "up", index, enabled = index > 0) { moveFile(index, -1) })
            add(button(Material2AL.ARROW_DOWNWARD, tr("projects.new.files.down"), "down", index, enabled = index < paths.lastIndex) {
                moveFile(index, 1)
            })
            add(button(Material2AL.CLOSE, tr("projects.new.files.remove"), "remove", index, danger = true) { removeFile(index) })
        }, BorderLayout.EAST)
    }

    private fun button(
        ikon: Material2AL,
        tooltip: String,
        action: String,
        index: Int,
        enabled: Boolean = true,
        danger: Boolean = false,
        onClick: () -> Unit,
    ) = RowIconButton(ikon, tooltip, danger = danger, color = if (enabled) Palette.FG_2 else Palette.LINE_2, side = 24).apply {
        name = "new-project-file-$action-$index"
        isEnabled = enabled
        addActionListener { if (isEnabled) onClick() }
    }

    private fun moveFile(index: Int, step: Int) {
        val target = index + step
        if (target !in paths.indices) return
        val changed = paths.toMutableList().apply { add(target, removeAt(index)) }
        paths = changed
        onChange(changed)
    }

    private fun removeFile(index: Int) {
        val changed = paths.toMutableList().apply { removeAt(index) }
        paths = changed
        onChange(changed)
    }

    /** The folder of a file in gray. A long folder loses its start, so the end of the folder stays visible. */
    private class FolderLabel(private val folder: String) : JLabel(folder) {
        private val folderFont = ProjectsUi.font(11.5f)

        /** The label takes the free width of the row. A long folder does not make the dialog wider. */
        override fun getPreferredSize() = Dimension(0, super.getPreferredSize().height)
        override fun getMinimumSize() = Dimension(0, 0)

        override fun paintComponent(g: Graphics) {
            val g2 = ProjectsUi.smooth(g)
            try {
                val shown = ProjectsUi.ellipsizeStart(folder, folderFont, width - 1f)
                ProjectsUi.drawText(g2, shown, folderFont, Palette.FG_3, 0f, 0f, height.toFloat())
            } finally {
                g2.dispose()
            }
        }
    }

    private companion object {
        const val ROW_HEIGHT = 28
        const val VISIBLE_ROWS = 6
    }
}
