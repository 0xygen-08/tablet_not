package com.tabletnotes.app.feature.chat

import com.tabletnotes.app.R
import com.tabletnotes.app.feature.chat.auth.AlreadyFriendsOrPendingException
import com.tabletnotes.app.feature.chat.auth.BlockedUserInteractionException
import com.tabletnotes.app.feature.chat.auth.CannotFriendSelfException
import com.tabletnotes.app.feature.chat.auth.FirebaseErrorMapper
import com.tabletnotes.app.feature.chat.auth.GroupMemberLimitExceededException
import com.tabletnotes.app.feature.chat.auth.InvalidUsernameFormatException
import com.tabletnotes.app.feature.chat.auth.MessageTooLongException
import com.tabletnotes.app.feature.chat.auth.UserNotFoundByUsernameException
import com.tabletnotes.app.feature.chat.auth.UsernameAlreadyTakenException
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseErrorMapperTest {

    @Test
    fun `maps domain exceptions to friendly localized string resources`() {
        assertEquals(
            R.string.auth_error_invalid_username,
            FirebaseErrorMapper.toFriendlyMessageRes(InvalidUsernameFormatException())
        )
        assertEquals(
            R.string.auth_error_username_taken,
            FirebaseErrorMapper.toFriendlyMessageRes(UsernameAlreadyTakenException())
        )
        assertEquals(
            R.string.friends_error_user_not_found,
            FirebaseErrorMapper.toFriendlyMessageRes(UserNotFoundByUsernameException())
        )
        assertEquals(
            R.string.friends_error_cannot_add_self,
            FirebaseErrorMapper.toFriendlyMessageRes(CannotFriendSelfException())
        )
        assertEquals(
            R.string.friends_error_already_exists,
            FirebaseErrorMapper.toFriendlyMessageRes(AlreadyFriendsOrPendingException())
        )
        assertEquals(
            R.string.chat_error_message_too_long,
            FirebaseErrorMapper.toFriendlyMessageRes(MessageTooLongException())
        )
        assertEquals(
            R.string.group_error_member_limit,
            FirebaseErrorMapper.toFriendlyMessageRes(GroupMemberLimitExceededException())
        )
        assertEquals(
            R.string.friends_error_blocked_interaction,
            FirebaseErrorMapper.toFriendlyMessageRes(BlockedUserInteractionException())
        )
    }

    @Test
    fun `unknown technical exception never leaks raw text and returns generic resource`() {
        val raw = RuntimeException("INTERNAL_GRPC_STACKTRACE_SECRET")
        assertEquals(R.string.auth_error_generic, FirebaseErrorMapper.toFriendlyMessageRes(raw))
    }
}
