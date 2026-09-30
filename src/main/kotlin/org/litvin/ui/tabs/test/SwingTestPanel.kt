package org.litvin.ui.tabs.test

import org.litvin.AssOverlayWriter
import org.litvin.OverlaySpan
import org.litvin.ScoreboardComponent
import org.litvin.media.PlayerStatus
import org.litvin.media.mpv.MpvSwingMediaPlayerAdapter
import org.litvin.ui.UiStyles
import org.litvin.ui.commons.ColorPickerDialog
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.MonoFont
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ScrubBar
import org.litvin.ui.commons.Theme
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileNameExtensionFilter

class SwingTestPanel : JPanel(BorderLayout()) {
    private val player = MpvSwingMediaPlayerAdapter()
    private var pendingVideoFile: File? = null
    private var projectManifestPath: String? = null
    private var testP1Pts: Int = 0
    private var testP2Pts: Int = 0
    private var testP1Games: Int = 0
    private var testP2Games: Int = 0
    private var subtitleGeneration: Int = 0

    private val playPauseButton: JButton = UiStyles.squarePrimaryButton(UiStyles.playIcon(26), size = 52) {
        togglePlayPause()
    }.apply {
        toolTipText = "Play"
    }

    private val fileLabel = JLabel("No video loaded").apply {
        foreground = Palette.FG_2
    }

    private val projectLabel = JLabel("No project loaded").apply {
        foreground = Palette.FG_2
    }
    private val scoreLabel = JLabel("Score: 0 - 0").apply {
        foreground = Palette.FG
    }
    private val rotationValueLabel = JLabel("0.0 deg").apply {
        foreground = Palette.FG
    }
    private val rotationSlider = JSlider(-360, 360, 0).apply {
        toolTipText = "Rotation degrees"
        isOpaque = false
        putClientProperty("JSlider.isFilled", true)
    }

    private val scrubBar = ScrubBar(
        onUserScrub = { target ->
            player.seek(target)
            refreshScrub()
        },
        tooltip = "Seek test video",
        sliderComponentName = "test-seek",
    )

    // ---- Experimental theme: the accent and the background seeds of the Palette ----

    private var syncingTheme = false
    private val accentField = themeHexField("test-theme-accent-hex") { applyTheme(accent = it) }
    private val backgroundField = themeHexField("test-theme-background-hex") { applyTheme(background = it) }

    private val idleUiTimer = Timer(100) {
        if (player.status() != PlayerStatus.PLAYING) {
            refreshScrub()
        }
    }.apply { isRepeats = true }

