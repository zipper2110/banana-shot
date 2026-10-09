package org.litvin.ui.tabs.projects.components

import org.junit.jupiter.api.Test
import org.litvin.ui.tabs.projects.presenter.ProjectCardState
import org.litvin.ui.tabs.projects.presenter.ProjectStatsState
import java.awt.Component
import java.awt.Container
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectRowTest {
    private val project = ProjectCardState(
        path = "C:\\projects\\club-final\\project.json",
        name = "Club final",
        secondary = "D:\\video\\2026\\PXL_20260920_153210877.mp4",
        id = "p1",
        stats = ProjectStatsState("1:52:44", "14.61 GB", "96/128", "38", scoredCount = 96, pointCount = 128, sport = "Padel"),
    )

    @Test
    fun theRowShowsTheFiguresInTheirColumns() {
        SwingUtilities.invokeAndWait {
            val row = row(project)

            assertEquals("Club final", find<JLabel>(row, "projects-name-p1").text)
            assertEquals(project.secondary, find<JLabel>(row, "projects-video-p1").toolTipText)
            assertEquals("1:52:44", find<JLabel>(row, "projects-duration-p1").text)
            assertEquals("14.61 GB", find<JLabel>(row, "projects-size-p1").text)
            assertEquals("96/128", find<JLabel>(row, "projects-scored-p1").text)
            assertEquals("96 of 128 points have a score", find<JLabel>(row, "projects-scored-p1").toolTipText)
            assertEquals("38", find<JLabel>(row, "projects-favorites-p1").text)
            assertEquals("Padel", find<JLabel>(row, "projects-sport-p1").text)
            assertNull(findOrNull(row, "projects-video-missing-p1"))
        }
    }

    @Test
    fun theOpenProjectCannotBeDeleted() {
        SwingUtilities.invokeAndWait {
            val row = row(project, isCurrent = true)

            val delete = find<AbstractButton>(row, "projects-delete-p1")
            assertFalse(delete.isEnabled)
            assertEquals("You cannot delete the open project.", delete.toolTipText)
            assertTrue(find<AbstractButton>(row, "projects-rename-p1").isEnabled)
        }
    }

    @Test
    fun aMissingVideoShowsTheMessageInTheStatusIconAndThePathTooltip() {
        SwingUtilities.invokeAndWait {
            val missing = project.copy(stats = project.stats!!.copy(videoMissingMessage = "The video is not on the disk anymore."))
            val row = row(missing)

            assertEquals("The video is not on the disk anymore.", find<JLabel>(row, "projects-video-missing-p1").toolTipText)
            assertEquals(
                "<html>The video is not on the disk anymore.<br>D:\\video\\2026\\PXL_20260920_153210877.mp4</html>",
                find<JLabel>(row, "projects-video-p1").toolTipText,
            )
        }
    }

    @Test
    fun theFiguresAreEmptyWhileThePresenterLoadsThem() {
        SwingUtilities.invokeAndWait {
            val row = row(project.copy(stats = null))

            assertEquals("", find<JLabel>(row, "projects-duration-p1").text)
            assertEquals("", find<JLabel>(row, "projects-scored-p1").text)
            assertNull(find<JLabel>(row, "projects-scored-p1").toolTipText)
        }
    }

    @Test
    fun theButtonsAndAClickCallTheActions() {
        val calls = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            val row = ProjectRow(
                project,
                isCurrent = false,
                onRename = { calls += "rename" },
                onDelete = { calls += "delete" },
                onOpen = { calls += "open" },
            )
            find<AbstractButton>(row, "projects-rename-p1").doClick()
            find<AbstractButton>(row, "projects-delete-p1").doClick()
            find<AbstractButton>(row, "projects-open-p1").doClick()
            val name = find<JLabel>(row, "projects-name-p1")
            name.dispatchEvent(click(name, clickCount = 1))
            name.dispatchEvent(click(name, clickCount = 2))
            assertEquals(Cursor.HAND_CURSOR, row.cursor.type)
        }
        assertEquals(listOf("rename", "delete", "open", "open"), calls)
    }

    @Test
    fun theOpenProjectHasNoOpenButtonAndIgnoresAClick() {
        val calls = mutableListOf<String>()
        SwingUtilities.invokeAndWait {
            val row = ProjectRow(project, isCurrent = true, onRename = {}, onDelete = {}, onOpen = { calls += "open" })
            val name = find<JLabel>(row, "projects-name-p1")
            name.dispatchEvent(click(name, clickCount = 1))

            assertNull(findOrNull(row, "projects-open-p1"))
            assertEquals(Cursor.DEFAULT_CURSOR, row.cursor.type)
        }
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun theColumnsFillTheRowAndKeepTheVideoColumnInANarrowRow() {
        val wide = ProjectsTableColumns.columns(1152)
        val (nameX, nameWidth) = wide[ProjectsTableColumns.NAME]
        val (videoX, videoWidth) = wide[ProjectsTableColumns.VIDEO]
        assertEquals(nameX + nameWidth + 14, videoX)
        assertEquals(1152 - 10, wide.last().first + wide.last().second)
        // The name and the path share the free width 1 : 1.6.
        assertEquals(1.6, videoWidth.toDouble() / nameWidth, 0.02)

        val narrow = ProjectsTableColumns.columns(ProjectsTableColumns.minimumWidth - 100)
        assertTrue(narrow[ProjectsTableColumns.VIDEO].second > 0)
        assertTrue(narrow[ProjectsTableColumns.NAME].second > 0)
    }

    private fun row(project: ProjectCardState, isCurrent: Boolean = false) =
        ProjectRow(project, isCurrent, onRename = {}, onDelete = {}, onOpen = {})

    private fun click(c: Component, clickCount: Int) =
        MouseEvent(c, MouseEvent.MOUSE_CLICKED, 0L, 0, 5, 5, clickCount, false, MouseEvent.BUTTON1)

    private inline fun <reified T : Component> find(root: Component, name: String): T =
        assertNotNull(findOrNull(root, name) as? T, "No component $name")

    private fun findOrNull(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root is Container) root.components.forEach { child -> findOrNull(child, name)?.let { return it } }
        return null
    }
}
