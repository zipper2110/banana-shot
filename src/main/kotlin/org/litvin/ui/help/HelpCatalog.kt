package org.litvin.ui.help

import org.litvin.ui.commons.AppShortcut
import org.litvin.ui.commons.AppShortcuts
import org.litvin.i18n.tr

/** The pages of the Help window. [titleKey] is the catalog key of the page title. */
enum class HelpPage(private val titleKey: String) {
    OVERVIEW("help.page.overview"),
    PROJECTS("tab.projects"),
    POINTS("tab.points"),
    STATISTICS("tab.statistics"),
    COLORS("adjust.colors"),
    CROP("adjust.transform"),
    SHARE("tab.thumbnail"),
    EXPORT("tab.export"),
    ;

    val title: String get() = tr(titleKey)
}

data class HelpShortcut(
    val shortcut: AppShortcut,
    val action: String,
)

data class HelpContent(
    val page: HelpPage,
    val summary: String,
    val workflow: List<String>,
    val actions: List<String>,
    val goodToKnow: List<String>,
    val shortcuts: List<HelpShortcut>,
)

object HelpCatalog {
    /** The help pages in the app language. The texts are in the help catalog files. */
    val pages: List<HelpContent>
        get() = listOf(
        HelpContent(
            page = HelpPage.OVERVIEW,
            summary = tr("help.overview.summary"),
            workflow = listOf(
                tr("help.overview.workflow.1"),
                tr("help.overview.workflow.2"),
                tr("help.overview.workflow.3"),
                tr("help.overview.workflow.4"),
                tr("help.overview.workflow.5"),
                tr("help.overview.workflow.6"),
                tr("help.overview.workflow.7"),
            ),
            actions = listOf(
                tr("help.overview.action.1"),
                tr("help.overview.action.2"),
                tr("help.overview.action.3"),
                tr("help.overview.action.4"),
                tr("help.overview.action.5"),
            ),
            goodToKnow = listOf(
                tr("help.overview.note.1"),
                tr("help.overview.note.2"),
                tr("help.overview.note.3"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, tr("help.overview.shortcut.help")),
            ),
        ),
        HelpContent(
            page = HelpPage.PROJECTS,
            summary = tr("help.projects.summary"),
            workflow = listOf(
                tr("help.projects.workflow.1"),
                tr("help.projects.workflow.2"),
                tr("help.projects.workflow.3"),
                tr("help.projects.workflow.4"),
                tr("help.projects.workflow.5"),
            ),
            actions = listOf(
                tr("help.projects.action.1"),
                tr("help.projects.action.2"),
                tr("help.projects.action.3"),
                tr("help.projects.action.4"),
                tr("help.projects.action.5"),
            ),
            goodToKnow = listOf(
                tr("help.projects.note.1"),
                tr("help.projects.note.2"),
                tr("help.projects.note.3"),
                tr("help.projects.note.4"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, tr("help.projects.shortcut.help")),
            ),
        ),
        HelpContent(
            page = HelpPage.COLORS,
            summary = tr("help.colors.summary"),
            workflow = listOf(
                tr("help.colors.workflow.1"),
                tr("help.colors.workflow.2"),
            ),
            actions = listOf(
                tr("help.colors.action.1"),
                tr("help.colors.action.2"),
                tr("help.colors.action.3"),
            ),
            goodToKnow = listOf(
                tr("help.colors.note.1"),
                tr("help.colors.note.2"),
                tr("help.colors.note.3"),
                tr("help.colors.note.4"),
                tr("help.colors.note.5"),
                tr("help.colors.note.6"),
                tr("help.colors.note.7"),
                tr("help.colors.note.8"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.PLAY_PAUSE, tr("help.colors.shortcut.playPause")),
                HelpShortcut(AppShortcuts.HELP, tr("help.colors.shortcut.help")),
            ),
        ),
        HelpContent(
            page = HelpPage.CROP,
            summary = tr("help.crop.summary"),
            workflow = listOf(
                tr("help.crop.workflow.1"),
                tr("help.crop.workflow.2"),
                tr("help.crop.workflow.4"),
                tr("help.crop.workflow.5"),
            ),
            actions = listOf(
                tr("help.crop.action.1"),
                tr("help.crop.action.2"),
                tr("help.crop.action.3"),
                tr("help.crop.action.4"),
            ),
            goodToKnow = listOf(
                tr("help.crop.note.1"),
                tr("help.crop.note.2"),
                tr("help.crop.note.3"),
                tr("help.crop.note.4"),
                tr("help.crop.note.5"),
                tr("help.crop.note.6"),
                tr("help.crop.note.7"),
                tr("help.crop.note.8"),
                tr("help.crop.note.9"),
                tr("help.crop.note.10"),
                tr("help.crop.note.11"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.PLAY_PAUSE, tr("help.crop.shortcut.playPause")),
                HelpShortcut(AppShortcuts.LEFT, tr("help.crop.shortcut.left")),
                HelpShortcut(AppShortcuts.RIGHT, tr("help.crop.shortcut.right")),
                HelpShortcut(AppShortcuts.UP, tr("help.crop.shortcut.up")),
                HelpShortcut(AppShortcuts.DOWN, tr("help.crop.shortcut.down")),
                HelpShortcut(AppShortcuts.SHIFT_LEFT, tr("help.crop.shortcut.shiftLeft")),
                HelpShortcut(AppShortcuts.SHIFT_RIGHT, tr("help.crop.shortcut.shiftRight")),
                HelpShortcut(AppShortcuts.SHIFT_UP, tr("help.crop.shortcut.shiftUp")),
                HelpShortcut(AppShortcuts.SHIFT_DOWN, tr("help.crop.shortcut.shiftDown")),
                HelpShortcut(AppShortcuts.HELP, tr("help.crop.shortcut.help")),
            ),
        ),
        HelpContent(
            page = HelpPage.POINTS,
            summary = tr("help.points.summary"),
            workflow = listOf(
                tr("help.points.workflow.1"),
                tr("help.points.workflow.2"),
                tr("help.points.workflow.3"),
                tr("help.points.workflow.4"),
                tr("help.points.workflow.5"),
                tr("help.points.workflow.6"),
            ),
            actions = listOf(
                tr("help.points.action.1"),
                tr("help.points.action.2"),
                tr("help.points.action.3"),
                tr("help.points.action.4"),
                tr("help.points.action.5"),
                tr("help.points.action.6"),
                tr("help.points.action.7"),
                tr("help.points.action.8"),
                tr("help.points.action.9"),
            ),
            goodToKnow = listOf(
                tr("help.points.note.1"),
                tr("help.points.note.2"),
                tr("help.points.note.3"),
                tr("help.points.note.4"),
                tr("help.points.note.5"),
                tr("help.points.note.6"),
                tr("help.points.note.7"),
                tr("help.points.note.8"),
                tr("help.points.note.9"),
                tr("help.points.note.10"),
                tr("help.points.note.11"),
                tr("help.points.note.12"),
                tr("help.points.note.13"),
                tr("help.points.note.14"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.PLAY_PAUSE, tr("help.points.shortcut.playPause")),
                HelpShortcut(AppShortcuts.POINT_START, tr("help.points.shortcut.pointStart")),
                HelpShortcut(AppShortcuts.POINT_END, tr("help.points.shortcut.pointEnd")),
                HelpShortcut(AppShortcuts.SCORE_PLAYER_1, tr("help.points.shortcut.scorePlayer1")),
                HelpShortcut(AppShortcuts.SCORE_NO_POINT, tr("help.points.shortcut.scoreNoPoint")),
                HelpShortcut(AppShortcuts.SCORE_PLAYER_2, tr("help.points.shortcut.scorePlayer2")),
                HelpShortcut(AppShortcuts.NEXT_POINT, tr("help.points.shortcut.nextPoint")),
                HelpShortcut(AppShortcuts.PREVIOUS_POINT, tr("help.points.shortcut.previousPoint")),
                HelpShortcut(AppShortcuts.SWITCH_SERVE, tr("help.points.shortcut.switchServe")),
                HelpShortcut(AppShortcuts.TOGGLE_FAVORITE, tr("help.points.shortcut.toggleFavorite")),
                HelpShortcut(AppShortcuts.DELETE, tr("help.points.shortcut.delete")),
                HelpShortcut(AppShortcuts.TOGGLE_FRAME_STEP, tr("help.points.shortcut.toggleFrameStep")),
                HelpShortcut(AppShortcuts.LEFT, tr("help.points.shortcut.left")),
                HelpShortcut(AppShortcuts.RIGHT, tr("help.points.shortcut.right")),
                HelpShortcut(AppShortcuts.SHIFT_LEFT, tr("help.points.shortcut.shiftLeft")),
                HelpShortcut(AppShortcuts.SHIFT_RIGHT, tr("help.points.shortcut.shiftRight")),
                HelpShortcut(AppShortcuts.UP, tr("help.points.shortcut.up")),
                HelpShortcut(AppShortcuts.DOWN, tr("help.points.shortcut.down")),
                HelpShortcut(AppShortcuts.HELP, tr("help.points.shortcut.help")),
            ),
        ),
        HelpContent(
            page = HelpPage.STATISTICS,
            summary = tr("help.statistics.summary"),
            workflow = listOf(
                tr("help.statistics.workflow.1"),
                tr("help.statistics.workflow.2"),
                tr("help.statistics.workflow.3"),
            ),
            actions = listOf(
                tr("help.statistics.action.1"),
                tr("help.statistics.action.2"),
                tr("help.statistics.action.3"),
                tr("help.statistics.action.4"),
                tr("help.statistics.action.5"),
                tr("help.statistics.action.6"),
                tr("help.statistics.action.7"),
            ),
            goodToKnow = listOf(
                tr("help.statistics.note.1"),
                tr("help.statistics.note.2"),
                tr("help.statistics.note.3"),
                tr("help.statistics.note.4"),
                tr("help.statistics.note.5"),
                tr("help.statistics.note.6"),
                tr("help.statistics.note.7"),
                tr("help.statistics.note.8"),
                tr("help.statistics.note.9"),
                tr("help.statistics.note.10"),
                tr("help.statistics.note.11"),
                tr("help.statistics.note.12"),
                tr("help.statistics.note.13"),
                tr("help.statistics.note.14"),
                tr("help.statistics.note.15"),
                tr("help.statistics.note.16"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, tr("help.statistics.shortcut.help")),
            ),
        ),
        HelpContent(
            page = HelpPage.EXPORT,
            summary = tr("help.export.summary"),
            workflow = listOf(
                tr("help.export.workflow.1"),
                tr("help.export.workflow.2"),
                tr("help.export.workflow.3"),
                tr("help.export.workflow.4"),
                tr("help.export.workflow.videoLanguage"),
                tr("help.export.workflow.5"),
                tr("help.export.workflow.6"),
                tr("help.export.workflow.7"),
            ),
            actions = listOf(
                tr("help.export.action.1"),
                tr("help.export.action.2"),
                tr("help.export.action.3"),
            ),
            goodToKnow = listOf(
                tr("help.export.note.1"),
                tr("help.export.note.2"),
                tr("help.export.note.3"),
                tr("help.export.note.4"),
                tr("help.export.note.5"),
                tr("help.export.note.6"),
                tr("help.export.note.7"),
                tr("help.export.note.8"),
                tr("help.export.note.9"),
                tr("help.export.note.10"),
                tr("help.export.note.11"),
                tr("help.export.note.12"),
                tr("help.export.note.13"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.HELP, tr("help.export.shortcut.help")),
            ),
        ),
        HelpContent(
            page = HelpPage.SHARE,
            summary = tr("help.share.summary"),
            workflow = listOf(
                tr("help.share.workflow.1"),
                tr("help.share.workflow.2"),
                tr("help.share.workflow.3"),
                tr("help.share.workflow.4"),
                tr("help.share.workflow.5"),
                tr("help.share.workflow.6"),
                tr("help.share.workflow.7"),
            ),
            actions = listOf(
                tr("help.share.action.1"),
                tr("help.share.action.2"),
                tr("help.share.action.3"),
                tr("help.share.action.4"),
                tr("help.share.action.5"),
                tr("help.share.action.6"),
            ),
            goodToKnow = listOf(
                tr("help.share.note.1"),
                tr("help.share.note.2"),
                tr("help.share.note.3"),
                tr("help.share.note.4"),
                tr("help.share.note.5"),
                tr("help.share.note.6"),
                tr("help.share.note.7"),
                tr("help.share.note.8"),
                tr("help.share.note.9"),
            ),
            shortcuts = listOf(
                HelpShortcut(AppShortcuts.LEFT, tr("help.share.shortcut.left")),
                HelpShortcut(AppShortcuts.RIGHT, tr("help.share.shortcut.right")),
                HelpShortcut(AppShortcuts.HELP, tr("help.share.shortcut.help")),
            ),
        ),
    )

    fun content(page: HelpPage): HelpContent = pages.first { it.page == page }
}
