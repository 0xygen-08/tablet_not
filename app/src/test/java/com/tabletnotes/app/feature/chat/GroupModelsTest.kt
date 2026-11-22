package com.tabletnotes.app.feature.chat

import com.tabletnotes.app.domain.model.GroupChatSummary
import com.tabletnotes.app.domain.model.GroupRole
import com.tabletnotes.app.domain.model.GroupSendPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupModelsTest {

    @Test
    fun `GroupRole and GroupSendPermission wire mapping falls back safely`() {
        assertEquals(GroupRole.OWNER, GroupRole.fromWire("owner"))
        assertEquals(GroupRole.ADMIN, GroupRole.fromWire("admin"))
        assertEquals(GroupRole.MEMBER, GroupRole.fromWire("unknown"))

        assertEquals(GroupSendPermission.EVERYONE, GroupSendPermission.fromWire("everyone"))
        assertEquals(GroupSendPermission.ADMINS_ONLY, GroupSendPermission.fromWire("admins_only"))
        assertEquals(GroupSendPermission.EVERYONE, GroupSendPermission.fromWire(null))
    }

    @Test
    fun `hasUnread reflects lastMessageAt vs lastReadAt`() {
        val unread = GroupChatSummary(
            groupId = "g1",
            name = "Fizik",
            lastMessageText = "Selam",
            lastMessageAtMillis = 2000L,
            lastReadAtMillis = 1000L
        )
        assertTrue(unread.hasUnread)

        val read = unread.copy(lastReadAtMillis = 2500L)
        assertFalse(read.hasUnread)
    }
}
