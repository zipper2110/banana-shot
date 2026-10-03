package org.litvin.feedback

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedbackLogTest {
    private val folder: File = createTempDirectory("feedback-log").toFile()

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    private fun write(name: String, text: String) = folder.resolve(name).writeText(text)

    @Test
    fun `no log file gives null`() {
        assertNull(FeedbackLog(folder).read())
        assertNull(FeedbackLog(folder.resolve("missing")).read())
    }

    @Test
    fun `a short newest file comes after the end of the older file`() {
        write(FeedbackLog.OLDER, "old 1\nold 2\nold 3\n")
        write(FeedbackLog.NEWEST, "new 1\n")
        // 6 bytes of the newest file and 9 of the older file. The cut line "old 2" does not show.
        assertEquals("old 3\nnew 1\n", FeedbackLog(folder, limit = 15).read())
    }

    @Test
    fun `a newest file at the limit gives only its end`() {
        write(FeedbackLog.OLDER, "old\n")
        write(FeedbackLog.NEWEST, "line 1\nline 2\nline 3\n")
        assertEquals("line 2\nline 3\n", FeedbackLog(folder, limit = 16).read())
    }

    @Test
    fun `the whole files when they are shorter than the limit`() {
        write(FeedbackLog.OLDER, "old\n")
        write(FeedbackLog.NEWEST, "new\n")
        assertEquals("old\nnew\n", FeedbackLog(folder).read())
    }

    @Test
    fun `a cut never breaks a UTF-8 character`() {
        write(FeedbackLog.NEWEST, "üüüü\nline ü\n")
        assertEquals("line ü\n", FeedbackLog(folder, limit = 10).read())
    }

    @Test
    fun `older log files are not used`() {
        write("bananashot.2.log", "very old\n")
        write(FeedbackLog.NEWEST, "new\n")
        assertEquals("new\n", FeedbackLog(folder).read())
    }

    @Test
    fun `the limit is 2 MB`() {
        assertEquals(2 * 1024 * 1024, FeedbackLog.LIMIT)
    }
}
