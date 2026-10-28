package org.litvin.ui.tabs.share

import org.litvin.ui.commons.ErrorTexts
import com.formdev.flatlaf.util.SystemFileChooser
import io.github.oshai.kotlinlogging.KotlinLogging
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ApplicationLayout
import org.litvin.FFmpegCommandBuilder
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.ExportSourceProbe
import org.litvin.media.ImageOverlay
import org.litvin.media.PlayerStatus
import org.litvin.media.ReplayView
import org.litvin.media.SwingMediaPlayer
import org.litvin.media.mpv.MpvFrameReader
import org.litvin.share.ColorRole
import org.litvin.share.FrameView
import org.litvin.share.SavedThumbnailV1
import org.litvin.share.ShareDefaults
import org.litvin.share.ShareDefaultsV1
import org.litvin.share.ShareIO
import org.litvin.share.SharePoint
import org.litvin.share.ShareProject
import org.litvin.share.ShareSettingsV1
import org.litvin.share.StyleGroup
import org.litvin.share.StyleLookV1
import org.litvin.share.TextPlace
import org.litvin.share.ThumbnailFace
import org.litvin.share.ThumbnailField
import org.litvin.share.ThumbnailLayout
import org.litvin.share.ThumbnailLogo
import org.litvin.share.ThumbnailLogos
import org.litvin.share.ThumbnailLooks
import org.litvin.share.ThumbnailPng
import org.litvin.share.ThumbnailRenderer
import org.litvin.share.ThumbnailScene
import org.litvin.share.ThumbnailStyle
import org.litvin.share.ThumbnailVideoType
import org.litvin.ui.commons.ColorPickerDialog
import org.litvin.ui.commons.DefaultFillSliderUI
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.RowIconButton
import org.litvin.ui.commons.SegmentedChoice
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.SwitchBox
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.UserDialogService
import org.litvin.ui.commons.KeyFrame
import org.litvin.ui.commons.KeyFramesRequest
import org.litvin.ui.commons.VideoFrameLoader
import org.litvin.ui.commons.VideoFrameRequest
import org.litvin.ui.commons.WrapText
import org.litvin.ui.commons.applyDarkScrollbar
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import org.litvin.i18n.tr
import org.litvin.i18n.DisplayFormats
import org.litvin.i18n.MachineFormats

/** The facts of the source video that the Share tab needs: the size for the zoom limit, and the frame rate for a frame step. */
data class ShareSourceFacts(val width: Int, val height: Int, val framesPerSecond: Double?)

/**
 * The Share tab (B-46): a YouTube thumbnail for the open project.
 *
 * The left side shows the preview, the point and the moment of the frame, the zoom, and the layout styles.
 * The right side has two tabs, Texts and Colors, and the Save block. The layout comes from design/share-redesign/final.html.
 * The project stores the settings in share.json. The last style, its colors, and its text position go to the next project.
 */
