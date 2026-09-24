# Voice Companion — Local AI Voice Companion

Aplikasi Android AI companion voice, 100% lokal, dengan multi-character
system, master ON/OFF switch, dan floating bubble. Dibuild & dijalankan
langsung dari HP.

## Ringkasan perubahan dari versi MVP sebelumnya

### Perbaikan babak ini (setelah review)
- **[BUG KRITIS - FIXED]** `ChatViewModel`: `aiPower`/`userPrefs` dulu di-assign
  sebagai property initializer SEBELUM `init{}` block yang manggil
  `ensureRepositoriesOnly()` — di Kotlin, urutan eksekusi property initializer
  & init block itu sesuai urutan penulisan di source, jadi ini bisa
  `UninitializedPropertyAccessException` saat MainActivity dibuka pertama kali
  (skenario paling umum, karena MainActivity = launcher activity). Sudah
  dipindah jadi init block pertama di class body. Sudah di-grep ulang ke
  seluruh file lain -- tidak ada pola yang sama di tempat lain (semua akses
  `CompanionEngineHost` lainnya ada di dalam fungsi/`onCreate()`, bukan
  property initializer level-class, jadi aman).
- Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.properties`)
  ditambahkan -- lihat catatan `gradle-wrapper.jar` di bagian batasan di bawah.

### File baru
- `data/CharacterEntity.kt`, `CharacterDao.kt`, `CharacterRepository.kt` — sistem multi-karakter
- `data/UserPreferences.kt`, `UserPreferencesRepository.kt` — preferences user (bukan karakter)
- `data/AiPowerRepository.kt` — master switch AI ON/OFF, persistent
- `data/PromptBuilder.kt` — gabungan BASE + CHARACTER + USER PREFS jadi satu system prompt
- `engine/CompanionEngineHost.kt` (rewrite total) — siklus hidup engine terpusat, race-safe
- `CharactersActivity.kt` + `CharacterEditorActivity.kt` + `PreferencesActivity.kt` (+ layout-nya) — layar baru
- `ui/CharacterAdapter.kt`

### File yang diedit besar-besaran
- `engine/LLMEngine.kt`, `MediaPipeLlmEngine.kt` — error spesifik (ModelMissing/InsufficientRam/ModelIncompatible), RAM pre-check, GenerationConfig (temperature/topK/maxTokens), reload
- `engine/AndroidSpeechEngine.kt` — cancel(), timeout handling
- `engine/AndroidTTSEngine.kt` — voice selection
- `data/ChatRepository.kt` — pakai Character + hormati toggle Memory ON/OFF (transient session kalau memory off)
- `service/CompanionForegroundService.kt` — master switch AI ON/OFF (bukan lagi pause/resume)
- `service/FloatingBubbleService.kt` — kontrol power/stop/open-app di mini chat, posisi persisten, ukuran bubble
- `ui/ChatViewModel.kt`, `MainActivity.kt`, `SettingsActivity.kt` — status IDLE/LISTENING/PROCESSING/SPEAKING/OFFLINE/ERROR, text input, tombol Stop, AI switch
- `AndroidManifest.xml` — 3 activity baru terdaftar

### File yang dihapus
- `data/PersonaConfig.kt`, `PersonaRepository.kt` — digantikan CharacterEntity/CharacterRepository (single persona → multi character)

### Dependency
**Tidak ada dependency baru ditambahkan.** Tetap: AndroidX core/appcompat/material/
constraintlayout/lifecycle/recyclerview, kotlinx-coroutines, Room, MediaPipe
`tasks-genai` (LLM lokal), Gson. **Tidak ada** Retrofit/OkHttp/Firebase/
analytics — sudah diaudit (`grep` seluruh source, hasil bersih).

### Migrasi database
`AppDatabase` naik dari versi 1 → 2 (nambah tabel `characters`). Karena app
ini belum pernah dirilis publik, dipakai `fallbackToDestructiveMigration()`
— artinya kalau kamu sudah pernah install versi MVP sebelumnya di HP, data
lama (conversation history) akan hilang saat pertama kali buka versi baru
ini. Ini bukan bug, ini keputusan sadar untuk MVP tahap ini (dicatat di
komentar `AppDatabase.kt`).

## Fitur yang sekarang ADA dan BENERAN JALAN

- ✅ Local LLM (MediaPipe/Gemma .task, RAM pre-check, error spesifik)
- ✅ Voice pipeline lokal: mic → STT on-device → LLM lokal → TTS → speaker
- ✅ Text input sebagai alternatif voice
- ✅ Floating bubble: buka mini chat, start/stop voice, lihat status, ON/OFF AI, buka app utama
- ✅ Foreground Service resmi + notification + master switch
- ✅ AI ON/OFF persistent (SharedPreferences), tidak auto-nyala sendiri setelah OFF
- ✅ Conversation history (Room), export JSON, clear conversation, clear all data
- ✅ Multiple characters: create/edit/duplicate/delete/select/reset-to-default
- ✅ Character editor lengkap: semua field di spek (personality, tone, mood, backstory,
  likes/dislikes, behavior rules, "never do" rules, conversation rules, greeting,
  advanced system prompt override)
- ✅ Persona Prompt Builder: BASE (safety, tidak bisa dihapus) + CHARACTER + USER PREFERENCES
- ✅ My Preferences: preferred name, language, response length, tone, emoji level, voice/auto-speak/memory toggle
- ✅ Settings terstruktur: AI / Voice / Character / Memory / Floating / System
- ✅ Status IDLE/LISTENING/PROCESSING/SPEAKING/OFFLINE/ERROR
- ✅ Model Manager: pick/reload/unload, status jelas, error ModelMissing/InsufficientRam/ModelIncompatible
- ✅ Voice settings: pilih voice TTS, speech rate, pitch, auto-speak
- ✅ Error handling: tidak crash kalau model hilang/gagal load/RAM kurang/permission ditolak/overlay ditolak
- ✅ Tidak ada request jaringan sama sekali (audit bersih)

## Yang JUJUR belum/tidak bisa dijamin (baca ini)

- **Tidak ada BOOT_COMPLETED receiver.** State AI ON/OFF persistent kalau APP
  dibuka lagi oleh user, TAPI kalau HP di-restart, service tidak otomatis
  menyala sendiri sampai kamu buka app-nya minimal sekali. Android 12+ juga
  membatasi start foreground service dari boot receiver, jadi ini bukan
  keputusan yang dibuat sembarangan.
- **Tidak ada jaminan 24/7.** Foreground service + notification bikin Android
  jauh lebih segan mematikan proses, tapi OEM (Xiaomi/Oppo/Vivo/Samsung) tetap
  bisa membatasi demi baterai/RAM. Tidak ada root/exploit/accessibility-abuse
  dipakai untuk memaksa ini — itu keputusan sadar mengikuti instruksi awal.
- **`stopGeneration()` belum instan mid-token** karena `generateResponse()` versi
  ini blocking/non-streaming. Migrasi ke `generateResponseAsync()` adalah
  langkah lanjutan yang jelas (dicatat di komentar `MediaPipeLlmEngine.kt`).
- **STT pakai `SpeechRecognizer` bawaan Android**, bukan Whisper (butuh native
  compile yang tidak realistis dibuild murni dari HP tanpa NDK/CMake).
- Kalau floating bubble DAN layar utama sama-sama listener aktif, listener
  STT/TTS terakhir yang di-set yang menerima update (keduanya tetap
  fungsional, ini batasan UI-level bukan crash).
- Temperature/max tokens/topK berlaku setelah **Reload model** (bukan
  live per-generate), karena parameter ini di-set saat `LlmInference`
  di-create, bukan per panggilan.

### Gradle Wrapper — sudah lengkap
`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, dan
`gradle/wrapper/gradle-wrapper.jar` sudah lengkap semua di project ini.
`gradle-wrapper.jar` sudah diverifikasi: dijalankan (`java -cp gradle-wrapper.jar
org.gradle.wrapper.GradleWrapperMain`) dan terbukti benar membaca
`gradle-wrapper.properties` lalu mencoba men-download distribusi Gradle 8.7
dari `services.gradle.org` sesuai konfigurasi. Tinggal jalankan
`./gradlew assembleDebug` di device/PC yang ada internet.



