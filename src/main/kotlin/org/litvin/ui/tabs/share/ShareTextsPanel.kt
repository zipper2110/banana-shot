package org.litvin.ui.tabs.share

import org.litvin.share.ColorRole
import org.litvin.share.ThumbnailColorSets
import org.litvin.share.ThumbnailFace
import org.litvin.share.ThumbnailField
import org.litvin.share.ThumbnailFonts
import org.litvin.share.ThumbnailLogos
import org.litvin.share.ThumbnailLooks
import org.litvin.share.ThumbnailVideoType
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.Stack
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.swing.Box
import javax.swing.Icon
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import kotlin.math.ceil
import kotlin.math.roundToInt
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.AbstractDocument
import javax.swing.text.AttributeSet
import javax.swing.text.DocumentFilter
import org.litvin.i18n.tr

/** What the Texts tab shows for one field. [source]: where the value comes from, or "Edited". */
internal data class FieldState(val value: String, val source: String, val edited: Boolean, val used: Boolean)

/** The state of the Texts tab. [finalScore] is null when the project has no scored points. */
internal data class TextsState(
    val type: ThumbnailVideoType,
    val fields: Map<ThumbnailField, FieldState>,
    val typeWords: String,
    val finalScore: String?,
    val headIsScore: Boolean,
    val anyEdited: Boolean,
    /** The logo of the user, or null. */
    val logo: BufferedImage? = null,
    /** True when the selected style shows the logo. */
    val showsLogo: Boolean = false,
    /** The font of the brand name. Null: the font of the style. */
    val brandFont: ThumbnailFace? = null,
    /** The color of the brand name: "#RRGGBB" or [ThumbnailLooks.AUTO]. */
    val brandColor: String = ThumbnailLooks.AUTO,
)

/**
 * The Texts tab of the side column: the video type and the text fields with their presets.
 * [onText] gets each change of a field. [onHead] gets a preset choice of the large words: false is the words of the
 * video type, true is the final score.
 */
