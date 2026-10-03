package org.litvin.feedback

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.Base64
import java.util.UUID

/** The topic of a report. [wireName] is the value of the contract (`feedback-contract/v1`). */
enum class FeedbackTopic(val wireName: String, val title: String) {
    PROBLEM("problem", "Problem"),
    IDEA("idea", "Idea"),
    QUESTION("question", "Question"),
    OTHER("other", "Other"),
}

/** The data that the app adds to each report. Each value has a maximum of [FeedbackRules.MAX_INFO] characters. */
data class FeedbackSystemInfo(val appVersion: String, val osName: String, val osVersion: String, val javaVersion: String) {
    /** One line for the form, for example "1.0.0 · Windows 11 10.0 · Java 21.0.4". */
    val summary: String get() = "$appVersion · $osName $osVersion · Java $javaVersion"

    companion object {
        fun current(appVersion: String): FeedbackSystemInfo = FeedbackSystemInfo(
            appVersion = info(appVersion),
            osName = info(System.getProperty("os.name")),
            osVersion = info(System.getProperty("os.version")),
            javaVersion = info(System.getProperty("java.version")),
        )

        private fun info(value: String?): String = value?.trim()?.take(FeedbackRules.MAX_INFO)?.ifEmpty { null } ?: "unknown"
    }
}

/**
 * One report to the author (B-8). [reportId] stays the same for each retry, so the Worker keeps the report only one
 * time. [log] is the log text of decision 12. The JSON has it as gzip and Base64.
 */
data class FeedbackReport(
    val reportId: UUID,
    val topic: FeedbackTopic,
    val message: String,
    val email: String?,
    val system: FeedbackSystemInfo,
    val error: String?,
    val log: String?,
) {
    init {
        require(reportId.version() == 4) { "The report ID must be a random UUID" }
        require(message.isNotBlank() && message.length <= FeedbackRules.MAX_MESSAGE) { "The message must have 1 to ${FeedbackRules.MAX_MESSAGE} characters" }
        require(email == null || FeedbackRules.isValidEmail(email)) { "The email address is not valid" }
        require(error == null || error.length in 1..FeedbackRules.MAX_MESSAGE) { "The error text is too long" }
    }

    /** The JSON of the contract, without the log. The user sees this text before the app sends it. */
    fun fieldsJson(): ObjectNode = mapper.createObjectNode().apply {
        put("report_id", reportId.toString())
        put("topic", topic.wireName)
        put("message", message)
        email?.let { put("email", it) }
        put("app_version", system.appVersion)
        put("os_name", system.osName)
        put("os_version", system.osVersion)
        put("java_version", system.javaVersion)
        error?.let { put("error", it) }
    }

    /** The request body: [fieldsJson] and the log as gzip and Base64. */
    fun toJson(): String = fieldsJson().apply {
        log?.let { put("log", Base64.getEncoder().encodeToString(FeedbackLog.gzip(it))) }
    }.toString()

    companion object {
        private val mapper = ObjectMapper()

        /** The text that "Show the data" shows: the exact fields and the exact log text. */
        fun dataText(report: FeedbackReport): String = buildString {
            append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report.fieldsJson()))
            if (report.log != null) {
                append("\n\nlog (the app compresses it with gzip before it sends it):\n\n")
                append(report.log)
            }
        }
    }
}

/** The limits of the contract (`feedback-contract/v1/report.schema.json`). */
object FeedbackRules {
    const val MAX_MESSAGE = 10_000
    const val MAX_INFO = 100
    const val MAX_EMAIL = 254
    private val email = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(value: String): Boolean = value.length in 3..MAX_EMAIL && email.matches(value)

    /** The error text of an entry point, cut to the limit. A blank text gives null. */
    fun errorText(value: String?): String? = value?.trim()?.take(MAX_MESSAGE)?.ifEmpty { null }
}
