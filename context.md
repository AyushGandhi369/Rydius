# Rydius — Context Documentation

> **Last Updated:** February 20, 2026
> **Purpose:** Single source of truth for any AI agent working on this codebase. Read this FIRST.

---

## 1. What This Project Is

**Rydius** (formerly RideMate) is a carpooling/ride-sharing platform for India. Drivers offer rides along their route; passengers search & match. Two components:

| Part | Stack | Location |
|------|-------|----------|
| **Backend** | Node.js + Express 5.1.0 + SQLite | `ridemate-backend/` |
| **Android App** | Kotlin 2.0 + Jetpack Compose + Material 3 | `ridemate-backend/RideMate-mobile/` |

The repo root also has legacy **web frontend** HTML/CSS/JS files. They are served by the backend for browser access but the Android app does NOT use them.

---

## 2. Tech Stack — Android App

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.0.0 |
| UI | Jetpack Compose BOM | 2024.06.00 |
| Material | Material 3 | (from BOM) |
| Build | AGP | 8.6.1 |
| compileSdk / targetSdk | 35 | minSdk 26 |
| Networking | Retrofit 2 + OkHttp 4 | 2.11.0 / 4.12.0 |
| WebSocket | Socket.IO Android client | 2.1.1 |
| Maps | MapLibre Android SDK | 11.5.2 |
| Map tiles | **Ola Maps** vector tiles | via style URL |
| Location | Play Services Location | 21.3.0 |
| Coroutines | kotlinx-coroutines-play-services | 1.8.1 |
| Navigation | Navigation Compose | 2.7.7 |
| Persistence | DataStore Preferences | 1.1.1 |
| Images | Coil Compose | 2.7.0 |
| Splash | core-splashscreen | 1.0.1 |
| Architecture | MVVM (ViewModel + Repository + ApiService) | — |

### What is **NOT** used (common AI mistake sources)
- **NOT Google Maps** — MapLibre + Ola Maps tiles. No `com.google.android.gms:play-services-maps`.
- **NOT Room / Hilt / Dagger / Koin** — no DI framework, no local database ORM.
- **NOT Ktor** — Retrofit only.
- **NOT DataStore Proto** — uses DataStore Preferences (SharedPreferences wrapper).
- **NOT Accompanist** — all permissions handled via `ActivityResultContracts`.

---

## 3. Tech Stack — Backend

| Technology | Version | Purpose |
|-----------|---------|---------|
| Node.js + Express | 5.1.0 | HTTP server |
| SQLite3 | 5.1.7 | Database (file: `database.db`) |
| Socket.io | 4.8.1 | WebSocket real-time |
| bcrypt | 6.0.0 | Password hashing |
| express-session | 1.18.1 | Cookie-based sessions |
| Resend | 4.6.0 | Email OTP service |

Runs on **port 3000**. Emulator reaches it at `http://10.0.2.2:3000`. Physical devices need `RIDEMATE_BASE_URL=http://YOUR_PC_IP:3000`.

---

## 4. Complete File Tree — Android App

