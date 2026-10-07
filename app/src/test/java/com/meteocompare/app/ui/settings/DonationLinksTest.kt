package com.meteocompare.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DonationLinksTest {
    @Test
    fun `github sponsors uses official Pat0chat link`() {
        assertEquals(
            "https://github.com/sponsors/Pat0chat",
            GITHUB_SPONSORS_URL
        )
    }
}
