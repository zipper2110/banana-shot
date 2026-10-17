package org.litvin.ui.tabs.share

import io.github.oshai.kotlinlogging.KotlinLogging
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ApplicationLayout
import org.litvin.adjustments.AdjustmentsIO
import org.litvin.adjustments.AdjustmentsV1
import org.litvin.export.ExportSourceProbe
import org.litvin.share.ColorRole
import org.litvin.share.FrameView
import org.litvin.share.SavedThumbnailV1
import org.litvin.share.ShareDefaults
import org.litvin.share.ShareDefaultsV1
import org.litvin.share.ShareIO
import org.litvin.share.SharePoint
import org.litvin.share.ShareProject
import org.litvin.share.ShareSettingsV1
import org.litvin.share.StyleLookV1
import org.litvin.share.TextPlace
import org.litvin.share.ThumbnailField
import org.litvin.share.ThumbnailLayout
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
    private val frameLoader: VideoFrameLoader = VideoFrameLoader(cacheSize = 6),
    private val stripLoader: VideoFrameLoader = VideoFrameLoader(cacheSize = 12),
    private val probeSource: (String) -> ShareSourceFacts? = ::probeShareSource,
    private val now: () -> Long = System::currentTimeMillis,
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

    /** The key frames of the point of the strip. The preview shows the nearest key frame while its frame loads. */
    private var stripFrames: List<KeyFrame> = emptyList()

    /** Only for the point list. The project does not store it. */
    private var favoritesOnly = false
    private var sideTab = SIDE_TEXTS
    private var updating = false

    private val probeExecutor = Executors.newSingleThreadExecutor { task -> Thread(task, "share-probe").apply { isDaemon = true } }
    private val saveTimer = Timer(SAVE_DELAY_MS) { writeSettings() }.apply { isRepeats = false }

    // ---------- Components ----------
    private val sizeNote = ShareUi.label("1280 × 720 PNG", 12f, Palette.FG_3)
    private val preview = ThumbnailView("share-preview")
    private val prevPoint = RowIconButton(Material2AL.CHEVRON_LEFT, "Previous point").apply { name = "share-prev-point" }
    private val nextPoint = RowIconButton(Material2AL.CHEVRON_RIGHT, "Next point").apply { name = "share-next-point" }
    private val favoriteIcon = UiKit.icon(Material2MZ.STAR, 14, Palette.YELLOW)
    private val pointCombo = JComboBox<SharePoint>().apply {
        name = "share-point"
        DialogKit.styleCombo(this)
        preferredSize = Dimension(200, 30)
        maximumRowCount = 16
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
    private val favoritesSwitch = SwitchBox("Only favorites", 12.5f).apply { name = "share-favorites-only" }
    private val strip = MomentStrip(::seekStrip)
    private val framePrev = RowIconButton(Material2AL.KEYBOARD_ARROW_LEFT, "One frame back (Left arrow)").apply { name = "share-frame-prev" }
    private val frameNext = RowIconButton(Material2AL.KEYBOARD_ARROW_RIGHT, "One frame forward (Right arrow)").apply { name = "share-frame-next" }
    private val momentTime = ShareUi.label("", 12f, Palette.FG_2).apply {
        name = "share-moment-time"
        horizontalAlignment = SwingConstants.CENTER
    }
    private val zoom = DefaultFillSliderUI.slider(100, 200, 100).apply {
        name = "share-zoom"
        getAccessibleContext().accessibleName = "Zoom"
    }
    private val zoomHint = ShareUi.hint()
    private val zoomValue = ShareUi.label("100 %", 12f, Palette.FG_2).apply { horizontalAlignment = SwingConstants.RIGHT }
    private val pickNote = NoteBox("share-pick-note", Material2AL.INFO)
    private val placeHint = ShareUi.hint()
    private val placeChoice = SegmentedChoice<TextPlace>("share-place", emptyList())
    private val styleCards = ThumbnailStyle.entries.associateWith { style -> StyleCard("share-style-${style.key}", style.title) }

    private val texts = ShareTextsPanel(
        onType = ::selectType,
        onText = ::editText,
        onHead = ::selectHead,
        onUsePresets = { change { it.copy(texts = emptyMap(), finalScore = false) } },
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
    private val fitNote = NoteBox("share-fit-note", Material2MZ.TEXT_FIELDS)
    private val saveButton = UiButton("Save thumbnail", Material2MZ.SAVE, UiButton.Kind.LIME).apply {
        name = "share-save"
        stretch = true
    }
    private val openButton = UiButton("Open folder", Material2AL.FOLDER_OPEN, UiButton.Kind.GHOST).apply { name = "share-open-folder" }

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
        showEmpty("No project", "Open a project in the Projects tab.", withButton = false)

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
        stripFrames = emptyList()
        strip.cells = List(MomentStrip.CELLS) { null }
        favoritesOnly = false
        facts = null
        factsFor = null
        probing = false
    }

    /** Reads the project again: the points, the scoring, and the adjustments can change in the other tabs. */
    fun onActivated() {
        val dir = projectDir
        val manifest = manifestPath
        if (dir == null || manifest == null) return showEmpty("No project", "Open a project in the Projects tab.", withButton = false)
        val loaded = try {
            shareDefaults = defaults.load()
            adjustments = AdjustmentsIO.readForProjectDir(dir)
            ShareProject.load(dir, manifest)
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot read the project for the Thumbnail tab: $dir" }
            return showEmpty("Cannot read the project", failure.message ?: failure.toString(), withButton = false)
        }
        if (project == null) settings = readSettings(dir)
        project = loaded
        if (loaded.points.isEmpty()) {
            return showEmpty("No points", "Mark the points in the Points tab. Then select a point for the thumbnail here.", withButton = true)
        }
        cards.show(body, CARD_SHARE)
        probeIfNeeded(loaded)
        render()
    }

    fun onDeactivated() = writeSettings()

    fun close() {
        writeSettings()
        probeExecutor.shutdownNow()
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

    private fun presets(project: ShareProject) = project.presets(type(project), shareDefaults.event)

    /** The value of a field: the edited text, the final score, or the preset. */
    private fun value(project: ShareProject, field: ThumbnailField): String {
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

    private fun pickColor(role: ColorRole, current: java.awt.Color) {
        val picked = ColorPickerDialog.pick(this, "${role.title} color", current) ?: return
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

    private fun seekStrip(position: Double, done: Boolean) {
        val project = project ?: return
        val point = currentPoint(project) ?: return
        val moment = point.startMs + (point.lengthMs * position).roundToLong()
        if (!done) {
            // The strip moves at once. The frame loads only for the last position of a drag.
            strip.position = position
            momentTime.text = momentText(point, moment)
            return
        }
        change { it.copy(pointId = point.id, momentMs = moment) }
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
        loadFrame(project, point)
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
        zoomValue.text = "${(view().zoom * 100).roundToInt()} %"
        zoomHint.text = when {
            facts == null -> ""
            maxZoom < MAX_ZOOM -> "Max ${(maxZoom * 100).roundToInt()} % for a ${facts?.height}p video, so that the frame stays sharp"
            else -> ""
        }
        zoomHint.toolTipText = zoomHint.text.ifEmpty { null }
        sizeNote.text = if (view().zoom > 1.0) "Drag the preview to move the frame · 1280 × 720 PNG" else "1280 × 720 PNG"
        pickNote.text = if (project.hasFavorites) "" else "No point has a star. The app selected a point near the middle of the match."
    }

    private fun momentText(point: SharePoint, moment: Long) =
        "${ShareProject.clockTenths(moment - point.startMs)} of ${ShareProject.clockTenths(point.lengthMs)}"

    private var currentLayout: ThumbnailLayout? = null

    private fun renderThumbnails(project: ShareProject) {
        val view = view()
        val message = if (frameFailed && !frameLoading) FRAME_ERROR else null
        val style = style()
        val layouts = ThumbnailStyle.entries.associateWith { ThumbnailRenderer.layout(scene(project, it)) }
        currentLayout = layouts.getValue(style)
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
            card.painter.show(layouts.getValue(cardStyle), null)
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
        placeHint.text = if (places.size > 1) "Text position" else "This style has one text position"
    }

    private fun textsState(project: ShareProject): TextsState {
        val presets = presets(project)
        val used = style().fields
        val fields = ThumbnailField.entries.associateWith { field ->
            val value = value(project, field)
            val choices = listOfNotNull(presets[field], project.finalScore.takeIf { field == ThumbnailField.HEAD })
            val edited = field.key in settings.texts && settings.texts[field.key] !in choices
            FieldState(value, field.source, edited, field in used)
        }
        return TextsState(
            type = type(project),
            fields = fields,
            typeWords = type(project).words,
            finalScore = project.finalScore,
            headIsScore = headIsScore(project),
            anyEdited = fields.values.any { it.edited },
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
            val tooltip = "${file.name} in ${file.parentFile?.absolutePath ?: "the export folder"}"
            when {
                saved == null -> line.show("${lineType.title} · not saved", Material2MZ.RADIO_BUTTON_UNCHECKED, Palette.FG_3, current, tooltip)
                current && saved.signature != signature ->
                    line.show("${lineType.title} · ${savedTime(saved.savedAtMs)}, changed since", Material2MZ.SCHEDULE, Palette.YELLOW, current, tooltip)
                else -> line.show("${lineType.title} · ${savedTime(saved.savedAtMs)}", Material2AL.CHECK_CIRCLE, Palette.LIME, current, tooltip)
            }
        }
        saveButton.text = if (project.thumbnailFile(folder, type).isFile) "Replace thumbnail" else "Save thumbnail"
        saveButton.isEnabled = frame != null && !frameLoading && !frameFailed
        saveButton.toolTipText = project.thumbnailFile(folder, type).absolutePath
        val layout = currentLayout
        fitNote.text = when {
            layout == null -> ""
            layout.shrunk && layout.small -> "A text is long, so the app made it too small for YouTube search. Use shorter words."
            layout.small -> "Some texts of this style are small. They are hard to read in YouTube search. Look at the small preview."
            layout.shrunk -> "A text is long, so the app made it smaller. Short words are easier to read."
            else -> ""
        }
    }

    private fun savedTime(ms: Long): String {
        val time = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
        val today = Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate()
        val clock = time.format(TIME_FORMAT)
        return when (time.toLocalDate()) {
            today -> "today $clock"
            today.minusDays(1) -> "yesterday $clock"
            else -> "${time.toLocalDate().format(DAY_FORMAT)} $clock"
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
        showKeyFrame(project, point)
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

    /** While the exact frame loads, the preview shows the nearest key frame of the strip at once. */
    private fun showKeyFrame(project: ShareProject, point: SharePoint) {
        val request = frameRequest ?: return
        if (!frameLoading || stripFor?.first != point.id) return
        val keyFrame = nearest(stripFrames, request.atMs) ?: return
        frame = keyFrame.image
        renderThumbnails(project)
    }

    private fun nearest(frames: List<KeyFrame>, atMs: Long): KeyFrame? = frames.minByOrNull { abs(it.atMs - atMs) }

    private fun cellMs(point: SharePoint, cell: Int) = point.startMs + (point.lengthMs * (cell + 0.5) / MomentStrip.CELLS).roundToLong()

    /** One ffmpeg process reads all key frames of the point. This is much faster than one process for each cell. */
    private fun loadStrip(project: ShareProject, point: SharePoint) {
        val source = project.sourcePath?.takeIf { File(it).isFile } ?: return
        val key = point.id to adjustments.hashCode()
        if (stripFor == key) return
        stripFor = key
        stripFrames = emptyList()
        stripLoader.cancelPending(stopRunning = true)
        val request = KeyFramesRequest(source, point.startMs, point.endMs, adjustments, STRIP_FRAME_WIDTH)
        stripLoader.cachedKeyFrames(request)?.let { frames -> return showStrip(project, point, frames) }
        strip.cells = List(MomentStrip.CELLS) { null }
        stripLoader.loadKeyFrames(request) { frames ->
            if (stripFor != key) return@loadKeyFrames
            if (frames.isEmpty()) loadStripFrames(source, point, key) else showStrip(project, point, frames)
        }
    }

    /** Each cell shows the key frame nearest to the time of the cell. */
    private fun showStrip(project: ShareProject, point: SharePoint, frames: List<KeyFrame>) {
        stripFrames = frames
        strip.cells = (0 until MomentStrip.CELLS).map { cell -> nearest(frames, cellMs(point, cell))?.image }
        showKeyFrame(project, point)
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
        return digest.take(12).joinToString("") { "%02x".format(it) }
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
                    "The thumbnail is ${"%.1f".format(Locale.ENGLISH, bytes / 1024.0 / 1024.0)} MB. YouTube accepts 2 MB or less. Select another moment or a larger zoom.",
                    "Large thumbnail",
                )
            }
        } catch (failure: Exception) {
            logger.warn(failure) { "Cannot save the thumbnail $file" }
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot save the thumbnail")
            return
        } finally {
            cursor = Cursor.getDefaultCursor()
        }
        // The league goes to the next project ("From your last thumbnail").
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
            dialogs.showError(this, failure.message ?: failure.toString(), "Cannot open the folder")
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
        val left = LeftColumn()
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
     * All parts have the width of the preview.
     */
    private inner class LeftColumn : JPanel(null) {
        private val title = ShareUi.sectionTitle("Thumbnail", Material2AL.IMAGE)
        private val pointLabel = ShareUi.label("Point")
        private val momentLabel = ShareUi.label("Moment")
        private val zoomLabel = ShareUi.label("Zoom")
        private val styleTitle = ShareUi.sectionTitle("Style")
        private val styles = styleCards.values.toList()

        init {
            background = Palette.BG
            listOf(
                title, sizeNote, preview, pointLabel, prevPoint, pointCombo, nextPoint, favoritesSwitch,
                momentLabel, strip, framePrev, momentTime, frameNext, zoomLabel, zoom, zoomHint, zoomValue, pickNote,
                styleTitle, placeHint, placeChoice,
            ).forEach { add(it) }
            styles.forEach { add(it) }
        }

        private fun stripHeight(w: Int) = ((w - LABEL_W - CONTROL_GAP - STEP_W) / MomentStrip.CELLS.toDouble() * 9 / 16).roundToInt().coerceAtLeast(ROW_H - 6)
        private fun noteHeight(w: Int) = if (pickNote.isVisible) pickNote.heightForWidth(w - LABEL_W - CONTROL_GAP) + ROW_GAP else 0
        private fun cardWidth(w: Int) = (w - CARD_GAP * (styles.size - 1)) / styles.size
        private fun cardHeight(w: Int) = (cardWidth(w) * 9.0 / 16).roundToInt() + CARD_NAME_H

        private fun contentHeight(w: Int): Int =
            TITLE_H + GAP + (w * 9.0 / 16).roundToInt() + GAP +
                ROW_H + ROW_GAP + maxOf(ROW_H, stripHeight(w)) + ROW_GAP + ROW_H + noteHeight(w) + GAP +
                STYLE_HEAD_H + GAP + cardHeight(w)

        override fun doLayout() {
            val availW = width - PAD * 2
            val availH = height - PAD * 2
            var w = availW
            // The widest preview whose column fits the height.
            while (w > MIN_W && contentHeight(w) > availH) w -= 8
            w = w.coerceAtLeast(minOf(MIN_W, availW))
            val x = PAD
            var y = PAD
            title.setBounds(x, y, w / 2, TITLE_H)
            val noteW = UiKit.textWidth(sizeNote.text, sizeNote.font).roundToInt() + 6
            sizeNote.setBounds(x + w - noteW, y, noteW, TITLE_H)
            y += TITLE_H + GAP
            val previewH = (w * 9.0 / 16).roundToInt()
            preview.setBounds(x, y, w, previewH)
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
            val cardW = cardWidth(w)
            styles.forEachIndexed { index, card ->
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
            TabButton(SIDE_TEXTS, "Texts", Material2MZ.TITLE),
            TabButton(SIDE_COLORS, "Colors", Material2MZ.PALETTE),
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
            listOf(mini, currentLine, otherLine, fitNote, saveButton, openButton).forEach { add(it) }
        }

        /** The thumbnail has the full width of the block, as the buttons. */
        private fun miniHeight(width: Int) = width * 9 / 16

        private fun fitHeight(width: Int) = if (fitNote.isVisible) fitNote.heightForWidth(width) + 8 else 0

        private fun linesHeight(width: Int) = currentLine.heightForWidth(width) + otherLine.heightForWidth(width)

        override fun getPreferredSize(): Dimension {
            val w = SIDE_WIDTH - 32
            return Dimension(SIDE_WIDTH, 12 + miniHeight(w) + 8 + linesHeight(w) + 8 + fitHeight(w) + 30 + 14)
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
            if (fitNote.isVisible) {
                val h = fitNote.heightForWidth(w)
                fitNote.setBounds(x, y, w, h)
                y += h + 8
            }
            val openW = openButton.preferredSize.width
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
        private const val CARD_SHARE = "share"
        private const val CARD_EMPTY = "empty"
        private const val SIDE_TEXTS = "texts"
        private const val SIDE_COLORS = "colors"
        private const val SIDE_WIDTH = 340
        private const val SAVE_DELAY_MS = 400
        private const val MAX_ZOOM = 2.0
        private const val DEFAULT_FPS = 30.0
        /** The strip cells are small. The preview also shows these frames, for a short time, while its frame loads. */
        private const val STRIP_FRAME_WIDTH = 320
        private const val FRAME_ERROR = "The app cannot read this frame of the video.\nSelect another point or another moment."

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

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm", Locale.ENGLISH)
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    }
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
    private val button = UiButton("Open the Points tab", Material2MZ.SPORTS_TENNIS).apply { name = "share-open-points" }

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