### Build APK dari HP
AndroidIDE (yang lama) **sudah tidak dimaintain** per pertengahan 2026.
Pakai **Code on the Go** (App Dev for All, appdevforall.org) atau cek rilis
terbaru AndroidIDE di F-Droid. Import folder `VoiceCompanion` ini, biarkan
Gradle sync (butuh internet sekali di awal untuk download dependency),
lalu Build > Build APK (debug). Hasil di
`app/build/outputs/apk/debug/app-debug.apk`.

### Masukkan model lokal
Download model `.task` (contoh: cari "Gemma-3 1B .task" atau "Gemma-3n E4B"
di Hugging Face, format LiteRT/MediaPipe, terkuantisasi). Buka app >
Settings > AI > "Pilih Model" > pilih file yang sudah didownload.

### Aktifkan AI
Switch "AI ON/OFF" ada di 3 tempat yang semuanya sinkron: layar utama
(pojok kiri atas), Settings > AI, dan tombol power di mini chat floating
bubble. Default ON setelah setup pertama kali. Saat OFF: mic/TTS/service
semua berhenti dan TIDAK otomatis nyala lagi sampai kamu nyalakan manual.

### Buat/edit karakter
Settings > Character > "Manage Characters" (atau langsung buka
`CharactersActivity`). Tombol "+ Karakter Baru" untuk buat baru, tombol
"Pilih" di tiap kartu karakter untuk menjadikannya aktif, "Edit" untuk
ubah semua field, "Duplikat" untuk menyalin, "Hapus" untuk karakter
custom (karakter bawaan Luna/Assistant tidak bisa dihapus, cuma di-reset).

### Aktifkan floating bubble
Settings > Floating > toggle "Floating bubble ON/OFF" > sistem akan minta
izin "Tampil di atas aplikasi lain" kalau belum diberikan. Bubble bisa
di-drag, tap untuk buka mini chat (mic, stop, power AI, buka app, close).

## Batasan Android yang masih berlaku
Dirangkum di bagian "Yang JUJUR belum/tidak bisa dijamin" di atas — intinya:
tidak ada jaminan 24/7, tidak ada auto-start setelah reboot HP, dan semua
pembatasan baterai/RAM dari OEM tetap berlaku karena ini murni pakai API
resmi Android (foreground service + notification), bukan trik/bypass.
