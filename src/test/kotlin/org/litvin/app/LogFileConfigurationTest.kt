package org.litvin.app

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.util.LogbackMDCAdapter
import org.junit.jupiter.api.io.TempDir
import org.litvin.ApplicationLayout
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class LogFileConfigurationTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `the app configuration writes log files to the logs folder of the app data folder`() {
        val appData = tempDir.resolve("app-data").toFile()
        val previous = System.getProperty(APP_DATA_PROPERTY)
        System.setProperty(APP_DATA_PROPERTY, appData.absolutePath)
        ApplicationLayout.resetForTests()
        val context = LoggerContext().apply { setMDCAdapter(LogbackMDCAdapter()) }

        try {
            val configuration = checkNotNull(javaClass.classLoader.getResource("logback.xml"))
            JoranConfigurator().apply { setContext(context) }.doConfigure(configuration)
            context.getLogger("org.litvin.LogFileConfigurationTest").info("log file check")
            context.stop()

            val logFile = AppDataPaths(appData).logs.resolve("tennis-record.log")
            assertTrue(logFile.isFile, "Missing log file: $logFile")
            assertTrue("log file check" in logFile.readText())
        } finally {
            context.stop()
            if (previous == null) System.clearProperty(APP_DATA_PROPERTY) else System.setProperty(APP_DATA_PROPERTY, previous)
            ApplicationLayout.resetForTests()
        }
    }

    private companion object {
        const val APP_DATA_PROPERTY = "tennis.record.appDataDir"
    }
}