    init {
        isOpaque = true
        background = Palette.BG
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        val videoStack = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Palette.VIDEO_BG
            minimumSize = Dimension(640, 360)
            add(player.component, BorderLayout.CENTER)
            add(scrubBar, BorderLayout.SOUTH)
        }

        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = true
            background = Palette.BG
            border = BorderFactory.createEmptyBorder(8, 16, 8, 8)
        }

        val title = JLabel("Test").apply {
            foreground = Palette.FG
            font = font.deriveFont(font.style, font.size2D + 6.0f)
            alignmentX = LEFT_ALIGNMENT
        }
        val openButton = UiStyles.primarySmallButton("Open Video") { openVideo() }.apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }
        val applyRotationButton = UiStyles.primarySmallButton("Apply Rotation") {
            applyRotation()
        }.apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }
        val pointP1Button = UiStyles.primarySmallButton("Point Player 1") {
            giveTestPoint(playerOne = true)
        }.apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }
        val pointP2Button = UiStyles.primarySmallButton("Point Player 2") {
            giveTestPoint(playerOne = false)
        }.apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }
        val resetScoreButton = UiStyles.primarySmallButton("Reset Test Score") {
            resetTestScore()
        }.apply {
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        }

        playPauseButton.alignmentX = LEFT_ALIGNMENT
        fileLabel.alignmentX = LEFT_ALIGNMENT
        projectLabel.alignmentX = LEFT_ALIGNMENT
        scoreLabel.alignmentX = LEFT_ALIGNMENT
        rotationValueLabel.alignmentX = LEFT_ALIGNMENT
        rotationSlider.alignmentX = LEFT_ALIGNMENT
        rotationSlider.maximumSize = Dimension(Int.MAX_VALUE, 44)
        rotationSlider.addChangeListener {
            rotationValueLabel.text = String.format(java.util.Locale.US, "%.1f deg", currentRotationDeg())
        }

        rightPanel.add(title)
        rightPanel.add(Box.createRigidArea(Dimension(0, 18)))
        rightPanel.add(JLabel("Theme (experimental)").apply {
            foreground = Palette.FG_2
            alignmentX = LEFT_ALIGNMENT
        })
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(themeRow("Accent", "test-theme-accent", accentField, { Palette.accent }) { applyTheme(accent = it) })
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(themeRow("Background", "test-theme-background", backgroundField, { Palette.background }) {
            applyTheme(background = it)
        })
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(UiStyles.primarySmallButton("Reset Theme") {
            applyTheme(Palette.DEFAULT_ACCENT, Palette.DEFAULT_BACKGROUND)
        }.apply {
            name = "test-theme-reset"
            alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
        })
        rightPanel.add(Box.createRigidArea(Dimension(0, 20)))
        rightPanel.add(openButton)
        rightPanel.add(Box.createRigidArea(Dimension(0, 16)))
        rightPanel.add(playPauseButton)
        rightPanel.add(Box.createRigidArea(Dimension(0, 20)))
        rightPanel.add(JLabel("Rotation").apply {
            foreground = Palette.FG_2
            alignmentX = LEFT_ALIGNMENT
        })
        rightPanel.add(rotationSlider)
        rightPanel.add(rotationValueLabel)
        rightPanel.add(Box.createRigidArea(Dimension(0, 10)))
        rightPanel.add(applyRotationButton)
        rightPanel.add(Box.createRigidArea(Dimension(0, 20)))
        rightPanel.add(JLabel("Subtitle Scoreboard").apply {
            foreground = Palette.FG_2
            alignmentX = LEFT_ALIGNMENT
        })
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(pointP1Button)
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(pointP2Button)
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(resetScoreButton)
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(scoreLabel)
        rightPanel.add(Box.createRigidArea(Dimension(0, 20)))
        rightPanel.add(fileLabel)
        rightPanel.add(Box.createRigidArea(Dimension(0, 8)))
        rightPanel.add(projectLabel)

        // The right panel scrolls when the window is not high enough for all controls.
        val rightScroll = JScrollPane(rightPanel).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            viewport.background = Palette.BG
            preferredSize = Dimension(316, 0)
            verticalScrollBar.unitIncrement = 16
            applyDarkScrollbar(this, Palette.BG)
        }

        add(videoStack, BorderLayout.CENTER)
        add(rightScroll, BorderLayout.EAST)
        syncThemeFields()

        installPlayerCallbacks()
        idleUiTimer.start()
    }

    fun setProjectManifest(path: String) {
        projectManifestPath = path
        projectLabel.text = "Project: ${File(path).parentFile?.name ?: path}"
    }

    fun onActivated() {
        player.activatePreview("test tab activated")
        pendingVideoFile?.let {
            player.pause()
            refreshScrub()
        }
        EventQueue.invokeLater { player.component.requestFocusInWindow() }
    }

    fun onDeactivated() {
        player.pause()
        player.deactivatePreview("test tab deactivated")
    }

    private fun openVideo() {
        val chooser = JFileChooser().apply {
            dialogTitle = "Open video"
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter(
                "Video files",
                "mp4", "mov", "mkv", "avi", "m4v", "webm", "mts", "m2ts"
            )
        }
        val result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this))
        if (result != JFileChooser.APPROVE_OPTION) return

        val file = chooser.selectedFile ?: return
        pendingVideoFile = file
        fileLabel.text = file.name
        scrubBar.reset()
        resetTestScoreState()
        player.load(file)
        player.pause()
        refreshTestSubtitleOverlay()
        updatePlayPauseUi()
    }

    private fun applyTheme(accent: Color = Palette.accent, background: Color = Palette.background) {
        Theme.apply(accent, background)
        syncThemeFields()
    }

    /** Writes the seeds into the hex fields. A field that has the focus keeps the text of the user. */
    private fun syncThemeFields() {
        syncingTheme = true
        try {
            listOf(accentField to Palette.accent, backgroundField to Palette.background).forEach { (field, color) ->
                val text = Palette.hex(color)
                if (!field.hasFocus() && field.text != text) field.text = text
            }
        } finally {
            syncingTheme = false
        }
        repaint()
    }

    /** A hex input that applies each valid color at once. */
    private fun themeHexField(componentName: String, onColor: (Color) -> Unit) = JTextField().apply {
        name = componentName
        font = MonoFont.of(13f)
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = changed()
            override fun removeUpdate(e: DocumentEvent) = changed()
            override fun changedUpdate(e: DocumentEvent) = changed()
            private fun changed() {
                if (syncingTheme) return
                ColorPickerDialog.parseHex(text)?.let(onColor)
            }
        })
    }

    /** A caption, a swatch that opens the color picker, and a hex input. */
    private fun themeRow(
        caption: String,
        componentName: String,
        field: JTextField,
        current: () -> Color,
        onColor: (Color) -> Unit,
    ): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 28)
        add(JLabel(caption).apply {
            foreground = Palette.FG
            font = UiKit.font(13f)
            preferredSize = Dimension(84, 28)
            minimumSize = preferredSize
            maximumSize = preferredSize
        })
        add(ThemeSwatch(current).apply {
            name = "$componentName-swatch"
            toolTipText = "Choose the ${caption.lowercase()} color"
            addActionListener {
                ColorPickerDialog.pick(this, "$caption color", current(), onLiveChange = onColor)?.let(onColor)
            }
        })
        add(Box.createRigidArea(Dimension(8, 0)))
        add(DialogKit.inputBox(field, 28))
    }

    /** A square button that shows the current value of a theme seed. */
    private class ThemeSwatch(private val current: () -> Color) : JButton() {
        init {
            isContentAreaFilled = false
            isBorderPainted = false
            isFocusPainted = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            preferredSize = Dimension(28, 28)
            minimumSize = preferredSize
            maximumSize = preferredSize
        }

        override fun paintComponent(g: Graphics) {
            val g2 = UiKit.smooth(g)
            try {
                UiKit.paintBox(g2, 0, 0, width, height, 4, current(), if (model.isRollover) Palette.HOVER_LINE else Palette.LINE_2)
            } finally {
                g2.dispose()
            }
        }
    }

    private fun applyRotation() {
        player.applyPreviewRotation(currentRotationDeg(), "test tab apply rotation")
        updatePlayPauseUi()
    }

    private fun currentRotationDeg(): Float = rotationSlider.value / 2.0f

    private fun togglePlayPause() {
        val wasPlaying = player.status() == PlayerStatus.PLAYING
        if (wasPlaying) {
            player.pause()
        } else {
            player.play()
        }
        updatePlayPauseUi()
    }

    private fun installPlayerCallbacks() {
        player.onReady = {
            EventQueue.invokeLater {
                refreshScrub()
                refreshTestSubtitleOverlay()
                updatePlayPauseUi()
            }
        }
        player.onTimeChanged = {
            EventQueue.invokeLater {
                refreshScrub()
                updatePlayPauseUi()
            }
        }
        player.onStatusChanged = {
            EventQueue.invokeLater { updatePlayPauseUi() }
        }
    }

    private fun refreshScrub() {
        val duration = player.totalDurationMs()
        if (duration > 0) {
            scrubBar.setRange(0L, duration)
            scrubBar.setPosition(player.currentTimeMs())
        } else {
            scrubBar.reset()
        }
    }

    private fun updatePlayPauseUi() {
        val playing = player.status() == PlayerStatus.PLAYING
        playPauseButton.icon = if (playing) UiStyles.pauseIcon(26) else UiStyles.playIcon(26)
        playPauseButton.toolTipText = if (playing) "Pause" else "Play"
    }

    private fun giveTestPoint(playerOne: Boolean) {
        if (playerOne) {
            val result = advancePoint(testP1Pts, testP2Pts, testP1Games)
            testP1Pts = result.minePts
            testP2Pts = result.otherPts
            testP1Games = result.mineGames
        } else {
            val result = advancePoint(testP2Pts, testP1Pts, testP2Games)
            testP2Pts = result.minePts
            testP1Pts = result.otherPts
            testP2Games = result.mineGames
        }
        refreshTestSubtitleOverlay()
    }

    private data class PointAdvanceResult(
        val minePts: Int,
        val otherPts: Int,
        val mineGames: Int,
    )

    private fun advancePoint(minePts: Int, otherPts: Int, mineGames: Int): PointAdvanceResult {
        if (minePts >= 3 && otherPts >= 3) {
            return when {
                minePts == otherPts -> PointAdvanceResult(4, otherPts, mineGames)
                minePts > otherPts -> PointAdvanceResult(0, 0, mineGames + 1)
                else -> PointAdvanceResult(3, 3, mineGames)
            }
        }
        val next = minePts + 1
        return if (next >= 4) {
            PointAdvanceResult(0, 0, mineGames + 1)
        } else {
            PointAdvanceResult(next, otherPts, mineGames)
        }
    }

    private fun resetTestScore() {
        resetTestScoreState()
        refreshTestSubtitleOverlay()
    }

    private fun resetTestScoreState() {
        testP1Pts = 0
        testP2Pts = 0
        testP1Games = 0
        testP2Games = 0
        updateScoreLabel()
    }

    private fun refreshTestSubtitleOverlay() {
        val file = pendingVideoFile ?: return
        if (!file.exists()) return

        val duration = player.totalDurationMs().takeIf { it > 0L } ?: (6L * 60L * 60L * 1000L)
        val assFile = File(
            System.getProperty("java.io.tmpdir"),
            "tennis-record-test-scoreboard-${System.nanoTime()}-${subtitleGeneration++}.ass"
        )
        AssOverlayWriter.write(
            assFile,
            listOf(
                OverlaySpan(
                    startMs = 0L,
                    endMs = duration,
                    text = "",
                    p1Name = "Player 1",
                    p2Name = "Player 2",
                    p1ColorHex = ScoreboardComponent.DEFAULT_PLAYER1_HEX,
                    p2ColorHex = ScoreboardComponent.DEFAULT_PLAYER2_HEX,
                    p1Pts = testP1Pts,
                    p2Pts = testP2Pts,
                    gamesP1 = testP1Games,
                    gamesP2 = testP2Games,
                    setsP1 = 0,
                    setsP2 = 0,
                )
            ),
            outWidth = 1920,
            outHeight = 1080,
        )
        assFile.deleteOnExit()
        player.setSubtitleFile(assFile)
        updateScoreLabel()
    }

    private fun updateScoreLabel() {
        scoreLabel.text = "Score: ${pointsLabel(testP1Pts, testP2Pts)} - ${pointsLabel(testP2Pts, testP1Pts)}   Games: $testP1Games - $testP2Games"
    }

    private fun pointsLabel(mine: Int, other: Int): String {
        val base = arrayOf("0", "15", "30", "40")
        if (mine < 4 && other < 4) return base[mine.coerceIn(0, 3)]
        if (mine == other) return "40"
        return if (mine > other) "Ad" else "40"
    }
}
