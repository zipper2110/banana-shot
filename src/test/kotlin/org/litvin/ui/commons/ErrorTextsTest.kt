package org.litvin.ui.commons

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.litvin.i18n.AppLanguage
import org.litvin.i18n.I18n
import org.litvin.i18n.Messages
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException

class ErrorTextsTest {
    @AfterEach
    fun restore() {
        I18n.install(Messages.english())
    }

    @Test
    fun `the type of the error gives the text and the message stays as the detail`() {
        assertEquals(
            "The app cannot open or save the file, because Windows does not give access to it.\n\nDetails: C:\\a\\score.json",
            ErrorTexts.of(AccessDeniedException("C:\\a\\score.json")),
        )
        assertEquals(
            "The file or the folder is not found. Make sure that it was not moved or deleted.\n\nDetails: C:\\a",
            ErrorTexts.of(IOException("wrapped", NoSuchFileException("C:\\a"))).replace("wrapped", "C:\\a"),
        )
        assertEquals(
            "The app cannot open or save the file, because Windows does not give access to it.\n\nDetails: C:\\a (Access is denied)",
            ErrorTexts.of(FileNotFoundException("C:\\a (Access is denied)")),
        )
    }

    @Test
    fun `an unknown error without a message shows only the general text`() {
        assertEquals("An unexpected error occurred. The log file has the details.", ErrorTexts.of(IllegalStateException()))
    }

    @Test
    fun `the text uses the app language and the detail stays as it is`() {
        I18n.install(Messages.of(AppLanguage.SPANISH_SPAIN))
        assertEquals(
            "Se produjo un error inesperado. El archivo de registro tiene los detalles.\n\nDetalles: disk {full}",
            ErrorTexts.of(IOException("disk {full}")),
        )
    }
}
