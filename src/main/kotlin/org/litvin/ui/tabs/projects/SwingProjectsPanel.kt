package org.litvin.ui.tabs.projects

import org.litvin.scoring.Sport
import org.litvin.ui.commons.FilePicker
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.SystemFilePicker
import org.litvin.ui.commons.ThemeSettings
import org.litvin.ui.commons.ThemeSwitch
import org.litvin.ui.commons.SwingUserDialogService
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.applyDarkScrollbar
import org.litvin.ui.tabs.projects.components.DeleteProjectDialog
import org.litvin.ui.tabs.projects.components.NewProjectDialog
import org.litvin.ui.tabs.projects.components.NewProjectEditor
import org.litvin.ui.tabs.projects.components.NewProjectRequest
import org.litvin.ui.tabs.projects.components.ProjectDeleteConfirmer
import org.litvin.ui.tabs.projects.components.ProjectNameEditor
import org.litvin.ui.tabs.projects.components.ProjectVideosDialog
import org.litvin.ui.tabs.projects.components.ProjectVideosViewer
import org.litvin.ui.tabs.projects.components.ProjectRow
import org.litvin.ui.tabs.projects.components.ProjectsEmptyListCard
import org.litvin.ui.tabs.projects.components.ProjectsPaginationBar
import org.litvin.ui.tabs.projects.components.ProjectsTableColumns
import org.litvin.ui.tabs.projects.components.ProjectsUi
import org.litvin.ui.tabs.projects.components.RenameProjectDialog
import org.litvin.ui.tabs.projects.components.StartPanel
import org.litvin.ui.tabs.projects.presenter.ProjectCardState
import org.litvin.ui.tabs.projects.presenter.ProjectsIntent
import org.litvin.ui.tabs.projects.presenter.ProjectsPresenter
import org.litvin.ui.tabs.projects.presenter.ProjectsView
import org.litvin.ui.tabs.projects.presenter.ProjectsViewEffect
import org.litvin.ui.tabs.projects.presenter.ProjectsViewState
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Rectangle
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import javax.swing.Scrollable
import org.litvin.i18n.tr

/**
 * Passive Swing view for the Projects tab.
 *
 * The presenter owns project IO, pagination decisions, and navigation effects.
 * This panel only forwards user intents and renders immutable ProjectsViewState.
 */
