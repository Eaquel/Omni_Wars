# ⚔ Atlılar - Online Çok Oyunculu Android Oyunu

## Özellikler
- 🌐 Online çok oyunculu (Firebase Realtime Database)
- 🗺 10.000 x 10.000 birimlik büyük harita (sınır görünmez)
- 🔵 Oyuncu: Mavi kare | 🔴 Düşmanlar: Kırmızı kare
- 🏷 Nametag sistemi (Oyuncu 1, Oyuncu 2...)
- ⚔ 6 silah: Yumruk (varsayılan), Mızrak, Kılıç, Kalkan, Balta, Sopa
- 🏪 Market: Silah/Can/Hız/Yenilenme yükseltmeleri
- 🕹 Sanal joystick kontrolü
- 📱 32/64 bit cihaz desteği (armeabi-v7a, arm64-v8a, x86, x86_64)

## Kurulum

### 1. Firebase Kurulumu (ZORUNLU)
1. [Firebase Console](https://console.firebase.google.com) → Yeni proje
2. Android uygulaması ekle: paket `com.korkusuz.atlilar`
3. `google-services.json` dosyasını `app/` klasörüne koy
4. Realtime Database → Şu kuralları ayarla:
```json
{
  "rules": { ".read": "auth != null", ".write": "auth != null" }
}
```

### 2. Google Play Games (Opsiyonel)
- `app/src/main/res/values/strings.xml` → `game_services_project_id` değerini güncelle

### 3. Derleme
```bash
chmod +x gradlew
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`

## Oyun Kontrolleri
- **Sol joystick** → Hareket
- **Sağ ⚔ butonu** → Saldırı (en yakın düşmana)
- **Sol üst 🏪** → Market

## Proje Yapısı
```
app/src/main/kotlin/com/korkusuz/atlilar/
├── MainActivity.kt        ← Firebase giriş
├── MenuActivity.kt        ← Ana menü
├── RoomActivity.kt        ← Oda kur / Odaya katıl
├── GameActivity.kt        ← Oyun ekranı
├── ShopActivity.kt        ← Market
├── game/GameView.kt       ← Oyun motoru (SurfaceView)
├── firebase/              ← Firebase işlemleri
└── models/                ← PlayerData, EnemyData, WeaponType, RoomData
```