```
RideMate-mobile/
├── build.gradle.kts                     # Root build (plugins)
├── settings.gradle.kts
├── gradle.properties
├── app/
│   ├── build.gradle.kts                 # Dependencies, BuildConfig fields, ProGuard
│   └── src/main/
│       ├── AndroidManifest.xml          # Permissions: INTERNET, ACCESS_FINE/COARSE_LOCATION
│       └── java/com/rydius/mobile/
│           │
│           ├── MainActivity.kt          # Entry point. BroadcastReceiver for ACTION_SESSION_EXPIRED
│           ├── RideMateApp.kt           # Application class. Inits ApiClient, SessionManager
│           │
│           ├── data/
│           │   ├── api/
│           │   │   ├── ApiClient.kt     # Retrofit/OkHttp singleton. AuthInterceptor (401 handler)
│           │   │   └── ApiService.kt    # ALL Retrofit endpoint definitions (~35 endpoints)
│           │   ├── model/
│           │   │   └── Models.kt        # ALL data classes — requests, responses, DTOs
│           │   ├── repository/
│           │   │   ├── AuthRepository.kt    # Auth + profile methods
│           │   │   ├── MapRepository.kt     # Autocomplete, geocode, directions
│           │   │   ├── TripRepository.kt    # Trips, ride requests, matches, cost
│           │   │   └── SafeApiCall.kt       # Shared safeApiCall<T> utility
│           │   └── socket/
│           │       └── SocketManager.kt     # Socket.IO connection/events
│           │
│           ├── navigation/
│           │   └── NavGraph.kt          # All routes. Uri.encode() for params.
│           │                            # Routes: splash, login, signup, home,
│           │                            #         driver/{...}, passenger/{...},
│           │                            #         my_rides, profile, edit_profile
│           │
│           ├── ui/
│           │   ├── auth/
│           │   │   ├── AuthViewModel.kt     # Login/signup/OTP, forgot password, account deletion
│           │   │   ├── ForgotPasswordScreen.kt  # Two-step reset: email → OTP + new password
│           │   │   ├── LoginScreen.kt       # Email + password login form, forgot password link
│           │   │   └── SignupScreen.kt      # Name/email/password + OTP verification
│           │   │
│           │   ├── components/              # Shared composables
│           │   │   ├── BottomNavBar.kt      # Home/MyRides/Profile bottom nav
│           │   │   ├── CostSharingCard.kt   # ⚠️ Param = "costData" (NOT "costSharing")
│           │   │   ├── DriverCard.kt        # Driver info card in passenger view
│           │   │   ├── LocationSearchBar.kt # Autocomplete text field
│           │   │   ├── MapViewComposable.kt # MapLibre compose wrapper (see §6.4)
│           │   │   └── RoleSelector.kt      # Driver/Rider toggle
│           │   │
│           │   ├── driver/
│           │   │   ├── DriverConfirmationScreen.kt  # Trip creation + request management
│           │   │   └── DriverViewModel.kt           # Trip lifecycle, socket events
│           │   │
│           │   ├── home/
│           │   │   ├── HomeScreen.kt        # Main screen: location pickers, role, GPS
│           │   │   └── HomeViewModel.kt     # Location state, fetchCurrentLocation()
│           │   │
│           │   ├── passenger/
│           │   │   ├── PassengerConfirmationScreen.kt  # Driver selection, booking, cancel
│           │   │   └── PassengerViewModel.kt           # Available drivers, match flow, cancel
│           │   │
│           │   ├── profile/
│           │   │   ├── ProfileScreen.kt         # Uber-style profile, real ratings, delete account
│           │   │   ├── EditProfileScreen.kt     # Full edit form (photo, phone verify, etc.)
│           │   │   └── ProfileViewModel.kt      # Profile CRUD, photo upload, phone OTP, rating fetch
│           │   │
│           │   ├── rides/
│           │   │   ├── MyRidesScreen.kt         # Tabs: Active / Completed / Cancelled + rating dialog
│           │   │   └── MyRidesViewModel.kt      # Fetches /api/trips/my-rides, rating submission
│           │   │
│           │   └── theme/
│           │       ├── Color.kt             # Brand palette (see §8)
│           │       ├── Theme.kt             # Material 3 theme
│           │       └── Type.kt              # Typography
│           │
│           └── util/
│               ├── Constants.kt             # BASE_URL, OLA_API_KEY, map defaults (Ahmedabad)
│               ├── LocationHelper.kt        # Haversine, decodePolyline, closestPointOnSegment
│               └── SessionManager.kt        # SharedPreferences-backed login state
```

---

## 5. Navigation Routes (NavGraph.kt)

