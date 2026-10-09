package org.litvin.ui.tabs.projects

import org.junit.jupiter.api.Test
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.tabs.projects.presenter.ProjectCardState
import org.litvin.ui.tabs.projects.presenter.ProjectStatsState
import org.litvin.ui.tabs.projects.presenter.ProjectsIntent
import org.litvin.ui.tabs.projects.presenter.ProjectsPresenter
import org.litvin.ui.tabs.projects.presenter.ProjectsView
import org.litvin.ui.tabs.projects.presenter.ProjectsViewEffect
import org.litvin.ui.tabs.projects.presenter.ProjectsViewState
import java.awt.Component
import java.awt.Container
import java.io.File
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SwingProjectsPanelTest {
    private class RecordingPresenter : ProjectsPresenter {
        val intents = mutableListOf<ProjectsIntent>()
        override fun attach(view: ProjectsView) = Unit
        override fun detach() = Unit
        override fun onActivated() = Unit
        override fun onDeactivated() = Unit
        override fun onIntent(intent: ProjectsIntent) {
            intents += intent
        }
    }

    private val first = ProjectCardState("C:\\p\\a\\project.json", "Club final", "D:\\video\\a.mp4", id = "a")
    private val second = ProjectCardState("C:\\p\\b\\project.json", "Serve practice", "D:\\video\\b.mp4", id = "b")

    @Test
    fun withoutProjectsTheListShowsTheFirstStartHelp() {
        SwingUtilities.invokeAndWait {
            val panel = SwingProjectsPanel(RecordingPresenter())
            panel.render(ProjectsViewState())

            assertEquals("No open project", find<JLabel>(panel, "projects-current-name").text)
            assertNull(findOrNull(panel, "projects-current-rename"))
            assertTrue(find<Component>(panel, "projects-empty-list").isVisible)
            assertFalse(find<JLabel>(panel, "projects-list-hint").isVisible)
            assertFalse(find<Component>(panel, "projects-page-label").parent.isVisible)
        }
    }

    @Test
    fun theOpenProjectShowsInTheCardAndHasTheMarkInTheList() {
        SwingUtilities.invokeAndWait {
            val panel = SwingProjectsPanel(RecordingPresenter())
            panel.render(
                ProjectsViewState(
                    currentProject = first,
                    visibleProjects = listOf(first, second),
                    currentPage = 2,
                    totalPages = 3,
                    canGoPrevious = true,
                    canGoNext = true,
                    isPaginationVisible = true,
                ),
            )

            assertEquals("Club final", find<JLabel>(panel, "projects-current-name").text)
            assertEquals("D:\\video\\a.mp4", find<JLabel>(panel, "projects-current-video").text)
            assertFalse(find<AbstractButton>(panel, "projects-delete-a").isEnabled)
            assertTrue(find<AbstractButton>(panel, "projects-delete-b").isEnabled)
            assertTrue(find<JLabel>(panel, "projects-list-hint").isVisible)
            assertEquals("Page 2 / 3", find<JLabel>(panel, "projects-page-label").text)
        }
    }

    @Test
    fun loadedFiguresKeepTheRowButtons() {
        SwingUtilities.invokeAndWait {
            val panel = SwingProjectsPanel(RecordingPresenter())
            panel.render(ProjectsViewState(visibleProjects = listOf(first, second)))
            val open = find<AbstractButton>(panel, "projects-open-b")
            val delete = find<AbstractButton>(panel, "projects-delete-b")

            val loaded = second.copy(stats = ProjectStatsState("0:42:09", "5.02 GB", "0/61", "9", scoredCount = 0, pointCount = 61))
            panel.render(ProjectsViewState(visibleProjects = listOf(first, loaded)))

            assertSame(open, find<AbstractButton>(panel, "projects-open-b"))
            assertEquals("0:42:09", find<JLabel>(panel, "projects-duration-b").text)

            panel.render(ProjectsViewState(currentProject = second, visibleProjects = listOf(loaded, first)))
            assertNotSame(delete, find<AbstractButton>(panel, "projects-delete-b"))
            assertFalse(find<AbstractButton>(panel, "projects-delete-b").isEnabled)
            assertNull(findOrNull(panel, "projects-open-b"))
        }
    }

    @Test
    fun onlyAConfirmedDeletionDeletesTheProject() {
        val presenter = RecordingPresenter()
        val asked = mutableListOf<String>()
        var answer = false
        SwingUtilities.invokeAndWait {
            val panel = SwingProjectsPanel(
                presenter,
                deleteConfirmer = { _, name -> asked += name; answer },
            )
            panel.render(ProjectsViewState(visibleProjects = listOf(first, second)))
            find<AbstractButton>(panel, "projects-delete-b").doClick()
            answer = true
            find<AbstractButton>(panel, "projects-delete-b").doClick()
        }
        assertEquals(listOf("Serve practice", "Serve practice"), asked)
        assertEquals(listOf<ProjectsIntent>(ProjectsIntent.DeleteProject("C:\\p\\b\\project.json")), presenter.intents)
    }

    @Test
    fun theButtonsSendTheIntents() {
        val presenter = RecordingPresenter()
        SwingUtilities.invokeAndWait {
            val panel = SwingProjectsPanel(presenter)
            panel.render(
                ProjectsViewState(
                    visibleProjects = listOf(first, second),
                    currentPage = 2,
                    totalPages = 3,
                    canGoPrevious = true,
                    canGoNext = true,
                    isPaginationVisible = true,
                ),
            )
            find<AbstractButton>(panel, "projects-import-match").doClick()
            find<AbstractButton>(panel, "projects-open-b").doClick()
            find<AbstractButton>(panel, "projects-page-previous").doClick()
            find<AbstractButton>(panel, "projects-page-next").doClick()
        }
        assertEquals(
            listOf(
                ProjectsIntent.ImportNewMatch,
                ProjectsIntent.OpenProject("C:\\p\\b\\project.json"),
                ProjectsIntent.GoToPage(1),
                ProjectsIntent.GoToPage(3),
            ),
            presenter.intents,
        )
    }

    @Test
    fun locateVideoOpensThePickerInTheGivenFolderAndCancelSendsNothing() {
        val presenter = RecordingPresenter()
        val pickerFolders = mutableListOf<File?>()
        val located = File("E:\\match\\final.mp4")
        val picker = object : FilePicker {
            override fun chooseSourceVideo(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?): File? {
                pickerFolders += initialDirectory
                return located
            }

            override fun chooseExportDestination(parent: Component?, title: String, initialDirectory: File?, suggestedFile: File?) =
                error("unused")
        }
        var answer = false
        val dialogs = object : UserDialogService {
            override fun showInfo(parent: Component?, message: String, title: String) = Unit
            override fun showError(parent: Component?, message: String, title: String) = Unit
            override fun confirm(parent: Component?, message: String, title: String) = error("unused")
            override fun confirm(
                parent: Component?,
                message: String,
                title: String,
                confirmLabel: String,
                cancelLabel: String,
                destructive: Boolean,
            ): Boolean {
                assertTrue("D:\\video\\a.mp4" in message)
                assertEquals("Locate video...", confirmLabel)
                return answer
            }
        }
        val effect = ProjectsViewEffect.LocateMovedSourceVideo("C:\\p\\a\\project.json", "Club final", "D:\\video\\a.mp4", "D:\\video")
        SwingUtilities.invokeAndWait {
            val panel = SwingProjectsPanel(presenter, filePicker = picker, dialogs = dialogs)
            panel.renderEffect(effect)
            answer = true
            panel.renderEffect(effect)
        }
        assertEquals(listOf<File?>(File("D:\\video")), pickerFolders)
        assertEquals(
            listOf<ProjectsIntent>(ProjectsIntent.MissingSourceVideoSelected("C:\\p\\a\\project.json", located.absolutePath)),
            presenter.intents,
        )
    }

    private inline fun <reified T : Component> find(root: Component, name: String): T =
        assertNotNull(findOrNull(root, name) as? T, "No component $name")

    private fun findOrNull(root: Component, name: String): Component? {
        if (root.name == name) return root
        if (root is Container) root.components.forEach { child -> findOrNull(child, name)?.let { return it } }
        return null
    }
}
