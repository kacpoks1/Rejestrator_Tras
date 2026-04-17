# 📍 Rejestrator Tras

Natywna aplikacja Android do rejestrowania tras GPS z automatycznym uruchamianiem przez Bluetooth. Napisana w Kotlinie z Jetpack Compose.

[![Pobierz APK](https://img.shields.io/github/v/release/kacpoks1/Rejestrator_Tras?label=Pobierz%20APK&style=for-the-badge&logo=android&color=3DDC84)](https://github.com/kacpoks1/Rejestrator_Tras/releases/latest)

---

## Instalacja

Nie potrzebujesz Google Play — pobierz APK bezpośrednio:

1. Kliknij przycisk **Pobierz APK** powyżej lub wejdź na stronę [Releases](https://github.com/kacpoks1/Rejestrator_Tras/releases/latest)
2. Pobierz plik `app-release.apk` na swój telefon
3. Otwórz plik — Android może zapytać o zgodę na instalację z nieznanych źródeł
4. Zezwól na instalację w ustawieniach i zainstaluj

> **Android 8.0+** wymagany. Testowano na Androidzie 12 i 13.

---

## Funkcje

- **Rejestrowanie trasy na żywo** — śledzi Twoją pozycję w czasie rzeczywistym podczas jazdy samochodem, rowerem lub spaceru
- **Mapa OpenStreetMap** — pełnoekranowa interaktywna mapa przez osmdroid, bez klucza API
- **Eksport GPX** — każda trasa zapisywana jest jako plik `.gpx`, kompatybilny z popularnymi aplikacjami
- **Automatyczny start przez Bluetooth** — nagrywanie startuje gdy telefon połączy się z wybranym urządzeniem (np. radiem samochodowym) i zatrzymuje gdy się rozłączy
- **Nagrywanie w tle** — usługa pierwszoplanowa z blokadą ekranu, działa przy zgaszonym ekranie
- **Biblioteka tras** — przeglądaj, podglądaj i zarządzaj zapisanymi trasami; przesuń żeby usunąć, eksportuj pojedynczo lub wszystkie naraz

---

## Zrzuty ekranu

> _Dodaj tutaj swoje screenshoty_

---

## Stack technologiczny

| Warstwa | Technologia |
|---|---|
| Język | Kotlin |
| UI | Jetpack Compose |
| Architektura | MVVM + Repository |
| Mapa | osmdroid (OpenStreetMap) |
| Lokalizacja | FusedLocationProviderClient |
| Baza danych | Room |
| Preferencje | DataStore |
| Współbieżność | Kotlin Coroutines + StateFlow |
| Tło | Foreground Service + WakeLock |
| Bluetooth | BluetoothAdapter + BroadcastReceiver |

---

## Wymagania

- Android 8.0+ (API 26)
- Bluetooth (opcjonalnie, do funkcji automatycznego startu)
- Uprawnienie do lokalizacji (dokładna + w tle)

---

## Budowanie ze źródeł

### 1. Sklonuj repozytorium

```bash
git clone https://github.com/kacpoks1/Rejestrator_Tras.git
cd Rejestrator_Tras
```

### 2. Otwórz w Android Studio

Otwórz projekt w **Android Studio Hedgehog** lub nowszym. Gradle zsynchronizuje się automatycznie.

### 3. Zbuduj i uruchom

Podłącz urządzenie lub uruchom emulator, a następnie kliknij **Run** (`Shift+F10`).

> Nie potrzebujesz żadnych kluczy API — aplikacja korzysta z kafelków OpenStreetMap.

---

## Uprawnienia

Aplikacja prosi o następujące uprawnienia w czasie działania:

| Uprawnienie | Po co |
|---|---|
| `ACCESS_FINE_LOCATION` | Śledzenie GPS |
| `ACCESS_BACKGROUND_LOCATION` | Nagrywanie przy zgaszonym ekranie |
| `BLUETOOTH_CONNECT` | Odczyt sparowanych urządzeń (Android 12+) |
| `POST_NOTIFICATIONS` | Powiadomienie usługi pierwszoplanowej (Android 13+) |

Uprawnienie do lokalizacji w tle należy przyznać osobno — aplikacja przeprowadzi Cię przez ten proces.

---

## Automatyczny start przez Bluetooth

1. Kliknij **ikonę ustawień** na głównym ekranie
2. Wybierz **Bluetooth — automatyczny start**
3. Włącz przełącznik i wybierz sparowane urządzenie z listy
4. Kliknij **Zapisz**

Od tej chwili:
- Połączenie z wybranym urządzeniem → rejestrowanie trasy startuje automatycznie
- Rozłączenie → rejestrowanie zatrzymuje się i trasa jest zapisywana

**Zielona kropka** na ikonie ustawień oznacza, że automatyczny start przez Bluetooth jest aktywny.

---

## Pliki GPX

Trasy zapisywane są jako pliki GPX 1.1 w lokalizacji:

```
/Android/data/com.routetracker.app/files/GPX/
```

Każdy plik zawiera punkty trasy z szerokością i długością geograficzną, wysokością oraz znacznikiem czasu. Kompatybilne z Garmin, Google Earth, OsmAnd, Komoot i innymi.

Aby wyeksportować trasę:
- Otwórz **listę tras** (ustawienia → Zapisane trasy)
- Kliknij ikonę udostępniania przy wybranej trasie lub użyj **Eksportuj wszystkie**, aby pobrać archiwum `.zip`

---

## Struktura projektu

```
app/src/main/kotlin/com/routetracker/app/
├── MainActivity.kt
├── TrackingService.kt          # Usługa pierwszoplanowa, FusedLocation, WakeLock
├── RouteViewModel.kt           # Stan aplikacji, powiązanie z serwisem, logika BT
├── RouteRepository.kt          # Room + operacje na plikach GPX
├── GpxManager.kt               # Generowanie GPX, wzór Haversine'a, eksport
├── bluetooth/
│   ├── BluetoothManager.kt     # BroadcastReceiver, stan połączenia
│   └── BluetoothPreferences.kt # DataStore: flaga włączenia + zapisane urządzenie
├── models/
│   ├── Route.kt
│   └── TrackPoint.kt
└── ui/
    ├── MapScreen.kt
    ├── RouteListScreen.kt
    └── BluetoothSettingsScreen.kt
```

---

## Zgłaszanie błędów

Znalazłeś błąd? Otwórz [nowy Issue](https://github.com/kacpoks1/Rejestrator_Tras/issues/new) i opisz:
- wersję Androida
- co robiłeś gdy błąd wystąpił
- co się stało, a co powinno się stać

---

## Licencja

MIT — szczegóły w pliku [LICENSE](./LICENSE).
