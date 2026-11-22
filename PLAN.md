# Tablet Öncelikli El Yazısı ve Mesajlaşma Uygulaması — Faz 1 Mimari ve Geliştirme Planı (`PLAN.md`)

Bu doküman, kod yazımına başlamadan önce uygulamanın mimarisini, modül yapısını, teknoloji seçimlerini (gerekçeleriyle), olası riskleri ve adım adım ilerleyeceğimiz kilometre taşlarını (milestone) açıklar.

---

## 1. Önerilen Mimari (Architecture)

Uygulama **Clean Architecture prensiplerine dayalı MVVM (Model-View-ViewModel) + Repository** deseni ile geliştirilecektir. Tüm katmanlar **Kotlin Coroutines & Flow** ile reaktif ve asenkron çalışacak, bağımlılık yönetimi **Hilt (Dependency Injection)** ile sağlanacaktır.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        UI Katmanı (Jetpack Compose)                     │
│  • NoteEditorScreen (Stylus/Ink Canvas, Araç Çubuğu, Zoom/Pan)          │
│  • ChatListScreen & ChatDetailScreen (1:1 Sohbet, Not Önizleme)         │
│  • NearbyShareSheet (Çevrimdışı Yakın Paylaşım Ekranı)                  │
│  • ViewModels (StateFlow / SharedFlow ile tek yönlü veri akışı - UDF)   │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│                      Domain Katmanı (İş Mantığı)                        │
│  • Modeller: NoteDocument, Stroke, StrokePoint, TextBlock, ChatMessage  │
│  • Arayüzler (Interfaces):                                              │
│    - NoteRepository (Yerel kayıt, otomatik kayıt, önbellek)             │
│    - RelayStorage (Bulut "bekleme odası" dosya yükleme/indirme/silme)   │
│    - MessageRepository (1:1 mesajlaşma ve bildirim akışı)               │
│    - CryptoService (Uçtan uca şifreleme / anahtar yönetimi)             │
│    - NearbyTransferManager (Bluetooth + Wi-Fi Direct aktarım)           │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼─────────────────────────────────────┐
│                        Data Katmanı (Veri Kaynakları)                   │
│  • Yerel Veritabanı: Room (Not üst verileri, sohbet geçmişi, kuyruk)    │
│  • Dosya Sistemi: Atomik yazma (.tmp -> rename) + Binary Codec + PNG    │
│  • Uzak Sunucu (Relay): Firebase veya Supabase (Arayüz arkasında)       │
│  • Şifreleme: Google Tink / Lazysodium (X25519 + AES-256-GCM E2EE)      │
│  • Çevrimdışı Paylaşım: Google Nearby Connections API                   │
│  • Arka Plan İşleri: WorkManager (Kesilen yüklemeleri devam ettirme)    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Proje Modül / Paket Yapısı (Module & Package Structure)

Bakımı kolay, temiz ve modüler bir yapı için tek uygulama içinde katmanlı/özellik bazlı paketleme (veya isteğinize göre çoklu Gradle modülü) kullanacağız. Başlangıç için derleme hızını ve anlaşılırlığı koruyan modüler paket yapısı:

```text
app/src/main/java/com/tabletnotes/app/
├── core/
│   ├── common/          # Sonuç sarmalayıcıları (Result/Resource), Coroutine Dispatcher'lar
│   ├── designsystem/    # Tema (Color, Typography, Shapes), kolayca değiştirilebilir UI bileşenleri
│   ├── database/        # Room Database, DAO'lar ve Entity sınıfları
│   ├── storage/         # Atomik dosya okuyucu/yazıcı, düşük depolama alanı kontrolü
│   ├── crypto/          # Uçtan uca şifreleme (E2EE), anahtar üretimi ve Android Keystore yönetimi
│   └── network/         # İnternet bağlantı durumu izleyicisi (ConnectivityObserver)
├── feature/
│   ├── editor/          # M2: Not Editörü
│   │   ├── ink/         # Düşük gecikmeli çizim motoru, MotionEvent tahmini, avuç içi reddi
│   │   ├── canvas/      # Sonsuz/Çok sayfalı tuval, Zoom/Pan, Karo (Tile) Bitmap önbelleği
│   │   ├── spatial/     # R-Tree / Grid uzamsal indeksleme (hızlı silgi ve görünüm kırpma için)
│   │   ├── history/     # Undo / Redo (Geri al / İleri al) komut yığını
│   │   └── ui/          # Kalem, fosforlu kalem, silgi, renk/kalınlık seçici, metin kutuları
│   ├── format/          # M3: Özel Vektör Veri Formatı
│   │   ├── binary/      # Delta-kodlamalı, kuantize edilmiş, GZIP sıkıştırmalı binary okuyucu/yazıcı
│   │   ├── json/        # Hata ayıklama (Debug) için JSON dışa/içe aktarıcı
│   │   └── thumbnail/   # Liste önizlemeleri için yerel PNG küçük resim üretici
│   ├── chat/            # M4 & M5: 1:1 Sohbet ve Bekleme Odası (Relay)
│   │   ├── auth/        # E-posta / Telefon ile giriş ve kullanıcı arama
│   │   ├── relay/       # RelayStorage arayüzü ve Firebase/Supabase implementasyonu
│   │   ├── worker/      # WorkManager ile kesilen yükleme/indirmeleri devam ettirme
│   │   └── ui/          # WhatsApp tarzı 1:1 sohbet ekranı ve not eki kartları
│   └── nearby/          # M6: Çevrimdışı Yakın Paylaşım
│       ├── service/     # Nearby Connections API yöneticisi (Payload aktarım, iptal, yeniden deneme)
│       ├── permissions/ # Android 8.0 - 15+ Bluetooth/Wi-Fi/Konum çalışma zamanı izin yöneticisi
│       └── ui/          # Cihaz keşif, ilerleme çubuğu (progress), iptal/yeniden dene ekranı
└── di/                  # Hilt Dependency Injection modülleri
```

