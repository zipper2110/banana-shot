package org.litvin.license

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildInfoTest {
    @Test
    fun `BUILD_DATE is a valid yyyy-MM-dd date that is not in the future`() {
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(BuildInfo.BUILD_DATE), BuildInfo.BUILD_DATE)
        val buildDate = LocalDate.parse(BuildInfo.BUILD_DATE)
        assertTrue(!buildDate.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1)), "Build date $buildDate is in the future")
    }

    @Test
    fun `VERSION is the version from pom xml`() {
        val projectVersion = assertNotNull(
            System.getProperty("bananashot.test.projectVersion"),
            "Run this test with Maven. Surefire sets bananashot.test.projectVersion.",
        )
        assertEquals(projectVersion, BuildInfo.VERSION)
    }
}
