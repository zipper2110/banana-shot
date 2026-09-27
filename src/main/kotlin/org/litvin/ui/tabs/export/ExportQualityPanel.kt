package org.litvin.ui.tabs.export

import org.litvin.export.EncoderCapabilities
import org.litvin.export.ExportEncoder
import org.litvin.export.ExportFrameRateChoice
import org.litvin.export.ExportQualityLevel
import org.litvin.export.ExportResolutionChoice
import org.litvin.export.ExportSimplePreset
import org.litvin.export.ExportSourceInfo
import org.litvin.export.ExportVideoOptions
import org.litvin.export.ExportVideoTarget
import org.litvin.export.RenderFormatting
import org.litvin.ui.tabs.export.ExportUi.Weight
import java.awt.Dimension
import javax.swing.ButtonGroup
import javax.swing.JPanel

/** The video settings and the encoder that the next export uses. */
data class ExportQualitySelection(
    val target: ExportVideoTarget,
    val encoder: ExportEncoder,
)

/**
 * The content of the "Quality and file size" step. The Simple mode has three quality tiles and a table of details.
 * The Advanced mode has resolution, frame rate, bitrate and encoder controls. The selected mode decides
 * which settings the export uses. [modeControl] is the Simple / Advanced control; the step header shows it.
 */
