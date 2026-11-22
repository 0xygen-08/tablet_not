package com.tabletnotes.app.feature.chat.friends

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.tabletnotes.app.core.firebase.FirebaseProvider
import com.tabletnotes.app.domain.model.AvatarPreset
import com.tabletnotes.app.domain.model.BlockedUserRecord
import com.tabletnotes.app.domain.model.ChatIdUtils
import com.tabletnotes.app.domain.model.FriendshipRecord
import com.tabletnotes.app.domain.model.FriendshipStatus
import com.tabletnotes.app.domain.model.PublicUserProfile
import com.tabletnotes.app.domain.model.UsernameValidator
import com.tabletnotes.app.feature.chat.auth.AlreadyFriendsOrPendingException
import com.tabletnotes.app.feature.chat.auth.BlockedUserInteractionException
import com.tabletnotes.app.feature.chat.auth.CannotFriendSelfException
import com.tabletnotes.app.feature.chat.auth.FirebaseNotConfiguredException
import com.tabletnotes.app.feature.chat.auth.InvalidUsernameFormatException
import com.tabletnotes.app.feature.chat.auth.UserNotFoundByUsernameException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

interface FriendsRepository {
    val friendships: StateFlow<List<FriendshipRecord>>
    val blockedUsers: StateFlow<List<BlockedUserRecord>>

    fun attachListeners(myUid: String)
    fun detachListeners()

    suspend fun findUserByExactUsername(rawUsername: String): Result<PublicUserProfile>

    suspend fun sendFriendRequest(
        myProfile: PublicUserProfile,
        targetProfile: PublicUserProfile
    ): Result<Unit>

    suspend fun acceptFriendRequest(myUid: String, otherUid: String): Result<Unit>
    suspend fun removeOrCancelFriendship(myUid: String, otherUid: String): Result<Unit>

    suspend fun blockUser(myUid: String, targetProfile: PublicUserProfile): Result<Unit>
    suspend fun unblockUser(myUid: String, blockedUid: String): Result<Unit>

    suspend fun submitReport(
        reporterUid: String,
        targetType: String,
        targetUserId: String?,
        targetChatId: String?,
        targetMessageId: String?,
        reason: String
    ): Result<Unit>
}

