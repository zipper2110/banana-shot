package org.litvin.share

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.litvin.JsonFileIO
import java.io.File
import java.util.prefs.Preferences

/**
 * The Share settings of a project. They are stored in share.json in the project directory,
 * so that the tab shows the same thumbnail when the user opens the project again.
 * A null value uses the default (for example the default point).
 */
data class ShareSettingsV1(
    val version: Int = 1,
    /** [ThumbnailVideoType.key]. */
    val videoType: String? = null,
    /** The id of the point. If the point is not in the project any more, the app uses the default point. */
    val pointId: String? = null,
    /** The moment in the source video. Null is the middle of the point. */
    val momentMs: Long? = null,
    val zoom: Double = 1.0,
    val panX: Double = 0.5,
    val panY: Double = 0.5,
    /** [ThumbnailStyle.key]. */
    val style: String? = null,
    /** The text position and the colors of each style, by [ThumbnailStyle.key]. */
    val looks: Map<String, StyleLookV1> = emptyMap(),
    /** The fields that the user edited, by [ThumbnailField.key]. The other fields use their presets. */
    val texts: Map<String, String> = emptyMap(),
    /** True when the large words show the final score. The edited large words in [texts] come first. */
    val finalScore: Boolean = false,
    /** The saved PNG of each video type, by [ThumbnailVideoType.key]. */
    val saved: Map<String, SavedThumbnailV1> = emptyMap(),
)

/** A saved PNG. [signature] identifies the thumbnail, so that the tab can tell when the preview changed since the save. */
data class SavedThumbnailV1(
    val path: String,
    val savedAtMs: Long,
    val signature: String,
)

/**
 * The values that go to the next project: the last style, the text position and the colors of each style, the last
 * league or event, the last brand name, the logo, and the font and the color of the brand name. The other texts of the project and the final score do not go to
 * the next project.
 */
data class ShareDefaultsV1(
    val style: String? = null,
    val looks: Map<String, StyleLookV1> = emptyMap(),
    val event: String? = null,
    val club: String? = null,
    /** The PNG copy of the logo in the app data folder (see [ThumbnailLogos.store]). Null: no logo. */
    val logo: String? = null,
    /** [ThumbnailFace.name] of the brand name. Null: the font of each style. */
    val brandFont: String? = null,
    /** The color of the brand name, "#RRGGBB". Null: the text color of each style. */
    val brandColor: String? = null,
)

object ShareIO {
    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

    fun shareFilePath(projectDir: String): String = File(projectDir, "share.json").absolutePath

    /** Returns null when the project has no share.json. */
    fun readForProjectDir(projectDir: String): ShareSettingsV1? {
        val path = shareFilePath(projectDir)
        if (!File(path).exists()) return null
        return JsonFileIO.read(mapper, path, ShareSettingsV1::class.java)
    }

    fun writeForProjectDir(projectDir: String, settings: ShareSettingsV1) {
        JsonFileIO.writeAtomically(mapper, shareFilePath(projectDir), settings)
    }

    fun defaultsToJson(defaults: ShareDefaultsV1): String = mapper.writeValueAsString(defaults)

    fun defaultsFromJson(json: String): ShareDefaultsV1 = mapper.readValue(json, ShareDefaultsV1::class.java)
}

/** The Share values for the next project. */
interface ShareDefaults {
    fun load(): ShareDefaultsV1
    fun save(defaults: ShareDefaultsV1)
}

class PreferencesShareDefaults(private val preferences: Preferences) : ShareDefaults {
    override fun load(): ShareDefaultsV1 =
        preferences.get(KEY_DEFAULTS, null)?.let { json -> runCatching { ShareIO.defaultsFromJson(json) }.getOrNull() } ?: ShareDefaultsV1()

    override fun save(defaults: ShareDefaultsV1) {
        preferences.put(KEY_DEFAULTS, ShareIO.defaultsToJson(defaults))
    }

    private companion object {
        const val KEY_DEFAULTS = "share-defaults"
    }
}

/** Keeps the defaults in memory, for the tests. */
class InMemoryShareDefaults(private var defaults: ShareDefaultsV1 = ShareDefaultsV1()) : ShareDefaults {
    override fun load(): ShareDefaultsV1 = defaults

    override fun save(defaults: ShareDefaultsV1) {
        this.defaults = defaults
    }
}
