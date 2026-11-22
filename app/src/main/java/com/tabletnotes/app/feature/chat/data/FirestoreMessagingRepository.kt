package com.tabletnotes.app.feature.chat.data

import android.content.Context
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tabletnotes.app.core.firebase.FirebaseProvider
import com.tabletnotes.app.domain.model.AvatarPreset
import com.tabletnotes.app.domain.model.ChatIdUtils
import com.tabletnotes.app.domain.model.DirectChatSummary
import com.tabletnotes.app.domain.model.FirestoreChatMessage
import com.tabletnotes.app.domain.model.GroupChatSummary
import com.tabletnotes.app.domain.model.GroupRole
import com.tabletnotes.app.domain.model.GroupSendPermission
import com.tabletnotes.app.domain.model.MessageDeliveryStatus
import com.tabletnotes.app.domain.model.PublicUserProfile
import com.tabletnotes.app.feature.chat.auth.FirebaseNotConfiguredException
import com.tabletnotes.app.feature.chat.auth.GroupMemberLimitExceededException
import com.tabletnotes.app.feature.chat.auth.MessageTooLongException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.tasks.await

interface MessagingRepository {
    val directChats: StateFlow<List<DirectChatSummary>>
    val groups: StateFlow<List<GroupChatSummary>>
    val activeMessages: StateFlow<List<FirestoreChatMessage>>
    val hasMoreOlderMessages: StateFlow<Boolean>
    val isLoadingOlderMessages: StateFlow<Boolean>

    fun attachOverviewListeners(myUid: String)
    fun detachOverviewListeners()

    fun attachActiveConversationListener(conversationId: String, isGroup: Boolean)
    fun detachActiveConversationListener()

    suspend fun loadOlderMessages(conversationId: String, isGroup: Boolean): Result<Unit>

    suspend fun sendDirectMessage(
        myProfile: PublicUserProfile,
        peerProfile: PublicUserProfile,
        text: String,
        retryLocalId: String? = null
    ): Result<Unit>

    suspend fun markDirectChatAsRead(myUid: String, chatId: String)

    suspend fun sendGroupMessage(
        myProfile: PublicUserProfile,
        groupId: String,
        text: String,
        retryLocalId: String? = null
    ): Result<Unit>

    suspend fun markGroupAsRead(myUid: String, groupId: String)

    suspend fun deleteOwnMessage(conversationId: String, isGroup: Boolean, messageId: String): Result<Unit>
    fun discardFailedLocalMessage(localId: String)

    suspend fun createGroup(
        myProfile: PublicUserProfile,
        name: String,
        description: String,
        avatarPreset: String,
        members: List<PublicUserProfile>
    ): Result<String>

    suspend fun updateGroupSettings(
        groupId: String,
        name: String,
        description: String,
        avatarPreset: String,
        whoCanSend: GroupSendPermission
    ): Result<Unit>

    suspend fun addGroupMembers(groupId: String, newMembers: List<PublicUserProfile>): Result<Unit>
    suspend fun removeGroupMember(groupId: String, targetUid: String): Result<Unit>
    suspend fun setGroupMemberRole(groupId: String, targetUid: String, newRole: GroupRole): Result<Unit>
    suspend fun leaveGroup(myUid: String, groupId: String): Result<Unit>
    suspend fun deleteGroup(groupId: String): Result<Unit>

    fun isGroupMuted(groupId: String): Boolean
    fun toggleGroupMute(groupId: String): Boolean
}

