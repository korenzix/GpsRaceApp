# GPS Race App — MVP

A GPS-based 100-metre sprint competition for Android.  
This is a functional MVP designed to validate: GPS accuracy, distance measurement, and map display — before investing in multiplayer infrastructure.

---

## Quick Start

### Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog (2023.1+) or newer |
| Android SDK | API 34 |
| Kotlin | 1.9.23 |
| Physical Android device | API 26+ (Android 8.0+) |

> **Important:** GPS must be tested on a real device. The emulator's mock GPS is not representative of real-world accuracy.

---

### 1. Get a Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a project (or use an existing one)
3. Enable **Maps SDK for Android**
4. Create an API Key under **Credentials**
5. (Recommended) Restrict the key to `com.gpsrace.app` package + SHA-1 fingerprint

---

### 2. Configure local.properties

Copy the example file and fill in your values:

```bash
cp local.properties.example local.properties
```

Edit `local.properties`:

```
sdk.dir=/Users/yourname/Library/Android/sdk
MAPS_API_KEY=AIzaSy...your_actual_key_here
```

> `local.properties` is git-ignored — your API key will never be committed.

---

### 3. Build & Run

```bash
# In Android Studio: just press Run ▶
# Or from terminal:
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## How to Use

1. Open the app on your phone — outdoors with clear sky view
2. Wait for the GPS accuracy badge (top-right of map) to show **≤ 10m**
3. Press **START RACE**
4. Walk/run 100 metres in any direction
5. The app detects when you've covered 100m and shows your finish time

---

## Project Structure

```
GpsRaceApp/
├── app/src/main/java/com/gpsrace/app/
│   │
│   ├── MainActivity.kt              # Activity shell — stays thin
│   │
│   ├── ui/
│   │   ├── MainScreen.kt            # All Compose UI for the race screen
│   │   ├── RaceViewModel.kt         # State management, GPS → UI bridge
│   │   └── theme/
│   │       └── Theme.kt             # MVP colour theme
│   │
│   ├── location/
│   │   └── LocationTracker.kt       # FusedLocationProviderClient → Flow<Location>
│   │
│   ├── race/
│   │   ├── RaceEngine.kt            # Pure race logic + anti-cheat
│   │   └── RaceState.kt             # Data classes & sealed status hierarchy
│   │
│   └── utils/
│       └── LocationUtils.kt         # Distance math, formatting, anti-cheat helpers
│
├── app/build.gradle.kts             # Dependencies & build config
├── local.properties.example         # Template for API keys
└── README.md
```

---

## Anti-Cheat (MVP level)

| Check | Threshold | Notes |
|-------|-----------|-------|
| GPS accuracy | ≤ 20 m horizontal | Ignores noisy fixes |
| Speed (GPS chipset) | ≤ 15 m/s (~54 km/h) | Usain Bolt peak ≈ 12.4 m/s |
| Position jump | ≤ 20 m/s implied | Catches GPS dropout teleport |

---

## Architecture Decisions

- **Single Activity + Jetpack Compose** — simple, modern, easy to extend.
- **StateFlow** — unidirectional data flow; UI never writes directly to state.
- **RaceEngine is framework-free** — easy to unit test; designed to be swapped for a networked version.
- **callbackFlow** wrapping FusedLocationProviderClient — automatic cleanup on cancellation.
- **API key in local.properties** — never in version control.

---

## TODO / Roadmap

### Multiplayer
- [ ] Firebase Realtime Database or WebSocket for live position sharing
- [ ] Room creation (host) / room joining (guest) with share code
- [ ] Countdown timer (3-2-1-GO) synchronised between devices
- [ ] Opponent positions shown as markers on the map

### Matchmaking
- [ ] Random matchmaking queue (find opponent with similar pace)
- [ ] Friend invites via deep link / share sheet
- [ ] Region-based lobby (low-latency pairing)

### Anti-Cheat (advanced)
- [ ] Server-side distance validation (reject client-reported totals)
- [ ] Accelerometer cross-validation (correlate IMU with GPS movement)
- [ ] Replay analysis on server after race
- [ ] Device attestation (Play Integrity API)

### Leaderboard & Rankings
- [ ] Personal bests stored locally (Room database)
- [ ] Global leaderboard via Firestore
- [ ] Weekly / all-time rankings
- [ ] ELO-style rating system

### UX Polish
- [ ] Countdown animation before race start
- [ ] Finish line animation / confetti
- [ ] Race summary screen with pace chart
- [ ] Share result card to social media

### Voice & Social
- [ ] In-race voice chat (WebRTC)
- [ ] Post-race text chat in room
- [ ] Friends list & challenges

### Infrastructure
- [ ] Node.js or Firebase Cloud Functions backend
- [ ] User authentication (Google Sign-In / Firebase Auth)
- [ ] Analytics (Firebase Analytics / Amplitude)
- [ ] Crash reporting (Firebase Crashlytics)

---

## Known MVP Limitations

- **No multiplayer** — race is solo; multiplayer infrastructure is stubbed out in code comments.
- **No persistence** — results are lost when app closes.
- **GPS cold-start delay** — first fix can take 30–60 seconds outdoors.
- **Indoor accuracy** — GPS degrades significantly indoors; test outdoors.

---

## Dependencies

| Library | Purpose |
|---------|---------|
| `play-services-location` | FusedLocationProviderClient |
| `play-services-maps` | Google Maps Android SDK |
| `maps-compose` | Compose wrapper for Google Maps |
| `accompanist-permissions` | Runtime permission handling in Compose |
| `kotlinx-coroutines-android` | Async / Flow |
| `lifecycle-viewmodel-compose` | ViewModel integration |

---

## License

MIT — use freely for learning and prototyping.
