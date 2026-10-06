package org.litvin.ui.more

import org.litvin.ui.commons.SectionPage
import java.io.File

/** A file that the About section can open, for example the license. */
data class AboutDocument(val title: String, val file: File)

/** The facts that the About section shows. */
data class AboutInfo(
    val appName: String,
    val version: String,
    val documents: List<AboutDocument>,
    val components: List<Pair<String, String>>,
)

/** The About section of the More window. */
internal class AboutPage(
    info: AboutInfo,
    private val onOpenFile: (File) -> Boolean,
) : SectionPage(TITLE) {
    init {
        name = "more-about"
        valueRow("App", info.appName)
        valueRow("Version", info.version).name = "more-about-version"

        subheading("License")
        paragraph(
            "${info.appName} is source-available software under the Elastic License 2.0 (ELv2). " +
                "The app includes libmpv, FFmpeg, a Java runtime, and Java libraries. The third-party notices give their licenses.",
        )
        val documents = info.documents.filter { it.file.isFile }
        lateinit var status: StatusLine
        if (documents.isNotEmpty()) {
            buttonRow(*documents.map { document ->
                secondaryButton(document.title) {
                    status.text = if (onOpenFile(document.file)) "" else "The app cannot open ${document.file.name}."
                }
            }.toTypedArray())
        }
        status = statusLine()
        if (documents.isEmpty()) status.text = "The license files are not in this installation."

        subheading("Runtime")
        info.components.forEach { (label, value) -> valueRow(label, value) }
    }

    companion object {
        const val TITLE = "About"
    }
}
