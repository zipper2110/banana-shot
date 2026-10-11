package org.litvin.ui.commons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthorPortraitTest {
    @Test
    fun `the author photo is in the app and has a round shape`() {
        val image = assertNotNull(AuthorPortrait.image, "the resource /author/portrait.jpg is in the app")
        assertEquals(image.width, image.height)
        assertEquals(0, image.getRGB(0, 0) ushr 24, "the corner is transparent")
        assertTrue(image.getRGB(image.width / 2, image.height / 2) ushr 24 == 0xFF, "the middle is the photo")
    }
}