class SwingSharePanel(
    private val dialogs: UserDialogService,
    private val defaults: ShareDefaults,
    /** The folder of the last export, or null. The PNG goes to this folder, else to the project folder. */
    private val exportFolder: () -> File? = { null },
    /** Opens the Points tab, where the user marks the points. */
    private val onOpenPoints: () -> Unit = {},
    /**
     * Creates the video player of the large preview, as on the Points tab. A click on the strip then shows the frame at once.
     * Without the player, the large preview shows the frame image of [frameLoader].
     */
    private val createPlayer: (() -> SwingMediaPlayer)? = null,
    // The frame image of the small previews and the PNG. The hidden mpv player keeps the video open, so the image loads quickly.
    private val frameLoader: VideoFrameLoader = VideoFrameLoader(cacheSize = 6, extract = VideoFrameLoader.mpvOrFfmpeg()),
    private val stripLoader: VideoFrameLoader = VideoFrameLoader(cacheSize = 12),
    private val probeSource: (String) -> ShareSourceFacts? = ::probeShareSource,
    private val now: () -> Long = System::currentTimeMillis,
    /** The folder of the PNG copy of the logo (B-53). */
    private val logoFolder: () -> File = { File(ApplicationLayout.current().appDataDirectory, "share") },
    /** Asks the user for a logo file. Null: the user closed the dialog. */
    private val chooseLogo: (JComponent) -> File? = ::chooseLogoFile,
) : JPanel(BorderLayout()) {
    private val logger = KotlinLogging.logger {}

    private var projectDir: String? = null
    private var manifestPath: String? = null
    private var project: ShareProject? = null
    private var settings = ShareSettingsV1()
    private var adjustments = AdjustmentsV1()
    private var shareDefaults = ShareDefaultsV1()
    private var facts: ShareSourceFacts? = null
    private var factsFor: String? = null

    /** The frame waits for the probe, because the size of the frame depends on the size of the video. */
    private var probing = false

    /** The frame of the preview, the request that the preview shows or waits for, and a failed read. */
    private var frame: BufferedImage? = null
    private var frameRequest: VideoFrameRequest? = null
    private var frameLoading = false
    private var frameFailed = false
    private var stripFor: Pair<String, Int>? = null


    /** Only for the point list. The project does not store it. */
    private var favoritesOnly = false
    private var sideTab = SIDE_TEXTS
    private var updating = false

    /**
     * The video player of the large preview, the source in it, and its last exact seek.
     * [playerReady] is true when the player shows a frame of [playerSource]. Until then, the preview shows the frame image.
     * [playerFailed] is the source that the player cannot play. The preview then shows the frame image.
     */
    private var player: SwingMediaPlayer? = null
    private var playerPan: FramePan? = null
    private var playerSource: String? = null
    private var playerSeekMs: Long? = null

    /** When the last seek went to the player, while it decodes its frame, and the seek that waits for it. */
    private var seekSentAtMs: Long? = null
    private var queuedSeekMs: Long? = null
    private var playerReady = false
    private var playerFailed: String? = null
    private var overlayScene: ThumbnailScene? = null
    private var active = false

    /** The frame image loads a little after the live frame, so that the two decoders do not slow the player. */
    private val frameTimer = Timer(LIVE_FRAME_DELAY_MS) {
        val project = project
        val point = project?.let(::currentPoint)
        if (project != null && point != null) loadFrame(project, point)
    }.apply { isRepeats = false }

    private val probeExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "share-probe").apply { isDaemon = true } }
    private val saveTimer = Timer(SAVE_DELAY_MS) { writeSettings() }.apply { isRepeats = false }

    // ---------- Components ----------
    private val sizeNote = ShareUi.label("1280 × 720 PNG", 12f, Palette.FG_3)
    private val preview = ThumbnailView("share-preview")
    private val previewCards = CardLayout()
    private val previewHost = JPanel(previewCards).apply {
        name = "share-preview-host"
        isOpaque = false
        add(preview, PREVIEW_IMAGE)
    }
    private val prevPoint = RowIconButton(Material2AL.CHEVRON_LEFT, tr("share.prevPoint")).apply { name = "share-prev-point" }
    private val nextPoint = RowIconButton(Material2AL.CHEVRON_RIGHT, tr("share.nextPoint")).apply { name = "share-next-point" }
    private val favoriteIcon = UiKit.icon(Material2MZ.STAR, 14, Palette.YELLOW)
    private val pointCombo = JComboBox<SharePoint>().apply {
        name = "share-point"
        DialogKit.styleCombo(this)
        preferredSize = Dimension(200, 30)
        maximumRowCount = 16
        // The video of the large preview is a native window. A Swing popup cannot show over it.
        isLightWeightPopupEnabled = false
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component {
                val point = value as? SharePoint
                return super.getListCellRendererComponent(list, point?.label.orEmpty(), index, selected, focus).also {
                    icon = if (point?.favorite == true) favoriteIcon else null
                    iconTextGap = 6
                }
            }
        }
    }
    private val favoritesSwitch = SwitchBox(tr("export.content.favorites"), 12.5f).apply { name = "share-favorites-only" }
    private val strip = MomentStrip(::seekStrip)
    private val framePrev = RowIconButton(Material2AL.KEYBOARD_ARROW_LEFT, tr("share.frameBack")).apply { name = "share-frame-prev" }
    private val frameNext = RowIconButton(Material2AL.KEYBOARD_ARROW_RIGHT, tr("share.frameForward")).apply { name = "share-frame-next" }
    private val momentTime = ShareUi.label("", 12f, Palette.FG_2).apply {
        name = "share-moment-time"
        horizontalAlignment = SwingConstants.CENTER
    }
    private val zoom = DefaultFillSliderUI.slider(100, 200, 100).apply {
        name = "share-zoom"
        getAccessibleContext().accessibleName = tr("adjust.zoom")
    }
    private val zoomHint = ShareUi.hint()
    private val zoomValue = ShareUi.label(DisplayFormats.percent(100), 12f, Palette.FG_2).apply { horizontalAlignment = SwingConstants.RIGHT }
    private val pickNote = NoteBox("share-pick-note", Material2AL.INFO)
    private val placeHint = ShareUi.hint()
    private val placeChoice = SegmentedChoice<TextPlace>("share-place", emptyList())
    private val styleCards = ThumbnailStyle.entries.associateWith { style -> StyleCard("share-style-${style.key}", style.title) }

    /** The group of style cards that shows. Null: the group of the selected style. The project does not store it. */
    private var shownGroup: StyleGroup? = null
    private val groupChoice = SegmentedChoice("share-style-group", StyleGroup.entries.map { SegmentedChoice.Option(it, it.title) })
    private var leftColumn: JComponent? = null

    /** The logo of the user (B-53), and the file that it comes from. The logo is the same for all projects. */
    private var logo: ThumbnailLogo? = null
    private var logoFile: String? = null

    private val texts = ShareTextsPanel(
        onType = ::selectType,
        onText = ::editText,
        onHead = ::selectHead,
        onUsePresets = { change { it.copy(texts = emptyMap(), finalScore = false) } },
        onChooseLogo = ::addLogo,
        onRemoveLogo = ::removeLogo,
        onBrandFont = { face -> changeBrand { it.copy(brandFont = face?.name) } },
        onBrandColor = { value -> changeBrand { it.copy(brandColor = ThumbnailLooks.parseHex(value)?.let(ThumbnailLooks::hex)) } },
        onPickBrandColor = ::pickBrandColor,
    )
    private val colors = ShareColorsPanel(
        onSet = { id -> changeLook { look -> StyleLookV1(place = look.place, colorSet = id) } },
        onRole = { role, value -> changeLook { look -> look.copy(colors = look.colors + (role.key to value)) } },
        onStrength = { value, adjusting -> changeLook(save = !adjusting) { look -> look.copy(shadeStrength = value) } },
        onPickColor = ::pickColor,
        onResetColors = { changeLook { look -> look.withoutColors() } },
    )
    private val sideTabs = SideTabs(::selectSideTab)
    private val sideCards = JPanel(CardLayout())
    private val mini = ThumbnailView("share-mini", radius = 6)
    /** The line of the current video type, and the line of the other type. */
    private val currentLine = FileLine("share-file-current")
    private val otherLine = FileLine("share-file-other")
    private val saveButton = UiButton(tr("share.save"), Material2MZ.SAVE, UiButton.Kind.LIME).apply {
        name = "share-save"
        stretch = true
    }
    private val openButton = UiButton(tr("export.openFolder"), Material2AL.FOLDER_OPEN, UiButton.Kind.GHOST).apply {
        name = "share-open-folder"
        toolTipText = tr("export.openFolder")
    }

    private val cards = CardLayout()
    private val body = JPanel(cards)
    private val emptyView = ShareEmptyView { onOpenPoints() }

    init {
        name = "share-panel"
        background = Palette.BG
        body.background = Palette.BG
        body.add(shareView(), CARD_SHARE)
        body.add(emptyView, CARD_EMPTY)
        add(body, BorderLayout.CENTER)
        showEmpty(tr("stats.empty.noProject"), tr("stats.empty.noProject.text"), withButton = false)

        prevPoint.addActionListener { stepPoint(-1) }
        nextPoint.addActionListener { stepPoint(1) }
        pointCombo.addActionListener {
            if (updating) return@addActionListener
            (pointCombo.selectedItem as? SharePoint)?.let { point -> change { it.copy(pointId = point.id, momentMs = null) } }
        }
        favoritesSwitch.addActionListener {
            favoritesOnly = favoritesSwitch.isSelected
            val project = project ?: return@addActionListener
            val point = currentPoint(project)
            if (favoritesOnly && point != null && !point.favorite) {
                project.points.firstOrNull { it.favorite }?.let { first -> change { it.copy(pointId = first.id, momentMs = null) } }
            } else {
                render()
            }
        }
        framePrev.addActionListener { stepFrame(-1) }
        frameNext.addActionListener { stepFrame(1) }
        zoom.addChangeListener {
            if (updating) return@addChangeListener
            change(save = !zoom.valueIsAdjusting) { it.copy(zoom = zoom.value / 100.0) }
        }
        preview.onPan = { view, done -> change(save = done) { it.copy(panX = view.panX, panY = view.panY) } }
        placeChoice.onChange { place -> changeLook { look -> look.copy(place = place.key) } }
        groupChoice.onChange { group ->
            if (updating) return@onChange
            shownGroup = group
            leftColumn?.revalidate()
            render()
        }
        styleCards.forEach { (style, card) ->
            card.addActionListener {
                change { it.copy(style = style.key) }
                rememberLooks()
            }
        }
        saveButton.addActionListener { saveThumbnail() }
        openButton.addActionListener { openFolder() }

        // The arrow keys move one frame. A text field or a slider with the focus uses the keys first.
        for ((key, step) in listOf(KeyEvent.VK_LEFT to -1, KeyEvent.VK_RIGHT to 1)) {
            val action = "share-frame-$step"
            getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), action)
            actionMap.put(action, object : AbstractAction() {
                override fun actionPerformed(e: ActionEvent?) {
                    if (isShowing && project != null) stepFrame(step)
                }
            })
        }
    }

    // ---------- Project ----------

    fun setProjectManifest(path: String?) {
        writeSettings()
        manifestPath = path
        projectDir = path?.let { File(it).parentFile.absolutePath }
        project = null
        // The frame of another project is not correct for this project.
        frame = null
        frameRequest = null
        frameFailed = false
        stripFor = null
        strip.cells = List(MomentStrip.CELLS) { null }
        playerSource = null
        playerSeekMs = null
        seekSentAtMs = null
        queuedSeekMs = null
        playerReady = false
        playerFailed = null
        showPreviewCard()
        favoritesOnly = false
        facts = null
        factsFor = null
        probing = false
        shownGroup = null
    }

    /** Reads the project again: the points, the scoring, and the adjustments can change in the other tabs. */
    fun onActivated() {
        active = true
        player?.activatePreview("share activated")
        val dir = projectDir
        val manifest = manifestPath
        if (dir == null || manifest == null) return showEmpty(tr("stats.empty.noProject"), tr("stats.empty.noProject.text"), withButton = false)
        val loaded = try {
            shareDefaults = defaults.load()
            loadLogo()
            adjustments = AdjustmentsIO.readForProjectDir(dir)
            ShareProject.load(dir, manifest)
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot read the project for the Thumbnail tab: $dir" }
            return showEmpty(tr("share.error.read"), ErrorTexts.of(failure), withButton = false)
        }
        if (project == null) settings = readSettings(dir)
        project = loaded
        if (loaded.points.isEmpty()) {
            return showEmpty(tr("export.noPoints"), tr("share.empty.noPoints.text"), withButton = true)
        }
        cards.show(body, CARD_SHARE)
        probeIfNeeded(loaded)
        render()
    }

    fun onDeactivated() {
        active = false
        writeSettings()
        frameTimer.stop()
        // The player releases the decoder. The Points tab needs it.
        player?.let {
            it.pause()
            it.deactivatePreview("share deactivated")
        }
        // A seek that waits is lost. The next activation seeks again.
        playerSeekMs = null
        seekSentAtMs = null
        queuedSeekMs = null
        playerReady = false
        showPreviewCard()
    }

    fun close() {
        writeSettings()
        frameTimer.stop()
        probeExecutor.shutdownNow()
        player?.close()
        player = null
    }

    private fun readSettings(dir: String): ShareSettingsV1 = try {
        ShareIO.readForProjectDir(dir)
    } catch (failure: Exception) {
        logger.warn(failure) { "Cannot read share.json in $dir" }
        null
    } ?: ShareSettingsV1(style = shareDefaults.style, looks = shareDefaults.looks)

    private fun showEmpty(title: String, text: String, withButton: Boolean) {
        emptyView.show(title, text, withButton)
        cards.show(body, CARD_EMPTY)
    }

    /** Reads the size and the frame rate of the video one time for each project. The zoom limit needs the size. */
    private fun probeIfNeeded(project: ShareProject) {
        val source = project.sourcePath?.takeIf { File(it).isFile } ?: return
        if (factsFor == source) return
        factsFor = source
        probing = true
        probeExecutor.execute {
            val result = runCatching { probeSource(source) }.getOrNull()
            SwingUtilities.invokeLater {
                if (factsFor == source) {
                    facts = result
                    probing = false
                    render()
                }
            }
        }
    }

    // ---------- Derived values ----------

    private fun type(project: ShareProject) = ThumbnailVideoType.byKey(settings.videoType) ?: project.defaultVideoType

    private fun currentPoint(project: ShareProject): SharePoint? = project.point(settings.pointId) ?: project.defaultPoint

    private fun momentMs(point: SharePoint): Long = settings.momentMs?.takeIf { it in point.startMs..point.endMs } ?: point.middleMs

    private fun style() = ThumbnailStyle.byKey(settings.style) ?: ThumbnailStyle.WORDS

    private fun look(style: ThumbnailStyle): StyleLookV1? = settings.looks[style.key]

    private fun presets(project: ShareProject) = project.presets(type(project), shareDefaults.event, shareDefaults.club)

    /** The group of the style cards: the group that the user selected, else the group of the selected style. */
    private fun group() = shownGroup ?: style().group

    /** The value of a field: the edited text, the final score, or the preset. The brand name is the same in all projects. */
    private fun value(project: ShareProject, field: ThumbnailField): String {
        if (field == ThumbnailField.CLUB) return presets(project)[field].orEmpty()
        settings.texts[field.key]?.let { return it }
        if (field == ThumbnailField.HEAD && headIsScore(project)) return project.finalScore.orEmpty()
        return presets(project)[field].orEmpty()
    }

    private fun headIsScore(project: ShareProject) = settings.finalScore && project.finalScore != null && ThumbnailField.HEAD.key !in settings.texts

    private fun scene(project: ShareProject, style: ThumbnailStyle) = ThumbnailScene(
        style = style,
        place = ThumbnailLooks.place(style, look(style)),
        texts = ThumbnailField.entries.associateWith { value(project, it) },
        colors = ThumbnailLooks.colors(style, look(style)),
        logo = logo.takeIf { style.showsLogo },
        brandFace = ThumbnailFace.byKey(shareDefaults.brandFont),
        brandRgb = ThumbnailLooks.parseHex(shareDefaults.brandColor),
    )

    /** The zoom stops when the frame has less than 1280 px of the video: 150 % for a 1080p video, 200 % for 4K. */
    private fun maxZoom(): Double {
        val width = facts?.width ?: return 1.0
        return (floor(width / ThumbnailRenderer.WIDTH.toDouble() * 20) / 20).coerceIn(1.0, MAX_ZOOM)
    }

    private fun view() = FrameView(settings.zoom.coerceIn(1.0, maxZoom()), settings.panX.coerceIn(0.0, 1.0), settings.panY.coerceIn(0.0, 1.0))

    private fun request(project: ShareProject, point: SharePoint): VideoFrameRequest? {
        val source = project.sourcePath?.takeIf { File(it).isFile } ?: return null
        // The zoom needs the frame at the resolution of the video, up to two times the width of the PNG.
        val width = ((ThumbnailRenderer.WIDTH * maxZoom()).roundToInt() / 2) * 2
        return VideoFrameRequest(source, momentMs(point), adjustments, width)
    }

    // ---------- Changes ----------

    /** Changes the settings, shows the change, and saves share.json a little later ([save] false: only show it). */
    private fun change(save: Boolean = true, update: (ShareSettingsV1) -> ShareSettingsV1) {
        settings = update(settings)
        render()
        if (save) saveTimer.restart()
    }

    private fun changeLook(save: Boolean = true, update: (StyleLookV1) -> StyleLookV1) {
        val style = style()
        change(save) { it.copy(looks = it.looks + (style.key to update(it.looks[style.key] ?: StyleLookV1()))) }
        if (save) rememberLooks()
    }

    /** The last style, its colors, and its text position go to the next project. */
    private fun rememberLooks() {
        shareDefaults = shareDefaults.copy(style = style().key, looks = settings.looks)
        runCatching { defaults.save(shareDefaults) }.onFailure { logger.warn(it) { "Cannot save the thumbnail defaults" } }
    }

    /** The video type sets the large words again. */
    private fun selectType(type: ThumbnailVideoType) =
        change { it.copy(videoType = type.key, texts = it.texts - ThumbnailField.HEAD.key, finalScore = false) }

    private fun selectHead(finalScore: Boolean) =
        change { it.copy(texts = it.texts - ThumbnailField.HEAD.key, finalScore = finalScore) }

    /** A text that is the same as its preset is not an edit, so that the field follows its preset again. */
    private fun editText(field: ThumbnailField, text: String) {
        val project = project ?: return
        // The brand name goes to all projects at once, as the logo.
        if (field == ThumbnailField.CLUB) return changeBrand { it.copy(club = text.ifBlank { null }) }
        val preset = presets(project)[field].orEmpty()
        change {
            when {
                field == ThumbnailField.HEAD && text == project.finalScore -> it.copy(texts = it.texts - field.key, finalScore = true)
                field == ThumbnailField.HEAD && text == preset -> it.copy(texts = it.texts - field.key, finalScore = false)
                text == preset -> it.copy(texts = it.texts - field.key)
                else -> it.copy(texts = it.texts + (field.key to text))
            }
        }
    }

    // ---------- Logo ----------

    /** Reads the stored logo when its file changed, for example after a change in another window. */
    private fun loadLogo() {
        val path = shareDefaults.logo
        if (path == logoFile && (path == null || logo != null)) return
        logoFile = path
        logo = ThumbnailLogos.load(path?.let(::File))
        if (path != null && logo == null) logger.warn { "Cannot read the thumbnail logo $path" }
    }

    /** Reads a logo file, keeps a PNG copy of it, and shows it on the styles with a logo. */
    private fun addLogo() {
        val source = chooseLogo(this) ?: return
        val stored = try {
            cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
            ThumbnailLogos.store(source, logoFolder(), runCatching { ApplicationLayout.current().ffmpegExecutable }.getOrNull())
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot read the logo $source" }
            dialogs.showError(this, tr("share.logo.error.text", "name" to source.name), tr("share.logo.error"))
            return
        } finally {
            cursor = Cursor.getDefaultCursor()
        }
        shareDefaults = shareDefaults.copy(logo = stored.absolutePath)
        // The file name stays the same, so the logo must load again.
        logoFile = null
        loadLogo()
        runCatching { defaults.save(shareDefaults) }.onFailure { logger.warn(it) { "Cannot save the thumbnail defaults" } }
        render()
    }

    /** The font and the color of the brand name go to all styles and to the next project, as the logo. */
    private fun changeBrand(update: (ShareDefaultsV1) -> ShareDefaultsV1) {
        shareDefaults = update(shareDefaults)
        runCatching { defaults.save(shareDefaults) }.onFailure { logger.warn(it) { "Cannot save the thumbnail defaults" } }
        render()
    }

    private fun pickBrandColor(current: java.awt.Color) {
        val picked = ColorPickerDialog.pick(this, tr("share.brand.color.pick"), current) ?: return
        changeBrand { it.copy(brandColor = ThumbnailLooks.hex(picked.rgb)) }
    }

    private fun removeLogo() {
        shareDefaults = shareDefaults.copy(logo = null)
        loadLogo()
        runCatching { defaults.save(shareDefaults) }.onFailure { logger.warn(it) { "Cannot save the thumbnail defaults" } }
        render()
    }

    private fun pickColor(role: ColorRole, current: java.awt.Color) {
        val picked = ColorPickerDialog.pick(this, tr("scoreSettings.sideColor", "side" to role.title), current) ?: return
        changeLook { look -> look.copy(colors = look.colors + (role.key to ThumbnailLooks.hex(picked.rgb))) }
    }

    private fun visiblePoints(project: ShareProject): List<SharePoint> =
        if (favoritesOnly && project.hasFavorites) project.points.filter { it.favorite } else project.points

    private fun stepPoint(delta: Int) {
        val project = project ?: return
        val list = visiblePoints(project)
        val at = list.indexOf(currentPoint(project)).coerceAtLeast(0)
        val next = list.getOrNull((at + delta).coerceIn(0, list.lastIndex)) ?: return
        change { it.copy(pointId = next.id, momentMs = null) }
    }

    private fun frameStepMs(): Long = (1000.0 / (facts?.framesPerSecond?.takeIf { it > 0 } ?: DEFAULT_FPS)).roundToLong().coerceAtLeast(1)

    private fun stepFrame(delta: Int) {
        val project = project ?: return
        val point = currentPoint(project) ?: return
        val moment = (momentMs(point) + delta * frameStepMs()).coerceIn(point.startMs, point.endMs)
        change { it.copy(pointId = point.id, momentMs = moment) }
    }

    /**
     * The video player shows the exact frame for a click and during a drag. Key frames during a drag look confusing,
     * because they jump between the moments. The frame image loads only for the release.
     */
    private fun seekStrip(position: Double, step: StripSeek) {
        val project = project ?: return
        val point = currentPoint(project) ?: return
        val moment = point.startMs + (point.lengthMs * position).roundToLong()
        if (step == StripSeek.RELEASE) return change { it.copy(pointId = point.id, momentMs = moment) }
        strip.position = position
        momentTime.text = momentText(point, moment)
        if (playerReady && liveSource(project) == playerSource) seekPlayer(liveSeekMs(moment))
    }

    private fun selectSideTab(tab: String) {
        sideTab = tab
        render()
    }

    // ---------- Render ----------

    private fun render() {
        val project = project ?: return
        val point = currentPoint(project) ?: return
        updating = true
        try {
            renderPick(project, point)
            renderThumbnails(project)
            renderStyleHead()
            texts.show(textsState(project))
            colors.show(style(), look(style()))
            renderSave(project)
            sideTabs.selected = sideTab
            (sideCards.layout as CardLayout).show(sideCards, sideTab)
        } finally {
            updating = false
        }
        renderPreview(project, point)
        loadStrip(project, point)
        revalidate()
        repaint()
    }

    private fun renderPick(project: ShareProject, point: SharePoint) {
        favoritesSwitch.isEnabled = project.hasFavorites
        if (!project.hasFavorites) favoritesOnly = false
        favoritesSwitch.isSelected = favoritesOnly
        val list = visiblePoints(project).let { if (point in it) it else (it + point).sortedBy { p -> p.startMs } }
        val model = (0 until pointCombo.itemCount).map { pointCombo.getItemAt(it) }
        if (model != list) {
            pointCombo.removeAllItems()
            list.forEach(pointCombo::addItem)
        }
        pointCombo.selectedItem = point
        val at = list.indexOf(point)
        prevPoint.isEnabled = at > 0
        nextPoint.isEnabled = at < list.lastIndex

        val moment = momentMs(point)
        strip.position = if (point.lengthMs > 0) (moment - point.startMs).toDouble() / point.lengthMs else 0.5
        momentTime.text = momentText(point, moment)

        val maxZoom = maxZoom()
        zoom.maximum = (maxZoom * 100).roundToInt()
        zoom.isEnabled = maxZoom > 1.0
        if (!zoom.valueIsAdjusting) zoom.value = (view().zoom * 100).roundToInt()
        zoomValue.text = DisplayFormats.percent((view().zoom * 100).roundToInt())
        zoomHint.text = when {
            facts == null -> ""
            maxZoom < MAX_ZOOM -> tr("share.zoom.max", "percent" to (maxZoom * 100).roundToInt(), "height" to facts?.height.toString())
            else -> ""
        }
        zoomHint.toolTipText = zoomHint.text.ifEmpty { null }
        sizeNote.text = if (view().zoom > 1.0) tr("share.zoom.drag") + " · " + IMAGE_FORMAT else IMAGE_FORMAT
        pickNote.text = if (project.hasFavorites) "" else tr("share.pick.noFavorites")
    }

    private fun momentText(point: SharePoint, moment: Long) =
        tr(
            "share.moment",
            "time" to ShareProject.clockTenths(moment - point.startMs),
            "length" to ShareProject.clockTenths(point.lengthMs),
        )

    private fun renderThumbnails(project: ShareProject) {
        val view = view()
        val message = if (frameFailed && !frameLoading) FRAME_ERROR else null
        val style = style()
        // Only the cards of the shown group need a layout.
        val group = group()
        val layouts = ThumbnailStyle.entries.filter { it == style || it.group == group }.associateWith { ThumbnailRenderer.layout(scene(project, it)) }
        fun ThumbnailPainter.show(layout: ThumbnailLayout, badge: String?) {
            this.frame = this@SwingSharePanel.frame
            this.view = view
            this.layout = layout
            this.badge = badge
            this.message = message
        }
        preview.painter.show(layouts.getValue(style), null)
        preview.updateCursor()
        mini.painter.show(layouts.getValue(style), project.videoLength(type(project)))
        styleCards.forEach { (cardStyle, card) ->
            card.isVisible = cardStyle.group == group
            val layout = layouts[cardStyle]?.takeIf { card.isVisible } ?: return@forEach
            card.painter.show(layout, null)
            card.selectedStyle = cardStyle == style
            card.repaint()
        }
        preview.repaint()
        mini.repaint()
    }

    private fun renderStyleHead() {
        val style = style()
        val places = style.places
        placeChoice.setOptions(places.map { SegmentedChoice.Option(it, it.title) })
        placeChoice.selected = ThumbnailLooks.place(style, look(style))
        placeChoice.isVisible = places.size > 1
        placeHint.text = if (places.size > 1) tr("share.place") else tr("share.place.one")
        if (groupChoice.selected != group()) {
            groupChoice.selected = group()
            leftColumn?.revalidate()
        }
    }

    private fun textsState(project: ShareProject): TextsState {
        val presets = presets(project)
        val used = style().fields
        val fields = ThumbnailField.entries.associateWith { field ->
            val value = value(project, field)
            val choices = listOfNotNull(presets[field], project.finalScore.takeIf { field == ThumbnailField.HEAD })
            val edited = field != ThumbnailField.CLUB && field.key in settings.texts && settings.texts[field.key] !in choices
            FieldState(value, field.source, edited, field in used)
        }
        return TextsState(
            type = type(project),
            fields = fields,
            typeWords = type(project).words,
            finalScore = project.finalScore,
            headIsScore = headIsScore(project),
            anyEdited = fields.values.any { it.edited },
            logo = logo?.image,
            showsLogo = style().showsLogo,
            brandFont = ThumbnailFace.byKey(shareDefaults.brandFont),
            brandColor = shareDefaults.brandColor ?: ThumbnailLooks.AUTO,
        )
    }

    private fun renderSave(project: ShareProject) {
        val type = type(project)
        val folder = folder(project)
        val signature = signature(project)
        val ordered = listOf(type) + ThumbnailVideoType.entries.filter { it != type }
        listOf(currentLine, otherLine).zip(ordered).forEach { (line, lineType) ->
            val saved = settings.saved[lineType.key]?.takeIf { File(it.path).isFile }
            val current = lineType == type
            val file = saved?.let { File(it.path) } ?: project.thumbnailFile(folder, lineType)
            val tooltip = tr("share.file.tooltip", "name" to file.name, "folder" to (file.parentFile?.absolutePath ?: tr("share.file.exportFolder")))
            when {
                saved == null -> line.show("${lineType.title} · ${tr("share.file.notSaved")}", Material2MZ.RADIO_BUTTON_UNCHECKED, Palette.FG_3, current, tooltip)
                current && saved.signature != signature ->
                    line.show(
                        "${lineType.title} · ${tr("share.file.changed", "time" to savedTime(saved.savedAtMs))}",
                        Material2MZ.SCHEDULE, Palette.YELLOW, current, tooltip,
                    )
                else -> line.show("${lineType.title} · ${savedTime(saved.savedAtMs)}", Material2AL.CHECK_CIRCLE, Palette.LIME, current, tooltip)
            }
        }
        saveButton.text = if (project.thumbnailFile(folder, type).isFile) tr("share.replace") else tr("share.save")
        saveButton.isEnabled = frame != null && !frameLoading && !frameFailed
        saveButton.toolTipText = project.thumbnailFile(folder, type).absolutePath
    }

    private fun savedTime(ms: Long): String {
        val time = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val today = Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate()
        val clock = DisplayFormats.time(time)
        return when (time.toLocalDate()) {
            today -> tr("share.time.today", "time" to clock)
            today.minusDays(1) -> tr("share.time.yesterday", "time" to clock)
            else -> "${DisplayFormats.date(time.toLocalDate())} $clock"
        }
    }

    // ---------- Video player ----------

    /**
     * The source for the video player, or null when the large preview shows the frame image:
     * without a player, before the probe, after a player error, and for a video that is not 16:9.
     * The frame image covers the 16:9 preview. The player shows the whole frame, so the aspect must be the same.
     */
    private fun liveSource(project: ShareProject): String? {
        if (createPlayer == null || probing) return null
        val source = project.sourcePath?.takeIf { File(it).isFile } ?: return null
        val facts = facts ?: return null
        if (abs(facts.width * 9 - facts.height * 16) > facts.height * 16 / 100) return null
        return source.takeIf { it != playerFailed }
    }

    private fun renderPreview(project: ShareProject, point: SharePoint) {
        val source = liveSource(project)
        if (source == null || !active) {
            showPreviewCard()
            loadFrame(project, point)
            return
        }
        val player = player ?: createLivePlayer() ?: return loadFrame(project, point)
        if (source != playerSource) {
            playerSource = source
            playerSeekMs = null
            seekSentAtMs = null
            queuedSeekMs = null
            playerReady = false
            player.load(File(source))
        }
        player.applyPreviewAdjustments(adjustments)
        player.setReplayView(replayView(view()))
        seekPlayer(liveSeekMs(momentMs(point)))
        val scene = scene(project, style())
        if (scene != overlayScene) {
            overlayScene = scene
            player.setImageOverlay(layoutOverlay(ThumbnailRenderer.layout(scene)))
        }
        playerPan?.updateCursor()
        showPreviewCard()
        scheduleFrame(project, point)
    }

    /** The exact seek of the player for [moment]: the same frame as the frame image, the first frame at or after the still-frame time. */
    private fun liveSeekMs(moment: Long): Long = FFmpegCommandBuilder.stillFrameSeekMs(moment) + MpvFrameReader.HR_SEEK_TOLERANCE_MS

    /**
     * Sends an exact seek to the player. While the player decodes the frame of a seek, the next seek waits,
     * and only the newest one stays. Without this, each seek of a drag stops the seek before it, and the player shows no frame.
     */
    private fun seekPlayer(seekMs: Long) {
        if (seekMs == playerSeekMs) return
        playerSeekMs = seekMs
        val sentAt = seekSentAtMs
        if (sentAt != null && System.currentTimeMillis() - sentAt < SEEK_WAIT_MS) {
            queuedSeekMs = seekMs
            return
        }
        queuedSeekMs = null
        seekSentAtMs = System.currentTimeMillis()
        player?.seek(seekMs)
    }

    /** The player shows the frame of the last seek. The waiting seek can go now. */
    private fun onPlayerFrame() {
        seekSentAtMs = null
        val queued = queuedSeekMs ?: return
        queuedSeekMs = null
        seekSentAtMs = System.currentTimeMillis()
        player?.seek(queued)
    }

    private fun createLivePlayer(): SwingMediaPlayer? {
        val created = try {
            createPlayer?.invoke()
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot create the video player of the Thumbnail tab" }
            null
        } ?: return null
        created.onStatusChanged = { status ->
            if (status == PlayerStatus.ERROR) {
                SwingUtilities.invokeLater {
                    logger.warn { "The video player of the Thumbnail tab cannot play $playerSource. The preview shows the frame image." }
                    playerFailed = playerSource
                    playerReady = false
                    render()
                }
            }
        }
        created.onPlaybackRestart = {
            SwingUtilities.invokeLater {
                onPlayerFrame()
                if (active && playerSource != null && !playerReady) {
                    playerReady = true
                    showPreviewCard()
                }
            }
        }
        previewHost.add(created.component, PREVIEW_PLAYER)
        playerPan = FramePan(created.component, { view() }) { view, done -> change(save = done) { it.copy(panX = view.panX, panY = view.panY) } }
        player = created
        return created
    }

    /** The player shows when it has a frame of the source. Before that, the frame image shows. */
    private fun showPreviewCard() {
        val live = player != null && playerReady && active && project?.let(::liveSource) == playerSource
        previewCards.show(previewHost, if (live) PREVIEW_PLAYER else PREVIEW_IMAGE)
    }

    /**
     * The frame image of the small previews and the PNG loads a little later. The Save button waits for it.
     * Many fast clicks load only the image of the last click.
     */
    private fun scheduleFrame(project: ShareProject, point: SharePoint) {
        val request = request(project, point)
        if (request == frameRequest) return
        if (request != null && frameLoader.cached(request) != null) return loadFrame(project, point)
        frameLoading = true
        renderSave(project)
        frameTimer.restart()
    }

    /** The zoom of the frame as the zoom frame of the player: the part of the frame that the thumbnail shows. */
    private fun replayView(view: FrameView): ReplayView? {
        if (view.zoom <= 1.0) return null
        val width = 1.0 / view.zoom
        return ReplayView(zoomX = (1.0 - width) * view.panX, zoomY = (1.0 - width) * view.panY, zoomWidth = width)
    }

    /** The texts and the shapes of the layout over the video, at the size of the player. */
    private fun layoutOverlay(layout: ThumbnailLayout) = ImageOverlay { width, height ->
        BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE).also { image ->
            val g2 = image.createGraphics()
            try {
                g2.scale(width / ThumbnailRenderer.WIDTH.toDouble(), height / ThumbnailRenderer.HEIGHT.toDouble())
                // The overlay has no frame. A style with a gray part of the frame (Duotone) shows a dark part there.
                ThumbnailRenderer.paintLayout(g2, layout)
            } finally {
                g2.dispose()
            }
        }
    }

    // ---------- Frames ----------

    private fun loadFrame(project: ShareProject, point: SharePoint) {
        if (probing) {
            frameLoading = true
            renderSave(project)
            return
        }
        val request = request(project, point)
        if (request == frameRequest) return
        frameRequest = request
        if (request == null) {
            frame = null
            frameLoading = false
            frameFailed = true
            renderThumbnails(project)
            renderSave(project)
            return
        }
        // The preview keeps the old frame until the new frame is ready, so that it does not flash.
        val cached = frameLoader.cached(request)
        if (cached != null) {
            frame = cached
            frameLoading = false
            frameFailed = false
            renderThumbnails(project)
            renderSave(project)
            return
        }
        frameLoading = true
        renderSave(project)
        // Only the last request is necessary. For example, the user can press the arrow key many times.
        frameLoader.cancelPending(stopRunning = true)
        frameLoader.load(request) { image ->
            if (frameRequest != request) return@load
            frameLoading = false
            frameFailed = image == null
            if (image != null) frame = image
            this.project?.let {
                renderThumbnails(it)
                renderSave(it)
            }
        }
    }

    private fun nearest(frames: List<KeyFrame>, atMs: Long): KeyFrame? = frames.minByOrNull { abs(it.atMs - atMs) }

    private fun cellMs(point: SharePoint, cell: Int) = point.startMs + (point.lengthMs * (cell + 0.5) / MomentStrip.CELLS).roundToLong()

    /** One ffmpeg process reads all key frames of the point. This is much faster than one process for each cell. */
    private fun loadStrip(project: ShareProject, point: SharePoint) {
        val source = project.sourcePath?.takeIf { File(it).isFile } ?: return
        val key = point.id to adjustments.hashCode()
        if (stripFor == key) return
        stripFor = key
        stripLoader.cancelPending(stopRunning = true)
        val request = KeyFramesRequest(source, point.startMs, point.endMs, adjustments, STRIP_FRAME_WIDTH)
        stripLoader.cachedKeyFrames(request)?.let { frames -> return showStrip(point, frames) }
        strip.cells = List(MomentStrip.CELLS) { null }
        stripLoader.loadKeyFrames(request) { frames ->
            if (stripFor != key) return@loadKeyFrames
            if (frames.isEmpty()) loadStripFrames(source, point, key) else showStrip(point, frames)
        }
    }

    /** Each cell shows the key frame nearest to the time of the cell. */
    private fun showStrip(point: SharePoint, frames: List<KeyFrame>) {
        strip.cells = (0 until MomentStrip.CELLS).map { cell -> nearest(frames, cellMs(point, cell))?.image }
    }

    /** Without key frames in the point, each cell reads its own frame. This is slower. */
    private fun loadStripFrames(source: String, point: SharePoint, key: Pair<String, Int>) {
        val requests = (0 until MomentStrip.CELLS).map { cell -> VideoFrameRequest(source, cellMs(point, cell), adjustments, STRIP_FRAME_WIDTH) }
        val cells = MutableList<Image?>(MomentStrip.CELLS) { cell -> stripLoader.cached(requests[cell]) }
        strip.cells = cells.toList()
        requests.forEachIndexed { cell, request ->
            if (cells[cell] != null) return@forEachIndexed
            stripLoader.load(request) { image ->
                if (stripFor != key || image == null) return@load
                cells[cell] = image
                strip.cells = cells.toList()
            }
        }
    }

    // ---------- Save ----------

    private fun folder(project: ShareProject): File =
        exportFolder()?.takeIf { it.isDirectory } ?: File(project.projectDir)

    /** Identifies the thumbnail: the frame, the zoom, and the texts and colors of the style. */
    private fun signature(project: ShareProject): String {
        val point = currentPoint(project)
        val text = listOf(project.sourcePath, point?.let(::momentMs), adjustments, view(), scene(project, style())).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
        return digest.take(12).joinToString("") { MachineFormats.format("%02x", it) }
    }

    private fun saveThumbnail() {
        val project = project ?: return
        val frame = frame ?: return
        val type = type(project)
        val file = project.thumbnailFile(folder(project), type)
        val view = view()
        val scene = scene(project, style())
        try {
            cursor = Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
            val bytes = ThumbnailPng.write(file) { softness -> ThumbnailRenderer.render(frame, view, scene, softness) }
            if (bytes > ThumbnailPng.MAX_BYTES) {
                dialogs.showWarning(
                    this,
                    tr("share.large.text", "size" to DisplayFormats.decimal(bytes / 1024.0 / 1024.0, 1)),
                    tr("share.large"),
                )
            }
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save the thumbnail $file" }
            dialogs.showError(this, ErrorTexts.of(failure), tr("share.error.save"))
            return
        } finally {
            cursor = Cursor.getDefaultCursor()
        }
        // The league goes to the next project ("From your last thumbnail"). The brand name is already there.
        shareDefaults = shareDefaults.copy(event = value(project, ThumbnailField.EVENT).trim().ifEmpty { null })
        runCatching { defaults.save(shareDefaults) }.onFailure { logger.warn(it) { "Cannot save the thumbnail defaults" } }
        change { it.copy(saved = it.saved + (type.key to SavedThumbnailV1(file.absolutePath, now(), signature(project)))) }
        writeSettings()
    }

    /** Opens the export folder. When the file exists, Explorer selects it. */
    private fun openFolder() {
        val project = project ?: return
        val type = type(project)
        val file = settings.saved[type.key]?.let { File(it.path) }?.takeIf { it.isFile } ?: project.thumbnailFile(folder(project), type)
        try {
            if (file.isFile && System.getProperty("os.name").orEmpty().startsWith("Windows")) {
                ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
            } else {
                java.awt.Desktop.getDesktop().open(file.absoluteFile.parentFile)
            }
        } catch (failure: Exception) {
            dialogs.showError(this, ErrorTexts.of(failure), tr("export.error.openFolder"))
        }
    }

    private fun writeSettings() {
        saveTimer.stop()
        val dir = projectDir ?: return
        if (project == null) return
        try {
            ShareIO.writeForProjectDir(dir, settings)
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save share.json in $dir" }
        }
    }

    // ---------- Layout ----------

    private fun shareView(): JComponent {
        val left = LeftColumn().also { leftColumn = it }
        val side = JPanel(BorderLayout()).apply {
            background = Palette.PANEL
            border = BorderFactory.createMatteBorder(0, 1, 0, 0, Palette.LINE)
            preferredSize = Dimension(SIDE_WIDTH, 0)
            add(sideTabs, BorderLayout.NORTH)
            sideCards.isOpaque = false
            sideCards.add(scroll(texts), SIDE_TEXTS)
            sideCards.add(scroll(colors), SIDE_COLORS)
            add(sideCards, BorderLayout.CENTER)
            add(SaveBlock(), BorderLayout.SOUTH)
        }
        return JPanel(BorderLayout()).apply {
            background = Palette.BG
            add(left, BorderLayout.CENTER)
            add(side, BorderLayout.EAST)
        }
    }

    private fun scroll(content: JComponent): JScrollPane = DialogKit.scrollBody(content, Palette.PANEL).apply {
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = 16
        runCatching { applyDarkScrollbar(this, Palette.PANEL) }
    }

    /**
     * The left side. It does not scroll: the preview becomes smaller in a low window, so that the style cards stay visible.
     * The controls and the style cards always use the full width. A smaller preview is in the center above them, so the
     * column has no empty strip at its right side.
     */
    private inner class LeftColumn : JPanel(null) {
        private val title = ShareUi.sectionTitle(tr("tab.thumbnail"), Material2AL.IMAGE)
        private val pointLabel = ShareUi.label(tr("share.pointLabel"))
        private val momentLabel = ShareUi.label(tr("share.momentLabel"))
        private val zoomLabel = ShareUi.label(tr("adjust.zoom"))
        private val styleTitle = ShareUi.sectionTitle(tr("scoreboard.style"))
        init {
            background = Palette.BG
            listOf(
                title, sizeNote, previewHost, pointLabel, prevPoint, pointCombo, nextPoint, favoritesSwitch,
                momentLabel, strip, framePrev, momentTime, frameNext, zoomLabel, zoom, zoomHint, zoomValue, pickNote,
                styleTitle, placeHint, placeChoice, groupChoice,
            ).forEach { add(it) }
            styleCards.values.forEach { add(it) }
        }

        private fun stripHeight(w: Int) = ((w - LABEL_W - CONTROL_GAP - STEP_W) / MomentStrip.CELLS.toDouble() * 9 / 16).roundToInt().coerceAtLeast(ROW_H - 6)
        private fun noteHeight(w: Int) = if (pickNote.isVisible) pickNote.heightForWidth(w - LABEL_W - CONTROL_GAP) + ROW_GAP else 0

        /** The cards of one group are in one row. The row has room for the largest group, so the cards keep their size. */
        private val cardsInRow = StyleGroup.entries.maxOf { it.styles.size }
        private fun cardWidth(w: Int) = (w - CARD_GAP * (cardsInRow - 1)) / cardsInRow
        private fun cardHeight(w: Int) = (cardWidth(w) * 9.0 / 16).roundToInt() + CARD_NAME_H

        /** The height of all parts other than the preview, for the width [w]. */
        private fun heightWithoutPreview(w: Int): Int =
            TITLE_H + GAP + GAP +
                ROW_H + ROW_GAP + maxOf(ROW_H, stripHeight(w)) + ROW_GAP + ROW_H + noteHeight(w) + GAP +
                STYLE_HEAD_H + GAP + groupChoice.preferredSize.height + ROW_GAP + cardHeight(w)

        override fun doLayout() {
            val w = width - PAD * 2
            val availH = height - PAD * 2
            // The widest preview that fits the height. It is not narrower than MIN_W, and not wider than the column.
            val fitW = ((availH - heightWithoutPreview(w)) * 16.0 / 9).toInt()
            val previewW = fitW.coerceAtMost(w).coerceAtLeast(minOf(MIN_W, w))
            val x = PAD
            var y = PAD
            title.setBounds(x, y, w / 2, TITLE_H)
            val noteW = UiKit.textWidth(sizeNote.text, sizeNote.font).roundToInt() + 6
            sizeNote.setBounds(x + w - noteW, y, noteW, TITLE_H)
            y += TITLE_H + GAP
            val previewH = (previewW * 9.0 / 16).roundToInt()
            previewHost.setBounds(x + (w - previewW) / 2, y, previewW, previewH)
            y += previewH + GAP

            val cx = x + LABEL_W + CONTROL_GAP
            val cw = w - LABEL_W - CONTROL_GAP
            // Point
            pointLabel.setBounds(x, y, LABEL_W, ROW_H)
            val switchW = favoritesSwitch.preferredSize.width
            prevPoint.setBounds(cx, y + 2, 26, 26)
            nextPoint.setBounds(cx + cw - switchW - 12 - 26, y + 2, 26, 26)
            pointCombo.setBounds(cx + 26 + 6, y, cw - switchW - 12 - 26 * 2 - 12, ROW_H)
            favoritesSwitch.setBounds(cx + cw - switchW, y + (ROW_H - favoritesSwitch.preferredSize.height) / 2, switchW, favoritesSwitch.preferredSize.height)
            y += ROW_H + ROW_GAP
            // Moment
            val stripH = stripHeight(w)
            val momentH = maxOf(ROW_H, stripH)
            momentLabel.setBounds(x, y, LABEL_W, momentH)
            strip.setBounds(cx, y + (momentH - stripH) / 2, cw - STEP_W, stripH)
            val stepX = cx + cw - STEP_W + 6
            framePrev.setBounds(stepX, y + (momentH - 26) / 2, 26, 26)
            momentTime.setBounds(stepX + 26, y, STEP_W - 6 - 52, momentH)
            frameNext.setBounds(stepX + STEP_W - 6 - 26, y + (momentH - 26) / 2, 26, 26)
            y += momentH + ROW_GAP
            // Zoom
            zoomLabel.setBounds(x, y, LABEL_W, ROW_H)
            val hintW = if (zoomHint.text.isNullOrEmpty()) 0 else minOf(UiKit.textWidth(zoomHint.text, zoomHint.font).roundToInt() + 4, cw / 2)
            zoomValue.setBounds(cx + cw - 52, y, 52, ROW_H)
            zoomHint.setBounds(cx + cw - 52 - 8 - hintW, y, hintW, ROW_H)
            zoom.setBounds(cx, y, cw - 52 - 8 - hintW - (if (hintW > 0) 8 else 0), ROW_H)
            y += ROW_H
            if (pickNote.isVisible) {
                y += ROW_GAP
                val h = pickNote.heightForWidth(cw)
                pickNote.setBounds(cx, y, cw, h)
                y += h
            }
            y += GAP
            // Style
            styleHeadY = y
            y += 10
            val placeW = if (placeChoice.isVisible) placeChoice.preferredSize.width else 0
            val placeH = placeChoice.preferredSize.height
            placeChoice.setBounds(x + w - placeW, y + (STYLE_HEAD_H - 10 - placeH) / 2, placeW, placeH)
            val hintWidth = UiKit.textWidth(placeHint.text, placeHint.font).roundToInt() + 4
            placeHint.setBounds(x + w - placeW - (if (placeW > 0) 10 else 0) - hintWidth, y, hintWidth, STYLE_HEAD_H - 10)
            styleTitle.setBounds(x, y, w / 3, STYLE_HEAD_H - 10)
            y += STYLE_HEAD_H - 10 + GAP
            val groupH = groupChoice.preferredSize.height
            groupChoice.setBounds(x, y, minOf(w, groupChoice.preferredSize.width), groupH)
            y += groupH + ROW_GAP
            val cardW = cardWidth(w)
            val group = group()
            styleCards.entries.filter { it.key.group == group }.forEachIndexed { index, (_, card) ->
                card.setBounds(x + index * (cardW + CARD_GAP), y, cardW, cardHeight(w))
            }
            styleLineWidth = w
        }

        private var styleHeadY = 0
        private var styleLineWidth = 0

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.color = Palette.LINE
            g.fillRect(PAD, styleHeadY, styleLineWidth, 1)
        }
    }

    /** The Texts and Colors tabs at the top of the side column. */
    private class SideTabs(private val onSelect: (String) -> Unit) : JPanel(java.awt.GridLayout(1, 2, 4, 0)) {
        private val buttons = listOf(
            TabButton(SIDE_TEXTS, tr("share.texts"), Material2MZ.TITLE),
            TabButton(SIDE_COLORS, tr("adjust.colors"), Material2MZ.PALETTE),
        )

        var selected: String = SIDE_TEXTS
            set(value) {
                field = value
                repaint()
            }

        init {
            isOpaque = false
            border = BorderFactory.createEmptyBorder(0, 16, 0, 16)
            buttons.forEach { button ->
                add(button)
                button.addActionListener { onSelect(button.key) }
            }
        }

        override fun getPreferredSize() = Dimension(SIDE_WIDTH, 41)

        override fun paintComponent(g: Graphics) {
            g.color = Palette.LINE
            g.fillRect(0, height - 1, width, 1)
        }

        private inner class TabButton(val key: String, title: String, private val ikon: org.kordamp.ikonli.Ikon) : JButton(title) {
            init {
                name = "share-side-$key"
                isContentAreaFilled = false
                isBorderPainted = false
                isFocusPainted = false
                isOpaque = false
                isRolloverEnabled = true
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }

            override fun paintComponent(g: Graphics) {
                val g2 = UiKit.smooth(g)
                try {
                    val on = selected == key
                    val color = if (on || model.isRollover) Palette.FG else Palette.FG_2
                    val font = UiKit.font(13f, UiKit.Weight.SEMIBOLD)
                    val icon = UiKit.icon(ikon, 17, if (on) Palette.LIME else color)
                    val textW = UiKit.textWidth(text, font)
                    val x = (width - icon.iconWidth - 6 - textW) / 2
                    icon.paintIcon(this, g2, x.roundToInt(), (height - icon.iconHeight) / 2)
                    UiKit.drawText(g2, text, font, color, x + icon.iconWidth + 6, 0f, height.toFloat())
                    if (on) {
                        g2.color = Palette.LIME
                        g2.fillRect(0, height - 2, width, 2)
                    }
                } finally {
                    g2.dispose()
                }
            }
        }
    }

    /** The bottom of the side column: the small preview, the saved files, and the buttons. It is always visible. */
    private inner class SaveBlock : JPanel(null) {
        init {
            name = "share-save-block"
            background = Palette.PANEL
            listOf(mini, currentLine, otherLine, saveButton, openButton).forEach { add(it) }
        }

        /** The thumbnail has the full width of the block, as the buttons. */
        private fun miniHeight(width: Int) = width * 9 / 16

        private fun linesHeight(width: Int) = currentLine.heightForWidth(width) + otherLine.heightForWidth(width)

        override fun getPreferredSize(): Dimension {
            val w = SIDE_WIDTH - 32
            return Dimension(SIDE_WIDTH, 12 + miniHeight(w) + 8 + linesHeight(w) + 8 + 30 + 14)
        }

        override fun doLayout() {
            val w = width - 32
            val x = 16
            var y = 12
            mini.setBounds(x, y, w, miniHeight(w))
            y += miniHeight(w) + 8
            val currentH = currentLine.heightForWidth(w)
            currentLine.setBounds(x, y, w, currentH)
            otherLine.setBounds(x, y + currentH, w, otherLine.heightForWidth(w))
            y += linesHeight(w) + 8
            // When the save text does not fit next to "Open folder", the folder button shows only its icon (B-11 E4-T8).
            val openText = tr("export.openFolder")
            val fits = saveButton.preferredWidthFor(saveButton.text) + 8 + openButton.preferredWidthFor(openText) <= w
            val text = if (fits) openText else ""
            if (openButton.text != text) openButton.text = text
            val openW = openButton.preferredWidthFor(text)
            saveButton.setBounds(x, y, w - openW - 8, 30)
            openButton.setBounds(x + w - openW, y, openW, 30)
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            g.color = Palette.LINE
            g.fillRect(0, 0, width, 1)
        }
    }

    companion object {
        /** The size and the file type of the thumbnail. They are the same in all languages. */
        private const val IMAGE_FORMAT = "1280 × 720 PNG"
        private const val CARD_SHARE = "share"
        private const val CARD_EMPTY = "empty"
        private const val PREVIEW_IMAGE = "image"
        private const val PREVIEW_PLAYER = "player"
        private const val LIVE_FRAME_DELAY_MS = 250

        /** A seek waits at most this time for the frame of the seek before it. A lost event does not stop the seeks. */
        private const val SEEK_WAIT_MS = 1_000L
        private const val SIDE_TEXTS = "texts"
        private const val SIDE_COLORS = "colors"
        private const val SIDE_WIDTH = 340
        private const val SAVE_DELAY_MS = 400
        private const val MAX_ZOOM = 2.0
        private const val DEFAULT_FPS = 30.0
        /** The strip cells are small. */
        private const val STRIP_FRAME_WIDTH = 320
        private val FRAME_ERROR: String get() = tr("share.frameError") + "\n" + tr("share.frameError.action")

        private const val PAD = 16
        private const val GAP = 10
        private const val TITLE_H = 20
        private const val ROW_H = 30
        private const val ROW_GAP = 6
        private const val LABEL_W = 60
        private const val CONTROL_GAP = 10
        private const val STEP_W = 6 + 26 + 92 + 26
        private const val STYLE_HEAD_H = 48
        private const val CARD_GAP = 8
        private const val CARD_NAME_H = 24
        private const val MIN_W = 480


    }
}

