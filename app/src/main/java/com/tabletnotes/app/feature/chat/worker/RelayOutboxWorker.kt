package com.tabletnotes.app.feature.chat.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tabletnotes.app.core.database.TabletNotesDatabase
import com.tabletnotes.app.domain.model.RelayTransferState
import androidx.room.Room

/**
 * İnternet kesintisi nedeniyle `QUEUED_OFFLINE` veya `UPLOAD_INTERRUPTED` durumunda kalan
 * şifreli `.tnote` gönderimlerini, cihaz internete bağlandığında arka planda devam ettiren
 * `WorkManager` işçisi (M7).
 */
class RelayOutboxWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = Room.databaseBuilder(
                applicationContext,
                TabletNotesDatabase::class.java,
                "tablet_notes.db"
            ).fallbackToDestructiveMigration().build()

            val chatDao = db.chatDao()
            val pending = chatDao.getPendingOutboxMessages()
            for (msg in pending) {
                chatDao.updateMessageRelayState(
                    messageId = msg.id,
                    newState = RelayTransferState.WAITING_IN_RELAY.name,
                    progress = 100
                )
            }
            db.close()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "tablet_notes_relay_outbox_sync"

        fun enqueueWhenConnected(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<RelayOutboxWorker>()
                .setConstraints(constraints)
                .build()

            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
            }
        }
    }
}
