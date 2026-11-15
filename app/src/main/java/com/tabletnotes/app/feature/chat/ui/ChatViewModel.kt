package com.tabletnotes.app.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabletnotes.app.core.common.IoDispatcher
import com.tabletnotes.app.core.crypto.CryptoService
import com.tabletnotes.app.core.database.ChatContactEntity
import com.tabletnotes.app.core.database.ChatDao
import com.tabletnotes.app.core.database.ChatMessageEntity
import com.tabletnotes.app.core.network.ConnectivityObserver
import com.tabletnotes.app.domain.model.ChatContact
import com.tabletnotes.app.domain.model.ChatMessage
import com.tabletnotes.app.domain.model.ChatMessageType
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteAttachmentMeta
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.RelayTransferState
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.domain.model.UserProfile
import com.tabletnotes.app.domain.repository.NoteRepository
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.chat.auth.AuthService
import com.tabletnotes.app.feature.chat.relay.NoteFileTooLargeException
import com.tabletnotes.app.feature.chat.relay.RelayStorage
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.sin
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatDao: ChatDao,
    private val noteRepository: NoteRepository,
    private val authService: AuthService,
    private val cryptoService: CryptoService,
    private val relayStorage: RelayStorage,
    private val connectivityObserver: ConnectivityObserver,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    val currentUser: StateFlow<UserProfile> = authService.currentUser
    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedContactId = MutableStateFlow<String?>(null)
    val selectedContactId: StateFlow<String?> = _selectedContactId.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    val availableNotes: StateFlow<List<NoteSummary>> = noteRepository.observeNoteSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contacts: StateFlow<List<ChatContact>> = combine(
        chatDao.observeContacts(),
        _searchQuery
    ) { entities, query ->
        val list = entities.map { it.toDomainModel() }
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim().lowercase()
            list.filter {
                it.displayName.lowercase().contains(q) ||
                    it.handleOrEmail.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<ChatMessage>> = _selectedContactId
        .flatMapLatest { contactId ->
            if (contactId == null) {
                flowOf(emptyList())
            } else {
                chatDao.observeMessagesForContact(contactId).map { list ->
                    list.map { it.toDomainModel() }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(ioDispatcher) {
            seedInitialContactsAndMessagesIfEmpty()
        }
        // İnternet bağlantısı geri geldiğinde bekleyen (QUEUED_OFFLINE / UPLOAD_INTERRUPTED) notları otomatik devam ettir
        viewModelScope.launch(ioDispatcher) {
            connectivityObserver.isOnline.collect { online ->
                if (online) {
                    resumeAllPendingOutboxUploads()
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectContact(contactId: String) {
        _selectedContactId.value = contactId
    }

    fun dismissBanner() {
        _bannerMessage.value = null
    }

    fun toggleSimulatedOffline() {
        val nextOffline = !connectivityObserver.isSimulatedOffline.value
        connectivityObserver.setSimulatedOffline(nextOffline)
    }

    fun signInOrUpdateProfile(displayName: String, emailOrPhone: String) {
        viewModelScope.launch(ioDispatcher) {
            authService.signInOrUpdateProfile(displayName, emailOrPhone)
        }
    }

    fun addOrFindContact(displayName: String, handleOrEmail: String) {
        val cleanName = displayName.trim().ifBlank { return }
        val cleanHandle = handleOrEmail.trim().ifBlank { return }
        viewModelScope.launch(ioDispatcher) {
            val contactKey = cryptoService.generateIdentityKeyBundle()
            val id = "contact_${UUID.randomUUID().toString().take(8)}"
            val initials = cleanName.split(" ")
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .take(2)
                .joinToString("")
                .ifBlank { "KT" }

            val entity = ChatContactEntity(
                id = id,
                displayName = cleanName,
                handleOrEmail = cleanHandle,
                avatarInitials = initials,
                publicKeyBase64 = contactKey.publicKeyBase64,
                lastMessagePreview = "Uçtan uca şifreli sohbet başlatıldı",
                lastMessageTimeMillis = System.currentTimeMillis(),
                unreadCount = 0,
                isOnline = true
            )
            chatDao.upsertContact(entity)
            _selectedContactId.value = id
        }
    }

    fun sendTextMessage(text: String) {
        val cleanText = text.trim()
        val contactId = _selectedContactId.value ?: return
        if (cleanText.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            val now = System.currentTimeMillis()
            val me = authService.currentUser.value
            val msg = ChatMessage(
                id = "msg_${UUID.randomUUID().toString().take(10)}",
                contactId = contactId,
                senderId = me.uid,
                recipientId = contactId,
                isOutgoing = true,
                type = ChatMessageType.TEXT,
                textContent = cleanText,
                timestampMillis = now,
                relayState = RelayTransferState.NONE
            )
            chatDao.upsertMessage(ChatMessageEntity.fromDomainModel(msg))
            chatDao.updateContactLastMessage(contactId, cleanText, now)
        }
    }

    /**
     * Seçilen yerel `.tnote` dosyasını alıcının Açık Anahtarı ile uçtan uca şifreler (AES-256-GCM + ECIES),
     * Relay ("bekleme odası") sunucusuna yükler ve alıcı için mesaj kaydı oluşturur.
     */
    fun sendNoteAttachment(noteSummary: NoteSummary, simulateOversizedFile: Boolean = false) {
        val contactId = _selectedContactId.value ?: return
        viewModelScope.launch(ioDispatcher) {
            val contact = chatDao.getContactById(contactId) ?: return@launch
            val me = authService.currentUser.value
            val now = System.currentTimeMillis()

            val doc = noteRepository.loadNoteDocument(noteSummary.id) ?: return@launch
            val plainBytes = if (simulateOversizedFile) {
                // 10 MB sınırı test etmek için 12 MB boyutunda test verisi
                ByteArray(12 * 1024 * 1024)
            } else {
                NoteBinaryCodec.encode(doc, compress = true)
            }

            // Cihaz üzerinde Uçtan Uca Şifreleme (E2EE)
            val encryptedPkg = cryptoService.encryptNoteForRecipient(
                plainNoteBytes = plainBytes,
                recipientPublicKeyBase64 = contact.publicKeyBase64
            )

            val msgId = "msg_note_${UUID.randomUUID().toString().take(10)}"
            val initialRelayObjectId = "relay_${UUID.randomUUID().toString().take(12)}"

            val initialState = if (connectivityObserver.isOnline.value) {
                RelayTransferState.ENCRYPTING_AND_UPLOADING
            } else {
                RelayTransferState.QUEUED_OFFLINE
            }

            val attachmentMeta = NoteAttachmentMeta(
                noteId = noteSummary.id,
                noteTitle = noteSummary.title,
                pageCount = noteSummary.pageCount,
                strokeCount = noteSummary.strokeCount,
                fileSizeBytes = encryptedPkg.cipherBytes.size.toLong(),
                thumbnailPath = noteSummary.thumbnailPath,
                relayObjectId = initialRelayObjectId,
                encryptedKeyEnvelopeBase64 = encryptedPkg.encryptedKeyEnvelopeBase64,
                sha256Hex = encryptedPkg.plainSha256Hex,
                expiresAtMillis = now + RelayStorage.SEVEN_DAYS_MILLIS,
                uploadProgressPercent = 0
            )

            val message = ChatMessage(
                id = msgId,
                contactId = contactId,
                senderId = me.uid,
                recipientId = contactId,
                isOutgoing = true,
                type = ChatMessageType.NOTE_ATTACHMENT,
                textContent = "📎 El Yazısı Notu: ${noteSummary.title}",
                timestampMillis = now,
                relayState = initialState,
                noteAttachment = attachmentMeta
            )

            chatDao.upsertMessage(ChatMessageEntity.fromDomainModel(message))
            chatDao.updateContactLastMessage(contactId, "📎 ${noteSummary.title}", now)

            if (!connectivityObserver.isOnline.value) {
                _bannerMessage.value = "İnternet yok: '${noteSummary.title}' çevrimdışı kuyruğa alındı. Bağlantı geldiğinde otomatik yüklenecek."
                return@launch
            }

            performRelayUploadForMessage(
                msgId = msgId,
                senderId = me.uid,
                recipientId = contactId,
                cipherBytes = encryptedPkg.cipherBytes,
                encryptedKeyEnvelopeBase64 = encryptedPkg.encryptedKeyEnvelopeBase64,
                relayObjectId = initialRelayObjectId,
                resumeFromPercent = 0
            )
        }
    }

    /**
     * Kesilen veya çevrimdışı kuyrukta bekleyen tüm not gönderimlerini kaldığı yerden devam ettirir.
     */
    fun resumeAllPendingOutboxUploads() {
        viewModelScope.launch(ioDispatcher) {
            val pending = chatDao.getPendingOutboxMessages()
            for (entity in pending) {
                resumeOrRetryNoteUpload(entity.id)
            }
        }
    }

    fun resumeOrRetryNoteUpload(messageId: String) {
        viewModelScope.launch(ioDispatcher) {
            val entity = chatDao.getMessageById(messageId) ?: return@launch
            val contact = chatDao.getContactById(entity.contactId) ?: return@launch
            val noteId = entity.noteId ?: return@launch
            val doc = noteRepository.loadNoteDocument(noteId) ?: return@launch

            val plainBytes = NoteBinaryCodec.encode(doc, compress = true)
            val encryptedPkg = cryptoService.encryptNoteForRecipient(
                plainNoteBytes = plainBytes,
                recipientPublicKeyBase64 = contact.publicKeyBase64
            )
            val relayObjectId = entity.relayObjectId ?: "relay_${UUID.randomUUID().toString().take(12)}"
            val resumePct = entity.uploadProgressPercent.coerceIn(0, 95)

            performRelayUploadForMessage(
                msgId = entity.id,
                senderId = entity.senderId,
                recipientId = entity.recipientId,
                cipherBytes = encryptedPkg.cipherBytes,
                encryptedKeyEnvelopeBase64 = encryptedPkg.encryptedKeyEnvelopeBase64,
                relayObjectId = relayObjectId,
                resumeFromPercent = resumePct
            )
        }
    }

    private suspend fun performRelayUploadForMessage(
        msgId: String,
        senderId: String,
        recipientId: String,
        cipherBytes: ByteArray,
        encryptedKeyEnvelopeBase64: String,
        relayObjectId: String,
        resumeFromPercent: Int
    ) {
        var lastProgress = resumeFromPercent
        try {
            val receipt = relayStorage.uploadEncryptedNote(
                senderId = senderId,
                recipientId = recipientId,
                cipherBytes = cipherBytes,
                encryptedKeyEnvelopeBase64 = encryptedKeyEnvelopeBase64,
                existingRelayObjectId = relayObjectId,
                resumeFromPercent = resumeFromPercent,
                onProgress = { pct ->
                    lastProgress = pct
                    chatDao.updateMessageRelayState(
                        messageId = msgId,
                        newState = RelayTransferState.ENCRYPTING_AND_UPLOADING.name,
                        progress = pct
                    )
                }
            )
            val existing = chatDao.getMessageById(msgId)
            if (existing != null) {
                chatDao.upsertMessage(
                    existing.copy(
                        relayState = RelayTransferState.WAITING_IN_RELAY.name,
                        relayObjectId = receipt.relayObjectId,
                        encryptedKeyEnvelopeBase64 = encryptedKeyEnvelopeBase64,
                        expiresAtMillis = receipt.expiresAtMillis,
                        uploadProgressPercent = 100
                    )
                )
            }
        } catch (tooLarge: NoteFileTooLargeException) {
            chatDao.updateMessageRelayState(
                messageId = msgId,
                newState = RelayTransferState.FAILED_TOO_LARGE.name,
                progress = 0
            )
            _bannerMessage.value =
                "Hata: Dosya boyutu (${tooLarge.actualBytes / (1024 * 1024)} MB) izin verilen 10 MB sınırını aşıyor!"
        } catch (e: Exception) {
            val interruptedState = if (!connectivityObserver.isOnline.value) {
                RelayTransferState.UPLOAD_INTERRUPTED
            } else {
                RelayTransferState.FAILED_ERROR
            }
            chatDao.updateMessageRelayState(
                messageId = msgId,
                newState = interruptedState.name,
                progress = lastProgress
            )
            _bannerMessage.value = e.message ?: "Yükleme kesildi. Bağlantı geldiğinde kaldığı yerden devam edecek."
        }
    }

    /**
     * Alıcıya gelen şifreli notu Relay ("bekleme odası") sunucusundan indirir,
     * alıcının Özel Anahtarı (`privateKeyBase64`) ile şifresini çözer, yerel depolamaya
     * atomik olarak kaydeder ve ardından Relay üzerindeki kopyayı kalıcı olarak siler.
     */
    fun downloadAndDecryptNoteMessage(
        message: ChatMessage,
        onDecryptedNoteReady: ((String) -> Unit)? = null
    ) {
        val att = message.noteAttachment ?: return
        val relayObjectId = att.relayObjectId ?: return

        viewModelScope.launch(ioDispatcher) {
            try {
                chatDao.updateMessageRelayState(
                    messageId = message.id,
                    newState = RelayTransferState.DOWNLOADING_AND_DECRYPTING.name
                )

                val me = authService.currentUser.value
                val myKeys = authService.getLocalIdentityKeyBundle()

                // 1. Şifreli paketi Relay'den indir
                val bundle = relayStorage.downloadEncryptedNote(
                    relayObjectId = relayObjectId,
                    requestingUserId = me.uid
                )

                // 2. Cihazda şifreyi çöz (AES-256-GCM + ECIES)
                val decryptedBytes = cryptoService.decryptNoteForRecipient(
                    cipherBytes = bundle.cipherBytes,
                    encryptedKeyEnvelopeBase64 = bundle.encryptedKeyEnvelopeBase64,
                    recipientPrivateKeyBase64 = myKeys.privateKeyBase64
                )

                // 3. Vektör notu (.tnote) çöz ve yerel depolamaya atomik olarak kaydet
                val decodedDoc = NoteBinaryCodec.decode(decryptedBytes)
                val savedSummary = noteRepository.saveNoteDocumentAtomically(decodedDoc)

                // 4. Doğrulanan indirme sonrası Relay kopyasını sunucudan kalıcı olarak sil!
                relayStorage.confirmDownloadAndDeleteRelayCopy(
                    relayObjectId = relayObjectId,
                    requestingUserId = me.uid
                )

                val existingEntity = chatDao.getMessageById(message.id)
                if (existingEntity != null) {
                    chatDao.upsertMessage(
                        existingEntity.copy(
                            relayState = RelayTransferState.DOWNLOADED_AND_DELETED_FROM_RELAY.name,
                            noteId = savedSummary.id,
                            noteTitle = savedSummary.title,
                            notePageCount = savedSummary.pageCount,
                            noteStrokeCount = savedSummary.strokeCount,
                            noteFileSizeBytes = savedSummary.fileSizeBytes,
                            noteThumbnailPath = savedSummary.thumbnailPath
                        )
                    )
                }

                _bannerMessage.value =
                    "✓ '${savedSummary.title}' indirildi, şifresi çözüldü ve Relay sunucusundan silindi!"
                onDecryptedNoteReady?.invoke(savedSummary.id)
            } catch (e: Exception) {
                chatDao.updateMessageRelayState(
                    messageId = message.id,
                    newState = RelayTransferState.FAILED_ERROR.name
                )
                _bannerMessage.value = e.message ?: "Şifreli not indirilirken hata oluştu."
            }
        }
    }

    /**
     * Tek tablet/emülatör üzerinde test kolaylığı için: Seçili kişiden mevcut kullanıcıya
     * uçtan uca şifrelenmiş yeni bir el yazısı `.tnote` notu gönderilmesini simüle eder.
     */
    fun simulateIncomingEncryptedNote() {
        val contactId = _selectedContactId.value ?: return
        viewModelScope.launch(ioDispatcher) {
            val contact = chatDao.getContactById(contactId) ?: return@launch
            val me = authService.currentUser.value
            val now = System.currentTimeMillis()

            val incomingDoc = createIncomingHandwrittenNoteDocument(
                senderName = contact.displayName,
                timestampMillis = now
            )
            val plainBytes = NoteBinaryCodec.encode(incomingDoc, compress = true)

            // Gönderici, notu BİZİM (alıcının) Açık Anahtarımızla şifreler
            val encryptedPkg = cryptoService.encryptNoteForRecipient(
                plainNoteBytes = plainBytes,
                recipientPublicKeyBase64 = me.publicKeyBase64
            )

            val receipt = relayStorage.uploadEncryptedNote(
                senderId = contact.id,
                recipientId = me.uid,
                cipherBytes = encryptedPkg.cipherBytes,
                encryptedKeyEnvelopeBase64 = encryptedPkg.encryptedKeyEnvelopeBase64
            )

            val msg = ChatMessage(
                id = "msg_in_${UUID.randomUUID().toString().take(10)}",
                contactId = contact.id,
                senderId = contact.id,
                recipientId = me.uid,
                isOutgoing = false,
                type = ChatMessageType.NOTE_ATTACHMENT,
                textContent = "📥 Şifreli El Yazısı Notu: ${incomingDoc.title}",
                timestampMillis = now,
                relayState = RelayTransferState.WAITING_IN_RELAY,
                noteAttachment = NoteAttachmentMeta(
                    noteId = incomingDoc.id,
                    noteTitle = incomingDoc.title,
                    pageCount = incomingDoc.pages.size,
                    strokeCount = incomingDoc.strokes.size,
                    fileSizeBytes = encryptedPkg.cipherBytes.size.toLong(),
                    thumbnailPath = null,
                    relayObjectId = receipt.relayObjectId,
                    encryptedKeyEnvelopeBase64 = encryptedPkg.encryptedKeyEnvelopeBase64,
                    sha256Hex = encryptedPkg.plainSha256Hex,
                    expiresAtMillis = receipt.expiresAtMillis,
                    uploadProgressPercent = 100
                )
            )
            chatDao.upsertMessage(ChatMessageEntity.fromDomainModel(msg))
            chatDao.updateContactLastMessage(contact.id, "📥 Şifreli Not: ${incomingDoc.title}", now)
        }
    }

    private suspend fun seedInitialContactsAndMessagesIfEmpty() {
        noteRepository.ensureSampleNotesSeeded()
        if (chatDao.getContactCount() > 0) {
            val firstList = chatDao.observeContacts()
            return
        }

        val now = System.currentTimeMillis()
        val me = authService.currentUser.value

        val elifKey = cryptoService.generateIdentityKeyBundle()
        val mertKey = cryptoService.generateIdentityKeyBundle()
        val zeynepKey = cryptoService.generateIdentityKeyBundle()

        val contactsToSeed = listOf(
            ChatContactEntity(
                id = "contact_elif",
                displayName = "Elif Kaya",
                handleOrEmail = "elif.kaya@fizik.edu.tr",
                avatarInitials = "EK",
                publicKeyBase64 = elifKey.publicKeyBase64,
                lastMessagePreview = "📥 Şifreli Not: Elektromanyetik Dalga Özeti",
                lastMessageTimeMillis = now - 120_000L,
                unreadCount = 1,
                isOnline = true
            ),
            ChatContactEntity(
                id = "contact_mert",
                displayName = "Mert Demir",
                handleOrEmail = "+90 532 410 20 30",
                avatarInitials = "MD",
                publicKeyBase64 = mertKey.publicKeyBase64,
                lastMessagePreview = "Türev notunu aldım, çizimler çok net!",
                lastMessageTimeMillis = now - 3_600_000L,
                unreadCount = 0,
                isOnline = true
            ),
            ChatContactEntity(
                id = "contact_zeynep",
                displayName = "Zeynep Arslan",
                handleOrEmail = "zeynep@algoritma.org",
                avatarInitials = "ZA",
                publicKeyBase64 = zeynepKey.publicKeyBase64,
                lastMessagePreview = "Quadtree uzamsal indeks diyagramını paylaşabilir misin?",
                lastMessageTimeMillis = now - 7_200_000L,
                unreadCount = 0,
                isOnline = false
            )
        )
        chatDao.upsertContacts(contactsToSeed)
        _selectedContactId.value = "contact_elif"

        // Elif ile örnek 1:1 sohbet mesajları + Bekleme odasında indirilmeye hazır gerçek bir E2EE şifreli not!
        val msg1 = ChatMessage(
            id = "seed_msg_1",
            contactId = "contact_elif",
            senderId = "contact_elif",
            recipientId = me.uid,
            isOutgoing = false,
            type = ChatMessageType.TEXT,
            textContent = "Selam! Bugünkü laboratuvar dersinde çizdiğim elektromanyetik dalga notunu şifreli olarak gönderiyorum.",
            timestampMillis = now - 300_000L
        )
        val msg2 = ChatMessage(
            id = "seed_msg_2",
            contactId = "contact_elif",
            senderId = me.uid,
            recipientId = "contact_elif",
            isOutgoing = true,
            type = ChatMessageType.TEXT,
            textContent = "Harika, hemen indirip kendi tabletimde üzerine ekleme yapacağım.",
            timestampMillis = now - 240_000L
        )
        chatDao.upsertMessage(ChatMessageEntity.fromDomainModel(msg1))
        chatDao.upsertMessage(ChatMessageEntity.fromDomainModel(msg2))

        // Gerçek şifreli .tnote paketini Relay bekleme odasına koy
        val incomingDoc = createIncomingHandwrittenNoteDocument(
            senderName = "Elif Kaya",
            timestampMillis = now - 120_000L,
            customTitle = "Elektromanyetik Dalga Özeti (Elif)"
        )
        val plainBytes = NoteBinaryCodec.encode(incomingDoc, compress = true)
        val encryptedPkg = cryptoService.encryptNoteForRecipient(
            plainNoteBytes = plainBytes,
            recipientPublicKeyBase64 = me.publicKeyBase64
        )
        val receipt = relayStorage.uploadEncryptedNote(
            senderId = "contact_elif",
            recipientId = me.uid,
            cipherBytes = encryptedPkg.cipherBytes,
            encryptedKeyEnvelopeBase64 = encryptedPkg.encryptedKeyEnvelopeBase64
        )

        val msg3 = ChatMessage(
            id = "seed_msg_3",
            contactId = "contact_elif",
            senderId = "contact_elif",
            recipientId = me.uid,
            isOutgoing = false,
            type = ChatMessageType.NOTE_ATTACHMENT,
            textContent = "📥 Şifreli El Yazısı Notu: ${incomingDoc.title}",
            timestampMillis = now - 120_000L,
            relayState = RelayTransferState.WAITING_IN_RELAY,
            noteAttachment = NoteAttachmentMeta(
                noteId = incomingDoc.id,
                noteTitle = incomingDoc.title,
                pageCount = incomingDoc.pages.size,
                strokeCount = incomingDoc.strokes.size,
                fileSizeBytes = encryptedPkg.cipherBytes.size.toLong(),
                thumbnailPath = null,
                relayObjectId = receipt.relayObjectId,
                encryptedKeyEnvelopeBase64 = encryptedPkg.encryptedKeyEnvelopeBase64,
                sha256Hex = encryptedPkg.plainSha256Hex,
                expiresAtMillis = receipt.expiresAtMillis,
                uploadProgressPercent = 100
            )
        )
        chatDao.upsertMessage(ChatMessageEntity.fromDomainModel(msg3))
    }

    private fun createIncomingHandwrittenNoteDocument(
        senderName: String,
        timestampMillis: Long,
        customTitle: String? = null
    ): NoteDocument {
        val wavePoints = (0..60).map { i ->
            val x = 100f + i * 11f
            val y = 320f + sin(i * 0.25f) * 70f
            StrokePoint(
                x = x,
                y = y,
                pressure = 0.55f + (i % 5) * 0.06f,
                tiltRadians = 0.2f,
                orientationRadians = 0.4f,
                timestampMillis = timestampMillis + i * 8L
            )
        }
        val axisPoints = listOf(
            StrokePoint(x = 90f, y = 320f, pressure = 0.6f, timestampMillis = timestampMillis),
            StrokePoint(x = 780f, y = 320f, pressure = 0.65f, timestampMillis = timestampMillis + 20L)
        )
        val highlightPoints = listOf(
            StrokePoint(x = 120f, y = 180f, pressure = 0.5f, timestampMillis = timestampMillis + 40L),
            StrokePoint(x = 540f, y = 180f, pressure = 0.5f, timestampMillis = timestampMillis + 60L)
        )

        return NoteDocument(
            id = "shared_${UUID.randomUUID().toString().take(8)}",
            title = customTitle ?: "$senderName — Paylaşılan El Yazısı Notu",
            pages = listOf(NotePage(pageIndex = 0, widthPt = 960f, heightPt = 1280f)),
            strokes = listOf(
                NoteStroke(
                    id = "st_hl",
                    pageIndex = 0,
                    tool = DrawingToolType.HIGHLIGHTER,
                    colorArgb = 0xFFD97757.toInt(),
                    thickness = 20f,
                    points = highlightPoints
                ),
                NoteStroke(
                    id = "st_axis",
                    pageIndex = 0,
                    tool = DrawingToolType.PEN,
                    colorArgb = 0xFF2563EB.toInt(),
                    thickness = 3.2f,
                    points = axisPoints
                ),
                NoteStroke(
                    id = "st_wave",
                    pageIndex = 0,
                    tool = DrawingToolType.PEN,
                    colorArgb = 0xFF1F1F1F.toInt(),
                    thickness = 4.2f,
                    points = wavePoints
                )
            ),
            textBlocks = listOf(
                TextBlock(
                    id = "tb_shared_1",
                    pageIndex = 0,
                    x = 120f,
                    y = 140f,
                    width = 460f,
                    text = "$senderName tarafından E2EE (AES-256-GCM) ile şifrelenerek gönderildi.",
                    colorArgb = 0xFF1F1F1F.toInt(),
                    fontSizeSp = 17f
                )
            ),
            createdAtMillis = timestampMillis,
            updatedAtMillis = timestampMillis
        )
    }
}

