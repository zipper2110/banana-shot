package org.litvin.ui.tabs.export

import org.junit.jupiter.api.Test
import javax.swing.SwingUtilities
import kotlin.test.assertTrue

class ExportControlsTest {
    /** B-11 E3-T22: Russian "Расширенно" next to the short "Просто" broke into two lines in the step header. */
    @Test
    fun `a long segment title in the step header stays on one line`() {
        SwingUtilities.invokeAndWait {
            val simple = SegmentButton("simple", "Просто", compact = true)
            val advanced = SegmentButton("advanced", "Расширенно", compact = true)
            val control = SegmentedControl().apply {
                addSegment(simple)
                addSegment(advanced)
            }
            val header = StepHeader(3, "Качество и размер файла", control)
            header.setSize(440, header.heightForWidth(440))
            header.doLayout()
            control.doLayout()
            listOf(simple, advanced).forEach { segment ->
                assertTrue(segment.width >= segment.preferredSize.width, "${segment.name}: ${segment.width} < ${segment.preferredSize.width}")
            }
        }
    }
}
