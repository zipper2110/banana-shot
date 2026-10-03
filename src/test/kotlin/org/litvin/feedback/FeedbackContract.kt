package org.litvin.feedback

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Base64
import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * The checks of `feedback-contract/v1` in Kotlin, for the tests. They are the same checks as `validation.ts` of the
 * Worker. The tests run them on the shared fixtures and on the JSON that the app makes.
 */
object FeedbackContract {
    private val mapper = ObjectMapper()
    private val uuid = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val base64 = Regex("^[A-Za-z0-9+/]+={0,2}$")
    private val required = setOf("report_id", "topic", "message", "app_version", "os_name", "os_version", "java_version")
    private val optional = setOf("email", "error", "log")

    fun validReports(): List<JsonNode> = read("valid-reports.json").path("validReports").toList()

    /** The name of each invalid fixture and its body text. */
    fun invalidReports(): List<Pair<String, String>> = read("invalid-reports.json").path("invalidReports").map { item ->
        val payload = item.path("payload")
        item.path("name").asText() to if (payload.isMissingNode) item.path("raw").asText() else payload.toString()
    }

    fun isValid(body: String): Boolean = runCatching { isValid(mapper.readTree(body)) }.getOrDefault(false)

    fun isValid(report: JsonNode): Boolean {
        if (!report.isObject) return false
        val keys = report.fieldNames().asSequence().toSet()
        if (!keys.containsAll(required) || !(required + optional).containsAll(keys)) return false
        fun text(key: String, min: Int, max: Int) = report.path(key).let { it.isTextual && it.asText().length in min..max }
        if (!text("report_id", 36, 36) || !uuid.matches(report.path("report_id").asText())) return false
        if (report.path("topic").asText(null) !in FeedbackTopic.entries.map { it.wireName } || !report.path("topic").isTextual) return false
        if (!text("message", 1, FeedbackRules.MAX_MESSAGE) || report.path("message").asText().isBlank()) return false
        if (!listOf("app_version", "os_name", "os_version", "java_version").all { text(it, 1, FeedbackRules.MAX_INFO) }) return false
        if (report.has("email") && !(report.path("email").isTextual && FeedbackRules.isValidEmail(report.path("email").asText()))) return false
        if (report.has("error") && !text("error", 1, FeedbackRules.MAX_MESSAGE)) return false
        if (report.has("log")) {
            val log = report.path("log").asText()
            if (!text("log", 4, 2_900_000) || !base64.matches(log)) return false
            val bytes = runCatching { Base64.getDecoder().decode(log) }.getOrNull() ?: return false
            if (bytes.size < 2 || bytes[0] != 0x1f.toByte() || bytes[1] != 0x8b.toByte()) return false
        }
        return true
    }

    private fun read(file: String): JsonNode = mapper.readTree(Path("feedback-contract/v1/$file").readText())
}
