package org.litvin.i18n

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Properties

/**
 * Reads the catalog files of a language from the class path (B-11 plan, section 2).
 *
 * Each language has the files `i18n/messages<suffix>.properties` and `i18n/help<suffix>.properties`. English has no
 * suffix, and Spanish has `_es`. The files are UTF-8. A byte order mark at the start does not change the first key.
 * A file with bytes that are not valid UTF-8 gives no texts for this language, and the log gets one error.
 */
object MessageCatalog {
    private val logger = KotlinLogging.logger {}

    /** The base names of the catalog files of each language. The help is long, so it has its own files. */
    val BASE_NAMES = listOf("messages", "help")

    fun resourcePath(baseName: String, language: AppLanguage): String =
        if (language == AppLanguage.SOURCE) "i18n/$baseName.properties" else "i18n/${baseName}_${language.code}.properties"

    /** All texts of [language]. A missing file gives no texts. */
    fun load(language: AppLanguage): Map<String, String> = buildMap {
        BASE_NAMES.forEach { baseName ->
            val path = resourcePath(baseName, language)
            val bytes = MessageCatalog::class.java.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            if (bytes == null) {
                if (language == AppLanguage.SOURCE) logger.error { "The catalog file $path is missing." }
                return@forEach
            }
            try {
                putAll(parse(bytes))
            } catch (failure: CharacterCodingException) {
                logger.error(failure) { "The catalog file $path is not valid UTF-8. The app uses English for its texts." }
            }
        }
    }

    /** The keys and the texts of one file. Throws [CharacterCodingException] when the bytes are not valid UTF-8. */
    fun parse(bytes: ByteArray): Map<String, String> {
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        var text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        if (text.startsWith('﻿')) text = text.substring(1)
        val properties = Properties()
        InputStreamReader(ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)), StandardCharsets.UTF_8).use(properties::load)
        return properties.stringPropertyNames().associateWith { properties.getProperty(it) }
    }
}
