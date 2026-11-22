package com.tabletnotes.app.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabletnotes.app.R
import com.tabletnotes.app.core.common.IoDispatcher
import com.tabletnotes.app.core.network.ConnectivityObserver
import com.tabletnotes.app.domain.model.AccountAuthState
import com.tabletnotes.app.domain.model.BlockedUserRecord
import com.tabletnotes.app.domain.model.ChatIdUtils
import com.tabletnotes.app.domain.model.DirectChatSummary
import com.tabletnotes.app.domain.model.FirestoreChatMessage
import com.tabletnotes.app.domain.model.FriendshipRecord
import com.tabletnotes.app.domain.model.FriendshipStatus
import com.tabletnotes.app.domain.model.GroupChatSummary
import com.tabletnotes.app.domain.model.GroupRole
import com.tabletnotes.app.domain.model.GroupSendPermission
import com.tabletnotes.app.domain.model.PublicUserProfile
import com.tabletnotes.app.feature.chat.auth.AuthService
import com.tabletnotes.app.feature.chat.auth.FirebaseErrorMapper
import com.tabletnotes.app.feature.chat.data.MessagingRepository
import com.tabletnotes.app.feature.chat.friends.FriendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ActiveConversationTarget {
    data class Direct(val chatId: String, val peer: PublicUserProfile) : ActiveConversationTarget
    data class Group(val groupId: String) : ActiveConversationTarget
}

