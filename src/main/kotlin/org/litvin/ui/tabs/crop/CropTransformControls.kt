package org.litvin.ui.tabs.crop

import org.litvin.adjustments.AdjustmentsV1
import org.litvin.adjustments.CropGeometryMath
import org.litvin.ui.commons.CardColumn
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.ResetAllButton
import org.litvin.ui.commons.SIDE_PANEL_WIDTH
import org.litvin.ui.commons.SidePanelHeader
import org.litvin.ui.commons.UiKit
import java.awt.BorderLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The right panel of the Transform tab: a header with the changed count and "Reset all",
 * the Crop and Rotation cards with the five sliders, and a card with the mouse and key hints.
 * The layout comes from design/transform-redesign/option-a.html.
 *
 * Each slider has a value field. A valid number in the field moves the slider at once.
 * Up and Down in the field change the value by one step.
 * Rotation is split across a coarse slider (0.5° steps) and a fine one (0.1° steps). Their sum is the angle.
 */
class CropTransformControls(
    private val onChanged: (AdjustmentsV1) -> Unit,
    private val onResetTransform: () -> Unit,
) : JPanel(BorderLayout()) {
    internal val rows: Map<TransformControl, TransformRow> = TransformControl.entries.associateWith { control ->
        TransformRow(control, first = TransformControl.entries.first { it.group == control.group } == control)
    }

    private val cards: Map<TransformGroup, TransformGroupCard> = TransformGroup.entries.associateWith { group ->
        TransformGroupCard(group, rows.values.filter { it.control.group == group })
    }

    internal val resetButton = ResetAllButton("crop-reset", "Reset Transform").apply { addActionListener { onResetTransform() } }

    private val statusLabel = JLabel().apply { name = "crop-status" }
    private val header = SidePanelHeader("Transform", statusLabel, resetButton)

    private val zoomSlider = slider(TransformControl.ZOOM)
    private val panXSlider = slider(TransformControl.PAN_X)
    private val panYSlider = slider(TransformControl.PAN_Y)
    private val rotationSlider = slider(TransformControl.ROTATION)
    private val fineRotationSlider = slider(TransformControl.FINE_ROTATION)

    private var updating = false
    private var current = AdjustmentsV1()
    private var lastEmittedRotation: Float? = null

    internal fun slider(control: TransformControl): JSlider = rows.getValue(control).slider

    /** The header status: "All at default" or "2 of 4 changed". */
    internal val statusText: String get() = statusLabel.text

    /** The total angle in the Rotation caption, for example "+2.3°". */
    internal val angleText: String get() = cards.getValue(TransformGroup.ROTATION).angleText

    init {
        name = "crop-transform-panel"
        isOpaque = true
        background = Palette.BG
        border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)

        rows.values.forEach { row -> bindRow(row) }

        val column = CardColumn().apply {
            TransformGroup.entries.forEach { add(cards.getValue(it)) }
            add(TransformHints())
        }

        add(header, BorderLayout.NORTH)
        add(column.inScrollPane(), BorderLayout.CENTER)
        refreshStatus()
    }

    fun render(adjustments: AdjustmentsV1) {
        updating = true
        try {
            // An older project can have a zoom below 1.0. Keep the value that it renders with.
            current = adjustments.copy(zoom = adjustments.zoom.coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM))
            zoomSlider.value = modelZoomToSlider(adjustments).coerceIn(zoomSlider.minimum, zoomSlider.maximum)
            panXSlider.value = (adjustments.panX * 100.0f).toInt().coerceIn(-100, 100)
            panYSlider.value = (adjustments.panY * 100.0f).toInt().coerceIn(-100, 100)
            renderRotation(adjustments.rotationDeg)
        } finally {
            updating = false
        }
        refreshStatus()
    }

    private fun bindRow(row: TransformRow) {
        row.slider.addChangeListener {
            publishFromSlider(row.control)
            refreshStatus()
        }
    }

    private fun publishFromSlider(control: TransformControl) {
        if (updating) return
        current = when (control) {
            TransformControl.ZOOM -> current.copy(zoom = zoomSliderToModel(zoomSlider.value))
            TransformControl.PAN_X -> current.copy(panX = (panXSlider.value / 100.0f).coerceIn(-1.0f, 1.0f))
            TransformControl.PAN_Y -> current.copy(panY = (panYSlider.value / 100.0f).coerceIn(-1.0f, 1.0f))
            TransformControl.ROTATION, TransformControl.FINE_ROTATION -> current.copy(rotationDeg = emitRotation())
        }
        onChanged(current)
    }

    /** Updates the header status, the Reset all style and the angle of the Rotation card. */
    private fun refreshStatus() {
        val angle = rotationFromSliders()
        cards.getValue(TransformGroup.ROTATION).angle = angle
        val changed = listOf(
            abs(current.zoom - 1.0f) > CHANGE_EPSILON,
            abs(current.panX) > CHANGE_EPSILON,
            abs(current.panY) > CHANGE_EPSILON,
            (angle * 10).roundToInt() != 0,
        ).count { it }
        header.showChanged(changed, TRANSFORM_VALUES)
    }

    /** The angle of the two rotation sliders together. */
    private fun rotationFromSliders(): Float =
        (rotationSlider.value / 2.0f + fineRotationSlider.value / 10.0f)
            .coerceIn(-ROTATION_LIMIT_DEG, ROTATION_LIMIT_DEG)

    private fun emitRotation(): Float =
        CropGeometryMath.normalizeRotation(rotationFromSliders()).also { lastEmittedRotation = it }

    /** Splits an angle that came from elsewhere back into the two sliders, leaving our own edits alone. */
    private fun renderRotation(rotationDeg: Float) {
        val emitted = lastEmittedRotation
        if (emitted != null && abs(rotationDeg - emitted) <= ROTATION_EPSILON_DEG) return
        lastEmittedRotation = rotationDeg
        val clamped = rotationDeg.coerceIn(-ROTATION_LIMIT_DEG, ROTATION_LIMIT_DEG)
        val coarse = (clamped * 2.0f).roundToInt().coerceIn(rotationSlider.minimum, rotationSlider.maximum)
        val fine = ((clamped - coarse / 2.0f) * 10.0f).roundToInt()
            .coerceIn(fineRotationSlider.minimum, fineRotationSlider.maximum)
        rotationSlider.value = coarse
        fineRotationSlider.value = fine
    }

    private fun zoomSliderToModel(value: Int): Float = (value / 100.0f).coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM)

    private fun modelZoomToSlider(adjustments: AdjustmentsV1): Int =
        (adjustments.zoom.coerceIn(AdjustmentsV1.MIN_ZOOM, AdjustmentsV1.MAX_ZOOM) * 100.0f).roundToInt()

    companion object {
        const val PANEL_WIDTH = SIDE_PANEL_WIDTH

        /** The status counts four values: zoom, pan X, pan Y and the angle. */
        private const val TRANSFORM_VALUES = 4
        private const val CHANGE_EPSILON = 0.0005f
        private const val ROTATION_LIMIT_DEG = 180.0f
        private const val ROTATION_EPSILON_DEG = 0.0001f
    }
}
