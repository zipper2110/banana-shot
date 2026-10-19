package org.litvin.feedback

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedbackReportTest {
    private val mapper = ObjectMapper()
    private val system = FeedbackSystemInfo("1.0.0", "Windows 11", "10.0", "21.0.4")
    private val id = UUID.fromString("00000000-0000-4000-8000-000000000301")

    private fun report(email: String? = null, error: String? = null, log: String? = null, message: String = "The export stops.") =
        FeedbackReport(id, FeedbackTopic.PROBLEM, message, email, system, error, log)

    @Test
    fun `the shared valid fixtures pass the Kotlin checks`() {
        FeedbackContract.validReports().forEach { assertTrue(FeedbackContract.isValid(it), it.toString()) }
    }

    @Test
    fun `the shared invalid fixtures fail the Kotlin checks`() {
        FeedbackContract.invalidReports().forEach { (name, body) -> assertFalse(FeedbackContract.isValid(body), name) }
    }

    @Test
    fun `the JSON of the app follows the contract`() {
        listOf(
            report(),
            report(email = "user@example.test"),
            report(error = "Export failed\n\nffmpeg exited with code 1"),
            report(log = "2026-10-03 12:00:00 INFO line\n"),
            report(email = "a@b.test", error = "e", log = "ü 🎾\n", message = "x".repeat(FeedbackRules.MAX_MESSAGE)),
        ).forEach { assertTrue(FeedbackContract.isValid(it.toJson()), it.toString()) }
    }

    @Test
    fun `the JSON has each field of the contract and no optional key without a value`() {
        val json = mapper.readTree(report().toJson())
        assertEquals(
            mapper.readTree(
                """{"report_id":"$id","topic":"problem","message":"The export stops.","app_version":"1.0.0",""" +
                    """"os_name":"Windows 11","os_version":"10.0","java_version":"21.0.4"}""",
            ),
            json,
        )
    }

    @Test
    fun `the log is gzip text in Base64`() {
        val log = "line one\nline two ü\n"
        val encoded = mapper.readTree(report(log = log).toJson()).path("log").asText()
        val text = GZIPInputStream(ByteArrayInputStream(Base64.getDecoder().decode(encoded))).readBytes().toString(Charsets.UTF_8)
        assertEquals(log, text)
    }

    @Test
    fun `the data text shows the exact fields and the log text, but not the Base64 log`() {
        val text = FeedbackReport.dataText(report(email = "user@example.test", log = "the log text"))
        assertTrue("\"report_id\" : \"$id\"" in text)
        assertTrue("\"email\" : \"user@example.test\"" in text)
        assertTrue(text.endsWith("the log text"))
        assertFalse("\"log\"" in text)
    }

    @Test
    fun `a report that breaks the contract cannot exist`() {
        assertFailsWith<IllegalArgumentException> { report(message = " ") }
        assertFailsWith<IllegalArgumentException> { report(message = "x".repeat(FeedbackRules.MAX_MESSAGE + 1)) }
        assertFailsWith<IllegalArgumentException> { report(email = "not an address") }
        assertFailsWith<IllegalArgumentException> {
            FeedbackReport(UUID.nameUUIDFromBytes(byteArrayOf(1)), FeedbackTopic.IDEA, "m", null, system, null, null)
        }
    }

    @Test
    fun `the system data has a maximum of 100 characters and no empty value`() {
        val previous = System.getProperty("os.version")
        try {
            System.setProperty("os.version", "9".repeat(150))
            val info = FeedbackSystemInfo.current("")
            assertEquals(100, info.osVersion.length)
            assertEquals("unknown", info.appVersion)
        } finally {
            System.setProperty("os.version", previous)
        }
    }

    @Test
    fun `the error text of an entry point is trimmed and cut to the limit`() {
        assertEquals(null, FeedbackRules.errorText("  \n "))
        assertEquals("Export failed", FeedbackRules.errorText("  Export failed \n"))
        assertEquals(FeedbackRules.MAX_MESSAGE, FeedbackRules.errorText("e".repeat(20_000))!!.length)
    }

    @Test
    fun `the language fields follow the contract and are optional`() {
        val withLanguages = FeedbackReport(id, FeedbackTopic.PROBLEM, "m", null, system.copy(appLanguage = "es", osLanguage = "other"), null, null)
        val json = mapper.readTree(withLanguages.toJson())
        assertEquals("es", json["app_language"].textValue())
        assertEquals("other", json["os_language"].textValue())
        assertTrue(FeedbackContract.isValid(json))
        val without = mapper.readTree(report().toJson())
        assertFalse(without.has("app_language") || without.has("os_language"))
    }

    @Test
    fun `the language list of the feedback schema is the list of the analytics contract`() {
        val analytics = mapper.readTree(kotlin.io.path.Path("analytics-contract/v1/attributes.json").toFile())
        assertEquals(analytics["attributes"]["os_language"].map { it.textValue() }, FeedbackContract.languages)
        assertEquals(org.litvin.analytics.AnalyticsEvent.Language.CODES, FeedbackContract.languages)
    }

    @Test
    fun `a text in other scripts and with emoji stays the same`() {
        val message = "Привет, 你好 🎾👍🏽 — ¿Cómo marco un punto?"
        val json = mapper.readTree(report(message = message).toJson())
        assertEquals(message, json["message"].textValue())
        assertTrue(FeedbackContract.isValid(json))
    }

    @Test
    fun `a cut never splits an emoji`() {
        val text = "x".repeat(FeedbackRules.MAX_MESSAGE - 1) + "🎾" + "y"
        assertEquals(FeedbackRules.MAX_MESSAGE + 2, text.length)
        val error = FeedbackRules.errorText(text)!!
        assertEquals(FeedbackRules.MAX_MESSAGE - 1, error.length)
        assertFalse(Character.isHighSurrogate(error.last()))
        assertTrue(FeedbackContract.isValid(report(error = error).toJson()))
        assertEquals("ab", FeedbackRules.cut("ab🎾", 3))
        assertEquals("ab🎾", FeedbackRules.cut("ab🎾", 4))
        assertEquals("", FeedbackRules.cut("🎾", 1))
    }

    @Test
    fun `email check`() {
        assertTrue(FeedbackRules.isValidEmail("a.b+c@sub.example.test"))
        listOf("", "a@b", "a b@c.de", "@c.de", "a@.").forEach { assertFalse(FeedbackRules.isValidEmail(it), it) }
    }
}
