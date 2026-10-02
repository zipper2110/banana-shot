package org.litvin.ui.commons

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.media.VideoErrorView
import org.litvin.media.VideoErrorViewFactory
import org.litvin.media.VideoProblem
import java.awt.Component
import java.awt.FlowLayout
import java.awt.GridBagLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Shows in the video area when the video does not open: an icon, the problem, an explanation
 * of the cause and of what to do, and a Retry button. It replaces a black area without an explanation.
 */
internal class VideoErrorPanel(onRetry: () -> Unit) : JPanel(GridBagLayout()), VideoErrorView {
    companion object : VideoErrorViewFactory {
        private const val TEXT_WIDTH = 440
        private val explanationFont get() = UiKit.font(13f)

        override fun create(onRetry: () -> Unit): VideoErrorView = VideoErrorPanel(onRetry)
    }

    private val title = JLabel(" ").apply {
        name = "video-error-title"
        font = UiKit.font(16f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
    }
    private val explanation = WrapText(" ", explanationFont, Palette.FG_2, lineFactor = 1.5f, wrapWidth = TEXT_WIDTH).apply {
        name = "video-error-explanation"
    }

    override val component: Component get() = this

    init {
        name = "video-error"
        isOpaque = true
        background = Palette.VIDEO_BG
        val retry = UiButton("Retry", Material2MZ.REFRESH).apply {
            name = "video-error-retry"
            toolTipText = "Open the video again"
            addActionListener { onRetry() }
        }
        // The stack gives each row the full text width, so a long line wraps and is never cut.
        val column = Stack(fixedWidth = TEXT_WIDTH).apply {
            add(JLabel(UiKit.icon(Material2AL.ERROR_OUTLINE, 32, Palette.RED), JLabel.LEFT), gapAfter = 12)
            add(title, gapAfter = 8)
            add(explanation, gapAfter = 18)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(retry)
            })
        }
        add(column)
    }

    override fun showProblem(problem: VideoProblem) {
        title.text = problem.title
        explanation.runs = listOf(TextRun(problem.explanation, explanationFont, Palette.FG_2))
        revalidate()
        repaint()
    }
}
