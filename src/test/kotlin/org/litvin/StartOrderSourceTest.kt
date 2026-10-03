package org.litvin

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The order of the first steps of `main` (build-expiry-spec.md, "Proxy" and "Velopack hook processes").
 * A test cannot run `main`, because `main` exits the process. Thus, this test reads the source.
 */
class StartOrderSourceTest {
    private val mainBody: String by lazy {
        val source = File("src/main/kotlin/org/litvin/SwingMainApp.kt").readText()
        source.substring(source.indexOf("fun main(args: Array<String>)"))
    }

    @Test
    fun `the first statement of main sets java_net_useSystemProxies with no condition`() {
        val firstStatement = mainBody.substringAfter("{").lineSequence()
            .map { it.trim() }
            .first { it.isNotEmpty() && !it.startsWith("//") }

        assertTrue(
            firstStatement == """System.setProperty("java.net.useSystemProxies", "true")""",
            "The first statement of main must set the proxy property. Found: $firstStatement",
        )
    }

    @Test
    fun `main exits for a Velopack hook before the lock and before the services`() {
        val proxy = mainBody.indexOf("java.net.useSystemProxies")
        val hook = mainBody.indexOf("VelopackHooks.isHookProcess(args)")
        val lock = mainBody.indexOf("takeInstanceLock()")
        val services = mainBody.indexOf("AppServices.production()")

        assertTrue(proxy in 0 until hook, "The proxy property comes before the hook check.")
        assertTrue(hook < lock, "The hook check comes before the lock of B-19.")
        assertTrue(lock < services, "The lock comes before the services read the saved state.")
    }

    @Test
    fun `main starts the expiry check after the lock and the services, and before the main window`() {
        val lock = mainBody.indexOf("takeInstanceLock()")
        val services = mainBody.indexOf("AppServices.production()")
        val expiry = mainBody.indexOf("services.expiry.start()")
        val dateCheck = mainBody.indexOf("services.expiry.runDateCheck()")
        val window = mainBody.indexOf("EventQueue.invokeLater")

        assertTrue(lock in 0 until services, "The lock comes before the services.")
        assertTrue(services < expiry, "The services make the expiry controller (E7-S1).")
        assertTrue(expiry < dateCheck, "Path A runs the date check after the start of the expiry check.")
        assertTrue(dateCheck < window, "The expiry check comes before the main window (E6-S2).")
    }

    @Test
    fun `no jpackage java option sets the proxy property`() {
        val script = File("distribution/windows/Build-AppImage.ps1").readText()

        assertFalse(script.contains("useSystemProxies"), "Set the proxy property in main, not in the jpackage --java-options.")
    }
}
