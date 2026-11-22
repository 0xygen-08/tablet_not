package com.tabletnotes.app.feature.chat

import com.tabletnotes.app.R
import com.tabletnotes.app.domain.model.UsernameValidator
import com.tabletnotes.app.feature.chat.auth.FirebaseErrorMapper
import com.tabletnotes.app.feature.chat.auth.InvalidDisplayNameException
import com.tabletnotes.app.feature.chat.auth.InvalidUsernameFormatException
import com.tabletnotes.app.feature.chat.auth.UsernameAlreadyTakenException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsernameValidatorTest {

    @Test
    fun `normalize strips leading at sign and converts to lowercase`() {
        assertEquals("ahmet_08", UsernameValidator.normalize("  @AhMeT_08  "))
        assertEquals("veli_yilmaz", UsernameValidator.normalize("Veli_Yilmaz"))
    }

    @Test
    fun `isValid accepts 3 to 20 lowercase alphanumeric and underscore chars`() {
        assertTrue(UsernameValidator.isValid("abc"))
        assertTrue(UsernameValidator.isValid("user_01_test"))
        assertTrue(UsernameValidator.isValid("a1234567890123456789")) // 20 karakter
    }

    @Test
    fun `isValid rejects too short, too long, spaces, dots, and non-ascii chars`() {
        assertFalse(UsernameValidator.isValid("ab")) // < 3
        assertFalse(UsernameValidator.isValid("a12345678901234567890")) // > 20
        assertFalse(UsernameValidator.isValid("ahmet yilmaz")) // boşluk
        assertFalse(UsernameValidator.isValid("ahmet.yilmaz")) // nokta
        assertFalse(UsernameValidator.isValid("ahmet_ğ")) // Türkçe özel karakter
        assertFalse(UsernameValidator.isValid("Ahmet")) // büyük harf (normalize edilmemiş)
    }

    @Test
    fun `error mapper never leaks raw exception strings and returns friendly resource ids`() {
        assertEquals(
            R.string.auth_error_invalid_username,
            FirebaseErrorMapper.toFriendlyMessageRes(InvalidUsernameFormatException())
        )
        assertEquals(
            R.string.auth_error_username_taken,
            FirebaseErrorMapper.toFriendlyMessageRes(UsernameAlreadyTakenException())
        )
        assertEquals(
            R.string.auth_error_invalid_display_name,
            FirebaseErrorMapper.toFriendlyMessageRes(InvalidDisplayNameException())
        )
    }
}
