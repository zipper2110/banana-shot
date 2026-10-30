package org.litvin.adjustments

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import org.litvin.JsonFileIO
import org.litvin.i18n.MachineFormats

/**
 * Adjustments v1 schema and JSON read/write helpers (epic 5.1).
 *
 * Storage:
 * - File name: adjustments.json placed in the project directory (next to edl.json/score.json)
 * - JSON library: Jackson Kotlin module (same configuration as EdlIO/ScoreIO)
 * - Unknown fields are preserved on read/write where feasible.
 */

/** Optional white balance group (v1). */
data class WhiteBalanceV1(
    val temperature: Float = 0.0f // normalized [-1.0, +1.0]
)

/**
 * A change point of the crop and rotation (B-47): from [atMs] in the source video, the geometry uses these values.
 * The change is a blend that ends at [atMs]. [GeometryTimeline] gives the values for each time.
 */
data class GeometryKeyV1(
    val atMs: Long = 0L,
    val zoom: Float = 1.0f,
    val panX: Float = 0.0f,
    val panY: Float = 0.0f,
    val rotationDeg: Float = 0.0f,
)

/**
 * A color change point (B-47): from [atMs] in the source video, the colors use these values.
 * The change is a linear blend that ends at [atMs]. [ColorTimeline] gives the values for each time.
 */
data class ColorKeyV1(
    val atMs: Long = 0L,
    val brightness: Float = 1.0f,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val shadows: Float = 0.0f,
    val highlights: Float = 0.0f,
    val temperature: Float = 0.0f,
)

/**
 * Adjustments model (v1). All fields optional with identity defaults.
 */
data class AdjustmentsV1(
    // Color
    val brightness: Float = 1.0f,  // [0.0, 2.0] multiplier; identity is 1.0
    val contrast: Float = 1.0f,    // [0.0, 2.0]
    val saturation: Float = 1.0f,  // [0.0, 3.0]
    val shadows: Float = 0.0f,     // normalized [-1.0, +1.0]; + lifts dark areas
    val highlights: Float = 0.0f,  // normalized [-1.0, +1.0]; - pulls bright areas down
    val whiteBalance: WhiteBalanceV1? = null, // optional; default identity

    // Geometry
    val zoom: Float = 1.0f,        // multiplier; clamp [MIN_ZOOM, MAX_ZOOM] at sinks
    val panX: Float = 0.0f,        // normalized [-1.0, +1.0]
    val panY: Float = 0.0f,        // normalized [-1.0, +1.0]
    val rotationDeg: Float = 0.0f, // [-180.0, +180.0]
    // Change points of the geometry, sorted by time. The geometry above applies before the first change point.
    @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val geometryKeys: List<GeometryKeyV1> = emptyList(),
    // Color change points, sorted by time. The colors above apply before the first color change point.
    @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
    @get:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val colorKeys: List<ColorKeyV1> = emptyList(),
    // The fill color of the areas outside the rotated video, as "#RRGGBB". Null is black.
    // It does not change at the change points.
    val backgroundColor: String? = null,

    // The trim (B-60): the source time where the video starts and where it ends. Null is the start or the end of
    // the source. Only the Points tab timeline and the "Full video" export use them. They have no change points.
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val trimStartMs: Long? = null,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    @get:JsonInclude(JsonInclude.Include.NON_NULL)
    val trimEndMs: Long? = null,
    // True after the dialog that tells that the trim cuts off points showed one time for this project (B-60).
    @field:JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @get:JsonInclude(JsonInclude.Include.NON_DEFAULT)
    val trimWarningShown: Boolean = false,

    // The version of the geometry rules (CropGeometryMath). Version 1 keeps the crop inside the frame and limits
    // its size with a rotation. Files of version 1 keep their crop until the Transform tab converts them.
    val version: Int = CURRENT_VERSION,
) {
    // Preserve unknown JSON properties for forward compatibility.
    @JsonIgnore
    private val unknowns: MutableMap<String, Any?> = LinkedHashMap()

    @JsonAnySetter
    fun setUnknown(key: String, value: Any?) {
        // Avoid clobbering known fields if Jackson routes them here for any reason
        when (key) {
            "brightness", "contrast", "saturation", "shadows", "highlights", "whiteBalance",
            "zoom", "panX", "panY", "rotationDeg", "geometryKeys", "colorKeys", "backgroundColor",
            "trimStartMs", "trimEndMs", "trimWarningShown", "version" -> return
            else -> unknowns[key] = value
        }
    }

    @JsonAnyGetter
    fun getUnknowns(): Map<String, Any?> = unknowns

    companion object {
        /**
         * The smallest zoom. The crop cannot be larger than the frame, so a zoom below 1.0 would render
         * the same as 1.0. Older projects can have a smaller saved value; it loads as 1.0.
         */
        const val MIN_ZOOM = 1.0f
        const val MAX_ZOOM = 2.5f

        /** The version of new adjustments: the crop can go past the frame edges and grow to the frame size. */
        const val CURRENT_VERSION = 2

        /** The fill color when [backgroundColor] is null. */
        const val DEFAULT_BACKGROUND_RGB = 0x000000
    }

    /** The fill color as 0xRRGGBB. A value that is not "#RRGGBB" gives black. */
    @get:JsonIgnore
    val backgroundRgb: Int
        get() = parseBackground(backgroundColor) ?: DEFAULT_BACKGROUND_RGB
}

/** The 0xRRGGBB value of a "#RRGGBB" text, or null when the text has another form. */
fun parseBackground(text: String?): Int? {
    val digits = text?.trim()?.removePrefix("#") ?: return null
    if (digits.length != 6) return null
    return digits.toIntOrNull(16)
}

/** The "#RRGGBB" text of a 0xRRGGBB value. */
fun backgroundHex(rgb: Int): String = MachineFormats.format("#%06X", rgb and 0xFFFFFF)

object AdjustmentsIO {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    /** Returns absolute path to adjustments.json for a given project directory. */
    fun adjustmentsFilePath(projectDir: String): String = File(projectDir, "adjustments.json").absolutePath

    /** Write AdjustmentsV1 to the given file path. */
    fun write(filePath: String, adj: AdjustmentsV1) {
        JsonFileIO.writeAtomically(mapper, filePath, adj)
    }

    /** Read AdjustmentsV1 from the given file path; returns identity defaults if file does not exist. */
    fun read(filePath: String): AdjustmentsV1 {
        val f = File(filePath)
        if (!f.exists()) return AdjustmentsV1()
        return JsonFileIO.read(mapper, f.path, AdjustmentsV1::class.java)
    }

    /** Convenience: read for a given project directory. */
    fun readForProjectDir(projectDir: String): AdjustmentsV1 = read(adjustmentsFilePath(projectDir))

    /** Convenience: write for a given project directory. */
    fun writeForProjectDir(projectDir: String, adj: AdjustmentsV1) = write(adjustmentsFilePath(projectDir), adj)
}
