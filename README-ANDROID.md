# KYNIME Android (WebView + login Google lewat halaman "Pilih akun" Google)

Aplikasi pembungkus situs KYNIME. Tombol **Lanjutkan dengan Google** membuka halaman
`accounts.google.com/.../accountchooser` ("Pilih akun — untuk melanjutkan ke ...") — halaman yang sama persis dengan di browser —
lewat **Chrome Custom Tab**. Google melarang halaman ini dimuat di dalam WebView, jadi tidak bisa ditanam langsung di aplikasi.

## Cara kerja
1. Situs membuat `state` acak, aplikasi membuka halaman Google di Custom Tab.
2. Pilih akun -> Google mengarahkan ke `https://situs-anda/gcb.html#id_token=...`.
3. `gcb.html` menitipkan token ke server (`google_stash`), lalu otomatis membuka aplikasi lagi (`kynime://done`; ada tombol "Kembali ke KYNIME" bila diblokir).
4. Situs di dalam aplikasi mengambil sesi (`google_claim`) dan login selesai. Tidak perlu OAuth client Android / SHA-1.

## 1. Setup Google Cloud (Web client yang sama dengan GOOGLE_CLIENT_ID di Vercel)
Credentials > OAuth client ID (Web application) > **Authorized redirect URIs** > tambahkan:
`https://kynime5.vercel.app/gcb.html`  (harus persis; tambahkan juga untuk domain lain bila ada)
Nama & logo di dialog ("untuk melanjutkan ke ...") diatur di Google Auth Platform > **Branding**. Ubah status ke *In production* agar semua akun bisa masuk.

## 2. Isi `gradle.properties`
`SITE_URL` = `https://kynime5.vercel.app/` (sudah diatur di paket ini).

## 3. Build APK (tanpa Android Studio)
1. Unggah isi folder ini ke repositori GitHub baru.
2. Tab **Actions > Build APK > Run workflow**.
3. Unduh artifact **KYNIME-debug-apk**, ekstrak jika perlu, lalu pasang APK di HP untuk pengujian.
Atau lokal: `gradle :app:assembleRelease` (JDK 17, Gradle 8.9, Android SDK 34).

## Keamanan signing
Paket ini sengaja tidak menyertakan `app/kynime.jks` atau kata sandi signing. Workflow membangun APK debug yang ditandatangani otomatis untuk pengujian. Jangan mengunggah keystore, kata sandi, token, atau rahasia ke GitHub. Untuk rilis publik, siapkan signing release terpisah melalui GitHub Actions Secrets.

## Catatan
- Situs harus memakai versi terbaru (`js/auth.js`, `api/auth.js`, `gcb.html`) dan Vercel sudah punya `GOOGLE_CLIENT_ID` + Redis.
- Jika pengguna menekan back dari tab Google tanpa memilih akun, login dibatalkan otomatis setelah beberapa detik.
- `app/kynime.jks` (password `kynime123`) hanya agar tanda tangan APK tetap sama antar build. Untuk Play Store buat keystore sendiri.
