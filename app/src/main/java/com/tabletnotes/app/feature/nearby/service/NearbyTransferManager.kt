package com.tabletnotes.app.feature.nearby.service

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.tabletnotes.app.domain.model.DrawingToolType
import com.tabletnotes.app.domain.model.NoteDocument
import com.tabletnotes.app.domain.model.NotePage
import com.tabletnotes.app.domain.model.NoteStroke
import com.tabletnotes.app.domain.model.StrokePoint
import com.tabletnotes.app.domain.model.TextBlock
import com.tabletnotes.app.domain.repository.NoteRepository
import com.tabletnotes.app.feature.format.binary.NoteBinaryCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NearbyPeerDevice(
    val endpointId: String,
    val deviceName: String,
    val isConnected: Boolean = false,
    val isSimulated: Boolean = false
)

enum class TransferDirection {
    OUTGOING,
    INCOMING
}

enum class NearbySessionState {
    CONNECTING,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class NearbyTransferSession(
    val sessionId: String,
    val peerEndpointId: String,
    val peerName: String,
    val noteId: String,
    val noteTitle: String,
    val direction: TransferDirection,
    val state: NearbySessionState,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val progressPercent: Int,
    val statusDetail: String
)

interface NearbyTransferManager {
    val isAdvertising: StateFlow<Boolean>
    val isDiscovering: StateFlow<Boolean>
    val discoveredPeers: StateFlow<List<NearbyPeerDevice>>
    val activeTransfer: StateFlow<NearbyTransferSession?>

