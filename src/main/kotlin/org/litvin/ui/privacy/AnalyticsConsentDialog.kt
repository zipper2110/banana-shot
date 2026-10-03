package org.litvin.ui.privacy

import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.AppInfo
import org.litvin.analytics.AnalyticsController
import org.litvin.ui.commons.DialogKit
import org.litvin.ui.commons.LeadRow
import org.litvin.ui.commons.MessageDialog
import org.litvin.ui.commons.MessageKind
import org.litvin.ui.commons.Palette
import org.litvin.ui.commons.PathBox
import org.litvin.ui.commons.Stack
import org.litvin.ui.commons.UiButton
import org.litvin.ui.commons.UiKit
import org.litvin.ui.commons.WrapText
import java.awt.Dialog
import java.awt.Dimension
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import javax.swing.JDialog
import javax.swing.JLabel

/**
 * The question about the optional usage analytics at the app start. It does not block the app.
 * The look comes from design/dialogs-redesign/messages.html ("consent"). Closing the window means "No thanks".
 */
object AnalyticsConsentDialog {
    private const val WIDTH = DialogKit.MEDIUM
    private const val TEXT_WIDTH = WIDTH - 84

    /** The first paragraph. It must agree with the privacy notice (docs/analytics/privacy-notice.md). */
    internal val INTRO = "Help improve ${AppInfo.NAME} by sending optional, anonymous usage counts."

    /** Shows the question. [onClosed] runs after the window closes, with any answer. */
    fun show(
        owner: Window,
        controller: AnalyticsController,
        privacyUrl: URI,
        linkOpener: PrivacyLinkOpener = PrivacyLinkOpener.DesktopBrowser,
        onClosed: () -> Unit = {},
    ) {
        val dialog = build(owner, controller, privacyUrl, linkOpener)
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent) = onClosed()
        })
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
    }

    /** Builds the packed window without showing it. */
    internal fun build(owner: Window?, controller: AnalyticsController, privacyUrl: URI, linkOpener: PrivacyLinkOpener): JDialog {
        val dialog = DialogKit.modal(owner, "Optional usage analytics", Dialog.ModalityType.MODELESS)
        dialog.name = "analytics-consent"
        fun decline() {
            controller.disable()
            dialog.dispose()
        }
        DialogKit.onEscape(dialog) { decline() }
        dialog.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) = controller.disable()
        })

        val fact = LeadRow(
            JLabel(UiKit.icon(Material2AL.BLOCK, 16, Palette.RED)).apply { preferredSize = Dimension(18, 19) },
            WrapText("We never collect video, project names, paths, scores, or personal details.", UiKit.font(12.5f), Palette.FG_2, 1.5f, TEXT_WIDTH),
            18,
            8,
        )
        // The address shows only when the app cannot open the browser, so the user can copy it.
        val copyLine = Stack(gap = 4).apply {
            add(WrapText("Copy this URL:", UiKit.font(12f), Palette.FG_3, 1.4f, TEXT_WIDTH))
            add(PathBox(privacyUrl.toString()))
            isVisible = false
        }
        val later = WrapText("You can change this later in More → Privacy.", UiKit.font(12f), Palette.FG_3, 1.45f, TEXT_WIDTH)
        val parts = listOf(
            MessageDialog.paragraph(INTRO, WIDTH),
            fact,
            copyLine,
            later,
        )

        val read = UiButton("Read privacy notice", Material2MZ.OPEN_IN_NEW, UiButton.Kind.GHOST).apply {
            addActionListener {
                if (!linkOpener.open(privacyUrl) && !copyLine.isVisible) {
                    copyLine.isVisible = true
                    dialog.pack()
                }
            }
        }
        val noThanks = UiButton("No thanks").apply { addActionListener { decline() } }
        val enable = UiButton("Enable analytics", kind = UiButton.Kind.LIME).apply {
            addActionListener {
                controller.enable()
                dialog.dispose()
            }
        }
        val footer = DialogKit.footer(left = listOf(read), right = listOf(noThanks, enable))
        dialog.contentPane = MessageDialog.content(MessageKind.INFO, "Optional usage analytics", parts, footer, WIDTH, Material2AL.INSIGHTS)
        dialog.isResizable = false
        dialog.pack()
        return dialog
    }
}
