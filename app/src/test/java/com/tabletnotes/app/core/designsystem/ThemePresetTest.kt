package com.tabletnotes.app.core.designsystem

import androidx.compose.ui.unit.dp
import com.tabletnotes.app.core.designsystem.theme.CourseFolderPalette
import com.tabletnotes.app.core.designsystem.theme.ThemePreset
import com.tabletnotes.app.core.designsystem.theme.toPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetTest {

    @Test
    fun `theme preset cycles through Night, Paper, Pastel, and System Auto themes`() {
        val first = ThemePreset.NIGHT_DARK
        val second = first.next()
        val third = second.next()
        val fourth = third.next()
        val backToFirst = fourth.next()

        assertEquals(ThemePreset.PAPER_LIGHT, second)
        assertEquals(ThemePreset.PASTEL_SOFT, third)
        assertEquals(ThemePreset.SYSTEM_AUTO, fourth)
        assertEquals(ThemePreset.NIGHT_DARK, backToFirst)
    }

    @Test
    fun `palettes report correct dark and light modes and 24dp card shape`() {
        assertTrue(ThemePreset.NIGHT_DARK.toPalette().isDark)
        assertFalse(ThemePreset.PAPER_LIGHT.toPalette().isDark)
        assertFalse(ThemePreset.PASTEL_SOFT.toPalette().isDark)
        assertTrue(ThemePreset.SYSTEM_AUTO.toPalette(systemInDarkTheme = true).isDark)
        assertFalse(ThemePreset.SYSTEM_AUTO.toPalette(systemInDarkTheme = false).isDark)

        assertEquals(24.dp, ThemePreset.NIGHT_DARK.toPalette().cardCornerRadius)
        assertEquals(24.dp, ThemePreset.PAPER_LIGHT.toPalette().cardCornerRadius)
        assertEquals(24.dp, ThemePreset.PASTEL_SOFT.toPalette().cardCornerRadius)
    }

    @Test
    fun `course folder palette maps Fizik, Kimya, Mat, Tarih, and Ing accurately`() {
        val fizik = CourseFolderPalette.styleForCourse("Fizik", 0)
        val kimya = CourseFolderPalette.styleForCourse("Kimya", 1)
        val mat = CourseFolderPalette.styleForCourse("Mat.", 2)
        val tarih = CourseFolderPalette.styleForCourse("Tarih", 3)
        val ing = CourseFolderPalette.styleForCourse("İng.", 4)

        assertNotNull(fizik)
        assertNotNull(kimya)
        assertNotNull(mat)
        assertNotNull(tarih)
        assertNotNull(ing)
        assertFalse(fizik.dotLight == kimya.dotLight)
    }
}
