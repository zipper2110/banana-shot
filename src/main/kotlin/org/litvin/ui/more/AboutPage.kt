package org.litvin.ui.more

import org.kordamp.ikonli.material2.Material2MZ
import org.litvin.ui.commons.SectionPage
import java.io.File
import java.net.URI
import org.litvin.i18n.tr

/** A file that the About section can open, for example the license. */
data class AboutDocument(val title: String, val file: File)

/** The facts that the About section shows. */
data class AboutInfo(
    val appName: String,
    val version: String,
    val documents: List<AboutDocument>,
    val components: List<Pair<String, String>>,
    val author: String? = null,
    val website: URI? = null,
)

/** The About section of the More window. [onOpenLink] opens the website in the browser. */
internal class AboutPage(
    info: AboutInfo,
    private val onOpenFile: (File) -> Boolean,
    private val onOpenLink: (URI) -> Boolean = { false },
) : SectionPage(TITLE) {
    init {
        name = "more-about"
        valueRow(tr("about.app"), info.appName)
        valueRow(tr("about.version"), info.version).name = "more-about-version"
        info.author?.let { valueRow(tr("about.author"), it).name = "more-about-author" }
        info.website?.let { website ->
            valueRow(tr("about.website"), website.toString()).name = "more-about-website"
            lateinit var websiteStatus: StatusLine
            buttonRow(secondaryButton(tr("about.openWebsite"), Material2MZ.OPEN_IN_NEW) {
                websiteStatus.text = if (onOpenLink(website)) "" else tr("about.openWebsite.failed")
            }.apply { name = "more-about-open-website" })
            websiteStatus = statusLine()
        }

        subheading(tr("about.license"))
        paragraph(tr("about.license.text", "app" to info.appName))
        val documents = info.documents.filter { it.file.isFile }
        lateinit var status: StatusLine
        if (documents.isNotEmpty()) {
            buttonRow(*documents.map { document ->
                secondaryButton(document.title) {
                    status.text = if (onOpenFile(document.file)) "" else tr("about.openFile.failed", "file" to document.file.name)
                }
            }.toTypedArray())
        }
        status = statusLine()
        if (documents.isEmpty()) status.text = tr("about.license.missing")

        subheading(tr("about.runtime"))
        info.components.forEach { (label, value) -> valueRow(label, value) }
    }

    companion object {
        const val ID = "about"
        val TITLE: String get() = tr("more.about")
    }
}
