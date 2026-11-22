package com.tabletnotes.app.feature.chat

import com.tabletnotes.app.domain.model.ChatIdUtils
import com.tabletnotes.app.feature.chat.friends.QrCodeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatIdAndQrParseTest {

    @Test
    fun `pairIdFor is deterministic regardless of uid order`() {
        val id1 = ChatIdUtils.pairIdFor("user_z", "user_a")
        val id2 = ChatIdUtils.pairIdFor("user_a", "user_z")
        assertEquals("user_a_user_z", id1)
        assertEquals(id1, id2)
    }

    @Test
    fun `blockIdFor puts blocker uid first`() {
        assertEquals("me_uid_bad_uid", ChatIdUtils.blockIdFor("me_uid", "bad_uid"))
    }

    @Test
    fun `parseScannedUsername accepts plain handle, at-prefix, and custom uri`() {
        assertEquals("ahmet_yilmaz", QrCodeHelper.parseScannedUsername("@ahmet_yilmaz"))
        assertEquals("mehmet_99", QrCodeHelper.parseScannedUsername("  MEHMET_99 "))
        assertEquals("zeynep_k", QrCodeHelper.parseScannedUsername("tabletnotes://user/@Zeynep_K"))
        assertNull(QrCodeHelper.parseScannedUsername("ab"))
        assertNull(QrCodeHelper.parseScannedUsername("invalid user!"))
        assertNull(QrCodeHelper.parseScannedUsername(null))
    }
}
