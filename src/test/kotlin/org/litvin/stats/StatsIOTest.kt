package org.litvin.stats

import org.litvin.scoring.ScoreIO
import org.litvin.scoring.ScoreV1
import org.litvin.scoring.Sport
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StatsIOTest {
    private val dir = Files.createTempDirectory("stats-io-").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun aProjectWithoutFilesGetsTheTennisLimits() {
        val settings = StatsIO.readForProjectDir(dir.absolutePath)

        assertEquals(10, settings.shortPointMaxSeconds)
        assertEquals(13, settings.longPointMinSeconds)
    }

    @Test
    fun aPadelProjectWithoutStatsFileGetsThePadelLimits() {
        ScoreIO.writeForProjectDir(dir.absolutePath, ScoreV1(sport = Sport.PADEL))

        val settings = StatsIO.readForProjectDir(dir.absolutePath)

        assertEquals(12, settings.shortPointMaxSeconds)
        assertEquals(18, settings.longPointMinSeconds)
    }

    @Test
    fun theSavedLimitsStayAfterAChangeOfTheSport() {
        ScoreIO.writeForProjectDir(dir.absolutePath, ScoreV1(sport = Sport.PADEL))
        StatsIO.writeForProjectDir(dir.absolutePath, StatsSettingsV1(shortPointMaxSeconds = 7, longPointMinSeconds = 20))
        ScoreIO.writeForProjectDir(dir.absolutePath, ScoreV1(sport = Sport.TENNIS))

        val settings = StatsIO.readForProjectDir(dir.absolutePath)

        assertEquals(7, settings.shortPointMaxSeconds)
        assertEquals(20, settings.longPointMinSeconds)
    }
}
