# Rydius Android — Native Ride-Sharing App

A fully native Android ride-sharing application built with **Jetpack Compose**, **Material 3**, and **Kotlin**. It connects to the same Node.js/Express backend as the web app and uses **Ola Maps** for all mapping features.

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0.0 |
| **UI** | Jetpack Compose (BOM 2024.06.00) + Material 3 |
| **Architecture** | MVVM (ViewModel + Repository) |
| **Navigation** | Navigation Compose 2.7.7 |
| **Networking** | Retrofit 2.11.0 + OkHttp 4.12.0 |
| **Real-time** | Socket.IO Android Client 2.1.1 |
| **Maps** | MapLibre Android SDK 11.5.2 (Ola Maps tiles) |
| **Location** | Play Services Location 21.3.0 |
| **Images** | Coil 2.7.0 |
| **Persistence** | DataStore Preferences 1.1.1 |
| **Min SDK** | 26 (Android 8.0) |
| **Target/Compile SDK** | 35 |

---

## Project Structure

```
app/src/main/java/com/rydius/mobile/
├── RideMateApp.kt              # Application class
├── MainActivity.kt             # Single-activity entry point (Compose)
│
├── data/
│   ├── api/
│   │   ├── ApiClient.kt        # Retrofit + OkHttp singleton, cookie-based auth
│   │   └── ApiService.kt       # All REST endpoint definitions
│   ├── model/
│   │   └── Models.kt           # Request/response data classes
│   ├── repository/
│   │   ├── AuthRepository.kt   # Login, signup, OTP, logout
│   │   ├── MapRepository.kt    # Autocomplete, geocode, directions
│   │   └── TripRepository.kt   # Trips, ride requests, matches, cost
│   └── socket/
│       └── SocketManager.kt    # Real-time driver↔passenger events
│
├── navigation/
│   └── NavGraph.kt             # All screen routes + parameterized navigation
│
├── ui/
│   ├── auth/
│   │   ├── AuthViewModel.kt
│   │   ├── LoginScreen.kt
│   │   └── SignupScreen.kt
│   ├── home/
│   │   ├── HomeViewModel.kt
│   │   └── HomeScreen.kt
│   ├── driver/
│   │   ├── DriverViewModel.kt
│   │   └── DriverConfirmationScreen.kt
│   ├── passenger/
│   │   ├── PassengerViewModel.kt
│   │   └── PassengerConfirmationScreen.kt
│   ├── rides/
│   │   └── MyRidesScreen.kt
│   ├── profile/
│   │   └── ProfileScreen.kt
│   ├── components/
│   │   ├── BottomNavBar.kt
│   │   ├── CostSharingCard.kt
│   │   ├── DriverCard.kt
│   │   ├── LocationSearchBar.kt
│   │   ├── MapViewComposable.kt
│   │   └── RoleSelector.kt
│   └── theme/
│       ├── Color.kt
│       ├── Theme.kt
│       └── Type.kt
│
└── util/
    ├── Constants.kt
    ├── LocationHelper.kt
    └── SessionManager.kt
```

---

## Features

### Authentication
- Email + password login with express-session cookies (auto-managed by OkHttp `JavaNetCookieJar`)
- Signup with OTP verification via backend email API
- Persistent login state via `SessionManager` (SharedPreferences)

### Ride Booking (Driver / Car Owner)
- Select role → Enter pickup & dropoff with Ola Maps autocomplete
- Configure seats (1–4) and departure time
- Trip creation → Route directions on MapLibre map
- Real-time cost sharing calculation (fuel price, mileage, per-passenger split, CO₂ savings)
- Passenger search with 30-second polling + Socket.IO push notifications
- Accept/Decline passenger match requests

### Ride Booking (Passenger / Rider)
- Enter locations → Create ride request
- Browse available drivers with fare, rating, distance info
- Select a driver → Match request sent
- Cost breakdown with savings display

### My Rides
- Tabbed view: Upcoming / Repeat / Completed / Other
- Empty states with contextual guidance
- Share & Earn referral section

### Profile
- User avatar with initials, rating display
- Profile completion progress bar
- Account verification, transactions, invite friends
- Sign out with session cleanup

### Maps
- **MapLibre** rendering with **Ola Maps** vector tile style
- Route polyline display with start/end circle markers
- Camera auto-fit to route bounds
- Ola Maps autocomplete & geocoding via backend proxy

### Real-time
- Socket.IO connection for instant driver–passenger notifications
- New passenger request alerts for drivers
- Driver selection notifications for passengers

---

## Setup & Build

### Prerequisites
- Android Studio Hedgehog or later
- JDK 17+
- Node.js backend running (see `ridemate-backend/`)

### Configure Backend URL
In `app/build.gradle.kts`, the `BASE_URL` is set to `http://10.0.2.2:3000` (Android emulator localhost). For physical device testing, change to your machine's LAN IP:

```kotlin
buildConfigField("String", "BASE_URL", "\"http://192.168.x.x:3000\"")
```

### Build & Run
```bash
# From RideMate-mobile/ directory
./gradlew assembleDebug

# Or open in Android Studio and run on device/emulator
```

### Backend Setup
```bash
cd ridemate-backend
cp .env.example .env
# Fill in OLA_MAPS_API_KEY, SESSION_SECRET, RESEND_API_KEY
npm install
node server.js
```

---

## Architecture Decisions

| Decision | Rationale |
|---|---|
| **Session cookies over JWT** | Backend uses `express-session`; OkHttp `JavaNetCookieJar` handles cookies transparently |
| **MapLibre over Google Maps** | Ola Maps provides vector tiles compatible with MapLibre; no Google dependency |
| **Backend proxy for map APIs** | API key stays server-side; Android app calls `/api/maps/*` endpoints |
| **Socket.IO for real-time** | Backend already uses Socket.IO; native client provides instant notifications |
| **Compose over XML** | Modern declarative UI with less boilerplate, better state management |
| **Single Activity** | Navigation Compose handles all screen transitions within one activity |

---

## API Endpoints Used

All API calls go through the Node.js backend at `BASE_URL`:

- **Auth**: `POST /auth/signup`, `POST /auth/verify-otp`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/status`
- **Maps**: `GET /api/maps/autocomplete`, `GET /api/maps/geocode`, `GET /api/maps/reverse-geocode`, `GET /api/maps/directions`
- **Trips**: `POST /api/trips`, `GET /api/trips/active`, `GET /api/trips/:id`, `POST /api/trips/:id/requests`, `DELETE /api/trips/:id`
- **Ride Requests**: `POST /api/ride-requests`, `GET /api/ride-requests/:id/available-drivers`
- **Matches**: `POST /api/matches`, `PUT /api/matches/:id/status`, `GET /api/matches/active`
- **Cost**: `POST /api/cost-sharing/calculate`, `GET /api/fuel-prices`

---

## License

Private — part of the Rydius ride-sharing platform.
