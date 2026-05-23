# WalkPlanner

Hyper-local run and walk route planner for Android. WalkPlanner uses GPS and OpenStreetMap data to generate loop routes, tracks active sessions, and stores history offline.

## Architecture

```
presentation/ (Compose screens + ViewModels)
       ↓
domain/ (use cases, models, repository interfaces — no Android imports)
       ↓
data/ (Room, Retrofit/Overpass, DataStore, WorkManager, location)
```

- **UI**: Jetpack Compose + Navigation Compose
- **DI**: Manual wiring in `WalkPlannerApplication`
- **Maps**: OSMDroid with local tile cache (100 MB)
- **Routes**: Overpass API with Room OSM graph cache (7-day expiry)
- **Routing**: Bidirectional A* on an adjacency-list street graph with Haversine edge distances and highway safety weights
- **Sessions**: WorkManager `SaveSessionWorker` persists completed runs
- **Active tracking**: `ActiveRunForegroundService` with pause/stop notification actions

## Build and run

**Prerequisites:** Android Studio, JDK 17, Android SDK 36

1. Open the project in Android Studio
2. Sync Gradle
3. Run on an emulator or device with location enabled

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | GPS route planning and live tracking |
| `ACCESS_COARSE_LOCATION` | Fallback when fine location is denied |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` | Active run notification |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Overpass API and connectivity checks |
| `WAKE_LOCK` | Keep CPU awake during active (non-paused) runs |

## Run unit tests

```bash
./gradlew testDebugUnitTest
```

Reports: `app/build/reports/tests/testDebugUnitTest/`

Key test suites:
- `DistanceInputValidatorTest`
- `RouteMapperTest`
- `GenerateRouteUseCaseTest`
- `RouteRepositoryImplTest` (Robolectric + in-memory Room)
- `ActiveRunViewModelTest`
- `LocationRepositoryImplTest`

## Run instrumented tests

```bash
./gradlew connectedDebugAndroidTest
```

## CI

GitHub Actions runs `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` on push/PR to `main` and `develop`.