/** The file dialog of the operating system for a logo: PNG, JPG, GIF, BMP, TIFF, WEBP, or SVG. */
private fun chooseLogoFile(parent: JComponent): File? {
    val chooser = SystemFileChooser().apply {
        dialogTitle = tr("share.logo.choose")
        fileSelectionMode = SystemFileChooser.FILES_ONLY
        isAcceptAllFileFilterUsed = false
        fileFilter = SystemFileChooser.FileNameExtensionFilter(tr("share.logo.files"), *ThumbnailLogos.EXTENSIONS.toTypedArray())
    }
    return if (chooser.showOpenDialog(parent) == SystemFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

/** Reads the size and the frame rate of the video with ffprobe. */
private fun probeShareSource(source: String): ShareSourceFacts? {
    val info = ExportSourceProbe.probe(source, ApplicationLayout.current().ffprobeExecutable)
    val resolution = info.resolution ?: return null
    return ShareSourceFacts(resolution.width, resolution.height, info.frameRate?.framesPerSecond)
}

/** The view when the tab has nothing to show: no project, no points, or a read error. */
private class ShareEmptyView(onOpenPoints: () -> Unit) : JPanel(java.awt.GridBagLayout()) {
    private val title = JLabel().apply {
        font = UiKit.font(16f, UiKit.Weight.SEMIBOLD)
        foreground = Palette.FG
        horizontalAlignment = SwingConstants.CENTER
    }
    private val text = WrapText("", UiKit.font(13f), Palette.FG_2, 1.5f, 380)
    private val button = UiButton(tr("share.openPoints"), Material2MZ.SPORTS_TENNIS).apply { name = "share-open-points" }

    init {
        name = "share-empty"
        background = Palette.BG
        add(Stack(pad = Insets(24, 24, 24, 24), gap = 10, fixedWidth = 420).apply {
            add(JLabel(UiKit.icon(Material2MZ.SHARE, 40, Palette.FG_2)).apply { horizontalAlignment = SwingConstants.CENTER })
            add(title)
            add(text)
            add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0)).apply {
                isOpaque = false
                add(button)
            })
        })
        button.addActionListener { onOpenPoints() }
    }

    fun show(title: String, text: String, withButton: Boolean) {
        this.title.text = title
        this.text.runs = listOf(org.litvin.ui.commons.TextRun(text, UiKit.font(13f), Palette.FG_2))
        button.isVisible = withButton
        revalidate()
        repaint()
    }
}