| Route constant | Pattern | Screen |
|---------------|---------|--------|
| `SPLASH` | `"splash"` | Auto-redirect |
| `LOGIN` | `"login"` | LoginScreen |
| `SIGNUP` | `"signup"` | SignupScreen |
| `HOME` | `"home"` | HomeScreen (with BottomNavBar) |
| `DRIVER` | `"driver/{startLocation}/{endLocation}/..."` | DriverConfirmationScreen (8 path params) |
| `PASSENGER` | `"passenger/{startLocation}/{endLocation}/..."` | PassengerConfirmationScreen (8 path params) |
| `MY_RIDES` | `"my_rides"` | MyRidesScreen |
| `PROFILE` | `"profile"` | ProfileScreen |
| `EDIT_PROFILE` | `"edit_profile"` | EditProfileScreen |
| `FORGOT_PASSWORD` | `"forgot_password"` | ForgotPasswordScreen |

**Navigation gotcha:** All string args in DRIVER/PASSENGER routes are `Uri.encode()`-ed. Never pass raw strings with `/` or special chars.

---

## 6. Critical Patterns & Gotchas (READ THIS)

### 6.1. Authentication — Cookie-based Sessions
The backend uses `express-session`. The Android app auto-persists cookies via `JavaNetCookieJar`:

```kotlin
// ApiClient.kt
val cookieManager = CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) }
val client = OkHttpClient.Builder()
    .cookieJar(JavaNetCookieJar(cookieManager))
    .addInterceptor(AuthInterceptor())  // 401 handler
    .addInterceptor(logging)
    .build()
```

**There are NO JWT tokens.** Session is a server-side cookie (`connect.sid`).

### 6.2. 401 Session Expiry Flow
`ApiClient.AuthInterceptor` intercepts every HTTP response. On 401:
1. Clears cookies from `CookieManager`
2. Clears `SessionManager`
3. Sends `LocalBroadcast` with action `ACTION_SESSION_EXPIRED`
4. `MainActivity` has a `BroadcastReceiver` that navigates to login

### 6.3. SafeApiCall — Shared Error Handling
All repositories use the shared `safeApiCall<T>` from `SafeApiCall.kt`:

```kotlin
suspend fun login(email: String, password: String): Result<LoginResponse> =
    safeApiCall { api.login(LoginRequest(email, password)) }
```

`safeApiCall` auto-parses JSON error responses: extracts the `message` or `error` field from `{"message":"..."}` responses so users see clean error text, not raw JSON.

**DO NOT** create private `safeCall` duplicates in repositories.

### 6.4. MapViewComposable — Encoded Polyline (CRITICAL)
`MapViewComposable` accepts `routePolyline: String?` — the **encoded** polyline string. It decodes internally via `LocationHelper.decodePolyline()`.

Uses **GeoJSON sources** + `CircleLayer` / `LineLayer`. Before adding sources/layers, existing ones with the same ID are removed first to prevent duplicate crashes on style reload.

**DO NOT:**
- Pass `List<Pair<Double,Double>>` — the param is `String?`
- Use `SymbolManager` / `LineManager` — they're deprecated in MapLibre 11.x
- Import `BitmapUtils`, `SymbolManager`, `SymbolOptions` — they don't exist

### 6.5. CostSharingCard Param Name
```kotlin
// CORRECT:
CostSharingCard(costData = costResponse)
// WRONG (won't compile):
CostSharingCard(costSharing = costResponse)
```

### 6.6. ViewModel Initialization Guard
`DriverViewModel` and `PassengerViewModel` both use `private var initialized = false` to prevent `LaunchedEffect(Unit)` from creating duplicate trips/requests on recomposition. Reset by `retry()`.

### 6.7. Profile Photo — Base64 Storage
Profile photos are stored as base64-encoded JPEG strings in the `profile_photo_url` column. `ProfileViewModel.uploadPhoto()` decodes the picked image, scales to max 512px, compresses to JPEG 80%, and sends as `data:image/jpeg;base64,...` to `POST /api/profile/photo`.