data class ReportRequest(
    val targetType: String,
    val targetLabel: String,
    val targetUserId: String?,
    val targetChatId: String?,
    val targetMessageId: String?
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val authService: AuthService,
    private val friendsRepository: FriendsRepository,
    private val messagingRepository: MessagingRepository,
    private val connectivityObserver: ConnectivityObserver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    val accountAuthState: StateFlow<AccountAuthState> = authService.authState
    val isFirebaseReady: Boolean get() = authService.isFirebaseReady
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline

    // --- Kimlik Doğrulama ve Hesap Durumu (M2 & M6) ---
    private val _authBusy = MutableStateFlow(false)
    val authBusy: StateFlow<Boolean> = _authBusy.asStateFlow()

    private val _authErrorRes = MutableStateFlow<Int?>(null)
    val authErrorRes: StateFlow<Int?> = _authErrorRes.asStateFlow()

    private val _authInfoRes = MutableStateFlow<Int?>(null)
    val authInfoRes: StateFlow<Int?> = _authInfoRes.asStateFlow()

    // --- Arama & Aktif Görünürlük ---
    private val _isChatScreenVisible = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _activeTarget = MutableStateFlow<ActiveConversationTarget?>(null)
    val activeTarget: StateFlow<ActiveConversationTarget?> = _activeTarget.asStateFlow()

    // --- Arkadaşlık & QR Arama Durumu (M3) ---
    val friendships: StateFlow<List<FriendshipRecord>> = friendsRepository.friendships
    val blockedUsers: StateFlow<List<BlockedUserRecord>> = friendsRepository.blockedUsers

    private val _friendSearchResult = MutableStateFlow<PublicUserProfile?>(null)
    val friendSearchResult: StateFlow<PublicUserProfile?> = _friendSearchResult.asStateFlow()

    private val _friendBusy = MutableStateFlow(false)
    val friendBusy: StateFlow<Boolean> = _friendBusy.asStateFlow()

    private val _friendErrorRes = MutableStateFlow<Int?>(null)
    val friendErrorRes: StateFlow<Int?> = _friendErrorRes.asStateFlow()

    private val _friendInfoRes = MutableStateFlow<Int?>(null)
    val friendInfoRes: StateFlow<Int?> = _friendInfoRes.asStateFlow()

    // --- Sohbet Geri Bildirim & Grup Durumu (M4 & M5) ---
    private val _chatNoticeRes = MutableStateFlow<Int?>(null)
    val chatNoticeRes: StateFlow<Int?> = _chatNoticeRes.asStateFlow()

    private val _groupBusy = MutableStateFlow(false)
    val groupBusy: StateFlow<Boolean> = _groupBusy.asStateFlow()

    private val _groupErrorRes = MutableStateFlow<Int?>(null)
    val groupErrorRes: StateFlow<Int?> = _groupErrorRes.asStateFlow()

    val activeMessages: StateFlow<List<FirestoreChatMessage>> = messagingRepository.activeMessages
    val hasMoreOlderMessages: StateFlow<Boolean> = messagingRepository.hasMoreOlderMessages
    val isLoadingOlderMessages: StateFlow<Boolean> = messagingRepository.isLoadingOlderMessages

    val acceptedFriends: StateFlow<List<FriendshipRecord>> = friendships
        .combine(_searchQuery) { list, _ ->
            list.filter { it.status == FriendshipStatus.ACCEPTED }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomingFriendRequests: StateFlow<List<FriendshipRecord>> = friendships
        .combine(_searchQuery) { list, _ ->
            list.filter { it.status == FriendshipStatus.PENDING && it.isIncoming }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outgoingFriendRequests: StateFlow<List<FriendshipRecord>> = friendships
        .combine(_searchQuery) { list, _ ->
            list.filter { it.status == FriendshipStatus.PENDING && !it.isIncoming }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val directChatItems: StateFlow<List<DirectChatSummary>> = combine(
        friendsRepository.friendships,
        messagingRepository.directChats,
        _searchQuery
    ) { friendList, chatList, query ->
        val accepted = friendList.filter { it.status == FriendshipStatus.ACCEPTED }
        val chatsById = chatList.associateBy { it.chatId }

        val merged = accepted.map { rec ->
            val existingChat = chatsById[rec.pairId]
            if (existingChat != null) {
                existingChat.copy(peerProfile = rec.otherProfile)
            } else {
                DirectChatSummary(
                    chatId = rec.pairId,
                    peerProfile = rec.otherProfile,
                    lastMessageText = "",
                    lastMessageAtMillis = rec.updatedAtMillis,
                    unreadCount = 0
                )
            }
        }.sortedByDescending { it.lastMessageAtMillis }

        val q = query.trim().lowercase()
        if (q.isBlank()) {
            merged
        } else {
            merged.filter {
                it.peerProfile.displayName.lowercase().contains(q) ||
                    it.peerProfile.username.lowercase().contains(q) ||
                    it.lastMessageText.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupChatItems: StateFlow<List<GroupChatSummary>> = combine(
        messagingRepository.groups,
        _searchQuery
    ) { groupList, query ->
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            groupList
        } else {
            groupList.filter {
                it.name.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.lastMessageText.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            combine(accountAuthState, _isChatScreenVisible) { auth, visible ->
                auth to visible
            }.collect { (auth, visible) ->
                if (visible && auth is AccountAuthState.SignedIn) {
                    val uid = auth.profile.uid
                    friendsRepository.attachListeners(uid)
                    messagingRepository.attachOverviewListeners(uid)
                } else {
                    friendsRepository.detachListeners()
                    messagingRepository.detachOverviewListeners()
                    messagingRepository.detachActiveConversationListener()
                    _activeTarget.value = null
                }
            }
        }
    }

    fun setChatSectionVisible(visible: Boolean) {
        _isChatScreenVisible.value = visible
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun dismissChatNotice() {
        _chatNoticeRes.value = null
    }

    // --- Aktif Sohbet Yönetimi (Yalnızca Açık Sohbet Dinlenir) ---

    fun openDirectConversation(peer: PublicUserProfile) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        val myUid = signedIn.profile.uid
        val chatId = ChatIdUtils.pairIdFor(myUid, peer.uid)
        _activeTarget.value = ActiveConversationTarget.Direct(chatId = chatId, peer = peer)
        messagingRepository.attachActiveConversationListener(chatId, isGroup = false)
        viewModelScope.launch(ioDispatcher) {
            messagingRepository.markDirectChatAsRead(myUid, chatId)
        }
    }

    fun openGroupConversation(groupId: String) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        val myUid = signedIn.profile.uid
        _activeTarget.value = ActiveConversationTarget.Group(groupId = groupId)
        messagingRepository.attachActiveConversationListener(groupId, isGroup = true)
        viewModelScope.launch(ioDispatcher) {
            messagingRepository.markGroupAsRead(myUid, groupId)
        }
    }

    fun closeActiveConversation() {
        _activeTarget.value = null
        messagingRepository.detachActiveConversationListener()
    }

    fun loadOlderMessages() {
        val target = _activeTarget.value ?: return
        val (convId, isGroup) = when (target) {
            is ActiveConversationTarget.Direct -> target.chatId to false
            is ActiveConversationTarget.Group -> target.groupId to true
        }
        viewModelScope.launch(ioDispatcher) {
            messagingRepository.loadOlderMessages(convId, isGroup)
                .onFailure { _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(it) }
        }
    }

    fun sendMessage(text: String) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        val target = _activeTarget.value ?: return
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return
        if (cleanText.length > 2000) {
            _chatNoticeRes.value = R.string.chat_error_message_too_long
            return
        }

        viewModelScope.launch(ioDispatcher) {
            val result = when (target) {
                is ActiveConversationTarget.Direct -> {
                    messagingRepository.sendDirectMessage(
                        myProfile = signedIn.profile,
                        peerProfile = target.peer,
                        text = cleanText
                    )
                }
                is ActiveConversationTarget.Group -> {
                    messagingRepository.sendGroupMessage(
                        myProfile = signedIn.profile,
                        groupId = target.groupId,
                        text = cleanText
                    )
                }
            }
            result.onFailure { err ->
                _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
            }
        }
    }

    fun retryFailedMessage(failedMessage: FirestoreChatMessage) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        val target = _activeTarget.value ?: return

        viewModelScope.launch(ioDispatcher) {
            val result = when (target) {
                is ActiveConversationTarget.Direct -> {
                    messagingRepository.sendDirectMessage(
                        myProfile = signedIn.profile,
                        peerProfile = target.peer,
                        text = failedMessage.text,
                        retryLocalId = failedMessage.id
                    )
                }
                is ActiveConversationTarget.Group -> {
                    messagingRepository.sendGroupMessage(
                        myProfile = signedIn.profile,
                        groupId = target.groupId,
                        text = failedMessage.text,
                        retryLocalId = failedMessage.id
                    )
                }
            }
            result.onFailure { err ->
                _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
            }
        }
    }

    fun deleteOwnMessage(messageId: String) {
        val target = _activeTarget.value ?: return
        val (convId, isGroup) = when (target) {
            is ActiveConversationTarget.Direct -> target.chatId to false
            is ActiveConversationTarget.Group -> target.groupId to true
        }
        viewModelScope.launch(ioDispatcher) {
            messagingRepository.deleteOwnMessage(convId, isGroup, messageId)
                .onFailure { err ->
                    _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
        }
    }

    // --- Arkadaşlar, QR, Engelleme ve Şikayet (M3) ---

    fun clearFriendFeedback() {
        _friendErrorRes.value = null
        _friendInfoRes.value = null
        _friendSearchResult.value = null
    }

    fun setFriendErrorRes(resId: Int) {
        _friendErrorRes.value = resId
        _friendInfoRes.value = null
    }

    fun searchUserByExactUsername(rawUsername: String) {
        viewModelScope.launch(ioDispatcher) {
            _friendBusy.value = true
            _friendErrorRes.value = null
            _friendInfoRes.value = null
            _friendSearchResult.value = null
            friendsRepository.findUserByExactUsername(rawUsername)
                .onSuccess { _friendSearchResult.value = it }
                .onFailure { err ->
                    _friendErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
            _friendBusy.value = false
        }
    }

    fun sendFriendRequest(target: PublicUserProfile) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _friendBusy.value = true
            _friendErrorRes.value = null
            _friendInfoRes.value = null
            friendsRepository.sendFriendRequest(signedIn.profile, target)
                .onSuccess { _friendInfoRes.value = R.string.friends_request_sent_notice }
                .onFailure { err ->
                    _friendErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
            _friendBusy.value = false
        }
    }

    fun acceptFriendRequest(otherUid: String) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _friendBusy.value = true
            friendsRepository.acceptFriendRequest(signedIn.profile.uid, otherUid)
                .onFailure { err ->
                    _friendErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                    _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
            _friendBusy.value = false
        }
    }

    fun rejectCancelOrUnfriend(otherUid: String) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _friendBusy.value = true
            friendsRepository.removeOrCancelFriendship(signedIn.profile.uid, otherUid)
                .onSuccess {
                    val active = _activeTarget.value
                    if (active is ActiveConversationTarget.Direct && active.peer.uid == otherUid) {
                        closeActiveConversation()
                    }
                }
                .onFailure { err ->
                    _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
            _friendBusy.value = false
        }
    }

    fun blockUser(target: PublicUserProfile) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _friendBusy.value = true
            friendsRepository.blockUser(signedIn.profile.uid, target)
                .onSuccess {
                    val active = _activeTarget.value
                    if (active is ActiveConversationTarget.Direct && active.peer.uid == target.uid) {
                        closeActiveConversation()
                    }
                    _friendSearchResult.value = null
                }
                .onFailure { err ->
                    _friendErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                    _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
            _friendBusy.value = false
        }
    }

    fun unblockUser(blockedUid: String) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _friendBusy.value = true
            friendsRepository.unblockUser(signedIn.profile.uid, blockedUid)
                .onFailure { err ->
                    _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
            _friendBusy.value = false
        }
    }

    fun submitReport(request: ReportRequest, reason: String) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _friendBusy.value = true
            friendsRepository.submitReport(
                reporterUid = signedIn.profile.uid,
                targetType = request.targetType,
                targetUserId = request.targetUserId,
                targetChatId = request.targetChatId,
                targetMessageId = request.targetMessageId,
                reason = reason
            ).onSuccess {
                _chatNoticeRes.value = R.string.report_submitted_notice
            }.onFailure { err ->
                _chatNoticeRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
            }
            _friendBusy.value = false
        }
    }

    // --- Gruplar ve Grup Ayarları (M5) ---

    fun clearGroupError() {
        _groupErrorRes.value = null
    }

    fun createGroup(
        name: String,
        description: String,
        avatarPreset: String,
        members: List<PublicUserProfile>,
        onCreated: (String) -> Unit
    ) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _groupBusy.value = true
            _groupErrorRes.value = null
            messagingRepository.createGroup(
                myProfile = signedIn.profile,
                name = name,
                description = description,
                avatarPreset = avatarPreset,
                members = members
            ).onSuccess { newGroupId ->
                openGroupConversation(newGroupId)
                onCreated(newGroupId)
            }.onFailure { err ->
                _groupErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
            }
            _groupBusy.value = false
        }
    }

    fun updateGroupSettings(
        groupId: String,
        name: String,
        description: String,
        avatarPreset: String,
        whoCanSend: GroupSendPermission
    ) {
        viewModelScope.launch(ioDispatcher) {
            _groupBusy.value = true
            _groupErrorRes.value = null
            messagingRepository.updateGroupSettings(groupId, name, description, avatarPreset, whoCanSend)
                .onFailure { err -> _groupErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _groupBusy.value = false
        }
    }

    fun addGroupMembers(groupId: String, newMembers: List<PublicUserProfile>) {
        viewModelScope.launch(ioDispatcher) {
            _groupBusy.value = true
            _groupErrorRes.value = null
            messagingRepository.addGroupMembers(groupId, newMembers)
                .onFailure { err -> _groupErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _groupBusy.value = false
        }
    }

    fun removeGroupMember(groupId: String, targetUid: String) {
        viewModelScope.launch(ioDispatcher) {
            _groupBusy.value = true
            _groupErrorRes.value = null
            messagingRepository.removeGroupMember(groupId, targetUid)
                .onFailure { err -> _groupErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _groupBusy.value = false
        }
    }

    fun setGroupMemberRole(groupId: String, targetUid: String, role: GroupRole) {
        viewModelScope.launch(ioDispatcher) {
            _groupBusy.value = true
            _groupErrorRes.value = null
            messagingRepository.setGroupMemberRole(groupId, targetUid, role)
                .onFailure { err -> _groupErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _groupBusy.value = false
        }
    }

    fun leaveGroup(groupId: String, onLeft: () -> Unit) {
        val signedIn = accountAuthState.value as? AccountAuthState.SignedIn ?: return
        viewModelScope.launch(ioDispatcher) {
            _groupBusy.value = true
            messagingRepository.leaveGroup(signedIn.profile.uid, groupId)
                .onSuccess {
                    closeActiveConversation()
                    onLeft()
                }
                .onFailure { err -> _groupErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _groupBusy.value = false
        }
    }

    fun deleteGroup(groupId: String, onDeleted: () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            _groupBusy.value = true
            messagingRepository.deleteGroup(groupId)
                .onSuccess {
                    closeActiveConversation()
                    onDeleted()
                }
                .onFailure { err -> _groupErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _groupBusy.value = false
        }
    }

    fun toggleGroupMute(groupId: String) {
        messagingRepository.toggleGroupMute(groupId)
    }

    // --- Kimlik Doğrulama & Hesap Silme Yardımcıları (M2 & M6) ---

    fun clearAuthFeedback() {
        _authErrorRes.value = null
        _authInfoRes.value = null
    }

    fun setAuthErrorRes(resId: Int) {
        _authErrorRes.value = resId
        _authInfoRes.value = null
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch(ioDispatcher) {
            _authBusy.value = true
            _authErrorRes.value = null
            _authInfoRes.value = null
            authService.signInWithEmail(email, password)
                .onFailure { err -> _authErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _authBusy.value = false
        }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        username: String,
        avatarPreset: String
    ) {
        viewModelScope.launch(ioDispatcher) {
            _authBusy.value = true
            _authErrorRes.value = null
            _authInfoRes.value = null
            authService.signUpWithEmail(email, password, displayName, username, avatarPreset)
                .onFailure { err -> _authErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _authBusy.value = false
        }
    }

    fun signInWithGoogleIdToken(idToken: String) {
        viewModelScope.launch(ioDispatcher) {
            _authBusy.value = true
            _authErrorRes.value = null
            _authInfoRes.value = null
            authService.signInWithGoogleIdToken(idToken)
                .onFailure { err -> _authErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _authBusy.value = false
        }
    }

    fun completeProfileSetup(
        displayName: String,
        username: String,
        avatarPreset: String
    ) {
        viewModelScope.launch(ioDispatcher) {
            _authBusy.value = true
            _authErrorRes.value = null
            _authInfoRes.value = null
            authService.completeProfileSetup(displayName, username, avatarPreset)
                .onSuccess { _authInfoRes.value = R.string.auth_profile_saved_notice }
                .onFailure { err -> _authErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _authBusy.value = false
        }
    }

    fun updateAccountProfile(
        displayName: String,
        username: String,
        avatarPreset: String
    ) {
        viewModelScope.launch(ioDispatcher) {
            _authBusy.value = true
            _authErrorRes.value = null
            _authInfoRes.value = null
            authService.updateProfile(displayName, username, avatarPreset)
                .onSuccess { _authInfoRes.value = R.string.auth_profile_saved_notice }
                .onFailure { err -> _authErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _authBusy.value = false
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _authErrorRes.value = R.string.auth_error_invalid_email
            return
        }
        viewModelScope.launch(ioDispatcher) {
            _authBusy.value = true
            _authErrorRes.value = null
            _authInfoRes.value = null
            authService.sendPasswordReset(email)
                .onSuccess { _authInfoRes.value = R.string.auth_password_reset_sent }
                .onFailure { err -> _authErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err) }
            _authBusy.value = false
        }
    }

    fun signOutAccount() {
        viewModelScope.launch(ioDispatcher) {
            authService.signOut()
            _authErrorRes.value = null
            _authInfoRes.value = null
        }
    }

    fun deleteAccountWithPassword(password: String, onDeleted: () -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            _authBusy.value = true
            _authErrorRes.value = null
            _authInfoRes.value = null
            // M6'da AuthService.deleteAccountWithPassword implemente edilene kadar yer tutucu koruma
            authService.deleteAccountWithPassword(password)
                .onSuccess {
                    onDeleted()
                }
                .onFailure { err ->
                    _authErrorRes.value = FirebaseErrorMapper.toFriendlyMessageRes(err)
                }
            _authBusy.value = false
        }
    }
}
