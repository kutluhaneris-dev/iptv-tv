# IPTV TV

Sony (ve diğer) Android TV'ler için kumandayla kullanılan IPTV oynatıcı.

- **Xtream Codes** girişi (sunucu + kullanıcı adı + şifre) ve **M3U playlist** linki
- Canlı TV, filmler, diziler (sezon/bölüm), EPG yayın akışı (Xtream kısa EPG veya XMLTV)
- Favoriler (bir öğede OK tuşuna basılı tutun), arama
- Oynatıcıda ▲▼ ile kanal değiştirme, rakam tuşlarıyla kanal numarasına atlama
- Canlı yayın formatı (TS / HLS) ve User-Agent ayarı: bazı sağlayıcılar için gerekli
- Bilgiler sadece TV'de saklanır

Kotlin, Jetpack Compose for TV ve Media3/ExoPlayer ile yazıldı.

## TV'ye kurulum

1. TV'de Play Store'dan **Downloader** (AFTVnews) uygulamasını kurun.
2. Ayarlar → Cihaz Tercihleri → Güvenlik ve kısıtlamalar → Bilinmeyen kaynaklar → **Downloader**'a izin verin.
3. Downloader'da şu linki açın:
   `https://github.com/<kullanıcı>/<repo>/releases/latest/download/iptv-tv.apk`
4. Kur'a basın. Yeni sürümler aynı linkten kurulur ve eski sürümün üzerine yazılır, ayarlar korunur.

## Derleme

Her `main` push'unda GitHub Actions APK'yı derler ve yeni bir release yayınlar.
Yerelde: `./gradlew assembleRelease` (Android SDK gerekir).

APK, depodaki `keystore/debug.keystore` ile imzalanır; böylece her sürüm öncekinin üzerine kurulabilir.
