package org.litvin.license

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class SavedVersionRulesTest {
    @TempDir
    lateinit var dataFolder: File

    private val first = """{"schema": 1, "rules": [{"id": "first", "toVersion": "1.0.0", "stopsOn": "2027-01-15"}]}"""
    private val second = """{"schema": 1, "rules": [{"id": "second", "toVersion": "1.1.0", "stopsOn": "2027-02-15"}]}"""

    private fun SavedVersionRules.ruleIds() = load()?.rules?.map { it.id }

    @Test
    fun `with no saved file, load gives null`() {
        assertNull(SavedVersionRules(dataFolder).load())
    }

    @Test
    fun `a new valid file replaces the saved file in the data folder`() {
        val saved = SavedVersionRules(dataFolder)
        assertIs<VersionRulesParse.Valid>(saved.replaceIfValid(first.toByteArray()))
        assertEquals(listOf("first"), saved.ruleIds())
        assertIs<VersionRulesParse.Valid>(saved.replaceIfValid(second.toByteArray()))
        assertEquals(listOf("second"), saved.ruleIds())
        assertEquals(dataFolder.resolve(SavedVersionRules.FILE_NAME), saved.file)
        assertEquals(second, saved.file.readText())
    }

    @Test
    fun `the saved file is read again after a restart`() {
        SavedVersionRules(dataFolder).replaceIfValid(first.toByteArray())
        assertEquals(listOf("first"), SavedVersionRules(dataFolder).ruleIds())
    }

    @Test
    fun `a file that is not valid does not replace the saved file`() {
        val saved = SavedVersionRules(dataFolder)
        saved.replaceIfValid(first.toByteArray())
        assertIs<VersionRulesParse.NotValid>(saved.replaceIfValid("<html>Not found</html>".toByteArray()))
        assertIs<VersionRulesParse.NotValid>(saved.replaceIfValid("""{"schema": 1}""".toByteArray()))
        assertEquals(listOf("first"), saved.ruleIds())
    }

    @Test
    fun `a file with an unknown schema does not replace the saved file`() {
        val saved = SavedVersionRules(dataFolder)
        saved.replaceIfValid(first.toByteArray())
        assertIs<VersionRulesParse.UnknownSchema>(saved.replaceIfValid("""{"schema": 2, "rules": []}""".toByteArray()))
        assertEquals(listOf("first"), saved.ruleIds())
    }

    @Test
    fun `a file that is not valid does not make a saved file`() {
        val saved = SavedVersionRules(dataFolder)
        saved.replaceIfValid("not json".toByteArray())
        assertFalse(saved.file.exists())
    }

    @Test
    fun `a damaged saved file gives no saved file and no error`() {
        val saved = SavedVersionRules(dataFolder)
        saved.replaceIfValid(first.toByteArray())
        saved.file.writeText(first.take(30))
        assertNull(saved.load())
    }

    @Test
    fun `a saved file with an unknown schema gives no saved file`() {
        val saved = SavedVersionRules(dataFolder)
        saved.file.writeText("""{"schema": 2, "rules": []}""")
        assertNull(saved.load())
    }

    @Test
    fun `a data folder that does not exist is made at the first write`() {
        val saved = SavedVersionRules(dataFolder.resolve("new"))
        saved.replaceIfValid(first.toByteArray())
        assertEquals(listOf("first"), saved.ruleIds())
    }

    @Test
    fun `a write that fails shows no error and gives the parse result`() {
        val notAFolder = dataFolder.resolve("file").apply { writeText("x") }
        val saved = SavedVersionRules(notAFolder)
        assertIs<VersionRulesParse.Valid>(saved.replaceIfValid(first.toByteArray()))
        assertNull(saved.load())
    }
}