---

## 3. Teknoloji Seçimleri ve Gerekçeleri

### A. Çizim / Kalem (Ink) Motoru Seçimi
**Önerim: `androidx.ink` (Jetpack Ink) + `androidx.input:input-motionprediction` + Özel Karo Önbellekli (Tiled) Compose Tuvali (Hibrit Mimari)**

* **Neden bu seçim?**
  1. **Ultra Düşük Gecikme (Wet Ink):** Google'ın geliştirdiği `androidx.ink:ink-authoring` kütüphanesi, arka planda `androidx.graphics:graphics-core` (`GLFrontBufferedRenderer`) kullanır. Kalem ekrana değdiği anda çizgiyi standart Android görünüm hiyerarşisini bekletmeden doğrudan ekranın ön tamponuna (front-buffer) çizer.
  2. **Hareket Tahmini (Prediction) ve Tarihsel Noktalar:** `MotionEvent.getHistoricalX/Y` verilerini ve `androidx.input:input-motionprediction` (`MotionEventPredictor`) kütüphanesini doğal olarak destekleyerek 60Hz/120Hz tablet ekranlarında gecikmeyi sıfıra yaklaştırır.
  3. **Geometri ve Silgi Performansı:** `androidx.ink:ink-geometry`, çizgilerin alan (mesh) hesaplamalarını ve kesişim testlerini C++ (JNI) katmanında çok hızlı yapar; bu da **çizgi silgisinin (stroke eraser)** 10.000+ çizgide bile takılmadan çalışmasını sağlar.
  4. **10.000+ Çizgi İçin Özel Karo (Tile) Önbelleği ve Uzamsal İndeks:** `androidx.ink` aktif çizimi tamamladığında ("dry ink"), çizgiyi kendi **2D Uzamsal Grid/Quadtree** indeksimize kaydedeceğiz ve sabitlenmiş çizgileri **256x256 veya 512x512 piksellik Bitmap Karolarına (Tiles)** çizeceğiz. Ekranda sadece o anki kamera (viewport) içine giren karolar çizilecek. Böylece 10.000+ çizgi olduğunda bile her karede 10.000 vektörü baştan çizmek yerine sadece ekrandaki birkaç bitmap karosunu çizerek **sabit 60/120 FPS** alacağız.

> **Alternatif:** Eğer `androidx.ink` kütüphanesini hiç kullanmak istemezseniz, doğrudan `androidx.graphics:graphics-core` (`GLFrontBufferedRenderer`) + özel Bezier/Catmull-Rom Path motoru da yazabiliriz. Ancak `androidx.ink` basınç/eğim geometrisini ve ön tampon yönetimini çok daha kararlı çözdüğü için **Jetpack Ink + Özel Karo Önbelleği** yaklaşımını öneriyorum. **Başlamadan önce bu seçimi onaylamanızı rica ederim.**

---

### B. Relay ("Bekleme Odası") Sunucu Seçimi: Firebase vs. Supabase
**Önerim: Firebase (Firebase Auth + Cloud Firestore + Cloud Storage + Cloud Functions / GCS Lifecycle)**

