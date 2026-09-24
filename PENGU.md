# Pengu Launcher — Android

PengusCraft'ın Android launcher'ı. [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android)
(PojavLauncher devamı) üzerine kurulu; Minecraft **Java Edition**'ı telefonda çalıştırır.
Lisans LGPL-3.0: bu klasördeki değiştirilmiş kaynak kodunun herkese açık yayınlanması gerekir.

## Pengu'ya özel kısımlar

| Nerede | Ne |
|---|---|
| `app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/pengu/PenguConfig.java` | Sunucu, MC sürümü, modlar — PC `launcher.js` CONFIG'in karşılığı |
| `pengu/PenguHazirlik.java` | OYNA: Fabric profili, zorunlu + FPS modları, serverlock modu, kaynak paketleri, options.txt, kilitli servers.dat, AuthMe |
| `pengu/PenguGuncelleme.java` | Uygulama içi otomatik güncelleme (`/indir/latest-android.json`) |
| `pengu/PenguSkin.java`, `PenguSunucu.java`, `PenguAyarlar.java` | Skin yükleme, sunucu durumu, sunucu şifresi |
| `pengu/ArkadaslarActivity.java` + `assets/pengu/arkadaslar.html` | Arkadaşlar, sohbet, sesli arama (friends-server) |
| `assets/pengu/mods`, `assets/pengu/packs` | `yayinla.ps1` bunları PC launcher'dan kopyalar |
| `fragments/MainMenuFragment.java`, `LocalLoginFragment.java` | Ana ekran ve offline giriş (şifreli) |

Oyuncunun oyun klasörü: `Android/data/xyz.penguscraft.launcher/files/penguscraft/`

## FPS Boost

Varsayılan olarak Sodium, FerriteCore, ModernFix, Lithium, ImmediatelyFast, EntityCulling ve Dynamic FPS
kurulur. Renderer MobileGlues (OpenGL → GLES). İlk kurulumda options.txt telefona göre ayarlanır:
görüş mesafesi 6, hızlı grafik, az parçacık, gölge/bulut/yumuşak ışık kapalı, 60 FPS sınırı.
Oyuncu ana ekrandaki **Performans** düğmesinden paketi veya yalnızca Sodium'u kapatabilir
(bazı eski GPU'larda Sodium sorun çıkarabiliyor).

## Güncelleme yayınlama

```powershell
cd C:\dev\pengu-android
.\yayinla.ps1 -Notlar "Yenilikler..."          # 1.0.0 -> 1.0.1
.\yayinla.ps1 -Surum 1.2.0 -Notlar "..."       # sürümü elle ver
```

Çıktı `mc-launcher\dist\android\` içinde: `Pengu-Launcher-<sürüm>.apk` ve `latest-android.json`.
İkisini de sitede `/indir/` klasörüne yükle. Uygulama açılışta json'u okur, yeni sürümü indirir,
SHA-256 ile doğrular ve kurulum ekranını açar.

serverlock modunu veya kaynak paketlerini güncellediğinde PC launcher'daki `assets/` klasörüne koy,
sonra `yayinla.ps1` çalıştır — Android sürümüne otomatik kopyalanır.

## İmza anahtarı — ÖNEMLİ

`pengu-release.jks` + `keystore.properties` git'e girmez. **Bunları yedekle** (USB, bulut).
Kaybolursa yeni APK'lar eski sürümün üstüne kurulamaz, otomatik güncelleme kırılır ve herkes
uygulamayı silip yeniden kurmak zorunda kalır.

## Derleme ortamı

- JDK 21 (`JAVA_HOME`), JDK 8 toolchain `C:\dev\jdk8` (`~/.gradle/gradle.properties` içinde)
- Android SDK `%LOCALAPPDATA%\Android\Sdk` (platform 37, NDK 27.3 + 28.2)
- `./gradlew.bat :app_pojavlauncher:assembleRelease`
