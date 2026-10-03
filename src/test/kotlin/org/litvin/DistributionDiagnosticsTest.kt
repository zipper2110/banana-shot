package org.litvin

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.IOException
import java.net.URI
import java.security.KeyStore
import java.security.KeyStoreException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DistributionDiagnosticsTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `the Windows-ROOT key store of this runtime loads`() {
        // Reads the Windows store only. The test does not change it.
        assertTrue(DistributionDiagnostics.windowsTrustStoreCheck().passed)
    }

    @Test
    fun `a key store that does not load fails the check`() {
        val check = DistributionDiagnostics.windowsTrustStoreCheck { throw KeyStoreException("Windows-ROOT not found") }

        assertFalse(check.passed)
        assertTrue(check.detail.contains("Windows-ROOT not found"), check.detail)
    }

    @Test
    fun `an empty key store fails the check`() {
        val empty = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }

        assertFalse(DistributionDiagnostics.windowsTrustStoreCheck { empty }.passed)
    }

    @Test
    fun `each HTTP status passes the HTTPS check`() {
        val requested = mutableListOf<URI>()

        val check = DistributionDiagnostics.httpsCheck { uri -> requested += uri; 400 }

        assertTrue(check.passed)
        assertEquals(listOf(DistributionDiagnostics.UPDATE_HOST), requested)
    }

    @Test
    fun `a failed connection fails the HTTPS check`() {
        val check = DistributionDiagnostics.httpsCheck { throw IOException("PKIX path building failed") }

        assertFalse(check.passed)
        assertTrue(check.detail.contains("PKIX"), check.detail)
    }
}