internal class ExportQualityPanel(
    private val settings: ExportSettingsPreferences,
) : Stack(0) {
    private val saved = settings.load()
    private var source = ExportSourceInfo.UNKNOWN
    private var outputDurationMs: Long? = null
    private var encoders: EncoderCapabilities? = null

    /** Called when a setting or an estimate changes, so that the footer can show the new values. */
    var onChange: (() -> Unit)? = null

    private val simpleTab = SegmentButton("export-mode-simple", "Simple", compact = true)
    private val advancedTab = SegmentButton("export-mode-advanced", "Advanced", compact = true)
    val modeControl = SegmentedControl().apply {
        addSegment(simpleTab)
        addSegment(advancedTab)
    }

    // Simple mode
    private val simpleTiles = ExportSimplePreset.entries.associateWith { QualityTile("export-quality-${it.id}") }
    private val spec = SpecTable()
    private val resolutionSpec = spec.row("Resolution")
    private val fpsSpec = spec.row("FPS")
    private val bitrateSpec = spec.row("Bitrate")
    private val sizeSpec = spec.row("File size")
    private val timeSpec = spec.row("Export time")
    private val simpleEncoderText = encoderLine("export-simple-encoder")
    private val simplePanel = Stack(0)

    // Advanced mode
    private val resolutionControl = SegmentedControl()
    private val frameRateControl = SegmentedControl()
    private var resolutionButtons: List<Pair<ExportResolutionChoice, SegmentButton>> = emptyList()
    private var frameRateButtons: List<Pair<ExportFrameRateChoice, SegmentButton>> = emptyList()
    private val bitrateSlider = ExportSliderUI.slider("export-bitrate")
    private val bitrateValueText = WrapText(emptyList(), align = WrapText.Align.RIGHT).apply { name = "export-bitrate-value" }
    private val bitrateNoteText = WrapText("", ExportUi.font(11f), ExportUi.FG_3).apply { name = "export-bitrate-note" }
    private val encoderControl = SegmentedControl()
    private var encoderButtons: List<Pair<ExportEncoder, SegmentButton>> = emptyList()
    private var chosenEncoderId: String? = saved.encoderId
    private val encoderDescriptionText = WrapText("", ExportUi.font(11.5f), ExportUi.FG_2)
    private val advancedGpuText = encoderLine("export-advanced-gpu")
    private val advancedPanel = Stack(0)

    init {
        name = "export-quality"
        simpleTab.addActionListener { showMode(advanced = false) }
        advancedTab.addActionListener { showMode(advanced = true) }

        val tiles = GridRows(3, 6, 6)
        val simpleGroup = ButtonGroup()
        simpleTiles.forEach { (preset, tile) ->
            simpleGroup.add(tile)
            tile.setTexts(preset.title, preset.summary)
            tile.addActionListener {
                settings.saveSimplePreset(preset.id)
                updateSimpleDetails()
                changed()
            }
            tiles.add(tile)
        }
        val simplePreset = ExportSimplePreset.fromId(saved.simplePresetId) ?: ExportSimplePreset.BEST
        simpleTiles.getValue(simplePreset).isSelected = true
        simplePanel.add(tiles)
        simplePanel.add(VGap(8))
        simplePanel.add(spec)
        simplePanel.add(VGap(8))
        simplePanel.add(simpleEncoderText)

        advancedPanel.add(field("Resolution", resolutionControl))
        advancedPanel.add(VGap(14))
        advancedPanel.add(field("Frame rate", frameRateControl))
        advancedPanel.add(VGap(14))
        val scale = GridRows(2, 0, 0).apply {
            add(WrapText("Worst quality", ExportUi.font(11f), ExportUi.FG_3).apply { name = "export-bitrate-worst" })
            add(WrapText("Best quality", ExportUi.font(11f), ExportUi.FG_3, align = WrapText.Align.RIGHT).apply { name = "export-bitrate-best" })
        }
        advancedPanel.add(Stack(0).apply {
            add(FieldHead("Bitrate", bitrateValueText))
            add(VGap(6))
            add(bitrateSlider)
            add(scale)
            add(VGap(3))
            add(bitrateNoteText)
        })
        advancedPanel.add(VGap(14))
        advancedPanel.add(Stack(0).apply {
            add(FieldHead("Encoder"))
            add(VGap(6))
            add(encoderControl)
            add(VGap(6))
            add(encoderDescriptionText)
            add(VGap(8))
            add(advancedGpuText)
        })
        bitrateSlider.addChangeListener {
            updateBitrateLabels()
            changed()
        }
        add(simplePanel)
        add(advancedPanel)

        applySource()
        applyEncoders()
        if (saved.advancedMode) advancedTab.isSelected = true else simpleTab.isSelected = true
        showMode(saved.advancedMode, save = false)
    }

    /** Shows the options for a new source video. The Advanced mode goes back to the source values. */
    fun setSource(info: ExportSourceInfo) {
        source = info
        applySource()
        changed()
    }

    /** Duration of the exported video, for the file size estimate. Null when it is not known. */
    fun setOutputDurationMs(durationMs: Long?) {
        outputDurationMs = durationMs
        updateSimpleDetails()
        updateBitrateLabels()
        changed()
    }

    /** Shows the encoders that passed the test encode. Until this call, the export uses the software encoder. */
    fun setEncoders(capabilities: EncoderCapabilities) {
        encoders = capabilities
        applyEncoders()
        changed()
    }

    fun isAdvancedMode(): Boolean = advancedTab.isSelected

    fun selection(): ExportQualitySelection {
        val capabilities = encoders ?: EncoderCapabilities.NONE
        if (!isAdvancedMode()) {
            return ExportQualitySelection(ExportVideoOptions.simpleTarget(selectedSimplePreset(), source), capabilities.best)
        }
        val resolution = resolutionButtons.firstOrNull { it.second.isSelected }?.first
            ?: ExportVideoOptions.defaultResolution(resolutionButtons.map { it.first })
        val frameRate = frameRateButtons.firstOrNull { it.second.isSelected }?.first
            ?: ExportVideoOptions.defaultFrameRate(frameRateButtons.map { it.first })
        val encoder = encoderButtons.firstOrNull { it.second.isSelected }?.first ?: capabilities.best
        return ExportQualitySelection(
            ExportVideoTarget(ExportVideoOptions.CUSTOM_PRESET_ID, resolution.resolution, frameRate.frameRate, bitrateSlider.value),
            encoder,
        )
    }

    /** The estimated file size of the current selection, for example "~9.72 GB", or "unknown". */
    fun selectedSize(): String = estimatedSize(selection().target.bitrateK)

    /** The resolution and the frame rate of the current selection, for example "4K · 60 fps". */
    fun selectedVideoSummary(): String {
        val target = selection().target
        return listOfNotNull(
            ExportVideoOptions.displayResolution(target.resolution.width, target.resolution.height),
            target.frameRate?.let { "${it.displayFps} fps" },
        ).joinToString(" · ")
    }

    private fun selectedSimplePreset(): ExportSimplePreset =
        simpleTiles.entries.firstOrNull { it.value.isSelected }?.key ?: ExportSimplePreset.BEST

    private fun showMode(advanced: Boolean, save: Boolean = true) {
        simplePanel.isVisible = !advanced
        advancedPanel.isVisible = advanced
        if (save) settings.saveAdvancedMode(advanced)
        revalidate()
        repaint()
        changed()
    }

    private fun changed() {
        onChange?.invoke()
    }

    private fun applySource() {
        val resolutionChoices = ExportVideoOptions.resolutionChoices(source.resolution)
        val defaultResolution = ExportVideoOptions.defaultResolution(resolutionChoices)
        resolutionControl.removeSegments()
        resolutionButtons = resolutionChoices.map { choice ->
            val button = SegmentButton(resolutionButtonName(choice), choice.title).apply {
                setSmall(levelParts(choice.level, if (choice.isSource) ORIGINAL else null))
                isEnabled = choice.available
                isSelected = choice == defaultResolution
                addActionListener { changed() }
            }
            resolutionControl.addSegment(button)
            choice to button
        }

        val frameRateChoices = ExportVideoOptions.frameRateChoices(source.frameRate)
        val defaultFrameRate = ExportVideoOptions.defaultFrameRate(frameRateChoices)
        frameRateControl.removeSegments()
        frameRateButtons = frameRateChoices.map { choice ->
            val note = when {
                !choice.isSource -> null
                source.variableFrameRate -> "$ORIGINAL, variable"
                else -> ORIGINAL
            }
            val button = SegmentButton(frameRateButtonName(choice), choice.title).apply {
                setSmall(levelParts(choice.level, note))
                isEnabled = choice.available
                isSelected = choice == defaultFrameRate
                addActionListener { changed() }
            }
            frameRateControl.addSegment(button)
            choice to button
        }

        val range = ExportVideoOptions.bitrateRange(source)
        bitrateSlider.minimum = range.minK
        bitrateSlider.maximum = range.maxK
        bitrateSlider.value = range.maxK
        bitrateNoteText.setText(
            if (range.estimated) {
                "The bitrate of the original video is not known. The maximum is a typical camera bitrate."
            } else {
                "The maximum is the bitrate of the original video."
            },
            ExportUi.font(11f),
            ExportUi.FG_3,
        )
        updateSimpleDetails()
        updateBitrateLabels()
        revalidate()
        repaint()
    }

    /** "low", "medium" or "high", and a green note after it, for example "high · original". */
    private fun levelParts(level: ExportQualityLevel?, note: String?): List<Pair<String, Boolean>> = when {
        level != null && note != null -> listOf("${level.label} · " to false, note to true)
        level != null -> listOf(level.label to false)
        note != null -> listOf(note to true)
        else -> listOf(" " to false)
    }

    private fun updateSimpleDetails() {
        simpleTiles.forEach { (preset, tile) ->
            tile.setValue(estimatedSize(ExportVideoOptions.simpleTarget(preset, source).bitrateK))
        }
        val preset = selectedSimplePreset()
        val target = ExportVideoOptions.simpleTarget(preset, source)
        val sameResolution = source.resolution?.let { it.width == target.resolution.width && it.height == target.resolution.height } == true
        resolutionSpec.set(
            ExportVideoOptions.displayResolution(target.resolution.width, target.resolution.height),
            ORIGINAL.takeIf { sameResolution },
        )
        val averageRate = source.averageFrameRate?.takeIf { source.variableFrameRate }
        fpsSpec.set(
            target.frameRate?.displayFps ?: "unknown",
            ORIGINAL.takeIf { target.frameRate != null },
            note = averageRate?.let { "The original frame rate is variable: ${it.displayFps} fps on average." },
        )
        when {
            source.bitrate == null -> bitrateSpec.set(ExportVideoOptions.formatBitrate(target.bitrateK))
            preset.bitrateFactor >= 1.0 -> bitrateSpec.set(ExportVideoOptions.formatBitrate(target.bitrateK), ORIGINAL)
            else -> bitrateSpec.set(
                ExportVideoOptions.formatBitrate(target.bitrateK),
                "${(preset.bitrateFactor * 100).toInt()}% of $ORIGINAL",
                mutedTag = true,
            )
        }
        sizeSpec.set(estimatedSize(target.bitrateK))
        timeSpec.set(preset.exportTime)
    }

    private fun updateBitrateLabels() {
        val bold = ExportUi.font(12.5f, Weight.BOLD)
        val regular = ExportUi.font(12.5f)
        bitrateValueText.runs = listOf(
            TextRun(ExportVideoOptions.formatBitrate(bitrateSlider.value), bold, ExportUi.FG),
            TextRun(" · file size ", regular, ExportUi.FG),
            TextRun(estimatedSize(bitrateSlider.value), bold, ExportUi.FG),
        )
    }

    private fun applyEncoders() {
        val capabilities = encoders
        encoderControl.removeSegments()
        if (capabilities == null) {
            simpleEncoderText.runs = listOf(
                TextRun("Detecting GPUs and encoders…\nUntil the detection ends, the export uses the software encoder.", ENCODER_FONT, ExportUi.FG_2),
            )
            advancedGpuText.runs = listOf(TextRun("Detecting GPUs and encoders…", ENCODER_FONT, ExportUi.FG_2))
            encoderButtons = emptyList()
            encoderControl.isVisible = false
            encoderDescriptionText.isVisible = false
            revalidate()
            return
        }
        val best = capabilities.best
        val gpus = gpuRuns(capabilities.gpuNames)
        advancedGpuText.runs = gpus
        simpleEncoderText.runs = listOf(
            TextRun("Selected encoder:", ENCODER_BOLD_FONT, ExportUi.FG),
            TextRun(" ${best.title}", ENCODER_FONT, ExportUi.FG_2),
        ) + (if (best.hardware) emptyList() else listOf(
            TextRun("\nNo hardware encoder works on this PC, so the processor encodes the video.", ENCODER_FONT, ExportUi.FG_2),
        )) + TextRun("\n", ENCODER_FONT, ExportUi.FG_2) + gpus

        val selected = capabilities.options.firstOrNull { it.id == chosenEncoderId } ?: best
        encoderButtons = capabilities.options.map { encoder ->
            val button = SegmentButton("export-encoder-${encoder.id}", encoder.title).apply {
                setSmall(listOf(if (encoder == best) "Best for this PC" to true else " " to false))
                toolTipText = encoder.description
                isSelected = encoder == selected
                addActionListener {
                    chosenEncoderId = encoder.id
                    settings.saveEncoder(encoder.id)
                    showEncoderDescription()
                    changed()
                }
            }
            encoderControl.addSegment(button)
            encoder to button
        }
        encoderControl.isVisible = true
        encoderDescriptionText.isVisible = true
        showEncoderDescription()
        revalidate()
        repaint()
    }

    private fun showEncoderDescription() {
        val encoder = encoderButtons.firstOrNull { it.second.isSelected }?.first ?: return
        encoderDescriptionText.setText(encoder.description, ExportUi.font(11.5f), ExportUi.FG_2)
    }

    /** "Detected GPUs:" and the GPU names in one line. */
    private fun gpuRuns(gpuNames: List<String>): List<TextRun> {
        val heading = if (gpuNames.size == 1) "Detected GPU:" else "Detected GPUs:"
        return listOf(
            TextRun(heading, ENCODER_BOLD_FONT, ExportUi.FG),
            TextRun(" " + gpuNames.ifEmpty { listOf("none") }.joinToString(", "), ENCODER_FONT, ExportUi.FG_2),
        )
    }

    private fun estimatedSize(bitrateK: Int): String =
        outputDurationMs?.let { "~" + RenderFormatting.formatSize(ExportVideoOptions.estimatedBytes(bitrateK, it)) } ?: "unknown"

    private fun resolutionButtonName(choice: ExportResolutionChoice): String =
        "export-resolution-" + if (choice.level == null) "source" else choice.title.lowercase()

    private fun frameRateButtonName(choice: ExportFrameRateChoice): String =
        "export-fps-" + if (choice.level == null) "source" else when (choice.level) {
            ExportQualityLevel.LOW -> "24"
            ExportQualityLevel.MEDIUM -> "30"
            ExportQualityLevel.HIGH -> "60"
        }

    private fun encoderLine(componentName: String) = WrapText(emptyList(), lineHeight = 1.6f).apply { name = componentName }

    /** A field of the Advanced mode: a bold label, and the control under it. */
    private fun field(label: String, control: SegmentedControl): Stack = Stack(0).apply {
        add(FieldHead(label))
        add(VGap(6))
        add(control)
    }

    /** The head of a field: a bold label on the left and an optional value on the right. */
    private class FieldHead(label: String, private val value: WrapText? = null) : JPanel(null), HeightForWidth {
        private val labelText = WrapText(label, ExportUi.font(12.5f, Weight.SEMIBOLD), ExportUi.FG)

        init {
            isOpaque = false
            add(labelText)
            value?.let(::add)
        }

        override fun heightForWidth(width: Int): Int = maxOf(labelText.heightForWidth(width), value?.heightForWidth(valueWidth(width)) ?: 0)

        override fun getPreferredSize() = Dimension(300, heightForWidth(if (width > 0) width else 380))

        private fun valueWidth(width: Int) = (width - labelText.naturalWidth() - 8).coerceAtLeast(0)

        override fun doLayout() {
            val labelWidth = labelText.naturalWidth()
            labelText.setBounds(0, 0, labelWidth, labelText.heightForWidth(labelWidth))
            value?.let { it.setBounds(width - valueWidth(width), 0, valueWidth(width), it.heightForWidth(valueWidth(width))) }
        }
    }

    private companion object {
        const val ORIGINAL = "original"
        val ENCODER_FONT = ExportUi.font(11.5f)
        val ENCODER_BOLD_FONT = ExportUi.font(11.5f, Weight.SEMIBOLD)
    }
}