class SwingProjectsPanel(
    private val presenter: ProjectsPresenter,
    private val filePicker: FilePicker = SystemFilePicker(),
    private val dialogs: UserDialogService = SwingUserDialogService(),
    private val newProjectEditor: NewProjectEditor = NewProjectDialog,
    private val projectNameEditor: ProjectNameEditor = RenameProjectDialog,
    private val deleteConfirmer: ProjectDeleteConfirmer = DeleteProjectDialog,
    private val themeSettings: ThemeSettings? = null,
    private val videosViewer: ProjectVideosViewer = ProjectVideosDialog,
) : JPanel(BorderLayout()), ProjectsView {
    private val startPanel = StartPanel(onImportNewMatch = { presenter.onIntent(ProjectsIntent.ImportNewMatch) })
    private val rows = RowsPanel()
    private val listCards = CardLayout()
    private val listArea = JPanel(listCards).apply { isOpaque = false }
    private val emptyListSlot = JPanel(BorderLayout()).apply { isOpaque = false }
    private val hint = JLabel(tr("projects.list.hint")).apply {
        name = "projects-list-hint"
        font = ProjectsUi.font(12f)
        foreground = Palette.FG_3
    }
    private val paginationBar = ProjectsPaginationBar(
        onPrevious = { presenter.onIntent(ProjectsIntent.GoToPage(lastState.currentPage - 1)) },
        onNext = { presenter.onIntent(ProjectsIntent.GoToPage(lastState.currentPage + 1)) },
    )
    private var lastState = ProjectsViewState()
    private var shownCurrentProject: ProjectCardState? = null

    var onProjectOpened: ((String) -> Unit)? = null

    /** Opens the feedback form with a text, to ask for the support of video files with different formats (B-49). */
    var onRequestFormatSupport: ((String) -> Unit)? = null

    /** Receives the name of the current project after an open, a create, or a rename. */
    var onCurrentProjectNameChanged: ((String?) -> Unit)? = null

    init {
        isOpaque = true
        background = Palette.BG

        add(startPanel, BorderLayout.WEST)
        add(buildListSection(), BorderLayout.CENTER)
        startPanel.showCurrentProject(name = null, videoPaths = emptyList(), onRename = null)
    }

    override fun addNotify() {
        super.addNotify()
        presenter.attach(this)
        presenter.onActivated()
    }

    override fun removeNotify() {
        presenter.onDeactivated()
        presenter.detach()
        super.removeNotify()
    }

    fun onActivated() {
        presenter.onActivated()
    }

    fun onDeactivated() {
        presenter.onDeactivated()
    }

    override fun render(state: ProjectsViewState) {
        val previousName = lastState.currentProject?.name
        lastState = state
        if (state.currentProject?.name != previousName) onCurrentProjectNameChanged?.invoke(state.currentProject?.name)
        renderCurrentProject(state.currentProject)
        renderProjectList(state)
        renderPagination(state)
    }

    override fun renderEffect(effect: ProjectsViewEffect) {
        when (effect) {
            is ProjectsViewEffect.ChooseSourceVideo -> {
                // The user can select several files of one match (B-49).
                chooseSourceVideos(tr("projects.chooseVideo"), effect.initialDirectory)?.let { paths ->
                    presenter.onIntent(ProjectsIntent.SourceVideoSelected(paths))
                }
            }
            is ProjectsViewEffect.ConfirmNewProject -> {
                newProjectEditor.edit(
                    parent = this,
                    initial = NewProjectRequest(
                        name = effect.name,
                        sourceVideoPath = effect.sourceVideoPath,
                        sport = effect.sport,
                        rules = if (effect.sport == Sport.PADEL) effect.padelRules else effect.sport.defaultRules(),
                        sourceVideoPaths = effect.sourceVideoPaths,
                    ),
                    chooseVideo = { dialog, current ->
                        // "Add videos" gives a folder: the picker opens in it and selects no file (B-49).
                        val folder = File(current).takeIf { it.isDirectory }
                        chooseSourceVideos(
                            tr("projects.chooseVideo"),
                            folder?.path ?: File(current).parent ?: File(effect.sourceVideoPath).parent,
                            suggestedFile = if (folder == null) File(current) else null,
                            parent = dialog,
                        )
                    },
                )?.let { request ->
                    presenter.onIntent(
                        ProjectsIntent.CreateProject(
                            request.name,
                            request.sourceVideoPath,
                            request.sport,
                            request.rules,
                            request.sourceVideoPaths,
                        )
                    )
                }
            }
            is ProjectsViewEffect.ChooseMissingSourceVideo -> {
                chooseSourceVideo(
                    tr("projects.chooseVideoFor", "project" to effect.projectName),
                    effect.initialDirectory,
                )?.let { path ->
                    presenter.onIntent(ProjectsIntent.MissingSourceVideoSelected(effect.manifestPath, path))
                }
            }
            is ProjectsViewEffect.LocateMovedSourceVideo -> locateMovedSourceVideo(effect)
            is ProjectsViewEffect.ProjectOpened -> onProjectOpened?.invoke(effect.manifestPath)
            is ProjectsViewEffect.ShowFormatWarning -> showFormatWarning(effect)
            is ProjectsViewEffect.ShowError ->
                if (effect.warning) dialogs.showWarning(this, effect.message, effect.title)
                else dialogs.showError(this, effect.message, effect.title)
        }
    }

    private fun buildListSection(): JComponent {
        val title = JLabel(tr("projects.recent")).apply {
            font = ProjectsUi.font(18f, ProjectsUi.Weight.SEMIBOLD)
            foreground = Palette.FG
            // A few pixels more than the measured text, so that the last letter is never cut.
            preferredSize = Dimension(preferredSize.width + 4, preferredSize.height)
            minimumSize = preferredSize
        }
        val head = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(0, 0, 10, 0)
            add(title)
            add(Box.createHorizontalStrut(10))
            add(hint)
            add(Box.createHorizontalGlue())
            add(paginationBar)
            add(Box.createVerticalStrut(30))
            themeSettings?.let { settings ->
                add(Box.createHorizontalStrut(28))
                add(JLabel(tr("projects.appTheme")).apply {
                    font = ProjectsUi.font(12.5f)
                    foreground = Palette.FG_3
                    // A few pixels more than the measured text, so that the last letter is never cut.
                    preferredSize = Dimension(preferredSize.width + 4, preferredSize.height)
                    maximumSize = preferredSize
                })
                add(Box.createHorizontalStrut(8))
                add(ThemeSwitch("projects-theme", settings))
            }
        }

        val listScroll = JScrollPane(rows).apply {
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            // The column header stays above the rows and has the same width as the viewport, also with a scroll bar.
            setColumnHeaderView(ProjectsTableColumns.header())
            columnHeader.isOpaque = false
            verticalScrollBar.unitIncrement = 18
            applyDarkScrollbar(this, Palette.BG)
        }
        listArea.add(listScroll, CARD_TABLE)
        listArea.add(emptyListSlot, CARD_EMPTY)

        return JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Palette.BG
            border = BorderFactory.createEmptyBorder(22, 24, 16, 24)
            add(head, BorderLayout.NORTH)
            add(listArea, BorderLayout.CENTER)
        }
    }

    private fun renderCurrentProject(project: ProjectCardState?) {
        // The figures do not show in the card, so a new card is necessary only for a new name or path.
        val shown = project?.copy(stats = null)
        if (shown == shownCurrentProject) return
        shownCurrentProject = shown
        startPanel.showCurrentProject(
            name = project?.name,
            videoPaths = project?.shownVideoPaths.orEmpty(),
            onRename = project?.let { { renameProject(it) } },
            onShowVideos = { project?.let(::showVideos) },
        )
    }

    private fun renderProjectList(state: ProjectsViewState) {
        val empty = state.visibleProjects.isEmpty()
        hint.isVisible = !empty
        if (empty) {
            rows.removeAll()
            emptyListSlot.removeAll()
            emptyListSlot.add(ProjectsEmptyListCard(state.emptyListMessage), BorderLayout.CENTER)
            listCards.show(listArea, CARD_EMPTY)
        } else {
            val shown = rows.components.filterIsInstance<ProjectRow>()
            val sameRows = shown.size == state.visibleProjects.size &&
                shown.zip(state.visibleProjects).all { (row, project) -> row.shows(project, isCurrent(project)) }
            if (sameRows) {
                // Only the figures changed. The rows stay, so a button under the pointer does not disappear.
                shown.zip(state.visibleProjects).forEach { (row, project) -> row.showStats(project) }
            } else {
                rows.removeAll()
                state.visibleProjects.forEach { project -> rows.add(buildProjectRow(project)) }
            }
            listCards.show(listArea, CARD_TABLE)
        }
        refresh(listArea)
        refresh(rows)
    }

    private fun renderPagination(state: ProjectsViewState) {
        paginationBar.render(
            currentPage = state.currentPage,
            totalPages = state.totalPages,
            canGoPrevious = state.canGoPrevious,
            canGoNext = state.canGoNext,
            isVisible = state.isPaginationVisible,
        )
        refresh(paginationBar)
    }

    private fun buildProjectRow(project: ProjectCardState): JComponent {
        return ProjectRow(
            project,
            isCurrent = isCurrent(project),
            onRename = { renameProject(project) },
            onDelete = { deleteProject(project) },
            onOpen = { presenter.onIntent(ProjectsIntent.OpenProject(project.path)) },
            onShowVideos = { showVideos(project) },
        )
    }

    /** Shows all video files of a project of several files (B-49). */
    private fun showVideos(project: ProjectCardState) {
        videosViewer.show(this, project.name, project.shownVideoPaths)
    }

    private fun isCurrent(project: ProjectCardState): Boolean = project.path == lastState.currentProject?.path

    private fun deleteProject(project: ProjectCardState) {
        if (deleteConfirmer.confirm(this, project.name)) presenter.onIntent(ProjectsIntent.DeleteProject(project.path))
    }

    private fun renameProject(project: ProjectCardState) {
        val name = projectNameEditor.edit(this, project.name, project.shownVideoPaths) ?: return
        if (name != project.name) presenter.onIntent(ProjectsIntent.RenameProject(project.path, name))
    }

    private fun locateMovedSourceVideo(effect: ProjectsViewEffect.LocateMovedSourceVideo) {
        val locate = dialogs.confirm(
            parent = this,
            message = tr("projects.videoMoved.text", "path" to effect.oldPath),
            title = tr("projects.error.open"),
            confirmLabel = tr("projects.videoMoved.locate"),
        )
        if (!locate) return
        chooseSourceVideo(tr("projects.locateVideoFor", "project" to effect.projectName), effect.initialDirectory)?.let { path ->
            presenter.onIntent(ProjectsIntent.MissingSourceVideoSelected(effect.manifestPath, path))
        }
    }

    /**
     * The video files of the new project have different formats (B-49). The warning asks the user to request the
     * support through the feedback form. "Send feedback" opens the form with the formats of the files.
     */
    private fun showFormatWarning(effect: ProjectsViewEffect.ShowFormatWarning) {
        val requestSupport = onRequestFormatSupport
        val message = tr("projects.formats.text", "project" to effect.projectName)
        if (requestSupport == null) {
            dialogs.showWarning(this, message, tr("projects.formats.title"))
            return
        }
        val send = dialogs.confirm(
            parent = this,
            message = message,
            title = tr("projects.formats.title"),
            confirmLabel = tr("projects.formats.feedback"),
            cancelLabel = tr("common.close"),
        )
        if (send) requestSupport(tr("projects.formats.report", "formats" to effect.report))
    }

    private fun chooseSourceVideos(
        title: String,
        initialDirectory: String?,
        suggestedFile: File? = null,
        parent: Component = this,
    ): List<String>? =
        filePicker.chooseSourceVideos(
            parent = parent,
            title = title,
            initialDirectory = initialDirectory?.let(::File),
            suggestedFile = suggestedFile?.takeIf { it.isFile },
        )?.map { it.absolutePath }?.takeIf { it.isNotEmpty() }

    private fun chooseSourceVideo(
        title: String,
        initialDirectory: String?,
        suggestedFile: File? = null,
        parent: Component = this,
    ): String? =
        filePicker.chooseSourceVideo(
            parent = parent,
            title = title,
            initialDirectory = initialDirectory?.let(::File),
            suggestedFile = suggestedFile?.takeIf { it.isFile },
        )?.absolutePath

    private fun refresh(container: Container) {
        container.revalidate()
        container.repaint()
    }

    /** The rows of the table, one under the other. The rows are as wide as the viewport. */
    private class RowsPanel : JPanel(), Scrollable {
        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 18
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = visibleRect.height
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

    private companion object {
        const val CARD_TABLE = "table"
        const val CARD_EMPTY = "empty"
    }
}
