/**
 * Sunucu Tarafı 7 Günlük Otomatik Silme (Server-Side TTL & Scheduled Cloud Function)
 *
 * İstemciden (tabletten) bağımsız olarak her 6 saatte bir çalışır ve 7 gün içinde
 * alıcısı tarafından indirilmemiş tüm şifreli `.tnote` dosyalarını ve Firestore
 * bekleme odası kayıtlarını sunucudan kalıcı olarak temizler.
 */
const { onSchedule } = require("firebase-functions/v2/scheduler");
const admin = require("firebase-admin");

admin.initializeApp();

exports.purgeExpiredRelayNotes = onSchedule("every 6 hours", async () => {
  const db = admin.firestore();
  const bucket = admin.storage().bucket();
  const nowMillis = Date.now();

  const expiredSnapshot = await db
    .collection("relay_waiting_room")
    .where("expiresAtMillis", "<=", nowMillis)
    .limit(500)
    .get();

  if (expiredSnapshot.empty) {
    return;
  }

  const batch = db.batch();
  const storageDeletePromises = [];

  for (const doc of expiredSnapshot.docs) {
    const data = doc.data();
    const filePath = `relay_notes/${data.recipientId}/${data.relayObjectId}.enc`;
    storageDeletePromises.push(
      bucket.file(filePath).delete({ ignoreNotFound: true })
    );
    batch.delete(doc.ref);
  }

  await Promise.all(storageDeletePromises);
  await batch.commit();
});