* **Neden Firebase?**
  1. **Kesilen Yüklemeleri Devam Ettirme (Resumable Uploads):** Firebase Storage Android SDK'sı, internet kesildiğinde büyük dosyaların yüklemesini kaldığı yerden devam ettirme (pause/resume/retry) özelliğini yerleşik olarak sunar.
  2. **7 Günde Otomatik Silme (Server-Side TTL):**
     - Google Cloud Storage (Firebase Storage'ın altyapısı), **Object Lifecycle Management** kuralı ile `/relay_notes/` klasöründeki 7 günü dolduran dosyaları sunucu tarafında **sıfır kod ve sıfır ek maliyetle** otomatik siler.
     - Firestore **TTL (Time-to-Live)** özelliği ile mesaj kayıtları da `expiresAt` alanına göre sunucu tarafından otomatik temizlenir.
     - Ek olarak zamanlanmış bir **Cloud Function** ile 7 günü geçen sahipsiz dosyalar ve kayıtlar denetlenebilir.
  3. **Güvenlik Kuralları (Security Rules):** Uygulama içinde hiçbir yönetici anahtarı (admin secret) bulunmaz. Firestore ve Storage Security Rules sayesinde bir dosyayı yalnızca `recipientId == request.auth.uid` olan alıcı indirebilir ve başarılı indirme sonrasında silebilir; ayrıca maksimum dosya boyutu (ör. 10 MB) doğrudan sunucu kuralıyla sınırlandırılır.

* **Supabase Ne Zaman Tercih Edilmeli? (Önemli Not):**
  - Firebase'de Cloud Functions ve bazı gelişmiş Storage özellikleri **Blaze (Kullandıkça Öde)** planı (kredi kartı tanımlama, ücretsiz kullanım kotası dahilinde ücret yazmaz) gerektirebilir.
  - Eğer tamamen kredi kartsız bir ücretsiz katman ve SQL tabanlı Row-Level Security (RLS) + `pg_cron` (7 günlük otomatik silme için) tercih ederseniz **Supabase** de harika bir seçenektir.
  - Uygulama kodunda tüm sunucu işlemlerini `RelayStorage` arayüzünün arkasına koyacağımız için mimarimiz her ikisine de %100 uyumludur. **Hangisini (Firebase mi, Supabase mi) kullanmak istediğinizi lütfen belirtin.**

---

### C. Özel Binary Not Formatı (`.tnote`) ve Сıkıştırma
- **Format Yapısı:**
  - **Başlık (Header):** Magic Bytes (`TNOT` - 4 bayt) + Format Versiyonu (`uint16`, başlangıç `v1`) + Bayraklar (Sıkıştırma türü, sayfa modu) + Tuval 메타verisi.
  - **Delta Kodlama (Delta Encoding) & Kuantizasyon:**
    - Her çizginin ilk noktası tam koordinat `(x0, y0)`, sonraki tüm noktaları ise bir önceki noktaya göre farkı `(Δx, Δy, Δpressure, Δtilt, Δtime)` olarak saklanır.
    - Koordinatlar `0.1 px` hassasiyetle tam sayıya yuvarlanır, basınç `0..1023` (10-bit), eğim `-90..90` (`int8`) olarak kuantize edilir.
    - Küçük fark sayıları **ZigZag + Variable-Length Integer (VarInt)** ile 1-2 bayta kadar küçültülür.
  - **Sıkıştırma:** Standart Java/Android `GZIPOutputStream` / `GZIPInputStream` (veya isteğe bağlı `zstd-jni`) ile sıkıştırılır. GZIP harici C++ bağımlılığı gerektirmeden tüm Android cihazlarda %70-%85 sıkıştırma oranı sağlar.
  - **Hata Ayıklama (Debug):** `NoteJsonCodec` ile notlar okunabilir JSON formatına dönüştürülebilir.

---

### D. Uçtan Uca Şifreleme (E2EE)
- **Kütüphane:** **Google Tink (`com.google.crypto.tink:tink-android`)**
- **Çalışma Prensibi:**
  1. Kullanıcı kayıt olduğunda cihazda Android Keystore korumalı bir **X25519 / ECIES (Hibrit Şifreleme)** anahtar çifti oluşturulur; sadece **Açık Anahtar (Public Key)** sunucudaki kullanıcı profiline yüklenir.
  2. Not gönderilirken cihazda rastgele tek kullanımlık bir **AES-256-GCM** anahtarı üretilir ve `.tnote` dosyası bu anahtarla şifrelenir (`StreamingAead`).
  3. Bu tek kullanımlık AES anahtarı, alıcının Açık Anahtarı ile şifrelenerek mesaj kaydına eklenir.
  4. Sunucu (Firebase/Supabase) dahil hiç kimse notun içeriğini göremez.

---

## 4. Riskler ve Çözüm Önlemleri

| Risk | Etki | Çözüm / Önlem |
| :--- | :--- | :--- |
| **10.000+ çizgide FPS düşüşü ve bellek (OOM) hatası** | Çizim yaparken takılma veya uygulamanın çökmesi | Sabitlenmiş çizgileri (committed strokes) doğrudan her karede vektör olarak çizmek yerine **Viewport Culling (sadece ekranda görünen alanı seçme)** + **LRU Bitmap Tile Cache (Karo Önbelleği)** kullanacağız. Ekran dışındaki karolar bellekten temizlenecek. |
| **Kayıt sırasında çökme veya pil bitmesi** | Not dosyasının bozulması (0 bayt kalması) | Tüm kayıtlar önce `note_id.tnote.tmp` geçici dosyasına yazılıp `FileDescriptor.sync()` ile diske zorlanacak, ardından **atomik `renameTo` / `Files.move(..., ATOMIC_MOVE)`** ile asıl dosyanın üzerine yazılacak. |
| **Farklı tabletlerde avuç içi (palm) algılama sorunları** | Yazı yazarken elin tuvali kaydırması veya yanlış çizgi atması | `MotionEvent.getToolType(index) == TOOL_TYPE_STYLUS` kontrolü, `FLAG_CANCELED` takibi ve "Sadece Kalem (Stylus Only)" modu. Kalem ekrana yaklaştığında (hover/down) parmak dokunuşlarını anında yok sayan durum makinesi. |
| **İnternet kesintisinde yüklemenin yarım kalması** | Notun karşı tarafa gitmemesi veya bekleyen dosyaların birikmesi | Gönderimler Room tabanlı yerel bir gönderim kuyruğuna (`Outbox`) yazılacak ve `WorkManager` ile internet geldiğinde kaldığı yerden devam ettirilecek. |
| **Android 12/13/14+ Nearby İzin Karmaşası** | Cihazların birbirini bulamaması | Android sürümüne göre (`BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`, `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`) dinamik izin kontrolcüsü ve anlaşılır izin açıklama ekranları. |

---

## 5. Kilometre Taşı (Milestone) Planı

Her kilometre taşını **tek tek** tamamlayacağız. Bir adım bitip siz tablette test edip onaylamadan diğerine geçmeyeceğiz:

1. **M1 — Proje Kurulumu (Project Setup):**
   - Gradle yapılandırması (Kotlin, Compose Material 3, Hilt, Room, Tink, Ink/Graphics, Nearby bağımlılıkları).
   - Temel uygulama iskeleti, kolayca değiştirilebilir Tema sistemi (`Theme.kt`, `Color.kt`, `Typography.kt`), Türkçe (varsayılan) ve İngilizce dil dosyaları (`strings.xml`), navigasyon iskeleti.
2. **M2 — Çizim Editörü ve Performans (Drawing Editor + Performance):**
   - Kalem, fosforlu kalem, çizgi silgisi (stroke eraser), renk ve kalınlık seçici, metin kutuları (text blocks).
   - Stylus (çizim, basınç, eğim) + Parmak (zoom/pan), "Sadece Kalem" modu, avuç içi reddi.
   - `MotionEvent` tarihsel noktalar + hareket tahmini (prediction) ile düşük gecikmeli çizim.
   - Undo/Redo, uzamsal indeksleme (Spatial Grid/Quadtree) ve 10.000+ çizgi için Bitmap Karo Önbelleği (Tile Cache) + FPS/performans test modu.
3. **M3 — Vektör Not Formatı ve Yerel Depolama (Stroke Format + Local Storage):**
   - Versiyonlu başlık, delta-kodlamalı koordinatlar, kuantize basınç, VarInt ve GZIP sıkıştırmalı `.tnote` binary formatı.
   - JSON debug dışa aktarım.
   - `encode -> decode` doğruluğunu kanıtlayan kapsamlı Birim Testler (Unit Tests).
   - Room veritabanı + Atomik otomatik kayıt (`temp -> atomic rename`), PNG küçük resim (thumbnail) üretimi ve yerel not listesi ekranı.
4. **M4 — Sohbet Arayüzü (Chat UI):**
   - WhatsApp tarzı 1:1 sohbet listesi ve mesajlaşma ekranı.
   - Metin mesajları ve özel "El Yazısı Not Eki" (küçük resim önizlemeli, indirme/yükleme durum göstergeli) balonları.
   - Notu sohbet içinden tek dokunuşla açıp görüntüleme / düzenleme akışı.
5. **M5 — Kimlik Doğrulama, Relay ("Bekleme Odası"), Şifreleme ve Otomatik Silme:**
   - Kullanıcı kayıt/giriş (E-posta veya Telefon) ve kullanıcı bulma ekranı.
   - `RelayStorage` arayüzü ve Firebase/Supabase bağlantısı.
   - Cihazda uçtan uca şifreleme (E2EE) -> Şifreli dosyayı Relay'e yükleme -> Alıcının indirip şifreyi çözmesi -> Başarılı indirme sonrası sunucudan anında silme.
   - 7 gün içinde indirilmeyen notların sunucu tarafında otomatik silinmesi (Server-side TTL / Scheduled Function) ve güvenlik kuralları (`storage.rules` / `firestore.rules`).
   - Dosya boyutu sınırı ve kesilen yüklemeleri devam ettirme.
6. **M6 — Nearby Çevrimdışı Paylaşım (Offline Sharing):**
   - Google Nearby Connections API (Bluetooth + Wi-Fi Direct) ile internetsiz doğrudan cihazdan cihaza not gönderimi.
   - Tüm Android sürümleri için çalışma zamanı izin yönetimi.
   - Gönderim/alım yüzdesi (progress bar), iptal etme (cancel) ve yeniden deneme (retry) arayüzü.
7. **M7 — İyileştirme ve Testler (Polish & Testing):**
   - İnternet yokken, depolama alanı doluyken ve yükleme kesildiğinde uç durum (edge-case) testleri.
   - Arayüz cilalama, performans doğrulaması ve son dokümantasyon.

---

## 6. Onaylanan Teknik ve Tasarım Kararları

Seçimleriniz doğrultusunda aşağıdaki kararlar kesinleşmiştir:

1. **Çizim (Ink) Motoru:** Aktif çizim için **Jetpack Ink (`androidx.ink`) + `MotionEventPredictor`**, sabitlenmiş çizgiler (10.000+ çizgi performansı) için **Özel Karo Önbellekli (Tiled) Compose Tuvali** hibrit yapısı kullanılacak.
2. **Backend (Relay) Altyapısı:** **Firebase** (`Firebase Auth`, `Cloud Firestore`, `Cloud Storage`, `Cloud Functions / GCS Object Lifecycle`) kullanılacak ve tüm sunucu işlemleri ileride başka bir servise kolayca geçilebilmesi için `RelayStorage` arayüzünün (interface) arkasına soyutlanacak.
3. **Tuval Sayfa Yapısı:** **Dikey Çoklu Sayfa (A4 / Tablet oranlı sayfalar, aşağı doğru yeni sayfa eklenebilen yapı)** kullanılacak.
4. **Tema ve Tasarım Sistemi:** Klasördeki `stitch_minimalist_social_notes_tablet.zip` tasarımındaki renk paleti (`Charcoal 950–100`, kareli defter dokusu, `18dp–22dp` squircle köşe yuvarlatmaları) başlangıç teması olarak kurulacak; ancak tüm renkler ve şekiller tek bir `ThemePalette` yapısı üzerinden yönetileceği için istediğiniz zaman tek satırla farklı temalarla (ör. Açık Kağıt Teması vb.) değiştirilebilecek.



---

# Faz 2 (Phase 2) — Firebase Spark (Ücretsiz Plan) Hesaplar, Arkadaşlar, 1:1 Sohbet ve Gruplar Planı

> [!IMPORTANT]
> **ÖNEMLİ GÜVENLİK VE TEST SÜRÜMÜ NOTU (FAZ 2):**
> Faz 2'de tüm veriler **tamamen ücretsiz Firebase Spark Planı** üzerinde (`Firebase Authentication` + `Cloud Firestore`) tutulur; kredi kartı veya Blaze/ücretli plan kesinlikle kullanılmaz.
> Bu aşamada **mesajlar Uçtan Uca Şifreli (E2EE) DEĞİLDİR** — mesajların şifrelenmesi ve sunucu üzerinden not dosyası paylaşımı **Faz 3** kapsamındadır. Bu durum hem burada hem de uygulamanın Ayarlar ekranında kısa bir **"Test Sürümü"** uyarısı ile kullanıcıya açıkça gösterilecektir. **Herkese açık profillerde (`users/{uid}`) e-posta adresi veya telefon numarası kesinlikle saklanmaz.**

---

## 7. Faz 2 Kısıtları ve Faz 3'e Bırakılan (Kapsam Dışı) Özellikler

### Spark Ücretsiz Plan Sınırları
- **İzin Verilen Servisler:** `Firebase Authentication` (E-posta/Şifre + Google ile Giriş + Şifre Sıfırlama) ve `Cloud Firestore` (+ isteğe bağlı `Crashlytics`).
- **Yasak / Kullanılmayan Servisler:** `Cloud Storage`, `Cloud Functions` (sunucu kodu yok) ve kredi kartı/Blaze planı gerektiren hiçbir servis eklenmeyecektir.
- Tüm yetkilendirme ve güvenlik kontrolleri doğrudan **Firestore Security Rules (`firestore.rules`)** tarafından zorunlu kılınacaktır (istemci hiçbir zaman güvenilir kabul edilmez).
- Kota dostu kullanım: Mesajlar sayfalamalı yüklenecek (**limit ~30**), gerçek zamanlı `SnapshotListener` dinleyicileri yalnızca ekranda görünürken bağlanıp ekran kapanınca sökülecek, Firestore çevrimdışı kalıcılığı (**offline persistence**) aktif olacak, periyodik sorgulama (`polling`) veya tüm koleksiyonu okuma (`list`) yapılmayacaktır.

### Kapsam Dışı — Faz 3'e Bırakılan Özellikler (`Out of Scope`)
Aşağıdaki özellikler Faz 2'de **yapılmayacaktır**; gelecek Faz 3 için ayrılmıştır:
1. Sunucu üzerinden not veya dosya gönderme (sohbet ekranındaki **"Not Ekle"** butonuna tıklandığında kullanıcıya *"Yakında — Şimdilik yakındaki tabletle Yakın Paylaşım (Nearby) üzerinden not paylaşabilirsiniz"* mesajı gösterilir; Faz 1'deki çevrimdışı Nearby paylaşımı aynen çalışmaya devam eder).
2. Anlık bildirimler (**Push Notifications / FCM**).
3. Sunucu tarafında dosyaların otomatik silinmesi (**TTL**).
4. Mesajlarda **Uçtan Uca Şifreleme (E2EE)**.
5. Davet linkleri / **Deep Links**.
6. Profil fotoğrafı yükleme (Cloud Storage gerektirdiği için Faz 2'de yerleşik renkli avatar seçenekleri veya adın baş harfleri kullanılır).
7. Sunucu taraflı hız sınırlama (**Rate Limiting**).
8. Hesap silindiğinde diğer kullanıcıların sohbetlerindeki mesajların sunucuda tam temizliği (sunucu kodu olmadan mümkün olmadığı için en iyi çaba — *best-effort* — temizlik yapılır).
9. Reklamlar ve analitik araçları.

---

## 8. Önerilen Firestore Veri Modeli (Koleksiyonlar ve Örnek Belgeler)

### 1) `users/{uid}` — Herkese Açık Kullanıcı Profili (Listelenemez!)
> **Gizlilik:** Burada kesinlikle `email` veya `phone` tutulmaz! Kullanıcıya özel ayarlar yalnızca sahibinin okuyabildiği alt belgede veya Auth içinde kalır.
```json
{
  "uid": "u_abc123",
  "username": "ahmet_yilmaz",
  "displayName": "Ahmet Yılmaz",
  "avatarPreset": "cobalt_circle",
  "createdAt": "ServerTimestamp",
  "updatedAt": "ServerTimestamp"
}
```

### 2) `usernames/{username}` — Benzersiz Kullanıcı Adı İndeksi (Sunucu Kodu Olmadan Uniqueness)
> Küçük harf, `3-20` karakter, sadece `a-z`, `0-9`, `_`. Kayıt sırasında `WriteBatch` ile hem `usernames/{username}` hem `users/{uid}` birlikte atomik oluşturulur.
```json
{
  "uid": "u_abc123",
  "username": "ahmet_yilmaz",
  "createdAt": "ServerTimestamp"
}
```

### 3) `friendships/{pairId}` — Arkadaşlık İstekleri ve Arkadaşlık Durumu
> `pairId = min(uid1, uid2) + "_" + max(uid1, uid2)` şeklinde deterministik üretilir. Böylece iki kullanıcı arasında aynı anda çakışan iki istek belgesi oluşamaz ve güvenlik kuralları `exists()` ile arkadaşlığı tek adımda doğrulayabilir.
```json
{
  "pairId": "u_abc123_u_xyz789",
  "users": ["u_abc123", "u_xyz789"],
  "uidA": "u_abc123",
  "uidB": "u_xyz789",
  "status": "accepted",
  "requesterId": "u_abc123",
  "receiverId": "u_xyz789",
  "createdAt": "ServerTimestamp",
  "updatedAt": "ServerTimestamp"
}
```

### 4) `blocks/{blockId}` — Engelleme Kayıtları
> `blockId = blockerUid + "_" + blockedUid`. Güvenlik kuralları hem arkadaşlık isteği atarken hem sohbet açarken/mesaj gönderirken iki yönlü `exists(/databases/.../documents/blocks/{a_b})` kontrolü yapar.
```json
{
  "blockerUid": "u_abc123",
  "blockedUid": "u_bad999",
  "createdAt": "ServerTimestamp"
}
```

### 5) `direct_chats/{chatId}` — 1:1 Doğrudan Sohbet (Yalnızca Metin)
> `chatId = min(uid1, uid2) + "_" + max(uid1, uid2)`. Sadece `accepted` arkadaşlar arasında ve engel yoksa oluşturulabilir.
```json
{
  "chatId": "u_abc123_u_xyz789",
  "participants": ["u_abc123", "u_xyz789"],
  "participantProfiles": {
    "u_abc123": { "displayName": "Ahmet Yılmaz", "username": "ahmet_yilmaz", "avatarPreset": "cobalt_circle" },
    "u_xyz789": { "displayName": "Mehmet Kaya", "username": "mehmet_k", "avatarPreset": "emerald_circle" }
  },
  "lastMessageText": "Yarınki fizik ödevini yaptın mı?",
  "lastMessageSenderId": "u_abc123",
  "lastMessageAt": "ServerTimestamp",
  "lastReadAt": {
    "u_abc123": "ServerTimestamp",
    "u_xyz789": "Timestamp"
  },
  "unreadCount": {
    "u_abc123": 0,
    "u_xyz789": 1
  },
  "createdAt": "ServerTimestamp",
  "updatedAt": "ServerTimestamp"
}
```

#### Alt Koleksiyon: `direct_chats/{chatId}/messages/{messageId}`
```json
{
  "messageId": "msg_01",
  "senderId": "u_abc123",
  "text": "Yarınki fizik ödevini yaptın mı?",
  "createdAt": "ServerTimestamp"
}
```

### 6) `groups/{groupId}` — Çalışma Grupları (Maksimum 50 Üye, Yalnızca Metin)
```json
{
  "groupId": "grp_fizik101",
  "name": "Fizik — Vektörler Grubu",
  "description": "Haftalık soru çözümü ve özet paylaşımı",
  "avatarPreset": "purple_circle",
  "ownerId": "u_abc123",
  "memberIds": ["u_abc123", "u_xyz789"],
  "roles": {
    "u_abc123": "owner",
    "u_xyz789": "member"
  },
  "memberProfiles": {
    "u_abc123": { "displayName": "Ahmet Yılmaz", "username": "ahmet_yilmaz", "avatarPreset": "cobalt_circle" },
    "u_xyz789": { "displayName": "Mehmet Kaya", "username": "mehmet_k", "avatarPreset": "emerald_circle" }
  },
  "whoCanSend": "everyone",
  "lastMessageText": "Akşam 8'de çalışma var.",
  "lastMessageSenderId": "u_abc123",
  "lastMessageSenderName": "Ahmet Yılmaz",
  "lastMessageAt": "ServerTimestamp",
  "lastReadAt": {
    "u_abc123": "ServerTimestamp"
  },
  "createdAt": "ServerTimestamp",
  "updatedAt": "ServerTimestamp"
}
```

#### Alt Koleksiyon: `groups/{groupId}/messages/{messageId}`
```json
{
  "messageId": "gmsg_01",
  "senderId": "u_abc123",
  "senderName": "Ahmet Yılmaz",
  "text": "Akşam 8'de çalışma var.",
  "createdAt": "ServerTimestamp"
}
```

### 7) `reports/{reportId}` — Kullanıcı / Mesaj Şikayetleri (Yalnızca Oluşturulabilir)
```json
{
  "reporterId": "u_xyz789",
  "targetType": "user",
  "targetUserId": "u_bad999",
  "targetChatId": null,
  "targetMessageId": null,
  "reason": "Rahatsız edici mesaj",
  "createdAt": "ServerTimestamp"
}
```

---

## 9. Güvenlik Kuralları (`firestore.rules`) Stratejisi

Sunucu kodu olmadan tam güvenlik şu kurallarla sağlanır:
1. **`users/{uid}` ve `usernames/{name}`:**
   - `allow list: if false;` — Tüm kullanıcıları listeleme veya önek arama (prefix search) kesinlikle kapalıdır; sadece tam `@kullanici_adi` veya `uid` ile tekil `get` yapılabilir.
   - `usernames/{name}` sadece belge mevcut değilse (`!exists`), adı `^[a-z0-9_]{3,20}$` kuralına uyuyorsa ve `request.resource.data.uid == request.auth.uid` ise oluşturulabilir (`allow update: if false`).
   - `users/{uid}` güncellenirken `username` değiştirilmek istenirse eski/yeni username belgeleriyle atomik tutarlılık aranır veya username değişimi atomik batch ile korunur.
2. **Engelleme ve Arkadaşlık (`blocks` & `friendships`):**
   - `blocks/{blockId}`: Sadece `blockerUid == request.auth.uid` tarafından okunabilir/oluşturulabilir/silinebilir (ayrıca güvenlik kuralları içinde `isBlockedBetween(u1, u2)` yardımcı fonksiyonu ile iki kullanıcı birbirine istek/sohbet gönderirken `exists` kontrolü yapılır).
   - `friendships/{pairId}`: Sadece `pairId` içindeki iki taraftan biri okuyabilir; oluştururken `!isBlockedBetween` ve `status == "pending"` + `requesterId == request.auth.uid` zorunludur. `accepted` durumuna yalnızca `receiverId` geçebilir; iptal/red/arkadaşlıktan çıkarma (`delete`) işlemini her iki taraf da yapabilir.
3. **Doğrudan Sohbet (`direct_chats` & `messages`):**
   - Oluşturma (`create`): `friendships/{chatId}` belgesi mevcut ve `status == "accepted"` olmalı, ayrıca `!isBlockedBetween(u1, u2)` sağlanmalıdır.
   - Okuma/Yazma: Sadece `participants` içindeki üyeler (`request.auth.uid in resource.data.participants`).
   - Mesaj ekleme: `senderId == request.auth.uid`, `!isBlockedBetween(...)`, `text` uzunluğu `1..2000`, `createdAt == request.time`.
   - Mesaj düzenleme (`update`): Herkese yasak (`false`); silme (`delete`): Yalnızca `resource.data.senderId == request.auth.uid`.
4. **Gruplar (`groups` & `messages`):**
   - Üye sayısı `memberIds.size() <= 50`.
   - Rol kontrolleri `roles[request.auth.uid]` üzerinden yapılır:
     - `owner`: Grubu silebilir, `admin` atayabilir/alabilir.
     - `admin` / `owner`: Üye ekleyebilir (sadece kendi arkadaşları UI'da seçilir, maks 50 üye kuralda zorlanır), üye çıkarabilir, grup adı/açıklaması/avatarı ve `whoCanSend` (`everyone` | `admins_only`) ayarını değiştirebilir.
     - `member`: Sadece gruptan ayrılabilir (`memberIds` ve `roles` içinden yalnızca kendi `uid`'sini kaldırır).
   - Mesaj gönderme: `whoCanSend == "everyone"` ise tüm üyeler; `whoCanSend == "admins_only"` ise yalnızca `owner` veya `admin` mesaj gönderebilir.
5. **Şikayetler (`reports`):**
   - `allow create` (yalnızca `reporterId == request.auth.uid`); `allow read, update, delete: if false`.
6. **`google-services.json` Hakkında Not:**
   - Proje `.gitignore` dosyasında `google-services.json` **hariç tutulmuştur** ve herkese açık (Public) GitHub deposuna **yüklenmemelidir**. Her geliştirici kendi Firebase projesinden indirdiği `google-services.json` dosyasını yerel olarak `app/google-services.json` konumuna koyar.

---

## 10. Okunmamış Mesaj Maliyeti ve QR Kodu Kütüphanesi Seçimi

### A. Okunmamış Mesaj (Unread Badge) Tasarımı ve Okuma Maliyeti
- **Ucuz ve Kota Dostu Çözüm:** Okunmamış mesaj sayısını hesaplamak için her sohbetin alt koleksiyonundaki mesajları (`messages`) sorgularsak her sohbet için onlarca ekstra Firestore okuma maliyeti doğar.
- Bunun yerine **okunmamış bilgisini doğrudan üst sohbet belgesinin içinde** saklıyoruz (`direct_chats/{chatId}` ve `groups/{groupId}`):
  - `lastReadAt.{uid}`: Kullanıcının sohbeti en son açtığı an.
  - `lastMessageAt` ve `lastMessageSenderId`: En son mesajın zamanı ve göndereni.
  - `unreadCount.{uid}`: Gönderen kişi son mesajı yazarken karşı tarafın `unreadCount` değerini atomik `FieldValue.increment(1)` ile artırır; alıcı sohbeti açtığında kendi `unreadCount.{uid}` alanını `0` ve `lastReadAt.{uid}` alanını `serverTimestamp()` olarak sıfırlar.
- **Okuma Maliyeti:** Sohbet listesi zaten açık olan sohbet belgelerini (`limit ~30`) okuduğu için **okunmamış nokta/sayı göstermek 0 ekstra Firestore okuma maliyetine** sahiptir!

### B. QR Kodu Kütüphanesi Seçimi (Neden Daha Basit?)
- **QR Oluşturma:** `com.google.zxing:core` (sadece birkaç yüz KB'lık saf Kotlin/Java kütüphanesi, ağ izni veya ağır kamera bağımlılığı yok; kendi `@kullanici_adi` verimizi anında siyah-beyaz QR bitmap'ine dönüştürür).
- **QR Okutma:** **Google Play Services Code Scanner (`com.google.android.gms:play-services-code-scanner`)** seçiyoruz. Çünkü kamera önizlemesi, odaklama ve `CAMERA` izni diyaloğunu Google Play Services kendisi hazır bir tam ekran pencere olarak yönetir; uygulamamıza ekstra CameraX/SurfaceView kodu eklemeye gerek kalmaz (Google Play Services olmayan sade emülatörlerde ise kullanıcı `@kullanici_adi` arama kutusundan da aynı işlemi anında yapabilir).

---

## 11. Ekran / Navigasyon Değişiklikleri ve Spark Kota Tahmini

### Ekran ve Navigasyon Akışı
- Mevcut üst pill sekme çubuğu korunur: **`Notlar` | `Arkadaşlar` | `Gruplar`**.
- **Çıkış Yapılmışken Faz 1 Koruması:** Kullanıcı oturum açmamışken (`FirebaseAuth.currentUser == null`) **`Notlar` sekmesi ve `Yakın Paylaşım (Nearby)` %100 çalışmaya devam eder**. `Arkadaşlar` ve `Gruplar` sekmelerinde ise dostça *"Giriş Yap / Hesap Oluştur"* ekranı görünür.
- **`Arkadaşlar` ve `Gruplar` Sekmesi (Tablet Yatay & Dikey Uyumu):**
  - Sağ alttaki `+` butonu kısa bir menü açar: **`Arkadaş Ekle (@kullanıcı adı / QR)`** ve **`Yeni Grup Oluştur`**.
  - Tablet yatay modda: Sol listede sohbetler/gruplar, sağda açık sohbet ekranı (*list-detail*). Dikey mod ve telefonlarda: Tek panel (*list -> detail*).
  - Arkadaşlık istekleri (gelen / giden / engellenenler) ve QR Tarama / QR Gösterme temiz alt sayfa veya modal olarak açılır.
  - Hesap ve Ayarlar ekranı (profil düzenleme, tema seçimi, "Test Sürümü — E2EE Faz 3'te" notu, çıkış yap, hesabı sil).

### Spark Ücretsiz Plan Kota Tahmini
- Spark ücretsiz limitleri: **Günde 50.000 okuma**, **20.000 yazma**, **20.000 silme**.
- Sayfalama (`limit(30)`), sohbet listesinde gömülü `unreadCount`, aktif ekran dışı dinleyicileri kapatma ve Firestore yerel önbellek (`persistenceEnabled = true`) sayesinde 1 aktif öğrencinin günlük tüketimi ortalama **~150–400 okuma** ve **~40–100 yazma** civarında kalır; yani küçük/orta bir sınıf veya arkadaş grubu ücretsiz Spark kotası içinde rahatça çalışır.

### Riskler ve Dikkat Edilecekler
| Risk | Etki | Çözüm |
| :--- | :--- | :--- |
| **Sunucu kodu olmadan hesap silme** | Kullanıcı silindiğinde başka kişilerin sohbetlerindeki eski mesajlar `senderId` olarak kalır. | Kullanıcının kendi `users/{uid}`, `usernames/{name}`, kendi arkadaşlık/engel kayıtları ve grup üyelikleri temizlenip Auth hesabı silinir; diğer kişilerin sohbet alt koleksiyonlarındaki mesajlar güvenlik gereği istemci tarafından toplu taranamaz — bu sınırlama **Faz 3 (Cloud Functions)** için dokümante edilir. |
| **Dinleyici (Listener) sızıntısı** | Ekran değişiminde açık kalan dinleyiciler kotayı harcar. | Her `SnapshotListener`, Jetpack Compose yaşam döngüsüne / ViewModel'e `DisposableEffect` veya `callbackFlow` ile bağlanır ve ekran görünmez olduğunda anında iptal edilir. |
| **Çift cihaz / İki hesap gerektiren testler** | Arkadaşlık ve 1:1 sohbeti tam test etmek tek cihazda sınırlıdır. | İki farklı test e-posta/Google hesabıyla emülatör + gerçek tablet üzerinden paralel test adımları her kilometre taşı sonunda paylaşılacaktır. |

---

## 12. Faz 2 Kilometre Taşı Planı (M1 – M7)

Her adım tek tek uygulanıp sizin onayınızla bir sonrakine geçilecektir:
- **M1 — Firebase Kurulumu:** *(Altyapı kodları ve `firestore.rules` hazır; `google-services.json` eklendikten sonra tam aktif olacak)* — Firebase Console adımları paylaşıldı, `cloud_storage` bağımlılığı kaldırıldı, `FirebaseAuth` + çevrimdışı önbellekli `Cloud Firestore` (`FirebaseProvider`) ve QR/Google Giriş kütüphaneleri bağlandı.
- **M2 — Kimlik Doğrulama, Profil ve Benzersiz `@kullanici_adi`:** E-posta+Şifre kayıt/giriş, Google ile Giriş, Şifre Sıfırlama, atomik `usernames/{name}` + `users/{uid}` batch kaydı, yerleşik avatar seçimi, profil düzenleme, çıkış yapıldığında Faz 1'in çalışmaya devam etmesi.
- **M3 — Arkadaşlar (Tam Arama, QR, İstekler, Engelleme, Şikayet):** Tam `@kullanici_adi` arama, ZXing + Play Services Code Scanner ile QR göster/oku, istek gönder/kabul/red/iptal/arkadaşlıktan çıkar, engelle/engeli kaldır, yalnızca oluşturulabilir `reports`.
- **M4 — Doğrudan 1:1 Sohbet (Yalnızca Metin) ve Okunmamış Sayacı:** Gerçek zamanlı mesajlaşma (`limit(30)`), gönderiliyor/gönderildi/hata+yeniden dene durumları, tarih ayırıcıları, kendi mesajını silme, max 2000 karakter, "Not Ekle" için "Yakında" mesajı, `unreadCount` + arama filtresi.
- **M5 — Çalışma Grupları, Roller ve Grup Ayarları:** Arkadaşlardan grup kurma (max 50 üye), `owner/admin/member` rolleri, rol yönetimi, "kimler mesaj atabilir", gruptan ayrılma, grubu silme (owner), yerel sessize alma (mute).
- **M6 — Hesap Silme, Ayarlar Ekranı, Hata ve Çevrimdışı İyileştirmeleri:** Yeniden doğrulama (*re-authenticate*) ile hesap silme, Ayarlar ekranında "Test Sürümü — E2EE yok" uyarısı, internet yokken önbellek banner'ı, Türkçe dostça hata mesajları.
- **M7 — Güvenlik Kuralları, Emulator Testleri, Tablet Düzeni ve Son İnceleme:** `firestore.rules`, `firestore.indexes.json`, `@firebase/rules-unit-testing` test paketi, yatay/dikey tablet düzeni cilalaması ve son kontrol.
