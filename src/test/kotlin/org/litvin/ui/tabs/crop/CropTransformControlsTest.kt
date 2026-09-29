package org.litvin.ui.tabs.crop

import org.junit.jupiter.api.Test
import org.litvin.adjustments.AdjustmentsV1
import java.awt.Component
import java.awt.Container
import java.awt.DefaultKeyboardFocusManager
import java.awt.KeyboardFocusManager
import javax.swing.JSlider
import javax.swing.JTextField
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CropTransformControlsTest {
    @Test
    fun editingFocusedReadoutDoesNotMutateDocumentDuringNotification() {
        try {
            System.setProperty("java.awt.headless", "true")
        } catch (_: Throwable) {
        }

        SwingUtilities.invokeAndWait {
            val previousFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val focusManager = TestFocusManager()
            KeyboardFocusManager.setCurrentKeyboardFocusManager(focusManager)

            try {
                val changes = mutableListOf<AdjustmentsV1>()
                val controls = CropTransformControls(
                    onChanged = { changes.add(it) },
                    onResetTransform = {},
                )
                val zoomReadout = controls.textFields().first()

                focusManager.focus(zoomReadout)
                try {
                    zoomReadout.text = "250"
                } finally {
                    focusManager.focus(null)
                }

                assertEquals("250", zoomReadout.text)
                assertTrue(changes.any { it.zoom == 2.5f }, "Expected typed zoom value to publish")
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousFocusManager)
            }
        }
    }

    @Test
    fun rotationSlidersCoverOneEightyAndFiveDegrees() {
        headless()

        SwingUtilities.invokeAndWait {
            val controls = CropTransformControls(onChanged = {}, onResetTransform = {})
            val coarse = controls.slider("crop-rotation")
            val fine = controls.slider("crop-rotation-fine")

            assertEquals(-360 to 360, coarse.minimum to coarse.maximum, "Rotation spans -180..180 in half degrees")
            assertEquals(-50 to 50, fine.minimum to fine.maximum, "Fine rotation spans -5..5 in tenths of a degree")
        }
    }

    @Test
    fun fineRotationNudgesTheCoarseAngle() {
        headless()

        SwingUtilities.invokeAndWait {
            val changes = mutableListOf<AdjustmentsV1>()
            val controls = CropTransformControls(onChanged = { changes.add(it) }, onResetTransform = {})

            controls.slider("crop-rotation").value = 30
            controls.slider("crop-rotation-fine").value = 3

            assertClose(15.3f, changes.last().rotationDeg)
        }
    }

    @Test
    fun renderSplitsAnExternalAngleAcrossBothSliders() {
        headless()

        SwingUtilities.invokeAndWait {
            val changes = mutableListOf<AdjustmentsV1>()
            val controls = CropTransformControls(onChanged = { changes.add(it) }, onResetTransform = {})

            controls.render(AdjustmentsV1(rotationDeg = 12.3f))

            assertEquals(25, controls.slider("crop-rotation").value, "Coarse slider takes the nearest half degree")
            assertEquals(-2, controls.slider("crop-rotation-fine").value, "Fine slider takes the remainder")
            assertTrue(changes.isEmpty(), "Rendering an incoming angle must not publish a change")
        }
    }

    @Test
    fun headerCountsTheFourTransformValuesAndRotationCaptionShowsTheAngle() {
        headless()

        SwingUtilities.invokeAndWait {
            val controls = CropTransformControls(onChanged = {}, onResetTransform = {})
            assertEquals("All at default", controls.statusText)
            assertEquals("0.0°", controls.angleText)
            assertTrue(controls.resetButton.quiet, "Reset all is quiet when all values are at the default")

            controls.render(AdjustmentsV1(zoom = 1.4f, panX = -0.25f, rotationDeg = -2.3f))

            assertEquals("3 of 4 changed", controls.statusText)
            assertEquals("−2.3°", controls.angleText)
            assertTrue(!controls.resetButton.quiet, "Reset all is normal when a value is changed")
        }
    }

    @Test
    fun valueFieldsShowTheValueWithASignAndTheRowMarksAChange() {
        headless()

        SwingUtilities.invokeAndWait {
            val controls = CropTransformControls(onChanged = {}, onResetTransform = {})
            controls.render(AdjustmentsV1(zoom = 1.4f, panX = 0.24f, panY = -0.12f, rotationDeg = 12.3f))

            val texts = TransformControl.entries.associateWith { controls.rows.getValue(it).valueField.textField.text }
            assertEquals("140", texts[TransformControl.ZOOM])
            assertEquals("+24", texts[TransformControl.PAN_X])
            assertEquals("-12", texts[TransformControl.PAN_Y])
            assertEquals("+12.5", texts[TransformControl.ROTATION])
            assertEquals("-0.2", texts[TransformControl.FINE_ROTATION])
            assertTrue(controls.rows.values.all { it.isChanged && it.valueField.changed })

            controls.render(AdjustmentsV1())

            assertEquals("0.0", controls.rows.getValue(TransformControl.ROTATION).valueField.textField.text)
            assertTrue(controls.rows.values.none { it.isChanged || it.valueField.changed })
        }
    }

    @Test
    fun valueFieldKeepsSpaceSoItDoesNotPlayTheVideo() {
        headless()

        SwingUtilities.invokeAndWait {
            val controls = CropTransformControls(onChanged = {}, onResetTransform = {})
            controls.rows.values.forEach { row ->
                val field = row.valueField.textField
                val binding = field.getInputMap(javax.swing.JComponent.WHEN_FOCUSED)[javax.swing.KeyStroke.getKeyStroke("SPACE")]
                assertTrue(binding != null && field.actionMap[binding] != null, "${field.name} must consume SPACE")
                assertEquals("${row.control.componentName}-value", field.name)
            }
        }
    }

    @Test
    fun upAndDownStepOneUnitAndFineRotationStepsATenthOfADegree() {
        headless()

        SwingUtilities.invokeAndWait {
            val changes = mutableListOf<AdjustmentsV1>()
            val controls = CropTransformControls(onChanged = { changes.add(it) }, onResetTransform = {})
            fun field(control: TransformControl) = controls.rows.getValue(control).valueField.textField

            field(TransformControl.ZOOM).pressKey("UP")
            field(TransformControl.ZOOM).releaseKey("UP")
            assertEquals(101, controls.slider("crop-zoom").value)
            assertEquals("101", field(TransformControl.ZOOM).text)

            field(TransformControl.PAN_X).pressKey("DOWN")
            field(TransformControl.PAN_X).releaseKey("DOWN")
            assertEquals(-1, controls.slider("crop-pan-x").value)

            field(TransformControl.ROTATION).pressKey("UP")
            field(TransformControl.ROTATION).releaseKey("UP")
            assertEquals("+1.0", field(TransformControl.ROTATION).text)

            field(TransformControl.FINE_ROTATION).pressKey("DOWN")
            field(TransformControl.FINE_ROTATION).releaseKey("DOWN")
            assertEquals("-0.1", field(TransformControl.FINE_ROTATION).text)
            assertClose(0.9f, changes.last().rotationDeg)

            // The value stays in the slider range.
            field(TransformControl.ZOOM).pressKey("DOWN")
            field(TransformControl.ZOOM).releaseKey("DOWN")
            field(TransformControl.ZOOM).pressKey("DOWN")
            field(TransformControl.ZOOM).releaseKey("DOWN")
            assertEquals(100, controls.slider("crop-zoom").value)
        }
    }

    @Test
    fun heldStepKeyRepeatsAfterTheHoldDelayAndStopsOnRelease() {
        headless()

        lateinit var controls: CropTransformControls
        lateinit var field: JTextField
        SwingUtilities.invokeAndWait {
            controls = CropTransformControls(onChanged = {}, onResetTransform = {})
            field = controls.rows.getValue(TransformControl.PAN_Y).valueField.textField
            field.pressKey("UP")
            // The key repeat of the system sends more presses. They do not add steps.
            field.pressKey("UP")
            field.pressKey("UP")
        }
        fun value(): Int {
            var value = 0
            SwingUtilities.invokeAndWait { value = controls.slider("crop-pan-y").value }
            return value
        }
        assertEquals(1, value())
        Thread.sleep(1_300)
        val held = value()
        assertTrue(held > 2, "The held key must repeat the step, but the value is $held")

        SwingUtilities.invokeAndWait { field.releaseKey("UP") }
        val released = value()
        Thread.sleep(300)
        assertEquals(released, value(), "The step must stop when the key is released")
    }

    @Test
    fun zoomDoesNotGoBelowOneHundredPercent() {
        headless()

        SwingUtilities.invokeAndWait {
            val changes = mutableListOf<AdjustmentsV1>()
            val controls = CropTransformControls(onChanged = { changes.add(it) }, onResetTransform = {})
            val zoom = controls.slider("crop-zoom")
            assertEquals(100 to 250, zoom.minimum to zoom.maximum, "Zoom spans 100..250 percent")

            zoom.value = 300
            assertEquals(250, zoom.value)
            assertEquals(2.5f, changes.last().zoom)

            zoom.value = 50
            assertEquals(100, zoom.value)
            assertEquals(1.0f, changes.last().zoom)

            // An older project can have a saved zoom above 2.5. It shows as 250%.
            controls.render(AdjustmentsV1(zoom = 4.0f))
            assertEquals(250, zoom.value)

            // An older project can have a saved zoom below 1.0. It shows as 100%, the value that it renders with.
            controls.render(AdjustmentsV1(zoom = 0.5f))
            assertEquals(100, zoom.value)
            assertEquals("All at default", controls.statusText)
        }
    }

    @Test
    fun typedTextAcceptsSignsUnitsAndTheMinusSign() {
        assertEquals(140, TransformControl.ZOOM.fromText(" 140% "))
        assertEquals(-24, TransformControl.PAN_X.fromText("−24"))
        assertEquals(24, TransformControl.PAN_Y.fromText("+24"))
        assertEquals(5, TransformControl.ROTATION.fromText("2.5°"))
        assertEquals(23, TransformControl.FINE_ROTATION.fromText("2.3"))
        assertEquals(null, TransformControl.ZOOM.fromText("abc"))
        assertEquals(null, TransformControl.ZOOM.fromText(""))
    }

    @Test
    fun resetAllButtonKeepsItsNameAndTooltip() {
        headless()

        SwingUtilities.invokeAndWait {
            var resets = 0
            val controls = CropTransformControls(onChanged = {}, onResetTransform = { resets++ })

            assertEquals("crop-reset", controls.resetButton.name)
            assertEquals("Reset all", controls.resetButton.text)
            assertEquals("Reset Transform", controls.resetButton.toolTipText)
            controls.resetButton.doClick()
            assertEquals(1, resets)
        }
    }

    private fun headless() {
        try {
            System.setProperty("java.awt.headless", "true")
        } catch (_: Throwable) {
        }
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) <= 0.0001f, "Expected $expected but was $actual")
    }

    private fun Component.slider(name: String): JSlider =
        sliders().firstOrNull { it.name == name } ?: throw AssertionError("No slider named $name")

    private fun Component.sliders(): List<JSlider> {
        val own = if (this is JSlider) listOf(this) else emptyList()
        val childMatches = if (this is Container) components.flatMap { it.sliders() } else emptyList()
        return own + childMatches
    }

    private class TestFocusManager : DefaultKeyboardFocusManager() {
        fun focus(component: Component?) {
            setGlobalFocusOwner(component)
        }
    }

    private fun Component.textFields(): List<JTextField> {
        val own = if (this is JTextField) listOf(this) else emptyList()
        val childMatches = if (this is Container) {
            components.flatMap { it.textFields() }
        } else {
            emptyList()
        }
        return own + childMatches
    }

    private fun JTextField.pressKey(key: String) = keyAction("pressed $key")

    private fun JTextField.releaseKey(key: String) = keyAction("released $key")

    private fun JTextField.keyAction(stroke: String) {
        val binding = getInputMap(javax.swing.JComponent.WHEN_FOCUSED)[javax.swing.KeyStroke.getKeyStroke(stroke)]
            ?: throw AssertionError("$name has no binding for $stroke")
        actionMap[binding].actionPerformed(java.awt.event.ActionEvent(this, java.awt.event.ActionEvent.ACTION_PERFORMED, stroke))
    }
}