internal class ShareTextsPanel(
    private val onType: (ThumbnailVideoType) -> Unit,
    private val onText: (ThumbnailField, String) -> Unit,
    private val onHead: (Boolean) -> Unit,
    private val onUsePresets: () -> Unit,
    /** Opens the file dialog of the logo. */
    private val onChooseLogo: () -> Unit = {},
    private val onRemoveLogo: () -> Unit = {},
    /** The font of the brand name. Null: the font of the style. */
    private val onBrandFont: (ThumbnailFace?) -> Unit = {},
    /** The color of the brand name: "#RRGGBB" or [ThumbnailLooks.AUTO]. */
    private val onBrandColor: (String) -> Unit = {},
    /** Opens the color dialog for the brand name. */
    private val onPickBrandColor: (Color) -> Unit = {},
) : Stack(pad = Insets(14, 16, 16, 16), gap = 12) {
    private val typeChoice = SegmentedChoice(
        "share-type",
        ThumbnailVideoType.entries.map { SegmentedChoice.Option(it, it.title) },
    )
    private val inputs = ThumbnailField.entries.associateWith { field -> input(field) }
    private val rows = HashMap<ThumbnailField, JComponent>()
    private val sources = HashMap<ThumbnailField, JLabel>()
    private val typeChip = ChipButton("share-head-type", "")
    private val scoreChip = ChipButton("share-head-score", tr("share.finalScore")).apply {
        toolTipText = tr("share.finalScore.tooltip")
    }
    private val scoreFinalChip = ChipButton("share-score-final", tr("share.finalScore"))
    private var finalScore: String? = null
    private val logoImage = JLabel().apply {
        name = "share-logo-image"
        horizontalAlignment = SwingConstants.CENTER
        preferredSize = Dimension(LOGO_W, LOGO_H)
    }
    private val logoAdd = LinkButton("share-logo-add", tr("share.logo.add"))
    private val logoRemove = LinkButton("share-logo-remove", tr("share.logo.remove"))
    private val logoRow = captioned(
        tr("share.logo"),
        null,
        JPanel(BorderLayout(10, 0)).apply {
            isOpaque = false
            add(logoImage, BorderLayout.WEST)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(logoAdd)
                add(Box.createHorizontalStrut(14))
                add(logoRemove)
            }, BorderLayout.CENTER)
        },
    ).apply { name = "share-logo" }
    private val usePresets = LinkButton("share-use-presets", tr("share.usePresets"))
    private var updating = false

    /** The font and the color of the brand name. They are the same for all styles and all projects. */
    private val brandFont = JComboBox<FaceOption>((listOf(FaceOption(null)) + ThumbnailFace.BRAND.map(::FaceOption)).toTypedArray()).apply {
        name = "share-brand-font"
        DialogKit.styleCombo(this)
        getAccessibleContext().accessibleName = tr("share.brand.font")
        maximumRowCount = ThumbnailFace.BRAND.size + 1
        // The video of the large preview is a native window. A Swing popup cannot show over it.
        isLightWeightPopupEnabled = false
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component {
                val option = value as? FaceOption
                return super.getListCellRendererComponent(list, option?.toString().orEmpty(), index, selected, focus).also {
                    // Each font shows in its own letters.
                    option?.face?.let { face -> font = ThumbnailFonts.font(face, 15.0) }
                }
            }
        }
        addActionListener { if (!updating) onBrandFont((selectedItem as? FaceOption)?.face) }
    }
    private var brandColorValue = ThumbnailLooks.AUTO
    private val brandAuto = ColorSwatch("share-brand-color-auto", tr("share.brand.color.auto"), null)
    private val brandSwatches = ThumbnailColorSets.brandSwatches().map { named ->
        ColorSwatch("share-brand-color-${named.id}", named.name, Color(named.rgb)) to named.rgb
    }
    private val brandCustom = ColorSwatch(
        "share-brand-color-other", tr("share.colors.other"), null, custom = true,
        colorWheel = ThumbnailColorSets.swatches(ColorRole.ACCENT).take(7).map { Color(it.rgb) },
    )
    private val brandLook = JPanel(BorderLayout(0, 6)).apply {
        isOpaque = false
        add(JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(ShareUi.label(tr("share.brand.font"), 12f, Palette.FG_2).apply { preferredSize = Dimension(BRAND_LABEL_W, 30) }, BorderLayout.WEST)
            add(brandFont, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(ShareUi.label(tr("share.brand.color"), 12f, Palette.FG_2).apply {
                preferredSize = Dimension(BRAND_LABEL_W, 30)
                verticalAlignment = SwingConstants.TOP
                border = javax.swing.BorderFactory.createEmptyBorder(6, 0, 0, 0)
            }, BorderLayout.WEST)
            add(SwatchRows().apply {
                add(brandAuto)
                brandSwatches.forEach { (swatch, _) -> add(swatch) }
                add(brandCustom)
            }, BorderLayout.CENTER)
        }, BorderLayout.CENTER)
    }

    init {
        name = "share-texts"
        add(JPanel(BorderLayout()).apply {
            isOpaque = false
            preferredSize = Dimension(0, 18)
            add(usePresets, BorderLayout.EAST)
        }, gapAfter = 4)
        add(captioned(tr("share.videoType"), null, typeChoice))
        typeChoice.onChange { onType(it) }
        usePresets.addActionListener { onUsePresets() }

        add(textField(ThumbnailField.HEAD, JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(box(ThumbnailField.HEAD), BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(typeChip)
                add(scoreChip)
            }, BorderLayout.CENTER)
        }))
        typeChip.addActionListener { onHead(false) }
        scoreChip.addActionListener { onHead(true) }
        add(textField(ThumbnailField.KIND, box(ThumbnailField.KIND)))
        add(textField(ThumbnailField.ROUND, box(ThumbnailField.ROUND)))
        add(textField(ThumbnailField.TEAM1, JPanel(GridLayout(2, 1, 0, 6)).apply {
            isOpaque = false
            add(box(ThumbnailField.TEAM1))
            add(box(ThumbnailField.TEAM2))
        }, caption = tr("scoreSettings.players")))
        add(textField(ThumbnailField.SCORE, JPanel(BorderLayout(0, 6)).apply {
            isOpaque = false
            add(box(ThumbnailField.SCORE), BorderLayout.NORTH)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(scoreFinalChip)
            }, BorderLayout.CENTER)
        }))
        scoreFinalChip.addActionListener { finalScore?.let { onText(ThumbnailField.SCORE, it) } }
        add(textField(ThumbnailField.EVENT, box(ThumbnailField.EVENT)))
        add(textField(ThumbnailField.COURT, box(ThumbnailField.COURT)))
        add(textField(ThumbnailField.DATE, box(ThumbnailField.DATE)))
        // The name is under the logo on the thumbnail, so its field is under the logo controls.
        add(logoRow)
        add(textField(ThumbnailField.CLUB, JPanel(BorderLayout(0, 8)).apply {
            isOpaque = false
            add(box(ThumbnailField.CLUB), BorderLayout.NORTH)
            add(brandLook, BorderLayout.CENTER)
        }))
        brandAuto.addActionListener { onBrandColor(ThumbnailLooks.AUTO) }
        brandSwatches.forEach { (swatch, rgb) -> swatch.addActionListener { onBrandColor(ThumbnailLooks.hex(rgb)) } }
        brandCustom.addActionListener { onPickBrandColor(Color(ThumbnailLooks.parseHex(brandColorValue) ?: 0xFFFFFF)) }
        logoAdd.addActionListener { onChooseLogo() }
        logoRemove.addActionListener { onRemoveLogo() }
        add(ShareUi.hint(tr("share.emptyField")))
        inputs.getValue(ThumbnailField.EVENT).toolTipText = tr("share.field.event.tooltip")
        inputs.getValue(ThumbnailField.SCORE).toolTipText = tr("share.field.score.tooltip")
        // An empty field does not show on the thumbnail. The gray example tells what the field is for.
        inputs.getValue(ThumbnailField.CLUB).putClientProperty("JTextField.placeholderText", tr("share.field.club.placeholder"))
        logoAdd.toolTipText = tr("share.logo.hint")
    }

    private fun input(field: ThumbnailField) = JTextField().apply {
        name = "share-field-${field.key}"
        getAccessibleContext().accessibleName = field.label
        (document as AbstractDocument).documentFilter = MaxLength(field.maxLength)
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = changed()
            override fun removeUpdate(e: DocumentEvent) = changed()
            override fun changedUpdate(e: DocumentEvent) = changed()
            private fun changed() {
                if (!updating) onText(field, text)
            }
        })
    }

    private fun box(field: ThumbnailField): JComponent = DialogKit.inputBox(inputs.getValue(field), boxHeight = 32)

    /** A caption with the source of the value on the right, and the input under it. */
    private fun textField(field: ThumbnailField, input: JComponent, caption: String = field.label): JComponent {
        val source = ShareUi.label("", 11f, Palette.FG_3)
        sources[field] = source
        return captioned(caption, source, input).also { rows[field] = it }
    }

    private fun captioned(caption: String, source: JLabel?, input: JComponent): JComponent = JPanel(BorderLayout(0, 5)).apply {
        isOpaque = false
        val label = ShareUi.label(caption, 12f, Palette.FG_2)
        add(JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(label, BorderLayout.WEST)
            if (source != null) add(source.apply { horizontalAlignment = SwingConstants.RIGHT }, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(input, BorderLayout.CENTER)
    }

    fun show(state: TextsState) {
        updating = true
        try {
            typeChoice.selected = state.type
            for ((field, input) in inputs) {
                val value = state.fields[field]?.value.orEmpty()
                // The field with the text cursor keeps its text, so that the cursor does not move while the user types.
                if (!input.isFocusOwner && input.text != value) input.text = value
            }
        } finally {
            updating = false
        }
        for ((field, source) in sources) {
            val keys = if (field == ThumbnailField.TEAM1) listOf(ThumbnailField.TEAM1, ThumbnailField.TEAM2) else listOf(field)
            val states = keys.mapNotNull { state.fields[it] }
            val edited = states.any { it.edited }
            val used = states.any { it.used }
            val empty = states.all { it.value.isBlank() }
            val text = when {
                edited -> tr("share.source.edited")
                empty -> tr("share.source.none")
                field == ThumbnailField.HEAD && state.headIsScore -> tr("share.source.finalScore")
                else -> field.source
            }
            source.text = text
            source.foreground = if (edited) Palette.YELLOW else Palette.FG_3
            // The selected style shows only some fields. The other fields keep their values for the other styles.
            rows[field]?.isVisible = used
        }
        val head = state.fields[ThumbnailField.HEAD]?.value.orEmpty()
        typeChip.text = state.typeWords
        typeChip.chosen = !state.headIsScore && head == state.typeWords
        scoreChip.isEnabled = state.finalScore != null
        scoreChip.chosen = state.headIsScore
        scoreChip.toolTipText = if (state.finalScore == null) {
            tr("share.finalScore.none")
        } else {
            tr("share.finalScore.tooltip.value", "score" to state.finalScore)
        }
        finalScore = state.finalScore
        val score = state.fields[ThumbnailField.SCORE]?.value.orEmpty()
        scoreFinalChip.isEnabled = state.finalScore != null
        scoreFinalChip.chosen = state.finalScore != null && score == state.finalScore
        scoreFinalChip.toolTipText = state.finalScore ?: tr("share.finalScore.none")
        logoRow.isVisible = state.showsLogo
        updating = true
        try {
            brandFont.selectedIndex = state.brandFont?.let { ThumbnailFace.BRAND.indexOf(it) + 1 } ?: 0
        } finally {
            updating = false
        }
        brandColorValue = state.brandColor
        val brandRgb = ThumbnailLooks.parseHex(state.brandColor)
        brandAuto.chosen = brandRgb == null
        brandSwatches.forEach { (swatch, rgb) -> swatch.chosen = rgb == brandRgb }
        val known = brandRgb == null || brandSwatches.any { it.second == brandRgb }
        brandCustom.chosen = !known
        brandCustom.color = if (known) null else Color(brandRgb!!)
        if ((logoImage.icon as? LogoIcon)?.image !== state.logo) logoImage.icon = state.logo?.let { LogoIcon(it) }
        logoImage.isVisible = state.logo != null
        logoAdd.text = if (state.logo == null) tr("share.logo.add") else tr("share.logo.change")
        logoRemove.isVisible = state.logo != null
        usePresets.isVisible = state.anyEdited
        revalidate()
        repaint()
    }

    /**
     * The logo in the box of the preview, with its aspect. The icon scales the logo to the pixels of the screen when
     * it paints, so the logo is sharp on a HiDPI screen. An image scaled to the logical size looks blurred there,
     * because Swing then makes it larger again.
     */
    private class LogoIcon(val image: BufferedImage) : Icon {
        private val scale = minOf(LOGO_W.toDouble() / image.width, LOGO_H.toDouble() / image.height)
        private val w = (image.width * scale).roundToInt().coerceAtLeast(1)
        private val h = (image.height * scale).roundToInt().coerceAtLeast(1)

        /** The scaled logo and its size in device pixels. */
        private var cache: Pair<Dimension, BufferedImage>? = null

        override fun getIconWidth() = w

        override fun getIconHeight() = h

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                val size = Dimension(
                    ceil(w * g2.transform.scaleX.coerceAtLeast(1.0)).toInt(),
                    ceil(h * g2.transform.scaleY.coerceAtLeast(1.0)).toInt(),
                )
                val scaled = cache?.takeIf { it.first == size }?.second
                    ?: ThumbnailLogos.scaledDown(image, size.width, size.height).also { cache = size to it }
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.drawImage(scaled, x, y, w, h, null)
            } finally {
                g2.dispose()
            }
        }
    }

    /** A font of the brand name in the list. [face] null: the font of the style. */
    private class FaceOption(val face: ThumbnailFace?) {
        override fun toString(): String = face?.title ?: tr("share.brand.font.auto")
    }

    /** Stops a field at its maximum length. */
    private class MaxLength(private val max: Int) : DocumentFilter() {
        override fun insertString(fb: FilterBypass, offset: Int, text: String?, attr: AttributeSet?) =
            replace(fb, offset, 0, text, attr)

        override fun replace(fb: FilterBypass, offset: Int, length: Int, text: String?, attrs: AttributeSet?) {
            val room = max - (fb.document.length - length)
            val fitted = text.orEmpty().replace('\n', ' ').take(room.coerceAtLeast(0))
            super.replace(fb, offset, length, fitted, attrs)
        }
    }
}

private const val LOGO_W = 72
private const val LOGO_H = 36
private const val BRAND_LABEL_W = 44
