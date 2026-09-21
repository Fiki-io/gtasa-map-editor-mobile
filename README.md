# GTA SA Map Editor Mobile (Clean-Room Native Android)

Aplikasi Android mandiri untuk menyunting peta (*Map Editor*) game **Grand Theft Auto: San Andreas**, dibangun 100% dari nol (*clean-room implementation*) menggunakan arsitektur modern Android (Gradle, Java, OpenGL ES 2.0/3.0, Material Components).

Dilengkapi dengan pipeline otomatis **GitHub Actions (CI/CD)** sehingga proses kompilasi APK dapat dilakukan langsung di cloud GitHub tanpa membebani laptop.

---

## Fitur Utama

1. **3D Viewport & Interactive Gizmo:**
   - Rendering dunia GTA SA secara real-time dengan pencahayaan dinamis dan frustum culling.
   - **3D Transform Gizmo:** Sumbu Merah (X), Hijau (Y), dan Biru (Z) yang dapat disentuh dan digeser secara visual untuk memindahkan posisi objek, serta cincin rotasi untuk memutar orientasi objek.
   - Kontrol kamera multi-touch: geser 1 jari untuk orbit, geser 2 jari untuk pan, dan cubit (*pinch*) untuk zoom.
2. **2D Radar Minimap Interaktif (`mapa.png`):**
   - Radar peta dunia San Andreas resolusi tinggi di pojok layar.
   - Menampilkan posisi kamera dan posisi objek yang sedang dipilih.
   - Fitur **Tap-to-Teleport**: sentuh titik mana pun di radar untuk langsung memindahkan kamera ke lokasi tersebut.
3. **Core Engine & Format GTA SA Asli:**
   - **IPLParser:** Membaca dan menulis file `.ipl` (Item Placement) standar GTA SA tanpa batas header kaku.
   - **IDEParser & DATParser:** Mengindeks seluruh file `.ide` dan konfigurasi `data/gta.dat`.
   - **IMGArchive:** Membaca arsip `gta3.img` versi 2 (2048-byte sector random access).
   - **DFFParser:** Mem-parsing geometri 3D RenderWare DFF ke vertex buffer OpenGL ES.
   - **TexturePackReader:** Membaca 11.879 tekstur GTA SA dari `textures.tpk` (mendukung kompresi `ETC1` dan `RGBA4444`) plus mendukung tekstur eksternal (`.png`/`.jpg`).
4. **Toolbar Lengkap:**
   - **Select Map:** Memilih area map dari daftar `gta.dat` (contoh: `LAe.ipl`, `LAs.ipl`, `SFn.ipl`, `vegasE.ipl`) atau membuka file `.ipl` kustom.
   - **Add Object:** Menambahkan objek baru berdasarkan ID atau nama model dari katalog IDE.
   - **Duplicate:** Menduplikasi objek terpilih.
   - **Delete:** Menghapus objek dari map.
   - **Properties (Inspector):** Mengedit koordinat numerik X, Y, Z dan rotasi secara presisi.
   - **Save IPL:** Menyimpan hasil editan ke file `.ipl` yang siap digunakan langsung di game atau ModLoader.

---

## Cara Build APK Otomatis Lewat GitHub Actions (GHA)

Anda **tidak perlu** mengompilasi APK di laptop Anda! GitHub Actions akan melakukan kompilasi di server cloud GitHub secara gratis.

### Langkah 1: Inisialisasi Git dan Push ke Repositori GitHub Anda
Jalankan perintah berikut di folder proyek ini (`/tmp/map`):

```bash
git init
git add .
git commit -m "Initial commit: GTA SA Map Editor Mobile"
git branch -M main
git remote add origin https://github.com/USERNAME-ANDA/NAMA-REPO-ANDA.git
git push -u origin main
```

### Langkah 2: Mengunduh Hasil APK dari GitHub Actions
1. Buka repositori GitHub Anda di browser (bisa lewat laptop atau langsung di HP Anda).
2. Klik tab **Actions** di bagian atas repositori.
3. Anda akan melihat workflow **"Build Android APK"** sedang berjalan. Tunggu sekitar 2–3 menit hingga muncul tanda centang hijau (Success).
4. Klik pada run workflow tersebut, lalu gulir ke bawah ke bagian **Artifacts**.
5. Klik **`GTASA-MapEditor-Debug-APK`** untuk mengunduh file `.apk`-nya.
6. Pasang (*install*) file APK tersebut di smartphone Android Anda!

---

## Struktur Folder Proyek

```
GTASAMapEditor/
├── .github/
│   └── workflows/
│       └── build.yml               # Workflow GitHub Actions untuk build APK
├── app/
│   ├── build.gradle                # Konfigurasi modul aplikasi
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml # Permissions & konfigurasi activity
│           ├── java/com/gtasa/mapeditor/
│           │   ├── core/           # IPLParser, IDEParser, DATParser, IMGArchive, DFFParser, TexturePackReader
│           │   ├── render/         # MapGLSurfaceView, MapRenderer, Camera3D, TransformGizmo, Shaders
│           │   └── ui/             # MainActivity, RadarOverlayView, MapSelectorDialog, ObjectInspectorDialog, AssetSetupDialog
│           └── res/                # Layouts, icons, and themes
├── build.gradle                    # Root gradle script
├── settings.gradle                 # Settings gradle
└── gradlew                         # Gradle wrapper executable
```

---

## Lisensi
Proyek ini dibuat untuk komunitas modding GTA: San Andreas sebagai perangkat lunak terbuka (*Clean-Room Open Source Implementation*).