The `ProfileScreen` and `EditProfileScreen` decode with `BitmapFactory.decodeByteArray()` and display via `Image(bitmap.asImageBitmap())`.

### 6.8. Phone Verification — OTP Flow
1. User enters phone → `POST /api/profile/verify-phone/send` → generates 6-digit OTP (logged to console in dev; returned in `dev_otp` field when `NODE_ENV !== 'production'`)
2. User enters OTP → `POST /api/profile/verify-phone/confirm` → sets `is_phone_verified = 1`
3. In-memory `phoneOtpStore` keyed by `userId`, expires after 5 minutes

### 6.9. Socket.IO Events
```
Client → Server: "driver-join-trip"              { tripId }
Client → Server: "passenger-selected-driver"     { tripId, matchId, ... }
Server → Client: "new-ride-request"              { match data }
Server → Client: "ride-accepted"                 { acceptance data }
Server → Client: "match-status-update"           { matchId, status, passengerId, driverId }
```

### 6.10. Passenger Match Status — Polling
After a passenger sends a match request, `PassengerViewModel` polls `GET /api/matches/active` every 5 seconds to detect when the driver accepts/rejects. States: `matchSent`, `matchAccepted`, `matchRejected`. The `PassengerConfirmationScreen` shows appropriate cards ("Ride Confirmed!" or "Request Declined — try another driver").

### 6.11. Trip Completion Flow
Drivers can complete a trip via `PUT /api/trips/:id/complete`. The `DriverConfirmationScreen` top bar has both "Complete" and "Cancel" buttons with confirmation dialogs. Completing a trip:
- Sets trip status to `completed`
- Marks accepted matches as `completed`
- Rejects remaining pending matches

### 6.12. Active Trip Recovery
On HomeScreen load, `HomeViewModel` checks `GET /api/trips/active`. If the driver has an active trip, a banner appears with "Resume Trip" (navigates to DriverConfirmationScreen) and "Dismiss" options. Prevents the confusing error when trying to create a duplicate trip.

### 6.13. Departure Time Picker
HomeScreen's departure time card is clickable — opens Android's native `DatePickerDialog` then `TimePickerDialog`. The selected date/time is sent as ISO format. If past or within 1 minute, defaults to "Now".

### 6.14. Pickup Distance Display
`DriverCard` shows pickup distance in meters for short distances (<1km) and km for longer ones. The backend returns `pickup_distance` in **meters**. Don't divide or multiply — just format correctly.

### 6.15. Password Reset — Two-Step OTP Flow
1. User taps "Forgot Password?" on LoginScreen → navigates to `FORGOT_PASSWORD` route
2. Step 1: Enter email → `POST /api/forgot-password` → server stores 6-digit OTP in `passwordResetStore` (in-memory, expires 5 min)
3. Step 2: Enter OTP + new password + confirm → `POST /api/reset-password` → verifies OTP (string-coerced), updates bcrypt hash
4. `AuthViewModel` tracks `resetStep` (EMAIL / OTP) and `resetEmail` state
5. `ForgotPasswordScreen` uses `DisposableEffect` to reset flow state on exit

### 6.16. Account Deletion — Transactional Cascade
`DELETE /api/account` performs atomic cascading deletion inside `BEGIN IMMEDIATE TRANSACTION`:
1. Delete matches → ride_requests → trips → ratings → user
2. Each step has error callback → `ROLLBACK` on failure
3. On success → `COMMIT` → destroy session → clear cookie
4. On Android side, `AuthViewModel.deleteAccount()` calls API then clears `SessionManager`
5. `ProfileScreen` has "type DELETE to confirm" dialog; clears stale errors on open

### 6.17. Rating System
- `POST /api/ratings` — submit rating (1-5 stars + optional review text)
- `GET /api/ratings/:userId` — returns `{average, count}` for user's ratings
- `GET /api/ratings/check/:matchId` — returns `{hasRated, rating}` for current user
- **ProfileScreen** shows dynamic star display (supports half-stars) with real average/count
- **MyRidesScreen** shows "Rate This Ride" button on completed passenger rides (not yet rated)
- Rating dialog has inline error display; errors are cleared on dialog dismiss
- `MyRidesViewModel.checkRatedMatches()` runs API calls in parallel via `async/awaitAll`

