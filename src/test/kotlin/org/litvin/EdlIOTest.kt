package org.litvin

import org.litvin.points.EdlIO
import org.litvin.points.EdlV1
import org.litvin.points.CommentDefaultsV1
import org.litvin.points.CommentPosition
import org.litvin.points.CommentStyle
import org.litvin.points.CommentV1
import org.litvin.points.PointV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class EdlIOTest {
    @Test
    fun roundTrip_edlJson() {
        val tmpDir: Path = Files.createTempDirectory("edl_rt_")
        val edlPath = tmpDir.resolve("edl.json").toFile().absolutePath

        val edl = EdlV1(
            points = listOf(
                PointV1(id = "A", startMs = 0, endMs = 500),
                PointV1(id = "B", startMs = 1_000, endMs = 2_000, label = "Point"),
                PointV1(id = "C", startMs = 2_000, endMs = 2_500, notes = "Nice serve")
            ),
            version = 1
        )

        EdlIO.write(edlPath, edl)
        val readBack = EdlIO.read(edlPath)

        assertEquals(1, readBack.version, "Version must be 1")
        assertEquals(edl.points.size, readBack.points.size, "Points count should match")
        assertEquals(edl, readBack, "Round-trip should preserve structure and values")
    }

    @Test
    fun read_missingFile_returnsEmptyEdl() {
        val tmpDir: Path = Files.createTempDirectory("edl_missing_")
        val edlPath = tmpDir.resolve("does_not_exist.json").toFile().absolutePath

        val readBack = EdlIO.read(edlPath)
        assertEquals(1, readBack.version)
        assertEquals(0, readBack.points.size)
    }

    @Test
    fun roundTrip_preservesFavoriteState() {
        val tmpDir: Path = Files.createTempDirectory("edl_fav_rt_")
        val edlPath = tmpDir.resolve("edl.json").toFile().absolutePath

        val edl = EdlV1(
            points = listOf(
                PointV1(id = "A", startMs = 0, endMs = 500, favorite = true),
                PointV1(id = "B", startMs = 1_000, endMs = 2_000, favorite = false),
            ),
            version = 1
        )

        EdlIO.write(edlPath, edl)
        val readBack = EdlIO.read(edlPath)

        assertTrue(readBack.points.first { it.id == "A" }.favorite)
        assertFalse(readBack.points.first { it.id == "B" }.favorite)
    }

    @Test
    fun read_legacyWithoutFavorite_defaultsToFalse() {
        val tmpDir: Path = Files.createTempDirectory("edl_fav_legacy_")
        val edlPath = tmpDir.resolve("edl.json")
        Files.writeString(
            edlPath,
            """
            {
              "version": 1,
              "points": [
                { "id": "A", "startMs": 0, "endMs": 500 }
              ]
            }
            """.trimIndent()
        )

        val readBack = EdlIO.read(edlPath.toFile().absolutePath)

        assertEquals(1, readBack.points.size)
        assertFalse(readBack.points[0].favorite)
    }

    @Test
    fun roundTrip_preservesCommentsAndProjectColorDefault() {
        val tmpDir: Path = Files.createTempDirectory("edl_comments_rt_")
        val edlPath = tmpDir.resolve("edl.json").toFile().absolutePath
        val edl = EdlV1(
            comments = listOf(
                CommentV1(7, 1_250, 3_500, "Ball was in", "#22AAFF"),
                CommentV1(8, 5_000, 2_000, "Let", "#FFEE00", CommentStyle.PILL, CommentPosition(0.25, 0.3)),
            ),
            commentDefaults = CommentDefaultsV1("#22AAFF"),
            nextCommentId = 9,
        )

        EdlIO.write(edlPath, edl)

        assertEquals(edl, EdlIO.read(edlPath))
        assertEquals(1, Regex("\"position\"").findAll(File(edlPath).readText()).count(), "A comment without a position writes no position")
    }

    @Test
    fun read_commentPositionOutsideTheFrame_movesToTheEdge() {
        val tmpDir: Path = Files.createTempDirectory("edl_comments_position_")
        val edlPath = tmpDir.resolve("edl.json")
        Files.writeString(
            edlPath,
            """{"version":1,"points":[],"nextCommentId":2,"comments":[""" +
                """{"id":1,"startMs":0,"durationMs":1000,"text":"Far","colorHex":"#FFFFFF","position":{"x":-0.5,"y":1.7}}]}""",
        )

        val read = EdlIO.read(edlPath.toFile().absolutePath)

        assertEquals(CommentPosition(0.0, 1.0), read.comments.single().position)
    }

    @Test
    fun read_commentWithoutOrWithUnknownStyle_usesOutline() {
        val tmpDir: Path = Files.createTempDirectory("edl_comments_style_")
        val edlPath = tmpDir.resolve("edl.json")
        Files.writeString(
            edlPath,
            """{"version":1,"points":[],"nextCommentId":3,"comments":[""" +
                """{"id":1,"startMs":0,"durationMs":1000,"text":"Old","colorHex":"#FFFFFF"},""" +
                """{"id":2,"startMs":0,"durationMs":1000,"text":"Future","colorHex":"#FFFFFF","style":"NEON"}]}""",
        )

        val read = EdlIO.read(edlPath.toFile().absolutePath)

        assertEquals(listOf(CommentStyle.OUTLINE, CommentStyle.OUTLINE), read.comments.map { it.style })
    }

    @Test
    fun read_legacyEdl_suppliesEmptyCommentsWhiteDefaultAndFirstId() {
        val tmpDir: Path = Files.createTempDirectory("edl_comments_legacy_")
        val edlPath = tmpDir.resolve("edl.json")
        Files.writeString(edlPath, """{"version":1,"points":[]}""")

        val read = EdlIO.read(edlPath.toFile().absolutePath)

        assertEquals(emptyList(), read.comments)
        assertEquals("#FFFFFF", read.commentDefaults.colorHex)
        assertEquals(1, read.nextCommentId)
    }

    @Test
    fun read_commentWithoutSizeAndOpacity_usesTheDefaults_andLimitsOtherValues() {
        val tmpDir: Path = Files.createTempDirectory("edl_comments_size_")
        val edlPath = tmpDir.resolve("edl.json")
        Files.writeString(
            edlPath,
            """{"version":1,"points":[],"nextCommentId":3,"comments":[""" +
                """{"id":1,"startMs":0,"durationMs":1000,"text":"Old","colorHex":"#FFFFFF"},""" +
                """{"id":2,"startMs":0,"durationMs":1000,"text":"Odd","colorHex":"#FFFFFF","sizePercent":900,"opacityPercent":-5}]}""",
        )

        val read = EdlIO.read(edlPath.toFile().absolutePath)

        assertEquals(listOf(100 to 100, CommentV1.MAX_SIZE_PERCENT to CommentV1.MIN_OPACITY_PERCENT), read.comments.map { it.sizePercent to it.opacityPercent })
    }

    @Test
    fun read_commentWithoutRotation_isNotTurned_andOtherAnglesTurnIntoTheRange() {
        val tmpDir: Path = Files.createTempDirectory("edl_comments_rotation_")
        val edlPath = tmpDir.resolve("edl.json")
        Files.writeString(
            edlPath,
            """{"version":1,"points":[],"nextCommentId":4,"comments":[""" +
                """{"id":1,"startMs":0,"durationMs":1000,"text":"Old","colorHex":"#FFFFFF"},""" +
                """{"id":2,"startMs":0,"durationMs":1000,"text":"Turned","colorHex":"#FFFFFF","rotationDeg":270},""" +
                """{"id":3,"startMs":0,"durationMs":1000,"text":"Upside down","colorHex":"#FFFFFF","rotationDeg":-180}]}""",
        )

        val read = EdlIO.read(edlPath.toFile().absolutePath)

        assertEquals(listOf(0, -90, -180), read.comments.map { it.rotationDeg })
        assertEquals(180, EdlIO.normalizeRotationDeg(180))
        assertEquals(180, EdlIO.normalizeRotationDeg(540))
        assertEquals(-170, EdlIO.normalizeRotationDeg(190))
    }
}