    fun startAdvertising(localDeviceName: String)
    fun startDiscovery()
    fun stopAll()
    fun sendNoteToPeer(peer: NearbyPeerDevice, document: NoteDocument)
    fun cancelActiveTransfer()
    fun retryLastTransfer()
    fun simulateDiscoverAndSend(document: NoteDocument)
    fun simulateIncomingNearbyNote()
}

/**
 * Google Nearby Connections API (`Strategy.P2P_POINT_TO_POINT` — Bluetooth + Wi-Fi Direct)
 * kullanarak internet olmadan doğrudan iki tablet arasında `.tnote` vektör notlarını
 * gönderen ve alan servis.
 */
@Singleton
class GoogleNearbyConnectionsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: NoteRepository
) : NearbyTransferManager {

    companion object {
        const val SERVICE_ID = "com.tabletnotes.app.nearby.p2p.v1"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    private val _isAdvertising = MutableStateFlow(false)
    override val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<NearbyPeerDevice>>(emptyList())
    override val discoveredPeers: StateFlow<List<NearbyPeerDevice>> = _discoveredPeers.asStateFlow()

    private val _activeTransfer = MutableStateFlow<NearbyTransferSession?>(null)
    override val activeTransfer: StateFlow<NearbyTransferSession?> = _activeTransfer.asStateFlow()

    private val incomingPayloads = ConcurrentHashMap<Long, Payload>()
    private var activePayloadId: Long? = null
    private var activeSimulationJob: Job? = null
    private var lastOutgoingPeer: NearbyPeerDevice? = null
    private var lastOutgoingDocument: NoteDocument? = null

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            incomingPayloads[payload.id] = payload
            activePayloadId = payload.id
            val peerName = _discoveredPeers.value.firstOrNull { it.endpointId == endpointId }?.deviceName
                ?: "Yakındaki Tablet ($endpointId)"

            val bytes = payload.asBytes()
            val total = bytes?.size?.toLong() ?: 1024L
            _activeTransfer.value = NearbyTransferSession(
                sessionId = "rx_${payload.id}",
                peerEndpointId = endpointId,
                peerName = peerName,
                noteId = "incoming",
                noteTitle = "Gelen El Yazısı Notu (.tnote)",
                direction = TransferDirection.INCOMING,
                state = NearbySessionState.IN_PROGRESS,
                bytesTransferred = 0L,
                totalBytes = total,
                progressPercent = 10,
                statusDetail = "Bluetooth + Wi-Fi Direct üzerinden alınıyor…"
            )
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val current = _activeTransfer.value ?: return
            val total = update.totalBytes.coerceAtLeast(1L)
            val transferred = update.bytesTransferred.coerceAtLeast(0L)
            val pct = ((transferred * 100L) / total).toInt().coerceIn(0, 100)

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    _activeTransfer.value = current.copy(
                        state = NearbySessionState.IN_PROGRESS,
                        bytesTransferred = transferred,
                        totalBytes = total,
                        progressPercent = pct,
                        statusDetail = "Wi-Fi Direct ile aktarılıyor (%$pct)"
                    )
                }

                PayloadTransferUpdate.Status.SUCCESS -> {
                    val receivedPayload = incomingPayloads.remove(update.payloadId)
                    if (receivedPayload != null && current.direction == TransferDirection.INCOMING) {
                        val rawBytes = receivedPayload.asBytes()
                        if (rawBytes != null) {
                            scope.launch {
                                runCatching {
                                    val doc = NoteBinaryCodec.decode(rawBytes)
                                    val saved = noteRepository.saveNoteDocumentAtomically(doc)
                                    _activeTransfer.value = current.copy(
                                        noteId = saved.id,
                                        noteTitle = saved.title,
                                        state = NearbySessionState.COMPLETED,
                                        bytesTransferred = total,
                                        totalBytes = total,
                                        progressPercent = 100,
                                        statusDetail = "✓ '${saved.title}' alındı ve Notlarım'a kaydedildi!"
                                    )
                                }
                            }
                            return
                        }
                    }
                    _activeTransfer.value = current.copy(
                        state = NearbySessionState.COMPLETED,
                        bytesTransferred = total,
                        totalBytes = total,
                        progressPercent = 100,
                        statusDetail = "✓ Aktarım başarıyla tamamlandı (%100)"
                    )
                }

                PayloadTransferUpdate.Status.CANCELED -> {
                    incomingPayloads.remove(update.payloadId)
                    _activeTransfer.value = current.copy(
                        state = NearbySessionState.CANCELLED,
                        statusDetail = "Aktarım kullanıcı tarafından iptal edildi."
                    )
                }

                PayloadTransferUpdate.Status.FAILURE -> {
                    incomingPayloads.remove(update.payloadId)
                    _activeTransfer.value = current.copy(
                        state = NearbySessionState.FAILED,
                        statusDetail = "Bağlantı koptu. 'Yeniden Dene' butonuna basabilirsiniz."
                    )
                }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Doğrudan eşler arası (P2P) bağlantıyı kabul et ve PayloadCallback'i bağla
            runCatching {
                connectionsClient.acceptConnection(endpointId, payloadCallback)
            }
            upsertPeer(
                NearbyPeerDevice(
                    endpointId = endpointId,
                    deviceName = info.endpointName,
                    isConnected = false
                )
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val ok = result.status.statusCode == ConnectionsStatusCodes.STATUS_OK
            _discoveredPeers.value = _discoveredPeers.value.map { peer ->
                if (peer.endpointId == endpointId) peer.copy(isConnected = ok) else peer
            }
        }

        override fun onDisconnected(endpointId: String) {
            _discoveredPeers.value = _discoveredPeers.value.map { peer ->
                if (peer.endpointId == endpointId) peer.copy(isConnected = false) else peer
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            upsertPeer(
                NearbyPeerDevice(
                    endpointId = endpointId,
                    deviceName = info.endpointName,
                    isConnected = false,
                    isSimulated = false
                )
            )
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredPeers.value = _discoveredPeers.value.filterNot { it.endpointId == endpointId }
        }
    }

    override fun startAdvertising(localDeviceName: String) {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()
        _isAdvertising.value = true
        runCatching {
            connectionsClient.startAdvertising(
                localDeviceName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            )
        }
    }

    override fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_POINT_TO_POINT)
            .build()
        _isDiscovering.value = true
        runCatching {
            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            )
        }
    }

    override fun stopAll() {
        activeSimulationJob?.cancel()
        _isAdvertising.value = false
        _isDiscovering.value = false
        runCatching {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        }
    }

    override fun sendNoteToPeer(peer: NearbyPeerDevice, document: NoteDocument) {
        lastOutgoingPeer = peer
        lastOutgoingDocument = document

        if (peer.isSimulated) {
            runSimulatedOutgoingTransfer(peer, document)
            return
        }

        scope.launch {
            val encodedBytes = NoteBinaryCodec.encode(document, compress = true)
            val total = encodedBytes.size.toLong()
            val payload = Payload.fromBytes(encodedBytes)
            activePayloadId = payload.id

            _activeTransfer.value = NearbyTransferSession(
                sessionId = "tx_${payload.id}",
                peerEndpointId = peer.endpointId,
                peerName = peer.deviceName,
                noteId = document.id,
                noteTitle = document.title,
                direction = TransferDirection.OUTGOING,
                state = NearbySessionState.CONNECTING,
                bytesTransferred = 0L,
                totalBytes = total,
                progressPercent = 5,
                statusDetail = "${peer.deviceName} ile Wi-Fi Direct kanalı kuruluyor…"
            )

            runCatching {
                if (!peer.isConnected) {
                    connectionsClient.requestConnection(
                        "Pixel Tablet",
                        peer.endpointId,
                        connectionLifecycleCallback
                    )
                }
                connectionsClient.sendPayload(peer.endpointId, payload)
            }.onFailure {
                _activeTransfer.value = _activeTransfer.value?.copy(
                    state = NearbySessionState.FAILED,
                    statusDetail = "Gönderim başlatılamadı: ${it.message}"
                )
            }
        }
    }

    override fun cancelActiveTransfer() {
        activeSimulationJob?.cancel()
        activePayloadId?.let { pid ->
            runCatching { connectionsClient.cancelPayload(pid) }
        }
        val current = _activeTransfer.value ?: return
        _activeTransfer.value = current.copy(
            state = NearbySessionState.CANCELLED,
            statusDetail = "Aktarım iptal edildi (%${current.progressPercent}). 'Yeniden Dene' ile tekrar gönderebilirsiniz."
        )
    }

    override fun retryLastTransfer() {
        val peer = lastOutgoingPeer
        val doc = lastOutgoingDocument
        if (peer != null && doc != null) {
            sendNoteToPeer(peer, doc)
            return
        }
        val current = _activeTransfer.value ?: return
        if (current.direction == TransferDirection.INCOMING) {
            simulateIncomingNearbyNote()
        }
    }

    override fun simulateDiscoverAndSend(document: NoteDocument) {
        val simulatedPeer = NearbyPeerDevice(
            endpointId = "sim_galaxy_tab_s9",
            deviceName = "Can'ın Tableti (Galaxy Tab S9 • Wi-Fi Direct)",
            isConnected = true,
            isSimulated = true
        )
        upsertPeer(simulatedPeer)
        sendNoteToPeer(simulatedPeer, document)
    }

    override fun simulateIncomingNearbyNote() {
        activeSimulationJob?.cancel()
        activeSimulationJob = scope.launch {
            val peerName = "Elif'in Tableti (Pixel Tablet #2)"
            val now = System.currentTimeMillis()
            val incomingDoc = NoteDocument(
                id = "nearby_${UUID.randomUUID().toString().take(8)}",
                title = "Nearby Paylaşımı — Lab Ölçüm Tablosu",
                pages = listOf(NotePage(pageIndex = 0, widthPt = 960f, heightPt = 1280f)),
                strokes = listOf(
                    NoteStroke(
                        id = "nb_st_1",
                        pageIndex = 0,
                        tool = DrawingToolType.HIGHLIGHTER,
                        colorArgb = 0xFF16A34A.toInt(),
                        thickness = 18f,
                        points = listOf(
                            StrokePoint(x = 110f, y = 210f, pressure = 0.5f, timestampMillis = now),
                            StrokePoint(x = 560f, y = 210f, pressure = 0.5f, timestampMillis = now + 20L)
                        )
                    ),
                    NoteStroke(
                        id = "nb_st_2",
                        pageIndex = 0,
                        tool = DrawingToolType.PEN,
                        colorArgb = 0xFF1F1F1F.toInt(),
                        thickness = 3.8f,
                        points = (0..45).map { idx ->
                            StrokePoint(
                                x = 110f + idx * 9f,
                                y = 310f + (idx % 6) * 12f,
                                pressure = 0.65f,
                                timestampMillis = now + 40L + idx * 8L
                            )
                        }
                    )
                ),
                textBlocks = listOf(
                    TextBlock(
                        id = "nb_tb_1",
                        pageIndex = 0,
                        x = 110f,
                        y = 170f,
                        width = 450f,
                        text = "İnternetsiz Nearby Connections (Bluetooth + Wi-Fi Direct) ile alındı.",
                        colorArgb = 0xFF1F1F1F.toInt(),
                        fontSizeSp = 16f
                    )
                ),
                createdAtMillis = now,
                updatedAtMillis = now
            )

            val encodedBytes = NoteBinaryCodec.encode(incomingDoc, compress = true)
            val total = encodedBytes.size.toLong()

            try {
                for (pct in listOf(0, 20, 45, 70, 90, 100)) {
                    val transferred = (total * pct) / 100L
                    _activeTransfer.value = NearbyTransferSession(
                        sessionId = "sim_rx",
                        peerEndpointId = "sim_elif_tablet",
                        peerName = peerName,
                        noteId = incomingDoc.id,
                        noteTitle = incomingDoc.title,
                        direction = TransferDirection.INCOMING,
                        state = if (pct < 100) NearbySessionState.IN_PROGRESS else NearbySessionState.COMPLETED,
                        bytesTransferred = transferred,
                        totalBytes = total,
                        progressPercent = pct,
                        statusDetail = if (pct < 100) {
                            "Bluetooth + Wi-Fi Direct ile alınıyor (%$pct)…"
                        } else {
                            "✓ '${incomingDoc.title}' alındı ve Notlarım'a kaydedildi!"
                        }
                    )
                    if (pct < 100) {
                        delay(260L)
                    }
                }
                noteRepository.saveNoteDocumentAtomically(incomingDoc)
            } catch (_: CancellationException) {
                // İptal edildiğinde cancelActiveTransfer durumu günceller
            }
        }
    }

    private fun runSimulatedOutgoingTransfer(peer: NearbyPeerDevice, document: NoteDocument) {
        activeSimulationJob?.cancel()
        activeSimulationJob = scope.launch {
            val encodedBytes = NoteBinaryCodec.encode(document, compress = true)
            val total = encodedBytes.size.toLong()

            try {
                for (pct in listOf(0, 15, 35, 60, 85, 100)) {
                    val transferred = (total * pct) / 100L
                    _activeTransfer.value = NearbyTransferSession(
                        sessionId = "sim_tx_${document.id}",
                        peerEndpointId = peer.endpointId,
                        peerName = peer.deviceName,
                        noteId = document.id,
                        noteTitle = document.title,
                        direction = TransferDirection.OUTGOING,
                        state = if (pct < 100) NearbySessionState.IN_PROGRESS else NearbySessionState.COMPLETED,
                        bytesTransferred = transferred,
                        totalBytes = total,
                        progressPercent = pct,
                        statusDetail = if (pct < 100) {
                            "Wi-Fi Direct ile '${peer.deviceName}' cihazına gönderiliyor (%$pct)…"
                        } else {
                            "✓ '${document.title}' (${total} bayt .tnote) başarıyla gönderildi!"
                        }
                    )
                    if (pct < 100) {
                        delay(280L)
                    }
                }
            } catch (_: CancellationException) {
                // İptal durumu cancelActiveTransfer tarafından işlenir
            }
        }
    }

    private fun upsertPeer(peer: NearbyPeerDevice) {
        val existing = _discoveredPeers.value.toMutableList()
        val idx = existing.indexOfFirst { it.endpointId == peer.endpointId }
        if (idx >= 0) {
            existing[idx] = peer
        } else {
            existing.add(0, peer)
        }
        _discoveredPeers.value = existing
    }
}