### 6.18. Passenger Ride Cancellation
`PUT /api/ride-requests/:id/cancel` sets request status to `cancelled` and updates any related match status. The backend waits for the match status update to complete before responding (not fire-and-forget).

`PassengerViewModel` uses a **separate `cancelError` state** (not `errorMessage`) so cancel failures show as inline text near the cancel button instead of triggering the full-screen error overlay.

---

## 7. Backend API Endpoints — Complete Reference

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/signup` | Register user (sends email OTP via Resend) |
| POST | `/api/verify-otp` | Verify email OTP |
| POST | `/api/login` | Login (creates session, returns `{user: {id, name, email}}`) |
| POST | `/api/logout` | Destroy session + clear cookie |
| GET | `/api/auth/status` | Returns `{isAuthenticated, user}` |
| GET | `/api/config` | Returns `{olaMapsApiKey}` |
| POST | `/api/forgot-password` | Send password reset OTP to email |
| POST | `/api/reset-password` | Verify OTP and set new password |
| DELETE | `/api/account` | Delete account (transactional cascade) |

### Profile (all require auth)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/profile` | Full user profile with all fields |
| PUT | `/api/profile` | Update profile fields (name, phone, gender, dob, vehicle, bio, etc.) |
| POST | `/api/profile/photo` | Upload base64 profile photo (max 5MB) |
| DELETE | `/api/profile/photo` | Remove profile photo |
| POST | `/api/profile/verify-phone/send` | Send phone OTP |
| POST | `/api/profile/verify-phone/confirm` | Confirm phone OTP |
| GET | `/api/profile/completion` | Profile completion stats (filled/total/percentage/fields) |

### Maps (proxy to Ola Maps API)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/maps/autocomplete?q=` | Location autocomplete |
| GET | `/api/maps/geocode?query=` | Forward geocode |
| GET | `/api/maps/reverse-geocode?lat=&lng=` | Reverse geocode |
| GET | `/api/maps/directions?origin=&destination=` | Route directions |

### Trips (driver)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/trips` | Create trip |
| GET | `/api/trips/active` | Driver's active trip |
| GET | `/api/trips/my-rides` | All rides as driver + passenger |
| GET | `/api/trips/:id` | Trip details |
| GET | `/api/trips/:id/requests` | Passenger requests for trip |
| GET | `/api/trips/:id/route-segment` | Privacy-safe route segment |
| PUT | `/api/trips/:id/cancel` | Cancel trip |
| PUT | `/api/trips/:id/complete` | Mark trip as completed |

### Ride Requests (passenger)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ride-requests` | Create ride request |
| PUT | `/api/ride-requests/:id/cancel` | Cancel ride request (passenger) |
| GET | `/api/available-drivers?pickup_lat=...` | Find matching drivers |

### Matches
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/matches` | Create match |
| PUT | `/api/matches/:id/status` | Accept/reject (body: `{status}`) |
| GET | `/api/matches/active` | Active matches |

### Cost Sharing
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/cost-sharing/calculate` | Calculate fare |
| GET | `/api/cost-sharing/fuel-prices` | Fuel prices by vehicle/fuel type |

