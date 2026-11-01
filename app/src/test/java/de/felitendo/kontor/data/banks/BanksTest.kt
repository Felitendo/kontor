package de.felitendo.kontor.data.banks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BanksTest {
    @Test
    fun matchesTypedNames() {
        assertEquals("chase", Banks.match("Chase")?.id)
        assertEquals("chase", Banks.match(" chase bank ")?.id)
        assertEquals("sparkasse", Banks.match("Kreissparkasse Ludwigsburg")?.id)
        assertEquals("sparkasse", Banks.match("Sparkasse KölnBonn")?.id)
        assertEquals("volksbank", Banks.match("Volksbank Mittelhessen")?.id)
        assertEquals("volksbank", Banks.match("Raiffeisenbank im Hochtaunus")?.id)
        assertEquals("sparda", Banks.match("Sparda-Bank West")?.id)
        assertEquals("ing", Banks.match("ING-DiBa")?.id)
    }

    @Test
    fun leavesUnknownBanksAlone() {
        assertNull(Banks.match("Hausbank Müller"))
        assertNull(Banks.match("X"))
    }
}
