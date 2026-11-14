package com.tabletnotes.app.core.designsystem

import com.tabletnotes.app.core.designsystem.theme.ThemePreset
import com.tabletnotes.app.core.designsystem.theme.toPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetTest {

    @Test
    fun `theme preset cycles through all three themes and returns to default`() {
        val first = ThemePreset.STITCH_CHARCOAL_DARK
        val second = first.next()
        val third = second.next()
        val backToFirst = third.next()

        assertEquals(ThemePreset.WARM_PAPER_LIGHT, second)
        assertEquals(ThemePreset.MIDNIGHT_OLED, third)
        assertEquals(ThemePreset.STITCH_CHARCOAL_DARK, backToFirst)
    }

    @Test
    fun `palettes report correct dark and light modes`() {
        assertTrue(ThemePreset.STITCH_CHARCOAL_DARK.toPalette().isDark)
        assertFalse(ThemePreset.WARM_PAPER_LIGHT.toPalette().isDark)
        assertTrue(ThemePreset.MIDNIGHT_OLED.toPalette().isDark)
    }
}