### Ratings
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/ratings` | Submit rating (1-5) + optional review |
| GET | `/api/ratings/:userId` | Get user's average rating & count |
| GET | `/api/ratings/check/:matchId` | Check if current user rated a match |

---

## 8. Database Schema (SQLite)

### users
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| name | TEXT | NOT NULL |
| email | TEXT | NOT NULL, UNIQUE |
| password | TEXT | bcrypt hash |
| phone | TEXT | Nullable |
| is_phone_verified | INTEGER | 0 or 1 |
| gender | TEXT | male/female/other/prefer_not_to_say |
| date_of_birth | TEXT | YYYY-MM-DD |
| vehicle_number | TEXT | Indian format (e.g. GJ01AB1234) |
| vehicle_model | TEXT | e.g. "Maruti Swift" |
| profile_photo_url | TEXT | base64 data URI |
| bio | TEXT | Free text |
| home_address | TEXT | Saved place |
| work_address | TEXT | Saved place |
| emergency_contact | TEXT | Phone number |
| preferred_role | TEXT | "both" (default), "driver", "rider" |

### trips
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| driver_id | INTEGER FK→users | |
| start_location, end_location | TEXT | Address strings |
| start_lat, start_lng, end_lat, end_lng | REAL | Coordinates |
| route_polyline | TEXT | Encoded polyline |
| distance_km | REAL | |
| duration_minutes | INTEGER | |
| departure_time | DATETIME | |
| available_seats | INTEGER | Default 1 |
| status | TEXT | active / completed / cancelled |
| created_at, updated_at | DATETIME | |

### ride_requests
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | |
| passenger_id | INTEGER FK→users | |
| pickup_location, dropoff_location | TEXT | |
| pickup_lat, pickup_lng, dropoff_lat, dropoff_lng | REAL | |
| requested_time | DATETIME | |
| status | TEXT | searching / matched / completed / cancelled |

### matches
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | |
| trip_id | INTEGER FK→trips | UNIQUE with ride_request_id |
| ride_request_id | INTEGER FK→ride_requests | |
| pickup_point_lat/lng, dropoff_point_lat/lng | REAL | Actual pickup/drop on route |
| fare_amount | DECIMAL | |
| status | TEXT | pending / accepted / rejected / completed |

### fuel_config
| Column | Type | Notes |
|--------|------|-------|
| vehicle_type | TEXT | "2W" or "4W" |
| fuel_type | TEXT | "petrol", "diesel", "cng", "electric" |
| cost_per_km | REAL | |

### ratings
| Column | Type | Notes |
|--------|------|-------|
| id | INTEGER PK | Auto-increment |
| trip_id | INTEGER FK→trips | |
| match_id | INTEGER FK→matches | UNIQUE with rater_id |
| rater_id | INTEGER FK→users | Who is rating |
| rated_id | INTEGER FK→users | Who is being rated |
| rating | INTEGER | 1-5 (CHECK constraint) |
| review | TEXT | Optional review text |
| created_at | DATETIME | Default CURRENT_TIMESTAMP |

---

## 9. Color Theme (Color.kt)

| Name | Hex | Role |
|------|-----|------|
| Primary | `#1A1A2E` | Deep navy — top bars, primary buttons, headers |
| PrimaryVariant | `#16213E` | Gradient partner for Primary |
| Secondary | `#00B4D8` | Cyan — accent buttons, icons, links, FABs |
| SecondaryVariant | `#0096C7` | Darker cyan |
| Accent | `#4CC9F0` | Lighter cyan — highlights |
| SurfaceLight | `#F8F9FA` | Light background |
| CardLight | `#FFFFFF` | Card backgrounds |
| TextPrimary | `#1A1A2E` | Body text |
| TextSecondary | `#6C757D` | Subtle text, labels |
| TextOnPrimary | `#FFFFFF` | White text on dark |
| Success | `#2ECC71` | Green — verified badges, confirmations |
| Warning | `#F39C12` | Orange — stars, warnings |
| Error | `#E74C3C` | Red — destructive actions, errors |
| Info | `#3498DB` | Blue — informational |
| DividerColor | `#E0E0E0` | Divider lines |
| RiderColor | `#00B4D8` | Rider toggle |
| DriverColor | `#2ECC71` | Driver toggle |

---

## 10. Key Data Models (Models.kt)

