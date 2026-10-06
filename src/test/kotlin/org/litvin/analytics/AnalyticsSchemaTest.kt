package org.litvin.analytics

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Kotlin side of `analytics-contract/v1`. The Worker tests read the same files. */
class AnalyticsSchemaTest {
    private val mapper = ObjectMapper()

    @Test
    fun `the counter keys are the keys of the contract`() {
        val contract = contract("counter-keys.json")["counterKeys"].map { it.textValue() }
        assertEquals(contract.size, contract.toSet().size, "The contract has no duplicate key")
        assertEquals(contract.toSortedSet(), AnalyticsSchema.COUNTER_KEYS.toSortedSet())
    }

    @Test
    fun `the essential keys are the essential keys of the contract`() {
        val contract = contract("counter-keys.json")["essentialCounterKeys"].map { it.textValue() }
        assertEquals(contract.toSortedSet(), AnalyticsSchema.ESSENTIAL_KEYS.toSortedSet())
        assertEquals(AnalyticsSchema.ESSENTIAL_KEYS, AnalyticsLevel.ESSENTIAL.counterKeys)
        assertEquals(AnalyticsSchema.COUNTER_KEYS, AnalyticsLevel.EXTENDED.counterKeys)
    }

    @Test
    fun `each counter key of the valid fixtures is known for the level of the summary`() {
        validSummaries().forEach { summary ->
            val level = AnalyticsLevel.entries.single { it.key == summary["level"].textValue() }
            summary["counters"].fieldNames().forEach { assertTrue(it in level.counterKeys, it) }
        }
    }

    @Test
    fun `the summary JSON has the fields of the contract`() {
        val json = mapper.readTree(SessionSummary(
            sessionId = "00000000-0000-4000-8000-000000000201",
            level = AnalyticsLevel.EXTENDED,
            appVersion = "1.0.0",
            osFamily = "windows",
            snapshot = 3,
            final = true,
            durationS = 912,
            activeS = 640,
            counters = mapOf("point_added" to 31, "tab_s_points" to 540),
        ).toJson())
        assertEquals(contract("smoke-summary.json").fieldNames().asSequence().toSet(), json.fieldNames().asSequence().toSet())
        assertEquals(
            mapper.readTree("""
                {"schema_version":2,"notice_version":1,"level":"extended","session_id":"00000000-0000-4000-8000-000000000201",
                 "app_version":"1.0.0","os_family":"windows","snapshot":3,"final":true,"duration_s":912,"active_s":640,
                 "counters":{"point_added":31,"tab_s_points":540}}
            """),
            json,
        )
    }

    @Test
    fun `the summary JSON keeps all app_version values as text`() {
        listOf("", "1.0-SNAPSHOT (dev build) ü/2026", "x".repeat(1000)).forEach { version ->
            val json = mapper.readTree(SessionSummary("id", AnalyticsLevel.ESSENTIAL, version, "other", 0, false, 0, 0, emptyMap()).toJson())
            assertEquals(version, json["app_version"].textValue())
        }
    }

    @Test
    fun `the summary JSON contains no forbidden data`() {
        val text = SessionSummary("id", AnalyticsLevel.EXTENDED, "1.0.0", "windows", 0, false, 0, 0, AnalyticsSchema.COUNTER_KEYS.associateWith { 1 }).toJson()
        listOf("path", "file", "email", "exception", "stack", "locale", "zone", "player", "score\"").forEach {
            assertFalse(text.contains(it, ignoreCase = true), it)
        }
    }

    @Test
    fun `the session bucket follows the number of the session`() {
        mapOf(1 to "session_n_1", 2 to "session_n_2_5", 5 to "session_n_2_5", 6 to "session_n_6_20",
            20 to "session_n_6_20", 21 to "session_n_21p", 5_000 to "session_n_21p").forEach { (number, key) ->
            assertEquals(key, AnalyticsSchema.sessionBucket(number))
        }
    }

    @Test
    fun `the resolution uses the short side of the frame`() {
        assertEquals(AnalyticsEvent.Resolution.P1080, AnalyticsEvent.Resolution.ofFrame(1920, 1080))
        assertEquals(AnalyticsEvent.Resolution.P1080, AnalyticsEvent.Resolution.ofFrame(1080, 1920))
        assertEquals(AnalyticsEvent.Resolution.P720, AnalyticsEvent.Resolution.ofFrame(1280, 720))
        assertEquals(AnalyticsEvent.Resolution.P1440, AnalyticsEvent.Resolution.ofFrame(2560, 1440))
        assertEquals(AnalyticsEvent.Resolution.P2160, AnalyticsEvent.Resolution.ofFrame(3840, 2160))
        assertEquals(AnalyticsEvent.Resolution.OTHER, AnalyticsEvent.Resolution.ofFrame(1000, 1000))
    }

    @Test
    fun `counters clamp at the value limit, skip zero, and refuse an unknown key`() {
        val counters = SessionCounters()
        counters.add("point_added", 999_999)
        counters.add("point_added", 5)
        counters.add("help_opened", 0)
        counters.addNanos("tab_s_points", 2_999_999_999)
        counters.addNanos("tab_s_stats", 999_999_999)
        assertEquals(mapOf("point_added" to 1_000_000, "tab_s_points" to 2), counters.values())
        assertFailsWith<IllegalArgumentException> { counters.add("path") }
        assertFailsWith<IllegalArgumentException> { counters.addNanos("C:\\secret.mp4", 1) }
    }

    private fun validSummaries(): List<JsonNode> = contract("valid-summaries.json")["validSummaries"].toList()

    private fun contract(name: String): JsonNode = mapper.readTree(Path("analytics-contract/v1/$name").readText())
}
