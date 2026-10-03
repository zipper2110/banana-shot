package org.litvin.license

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** The result of a parse of the version rules file. See "Valid file" in `build-expiry-spec.md`. */
sealed interface VersionRulesParse {
    /**
     * The file is valid. [problems] names each rule and each field that the app ignored. The
     * repository test uses it to find mistakes in `release/version-policy.json`.
     */
    data class Valid(val rules: VersionRules, val problems: List<String>) : VersionRulesParse

    /** `schema` is an integer that the app does not know. The app ignores the file. */
    data class UnknownSchema(val schema: String) : VersionRulesParse

    /** The whole file is not valid. The app ignores the file. */
    data class NotValid(val reason: String) : VersionRulesParse
}

object VersionRulesParser {
    const val SCHEMA = 1
    const val MAX_BODY_BYTES = 64 * 1024

    private val logger = KotlinLogging.logger {}

    private val mapper: ObjectMapper = ObjectMapper()
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

    private val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")

    fun parse(body: ByteArray): VersionRulesParse {
        if (body.size > MAX_BODY_BYTES) return VersionRulesParse.NotValid("body is larger than $MAX_BODY_BYTES bytes")
        val root = try {
            mapper.readTree(body)
        } catch (e: IOException) {
            return VersionRulesParse.NotValid("body is not JSON: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (root == null || !root.isObject) return VersionRulesParse.NotValid("top level is not an object")

        val schema = root.get("schema")
        if (schema == null || !schema.isIntegralNumber) return VersionRulesParse.NotValid("schema is missing or is not an integer")
        // A file with a different schema can have a different format. Thus, the app does not check its other fields.
        if (!schema.canConvertToInt() || schema.intValue() != SCHEMA) return VersionRulesParse.UnknownSchema(schema.asText())
        val rulesNode = root.get("rules")
        if (rulesNode == null || !rulesNode.isArray) return VersionRulesParse.NotValid("rules is missing or is not a list")

        val problems = mutableListOf<String>()
        val latest = root.get("latest")?.let { parseLatest(it, problems) }
        val rules = rulesNode.mapIndexedNotNull { index, node -> parseRule(index, node, problems) }
        problems.forEach { logger.warn { "Version rules file: $it" } }
        return VersionRulesParse.Valid(VersionRules(latest, rules), problems)
    }

    private fun parseLatest(node: JsonNode, problems: MutableList<String>): LatestRelease? {
        if (!node.isObject) {
            problems += "latest is ignored: it is not an object"
            return null
        }
        val version = node.requiredText("version")
        val downloadUrl = node.requiredText("downloadUrl")
        if (version == null || downloadUrl == null) {
            problems += "latest is ignored: version or downloadUrl is missing or is not a string"
            return null
        }
        return LatestRelease(
            version = version,
            downloadUrl = downloadUrl,
            installerUrl = node.optionalText("installerUrl", "latest", problems),
            notes = node.optionalText("notes", "latest", problems),
        )
    }

    private fun parseRule(index: Int, node: JsonNode, problems: MutableList<String>): VersionRule? {
        if (!node.isObject) {
            problems += "rule ${index + 1} is ignored: it is not an object"
            return null
        }
        val id = node.requiredText("id")
        val name = if (id != null) "rule '$id'" else "rule ${index + 1} (no id)"
        val toVersion = node.requiredText("toVersion")
        val stopsOn = node.requiredText("stopsOn")?.let(::parseDate)
        val fromVersionNode = node.get("fromVersion")
        val reason = when {
            id == null -> "id is missing or is not a string"
            toVersion == null -> "toVersion is missing or is not a string"
            stopsOn == null -> "stopsOn is missing or is not a yyyy-MM-dd date"
            fromVersionNode != null && !fromVersionNode.isTextual -> "fromVersion is not a string"
            else -> null
        }
        if (reason != null) {
            problems += "$name is ignored: $reason"
            return null
        }
        return VersionRule(
            id = id!!,
            fromVersion = fromVersionNode?.textValue(),
            toVersion = toVersion!!,
            stopsOn = stopsOn!!,
            message = node.optionalText("message", name, problems),
        )
    }

    private fun parseDate(text: String): LocalDate? {
        if (!datePattern.matches(text)) return null
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun JsonNode.requiredText(field: String): String? = get(field)?.takeIf { it.isTextual }?.textValue()

    private fun JsonNode.optionalText(field: String, owner: String, problems: MutableList<String>): String? {
        val value = get(field) ?: return null
        if (value.isTextual) return value.textValue()
        problems += "$field of $owner is ignored: it is not a string"
        return null
    }
}