### Profile (new)
```kotlin
data class UserProfile(
    val id: Int, val name: String, val email: String,
    val phone: String?, val isPhoneVerified: Int,
    val gender: String?, val dateOfBirth: String?,
    val vehicleNumber: String?, val vehicleModel: String?,
    val profilePhotoUrl: String?, val bio: String?,
    val homeAddress: String?, val workAddress: String?,
    val emergencyContact: String?, val preferredRole: String?
)

data class UpdateProfileRequest(
    val name: String?, val phone: String?, val gender: String?,
    val dateOfBirth: String?, val vehicleNumber: String?,
    val vehicleModel: String?, val bio: String?,
    val emergencyContact: String?, val homeAddress: String?,
    val workAddress: String?, val preferredRole: String?
)
```

### Other key models
- `TripData` — has `routePolyline: String?` (encoded), `driverName`, `availableSeats`
- `AvailableDriver` — returned by `/api/available-drivers`, used in `DriverCard`
- `MatchData` — uses `@SerializedName(alternate=[...])` for flexible parsing
- `CostSharingResponse` — `baseTripCost`, `costPerPassenger`, `driverSaved`, `co2SavedKg`
- `MyRide` — `userRole` field distinguishes "driver" vs "passenger" rides; `matchId` and `driverUserId` used for rating flow

### New models (added for recent features)
```kotlin
// Password reset
data class ForgotPasswordRequest(val email: String)
data class ResetPasswordRequest(val email: String, val otp: String, val newPassword: String)

// Ratings
data class SubmitRatingRequest(val matchId: Int, val rating: Int, val review: String?)
data class UserRatingResponse(val average: Double?, val count: Int)
data class RatingCheckResponse(val hasRated: Boolean, val rating: RatingData?)
data class RatingData(val rating: Int, val review: String?, val createdAt: String?)
```

---

## 11. SessionManager (util/SessionManager.kt)

SharedPreferences-based, NOT DataStore (despite DataStore in dependencies — it's only imported, SessionManager uses SharedPreferences directly).

```kotlin
// Keys: is_logged_in, user_id, user_name, user_email
// Methods: saveUser(id, name, email), clear()
// Properties: isLoggedIn, userId, userName, userEmail (all get/set)
```

---

## 12. Build Configuration

### gradle.properties
```properties
RIDEMATE_BASE_URL=http://10.0.2.2:3000    # emulator default
OLA_MAPS_API_KEY=your_key_here
```

### BuildConfig fields (generated)
- `BuildConfig.BASE_URL` — read by `Constants.BASE_URL`
- `BuildConfig.OLA_MAPS_API_KEY` — read by `Constants.OLA_API_KEY`
- `BuildConfig.VERSION_NAME` — displayed in ProfileScreen

### Release build guards
- `RIDEMATE_BASE_URL` must be `https://` (non-placeholder)
- `OLA_MAPS_API_KEY` must be non-blank

---

## 13. Build & Run

### Backend
```bash
cd ridemate-backend
npm install
node server.js       # → http://localhost:3000
```

### Android App
1. Set API key in `~/.gradle/gradle.properties`:
   ```
   OLA_MAPS_API_KEY=your_key_here
   ```
2. Open `ridemate-backend/RideMate-mobile/` in Android Studio
3. Run on emulator API 26+. Backend auto-configured at `10.0.2.2:3000`.
4. For physical device: add `RIDEMATE_BASE_URL=http://YOUR_PC_IP:3000` to `gradle.properties`.

---

## 14. Known Limitations / TODOs

- No push notifications (FCM) — only in-app Socket.IO
- No payment integration
- No dark mode toggle (theme supports it, no user switch)
- Resend API key hardcoded — should use env var
- `express-session` secret hardcoded — should use env var
- No rate limiting on API endpoints
- Profile photos stored as base64 in SQLite — should use file storage / S3 for production
- Phone OTP logged to console (no real SMS gateway like Twilio)
- No image compression on server side
- Only passengers can rate drivers (not vice versa)
- Password reset OTP stored in-memory (lost on server restart)