@Singleton
class FirestoreFriendsRepository @Inject constructor(
    private val firebaseProvider: FirebaseProvider
) : FriendsRepository {

    private val _friendships = MutableStateFlow<List<FriendshipRecord>>(emptyList())
    override val friendships: StateFlow<List<FriendshipRecord>> = _friendships.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<BlockedUserRecord>>(emptyList())
    override val blockedUsers: StateFlow<List<BlockedUserRecord>> = _blockedUsers.asStateFlow()

    private var friendshipsListener: ListenerRegistration? = null
    private var blocksListener: ListenerRegistration? = null
    private var activeUid: String? = null

    override fun attachListeners(myUid: String) {
        if (activeUid == myUid && friendshipsListener != null) return
        detachListeners()

        val firestore = firebaseProvider.firestoreOrNull ?: return
        activeUid = myUid

        friendshipsListener = firestore.collection("friendships")
            .whereArrayContains("users", myUid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _friendships.value = snapshot.documents.mapNotNull { doc ->
                    doc.toFriendshipRecord(myUid)
                }
            }

        blocksListener = firestore.collection("blocks")
            .whereEqualTo("blockerUid", myUid)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                _blockedUsers.value = snapshot.documents.mapNotNull { doc ->
                    doc.toBlockedUserRecord()
                }
            }
    }

    override fun detachListeners() {
        friendshipsListener?.remove()
        friendshipsListener = null
        blocksListener?.remove()
        blocksListener = null
        activeUid = null
        _friendships.value = emptyList()
        _blockedUsers.value = emptyList()
    }

    override suspend fun findUserByExactUsername(rawUsername: String): Result<PublicUserProfile> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val cleanUsername = UsernameValidator.normalize(rawUsername)
            if (!UsernameValidator.isValid(cleanUsername)) {
                throw InvalidUsernameFormatException()
            }

            // Güvenlik kuralı gereği koleksiyon listeleme veya önek arama asla yapılmaz; yalnızca tekil GET!
            val usernameDoc = firestore.collection("usernames")
                .document(cleanUsername)
                .get()
                .await()
            if (!usernameDoc.exists()) {
                throw UserNotFoundByUsernameException()
            }

            val uid = usernameDoc.getString("uid") ?: throw UserNotFoundByUsernameException()
            val userDoc = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            if (!userDoc.exists()) {
                throw UserNotFoundByUsernameException()
            }

            PublicUserProfile(
                uid = uid,
                username = userDoc.getString("username") ?: cleanUsername,
                displayName = userDoc.getString("displayName") ?: "@$cleanUsername",
                avatarPreset = userDoc.getString("avatarPreset") ?: AvatarPreset.COBALT_INITIALS.id,
                createdAtMillis = userDoc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                updatedAtMillis = userDoc.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
            )
        }

    override suspend fun sendFriendRequest(
        myProfile: PublicUserProfile,
        targetProfile: PublicUserProfile
    ): Result<Unit> = runCatching {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        if (myProfile.uid == targetProfile.uid) {
            throw CannotFriendSelfException()
        }

        val blockDoc = firestore.collection("blocks")
            .document(ChatIdUtils.blockIdFor(myProfile.uid, targetProfile.uid))
            .get()
            .await()
        if (blockDoc.exists()) {
            throw BlockedUserInteractionException()
        }

        val (uidA, uidB) = ChatIdUtils.sortedPair(myProfile.uid, targetProfile.uid)
        val pairId = "${uidA}_${uidB}"
        val friendshipRef = firestore.collection("friendships").document(pairId)
        val existing = friendshipRef.get().await()
        if (existing.exists()) {
            throw AlreadyFriendsOrPendingException()
        }

        val profileMap = mapOf(
            myProfile.uid to mapOf(
                "uid" to myProfile.uid,
                "username" to myProfile.username,
                "displayName" to myProfile.displayName,
                "avatarPreset" to myProfile.avatarPreset
            ),
            targetProfile.uid to mapOf(
                "uid" to targetProfile.uid,
                "username" to targetProfile.username,
                "displayName" to targetProfile.displayName,
                "avatarPreset" to targetProfile.avatarPreset
            )
        )

        friendshipRef.set(
            mapOf(
                "pairId" to pairId,
                "uidA" to uidA,
                "uidB" to uidB,
                "users" to listOf(uidA, uidB),
                "status" to "pending",
                "requesterId" to myProfile.uid,
                "receiverId" to targetProfile.uid,
                "userProfiles" to profileMap,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }

    override suspend fun acceptFriendRequest(myUid: String, otherUid: String): Result<Unit> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val pairId = ChatIdUtils.pairIdFor(myUid, otherUid)
            firestore.collection("friendships")
                .document(pairId)
                .update(
                    mapOf(
                        "status" to "accepted",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Unit
        }

    override suspend fun removeOrCancelFriendship(myUid: String, otherUid: String): Result<Unit> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val pairId = ChatIdUtils.pairIdFor(myUid, otherUid)
            firestore.collection("friendships")
                .document(pairId)
                .delete()
                .await()
            Unit
        }

    override suspend fun blockUser(myUid: String, targetProfile: PublicUserProfile): Result<Unit> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            if (myUid == targetProfile.uid) {
                throw CannotFriendSelfException()
            }

            val blockId = ChatIdUtils.blockIdFor(myUid, targetProfile.uid)
            val pairId = ChatIdUtils.pairIdFor(myUid, targetProfile.uid)
            val blockRef = firestore.collection("blocks").document(blockId)
            val friendshipRef = firestore.collection("friendships").document(pairId)
            val friendshipSnap = friendshipRef.get().await()

            val batch = firestore.batch()
            batch.set(
                blockRef,
                mapOf(
                    "blockerUid" to myUid,
                    "blockedUid" to targetProfile.uid,
                    "blockedUsername" to targetProfile.username,
                    "blockedDisplayName" to targetProfile.displayName,
                    "blockedAvatarPreset" to targetProfile.avatarPreset,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            if (friendshipSnap.exists()) {
                batch.delete(friendshipRef)
            }
            batch.commit().await()
            Unit
        }

    override suspend fun unblockUser(myUid: String, blockedUid: String): Result<Unit> =
        runCatching {
            val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
            val blockId = ChatIdUtils.blockIdFor(myUid, blockedUid)
            firestore.collection("blocks")
                .document(blockId)
                .delete()
                .await()
            Unit
        }

    override suspend fun submitReport(
        reporterUid: String,
        targetType: String,
        targetUserId: String?,
        targetChatId: String?,
        targetMessageId: String?,
        reason: String
    ): Result<Unit> = runCatching {
        val firestore = firebaseProvider.firestoreOrNull ?: throw FirebaseNotConfiguredException()
        val cleanReason = reason.trim().take(500)
        if (cleanReason.isEmpty()) {
            throw IllegalArgumentException("Report reason cannot be empty")
        }

        val payload = hashMapOf<String, Any?>(
            "reporterId" to reporterUid,
            "targetType" to targetType,
            "targetUserId" to targetUserId,
            "targetChatId" to targetChatId,
            "targetMessageId" to targetMessageId,
            "reason" to cleanReason,
            "createdAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("reports")
            .add(payload)
            .await()
        Unit
    }
}

private fun DocumentSnapshot.toFriendshipRecord(myUid: String): FriendshipRecord? {
    val pairId = getString("pairId") ?: id
    val users = (get("users") as? List<*>)?.mapNotNull { it as? String } ?: return null
    val otherUid = users.firstOrNull { it != myUid } ?: return null
    val statusWire = getString("status") ?: "pending"
    val status = if (statusWire == "accepted") FriendshipStatus.ACCEPTED else FriendshipStatus.PENDING
    val requesterId = getString("requesterId") ?: ""
    val receiverId = getString("receiverId") ?: ""

    val profilesMap = get("userProfiles") as? Map<*, *>
    val otherMap = profilesMap?.get(otherUid) as? Map<*, *>
    val otherProfile = PublicUserProfile(
        uid = otherUid,
        username = otherMap?.get("username") as? String ?: "kullanici",
        displayName = otherMap?.get("displayName") as? String ?: "Kullanıcı",
        avatarPreset = otherMap?.get("avatarPreset") as? String ?: AvatarPreset.COBALT_INITIALS.id
    )

    return FriendshipRecord(
        pairId = pairId,
        otherProfile = otherProfile,
        status = status,
        requesterId = requesterId,
        receiverId = receiverId,
        isIncoming = status == FriendshipStatus.PENDING && receiverId == myUid,
        updatedAtMillis = getTimestamp("updatedAt")?.toDate()?.time ?: 0L
    )
}

private fun DocumentSnapshot.toBlockedUserRecord(): BlockedUserRecord? {
    val blockedUid = getString("blockedUid") ?: return null
    val blockedUsername = getString("blockedUsername") ?: "kullanici"
    val blockedDisplayName = getString("blockedDisplayName") ?: "@$blockedUsername"
    val avatar = getString("blockedAvatarPreset") ?: AvatarPreset.CORAL_INITIALS.id
    return BlockedUserRecord(
        blockId = id,
        blockedProfile = PublicUserProfile(
            uid = blockedUid,
            username = blockedUsername,
            displayName = blockedDisplayName,
            avatarPreset = avatar
        ),
        createdAtMillis = getTimestamp("createdAt")?.toDate()?.time ?: 0L
    )
}