@Singleton
class FirestoreMessagingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val firebaseProvider: FirebaseProvider
) : MessagingRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutePrefs = context.getSharedPreferences("tabletnotes_group_mutes", Context.MODE_PRIVATE)

    private val _directChats = MutableStateFlow<List<DirectChatSummary>>(emptyList())
    override val directChats: StateFlow<List<DirectChatSummary>> = _directChats.asStateFlow()

    private val _groups = MutableStateFlow<List<GroupChatSummary>>(emptyList())
    override val groups: StateFlow<List<GroupChatSummary>> = _groups.asStateFlow()

    private val _remoteLiveMessages = MutableStateFlow<List<FirestoreChatMessage>>(emptyList())
    private val _olderPagedMessages = MutableStateFlow<List<FirestoreChatMessage>>(emptyList())
    private val _optimisticMessages = MutableStateFlow<List<Pair<String, FirestoreChatMessage>>>(emptyList())

    private val _activeConversationKey = MutableStateFlow<String?>(null)

    override val activeMessages: StateFlow<List<FirestoreChatMessage>> = combine(
        _olderPagedMessages,
        _remoteLiveMessages,
        _optimisticMessages,
        _activeConversationKey
    ) { older, live, optimistic, activeKey ->
        val byId = LinkedHashMap<String, FirestoreChatMessage>()
        (older + live).sortedBy { it.createdAtMillis }.forEach { byId[it.id] = it }
        optimistic.filter { it.first == activeKey }.forEach { (_, msg) ->
            byId[msg.id] = msg
        }
        byId.values.sortedBy { it.createdAtMillis }
    }.stateIn(repoScope, SharingStarted.Eagerly, emptyList())

    private val _hasMoreOlderMessages = MutableStateFlow(false)
    override val hasMoreOlderMessages: StateFlow<Boolean> = _hasMoreOlderMessages.asStateFlow()

    private val _isLoadingOlderMessages = MutableStateFlow(false)
    override val isLoadingOlderMessages: StateFlow<Boolean> = _isLoadingOlderMessages.asStateFlow()

    private var directChatsListener: ListenerRegistration? = null
    private var groupsListener: ListenerRegistration? = null
    private var activeMessagesListener: ListenerRegistration? = null

    private var activeOverviewUid: String? = null
    private var oldestLoadedSnapshot: DocumentSnapshot? = null
    private var newestSnapshotsTail: DocumentSnapshot? = null

    override fun attachOverviewListeners(myUid: String) {
        if (activeOverviewUid == myUid && directChatsListener != null && groupsListener != null) return
        detachOverviewListeners()

        val firestore = firebaseProvider.firestoreOrNull ?: return
        activeOverviewUid = myUid

        // Kota dostu: limit(30), composite index gerektirmemesi için sıralama bellek içinde yapılır
        directChatsListener = firestore.collection("direct_chats")
            .whereArrayContains("participants", myUid)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _directChats.value = snapshot.documents
                    .mapNotNull { it.toDirectChatSummary(myUid) }
                    .sortedByDescending { it.lastMessageAtMillis }
            }

        groupsListener = firestore.collection("groups")
            .whereArrayContains("memberIds", myUid)
            .limit(30)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _groups.value = snapshot.documents
                    .mapNotNull { it.toGroupChatSummary(myUid, isGroupMuted(it.id)) }
                    .sortedByDescending { it.lastMessageAtMillis }
            }
    }

    override fun detachOverviewListeners() {
        directChatsListener?.remove()
        directChatsListener = null
        groupsListener?.remove()
        groupsListener = null
        activeOverviewUid = null
        _directChats.value = emptyList()
        _groups.value = emptyList()
    }

    override fun attachActiveConversationListener(conversationId: String, isGroup: Boolean) {
        val convKey = conversationKey(conversationId, isGroup)
        if (_activeConversationKey.value == convKey && activeMessagesListener != null) return
        detachActiveConversationListener()

        val firestore = firebaseProvider.firestoreOrNull ?: return
        _activeConversationKey.value = convKey

        val collectionPath = if (isGroup) "groups" else "direct_chats"
        activeMessagesListener = firestore.collection(collectionPath)
            .document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val docs = snapshot.documents
                newestSnapshotsTail = docs.lastOrNull()
                if (_olderPagedMessages.value.isEmpty()) {
                    oldestLoadedSnapshot = newestSnapshotsTail
                    _hasMoreOlderMessages.value = docs.size >= PAGE_SIZE
                }
                val parsed = docs.mapNotNull { doc ->
                    doc.toFirestoreChatMessage(isGroup = isGroup)
                }.reversed()
                _remoteLiveMessages.value = parsed
            }
    }

    override fun detachActiveConversationListener() {
        activeMessagesListener?.remove()
        activeMessagesListener = null
        _activeConversationKey.value = null
        _remoteLiveMessages.value = emptyList()
        _olderPagedMessages.value = emptyList()
        oldestLoadedSnapshot = null
        newestSnapshotsTail = null
        _hasMoreOlderMessages.value = false
        _isLoadingOlderMessages.value = false
    }

    override suspend fun loadOlderMessages(conversationId: String, isGroup: Boolean): Result<Unit> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val anchor = oldestLoadedSnapshot ?: return@runCatching
            if (_isLoadingOlderMessages.value) return@runCatching

            _isLoadingOlderMessages.value = true
            try {
                val collectionPath = if (isGroup) "groups" else "direct_chats"
                val snap = firestore.collection(collectionPath)
                    .document(conversationId)
                    .collection("messages")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .startAfter(anchor)
                    .limit(PAGE_SIZE.toLong())
                    .get()
                    .await()

                val docs = snap.documents
                if (docs.isNotEmpty()) {
                    oldestLoadedSnapshot = docs.last()
                    val more = docs.mapNotNull { it.toFirestoreChatMessage(isGroup = isGroup) }.reversed()
                    _olderPagedMessages.value = (more + _olderPagedMessages.value).distinctBy { it.id }
                }
                _hasMoreOlderMessages.value = docs.size >= PAGE_SIZE
            } finally {
                _isLoadingOlderMessages.value = false
            }
        }

    override suspend fun sendDirectMessage(
        myProfile: PublicUserProfile,
        peerProfile: PublicUserProfile,
        text: String,
        retryLocalId: String?
    ): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || cleanText.length > MAX_MESSAGE_LENGTH) {
            return Result.failure(MessageTooLongException())
        }

        val (uidA, uidB) = ChatIdUtils.sortedPair(myProfile.uid, peerProfile.uid)
        val chatId = "${uidA}_${uidB}"
        val convKey = conversationKey(chatId, isGroup = false)
        val localId = retryLocalId ?: "local_${UUID.randomUUID()}"

        upsertOptimisticMessage(
            convKey = convKey,
            msg = FirestoreChatMessage(
                id = localId,
                senderId = myProfile.uid,
                senderName = myProfile.displayName,
                text = cleanText,
                createdAtMillis = System.currentTimeMillis(),
                deliveryStatus = MessageDeliveryStatus.SENDING
            )
        )

        return runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val chatRef = firestore.collection("direct_chats").document(chatId)
            val existingChat = chatRef.get().await()

            val participantProfiles = mapOf(
                myProfile.uid to mapOf(
                    "uid" to myProfile.uid,
                    "username" to myProfile.username,
                    "displayName" to myProfile.displayName,
                    "avatarPreset" to myProfile.avatarPreset
                ),
                peerProfile.uid to mapOf(
                    "uid" to peerProfile.uid,
                    "username" to peerProfile.username,
                    "displayName" to peerProfile.displayName,
                    "avatarPreset" to peerProfile.avatarPreset
                )
            )

            if (!existingChat.exists()) {
                chatRef.set(
                    mapOf(
                        "chatId" to chatId,
                        "participants" to listOf(uidA, uidB),
                        "participantProfiles" to participantProfiles,
                        "lastMessageText" to "",
                        "lastMessageSenderId" to "",
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "lastReadAt" to mapOf(myProfile.uid to FieldValue.serverTimestamp()),
                        "unreadCount" to mapOf(myProfile.uid to 0, peerProfile.uid to 0),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val msgRef = chatRef.collection("messages").document()
            val batch = firestore.batch()
            batch.set(
                msgRef,
                mapOf(
                    "messageId" to msgRef.id,
                    "senderId" to myProfile.uid,
                    "senderName" to myProfile.displayName,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                chatRef,
                mapOf(
                    "participantProfiles" to participantProfiles,
                    "lastMessageText" to cleanText.take(120),
                    "lastMessageSenderId" to myProfile.uid,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastReadAt.${myProfile.uid}" to FieldValue.serverTimestamp(),
                    "unreadCount.${myProfile.uid}" to 0,
                    "unreadCount.${peerProfile.uid}" to FieldValue.increment(1),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            removeOptimisticMessage(localId)
            Unit
        }.onFailure {
            markOptimisticMessageFailed(localId)
        }
    }

    override suspend fun markDirectChatAsRead(myUid: String, chatId: String) {
        val firestore = firebaseProvider.firestoreOrNull ?: return
        val existing = _directChats.value.firstOrNull { it.chatId == chatId } ?: return
        if (existing.unreadCount <= 0) return

        runCatching {
            firestore.collection("direct_chats")
                .document(chatId)
                .update(
                    mapOf(
                        "unreadCount.$myUid" to 0,
                        "lastReadAt.$myUid" to FieldValue.serverTimestamp()
                    )
                )
                .await()
        }
    }

    override suspend fun sendGroupMessage(
        myProfile: PublicUserProfile,
        groupId: String,
        text: String,
        retryLocalId: String?
    ): Result<Unit> {
        val cleanText = text.trim()
        if (cleanText.isEmpty() || cleanText.length > MAX_MESSAGE_LENGTH) {
            return Result.failure(MessageTooLongException())
        }

        val convKey = conversationKey(groupId, isGroup = true)
        val localId = retryLocalId ?: "local_${UUID.randomUUID()}"

        upsertOptimisticMessage(
            convKey = convKey,
            msg = FirestoreChatMessage(
                id = localId,
                senderId = myProfile.uid,
                senderName = myProfile.displayName,
                text = cleanText,
                createdAtMillis = System.currentTimeMillis(),
                deliveryStatus = MessageDeliveryStatus.SENDING
            )
        )

        return runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val groupRef = firestore.collection("groups").document(groupId)
            val msgRef = groupRef.collection("messages").document()

            val batch = firestore.batch()
            batch.set(
                msgRef,
                mapOf(
                    "messageId" to msgRef.id,
                    "senderId" to myProfile.uid,
                    "senderName" to myProfile.displayName,
                    "text" to cleanText,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                groupRef,
                mapOf(
                    "lastMessageText" to cleanText.take(120),
                    "lastMessageSenderId" to myProfile.uid,
                    "lastMessageSenderName" to myProfile.displayName,
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "lastReadAt.${myProfile.uid}" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            removeOptimisticMessage(localId)
            Unit
        }.onFailure {
            markOptimisticMessageFailed(localId)
        }
    }

    override suspend fun markGroupAsRead(myUid: String, groupId: String) {
        val firestore = firebaseProvider.firestoreOrNull ?: return
        val group = _groups.value.firstOrNull { it.groupId == groupId } ?: return
        if (!group.hasUnread) return

        runCatching {
            firestore.collection("groups")
                .document(groupId)
                .update("lastReadAt.$myUid", FieldValue.serverTimestamp())
                .await()
        }
    }

    override suspend fun deleteOwnMessage(
        conversationId: String,
        isGroup: Boolean,
        messageId: String
    ): Result<Unit> = runCatching {
        if (messageId.startsWith("local_")) {
            removeOptimisticMessage(messageId)
            return@runCatching
        }
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val col = if (isGroup) "groups" else "direct_chats"
        firestore.collection(col)
            .document(conversationId)
            .collection("messages")
            .document(messageId)
            .delete()
            .await()
        Unit
    }

    override fun discardFailedLocalMessage(localId: String) {
        removeOptimisticMessage(localId)
    }

    override suspend fun createGroup(
        myProfile: PublicUserProfile,
        name: String,
        description: String,
        avatarPreset: String,
        members: List<PublicUserProfile>
    ): Result<String> = runCatching {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val cleanName = name.trim().take(60)
        if (cleanName.isEmpty()) {
            throw IllegalArgumentException("Group name required")
        }

        val uniqueMembers = (listOf(myProfile) + members).distinctBy { it.uid }
        if (uniqueMembers.size > MAX_GROUP_MEMBERS) {
            throw GroupMemberLimitExceededException()
        }

        val memberIds = uniqueMembers.map { it.uid }
        val roles = uniqueMembers.associate { member ->
            member.uid to if (member.uid == myProfile.uid) GroupRole.OWNER.wireName else GroupRole.MEMBER.wireName
        }
        val profilesMap = uniqueMembers.associate { member ->
            member.uid to mapOf(
                "uid" to member.uid,
                "username" to member.username,
                "displayName" to member.displayName,
                "avatarPreset" to member.avatarPreset
            )
        }

        val groupRef = firestore.collection("groups").document()
        groupRef.set(
            mapOf(
                "groupId" to groupRef.id,
                "name" to cleanName,
                "description" to description.trim().take(300),
                "avatarPreset" to AvatarPreset.fromId(avatarPreset).id,
                "ownerId" to myProfile.uid,
                "memberIds" to memberIds,
                "roles" to roles,
                "memberProfiles" to profilesMap,
                "whoCanSend" to GroupSendPermission.EVERYONE.wireName,
                "lastMessageText" to "",
                "lastMessageSenderId" to "",
                "lastMessageSenderName" to "",
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastReadAt" to mapOf(myProfile.uid to FieldValue.serverTimestamp()),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        groupRef.id
    }

    override suspend fun updateGroupSettings(
        groupId: String,
        name: String,
        description: String,
        avatarPreset: String,
        whoCanSend: GroupSendPermission
    ): Result<Unit> = runCatching {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val cleanName = name.trim().take(60)
        if (cleanName.isEmpty()) throw IllegalArgumentException("Group name required")

        firestore.collection("groups")
            .document(groupId)
            .update(
                mapOf(
                    "name" to cleanName,
                    "description" to description.trim().take(300),
                    "avatarPreset" to AvatarPreset.fromId(avatarPreset).id,
                    "whoCanSend" to whoCanSend.wireName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }

    override suspend fun addGroupMembers(
        groupId: String,
        newMembers: List<PublicUserProfile>
    ): Result<Unit> = runCatching {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val groupRef = firestore.collection("groups").document(groupId)
        val snap = groupRef.get().await()
        val currentMemberIds = (snap.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

        val toAdd = newMembers.filter { it.uid !in currentMemberIds }
        if (toAdd.isEmpty()) return@runCatching
        if (currentMemberIds.size + toAdd.size > MAX_GROUP_MEMBERS) {
            throw GroupMemberLimitExceededException()
        }

        val updates = hashMapOf<String, Any>(
            "memberIds" to (currentMemberIds + toAdd.map { it.uid }),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        toAdd.forEach { member ->
            updates["roles.${member.uid}"] = GroupRole.MEMBER.wireName
            updates["memberProfiles.${member.uid}"] = mapOf(
                "uid" to member.uid,
                "username" to member.username,
                "displayName" to member.displayName,
                "avatarPreset" to member.avatarPreset
            )
        }
        groupRef.update(updates).await()
        Unit
    }

    override suspend fun removeGroupMember(groupId: String, targetUid: String): Result<Unit> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val groupRef = firestore.collection("groups").document(groupId)
            val snap = groupRef.get().await()
            val currentMemberIds = (snap.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val currentRoles = (snap.get("roles") as? Map<*, *>)?.entries
                ?.mapNotNull { (k, v) -> (k as? String)?.let { ks -> (v as? String)?.let { vs -> ks to vs } } }
                ?.toMap()
                ?.toMutableMap()
                ?: mutableMapOf()
            val currentProfiles = (snap.get("memberProfiles") as? Map<*, *>)?.entries
                ?.mapNotNull { (k, v) -> (k as? String)?.let { ks -> v?.let { ks to it } } }
                ?.toMap()
                ?.toMutableMap()
                ?: mutableMapOf()

            currentRoles.remove(targetUid)
            currentProfiles.remove(targetUid)

            groupRef.update(
                mapOf(
                    "memberIds" to currentMemberIds.filter { it != targetUid },
                    "roles" to currentRoles,
                    "memberProfiles" to currentProfiles,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }

    override suspend fun setGroupMemberRole(
        groupId: String,
        targetUid: String,
        newRole: GroupRole
    ): Result<Unit> = runCatching {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        firestore.collection("groups")
            .document(groupId)
            .update(
                mapOf(
                    "roles.$targetUid" to newRole.wireName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
        Unit
    }

    override suspend fun leaveGroup(myUid: String, groupId: String): Result<Unit> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val groupRef = firestore.collection("groups").document(groupId)
            val snap = groupRef.get().await()
            val currentMemberIds = (snap.get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            val currentRoles = (snap.get("roles") as? Map<*, *>)?.entries
                ?.mapNotNull { (k, v) -> (k as? String)?.let { ks -> (v as? String)?.let { vs -> ks to vs } } }
                ?.toMap()
                ?.toMutableMap()
                ?: mutableMapOf()

            currentRoles.remove(myUid)

            groupRef.update(
                mapOf(
                    "memberIds" to currentMemberIds.filter { it != myUid },
                    "roles" to currentRoles,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }

    override suspend fun deleteGroup(groupId: String): Result<Unit> = runCatching {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        firestore.collection("groups").document(groupId).delete().await()
        Unit
    }

    override fun isGroupMuted(groupId: String): Boolean =
        mutePrefs.getBoolean("mute_$groupId", false)

    override fun toggleGroupMute(groupId: String): Boolean {
        val next = !isGroupMuted(groupId)
        mutePrefs.edit().putBoolean("mute_$groupId", next).apply()
        _groups.value = _groups.value.map { grp ->
            if (grp.groupId == groupId) grp.copy(isMutedLocally = next) else grp
        }
        return next
    }

    private fun conversationKey(id: String, isGroup: Boolean): String =
        if (isGroup) "group:$id" else "direct:$id"

    private fun upsertOptimisticMessage(convKey: String, msg: FirestoreChatMessage) {
        _optimisticMessages.value = _optimisticMessages.value.filterNot { it.second.id == msg.id } + (convKey to msg)
    }

    private fun markOptimisticMessageFailed(localId: String) {
        _optimisticMessages.value = _optimisticMessages.value.map { (key, msg) ->
            if (msg.id == localId) key to msg.copy(deliveryStatus = MessageDeliveryStatus.FAILED) else key to msg
        }
    }

    private fun removeOptimisticMessage(localId: String) {
        _optimisticMessages.value = _optimisticMessages.value.filterNot { it.second.id == localId }
    }

    companion object {
        const val PAGE_SIZE = 30
        const val MAX_MESSAGE_LENGTH = 2000
        const val MAX_GROUP_MEMBERS = 50
    }
}

private fun DocumentSnapshot.toDirectChatSummary(myUid: String): DirectChatSummary? {
    val participants = (get("participants") as? List<*>)?.mapNotNull { it as? String } ?: return null
    val peerUid = participants.firstOrNull { it != myUid } ?: return null
    val profilesMap = get("participantProfiles") as? Map<*, *>
    val peerMap = profilesMap?.get(peerUid) as? Map<*, *>
    val peerProfile = PublicUserProfile(
        uid = peerUid,
        username = peerMap?.get("username") as? String ?: "kullanici",
        displayName = peerMap?.get("displayName") as? String ?: "Kullanıcı",
        avatarPreset = peerMap?.get("avatarPreset") as? String ?: AvatarPreset.COBALT_INITIALS.id
    )
    val unreadMap = get("unreadCount") as? Map<*, *>
    val unread = (unreadMap?.get(myUid) as? Number)?.toInt() ?: 0

    return DirectChatSummary(
        chatId = id,
        peerProfile = peerProfile,
        lastMessageText = getString("lastMessageText") ?: "",
        lastMessageSenderId = getString("lastMessageSenderId"),
        lastMessageAtMillis = getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L,
        unreadCount = unread
    )
}

private fun DocumentSnapshot.toGroupChatSummary(myUid: String, isMuted: Boolean): GroupChatSummary? {
    val memberIds = (get("memberIds") as? List<*>)?.mapNotNull { it as? String } ?: return null
    val rolesRaw = get("roles") as? Map<*, *> ?: emptyMap<Any, Any>()
    val roles = rolesRaw.entries.mapNotNull { (k, v) ->
        val uid = k as? String ?: return@mapNotNull null
        val role = GroupRole.fromWire(v as? String)
        uid to role
    }.toMap()

    val profilesRaw = get("memberProfiles") as? Map<*, *> ?: emptyMap<Any, Any>()
    val memberProfiles = memberIds.associateWith { uid ->
        val m = profilesRaw[uid] as? Map<*, *>
        PublicUserProfile(
            uid = uid,
            username = m?.get("username") as? String ?: "uye",
            displayName = m?.get("displayName") as? String ?: "Üye",
            avatarPreset = m?.get("avatarPreset") as? String ?: AvatarPreset.COBALT_INITIALS.id
        )
    }

    val lastReadRaw = get("lastReadAt") as? Map<*, *>
    val myLastRead = (lastReadRaw?.get(myUid) as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L

    return GroupChatSummary(
        groupId = id,
        name = getString("name") ?: "Grup",
        description = getString("description") ?: "",
        avatarPreset = getString("avatarPreset") ?: AvatarPreset.PURPLE_INITIALS.id,
        ownerId = getString("ownerId") ?: "",
        memberIds = memberIds,
        roles = roles,
        memberProfiles = memberProfiles,
        whoCanSend = GroupSendPermission.fromWire(getString("whoCanSend")),
        lastMessageText = getString("lastMessageText") ?: "",
        lastMessageSenderId = getString("lastMessageSenderId"),
        lastMessageSenderName = getString("lastMessageSenderName"),
        lastMessageAtMillis = getTimestamp("lastMessageAt")?.toDate()?.time ?: 0L,
        lastReadAtMillis = myLastRead,
        isMutedLocally = isMuted
    )
}

private fun DocumentSnapshot.toFirestoreChatMessage(isGroup: Boolean): FirestoreChatMessage? {
    val senderId = getString("senderId") ?: return null
    val text = getString("text") ?: return null
    val senderName = getString("senderName") ?: if (isGroup) "Üye" else ""
    val createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
    return FirestoreChatMessage(
        id = id,
        senderId = senderId,
        senderName = senderName,
        text = text,
        createdAtMillis = createdAtMillis,
        deliveryStatus = MessageDeliveryStatus.SENT
    )
}
