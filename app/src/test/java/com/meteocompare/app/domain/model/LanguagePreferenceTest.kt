package com.meteocompare.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguagePreferenceTest {

    @Test
    fun bcp47_tags_cover_all_supported_languages() {
        assertEquals(null, LanguagePreference.SYSTEM.bcp47Tag)
        assertEquals("fr", LanguagePreference.FRENCH.bcp47Tag)
        assertEquals("en", LanguagePreference.ENGLISH.bcp47Tag)
        assertEquals("es", LanguagePreference.SPANISH.bcp47Tag)
        assertEquals("de", LanguagePreference.GERMAN.bcp47Tag)
        assertEquals("it", LanguagePreference.ITALIAN.bcp47Tag)
    }

    @Test
    fun fromLanguageTag_accepts_regional_bcp47_tags() {
        assertEquals(LanguagePreference.FRENCH, LanguagePreference.fromLanguageTag("fr-FR"))
        assertEquals(LanguagePreference.ENGLISH, LanguagePreference.fromLanguageTag("en-GB"))
        assertEquals(LanguagePreference.SPANISH, LanguagePreference.fromLanguageTag("es-ES"))
        assertEquals(LanguagePreference.GERMAN, LanguagePreference.fromLanguageTag("de-DE"))
        assertEquals(LanguagePreference.ITALIAN, LanguagePreference.fromLanguageTag("it-IT"))
    }

    @Test
    fun unsupported_or_empty_tag_falls_back_to_system() {
        assertEquals(LanguagePreference.SYSTEM, LanguagePreference.fromLanguageTag(null))
        assertEquals(LanguagePreference.SYSTEM, LanguagePreference.fromLanguageTag(""))
        assertEquals(LanguagePreference.SYSTEM, LanguagePreference.fromLanguageTag("pt-BR"))
    }
}
