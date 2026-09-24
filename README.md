# SafeTrack Watch (child app for Wear OS)

The child-side app of SafeTrack, for the Samsung Galaxy Watch8 LTE (Wear OS, API 30+).
Kotlin, Jetpack Compose for Wear OS (Material 3), Fused Location, accelerometer +
gyroscope, WorkManager, DataStore, Android Keystore. It talks to the existing SafeTrack
Supabase backend only through four database functions (`pair_watch`,
`record_watch_heartbeat`, `record_watch_location`, `trigger_watch_sos`), the same
approach the child-phone mode of the SafeTrack app uses.

## What it does

| Function | Where |
|---|---|
| Device connection (6-digit guardian code, no QR) | `app/ui/connect`, `data/repository/DeviceRepositoryImpl.kt` |
| Stable Watch ID `ST-WATCH-XXXXXXXX` (kept across restarts, reboots and updates; a full uninstall may reset it) | `data/local/WatchIdProvider.kt` |
| Child dashboard, safe-zone status, device/connection status | `app/ui/dashboard` |
| Location tracking (every ~3 min, offline fix kept) | `services/location/LocationTracker.kt` |
| SOS by hold (1.5 s) or shake, 3-second cancellable countdown | `app/ui/dashboard/SosHoldButton.kt`, `services/sensors/ShakeDetector.kt`, `services/sos/SosController.kt` |
| Confirmed SOS saved before sending, retried until accepted | `data/repository/SosRepositoryImpl.kt`, `services/sos/SyncWorker.kt` |
| Heartbeat every 2 min (battery, network, model) | `services/heartbeat/HeartbeatLoop.kt` |
| Screen-off monitoring, restart after reboot | `services/MonitoringService.kt`, `services/BootReceiver.kt` |

## Setup

1. Apply `supabase/migrations/20260929_watch_rpc.sql` (SafeTrack repo) in the Supabase
   SQL Editor. It creates the watch functions; nothing needs deploying.
2. `local.properties` (git-ignored) needs the SDK path, the project URL and the
   **publishable** key (the same one the Guardian app uses):
   ```properties
   sdk.dir=C\:/Program Files (x86)/Android/android-sdk
   SAFETRACK_URL=https\://evffpbkxnjpizwqsrlqx.supabase.co
   SAFETRACK_PUBLISHABLE_KEY=sb_publishable_...
   ```
   The publishable key is public by design. The build refuses secret/service-role
   keys. Each watch authenticates with its own device token from `pair_watch`, stored
   encrypted with a Keystore key.
3. Build: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
4. Install on an emulator: `./gradlew installDebug`, or
   `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
   On a real watch, enable Developer options + Wireless debugging, then
   `adb pair <ip:port>`, `adb connect <ip:port>` and install.

For a release build, replace the debug signing config in `app/build.gradle.kts`.

## Pairing a watch

1. Open the app on the watch. The connection screen shows its **Watch ID**.
2. In the Guardian app: register/edit the child → enter the Watch ID → generate the
   watch connection code (valid 10 minutes, single use). Linking registers a new
   Watch ID automatically (`find_or_register_watch`).
3. On the watch: tap the code boxes → type the 6 digits on the watch keyboard → ✓ (or
   **CONNECT**) → **CONTINUE** → allow location.

If SafeTrack later rejects the watch (unlinked, disabled, or paired to another child),
the watch returns to the connection screen and says so.

## Backend (SafeTrack repo)

- `supabase/migrations/20260929_watch_rpc.sql`: the watch functions. They store
  battery/network/model on `smartwatch_devices`, advance `last_location_at`, record
  safe-zone entry/exit, and return the existing alert when an SOS with the same
  triggered time is resent, so there is no duplicate alert or push. Failures carry a
  `HINT` (`no_active_code`, `code_invalid`, ...) that the watch turns into a
  child-friendly message.
- `supabase/functions/scheduled-safety-scan`: now also runs the advisory AI review of
  each watch's newest location, which used to run inside `watch-ingest`. Redeploy it
  when AI review is enabled:
  `npx supabase functions deploy scheduled-safety-scan --project-ref evffpbkxnjpizwqsrlqx`.
- `watch-pair` / `watch-ingest` edge functions are no longer used by this app.
  Watches paired through them keep working (same token hash).
## Behaviour notes

- **SOS state**: activated (countdown) → cancelled, or confirmed → sending → sent, or
  pending (saved on the watch, retried by the monitoring service when the network
  returns and by WorkManager with backoff). A cancelled countdown never contacts SafeTrack.
- **SOS location**: the fix obtained at confirmation (up to 6 s wait), else the newest
  fix from the last 30 minutes, else the newest fix kept offline; if none, the server
  attaches the child's latest stored location.
- **Priority**: every sync round sends pending SOS first, then an offline location,
  then the heartbeat.
- **Connection status** comes from actual backend replies: Connected = SafeTrack
  answered within the last 5 minutes (the backend's disconnect window); Connecting =
  first contact or a request in flight; Disconnected = otherwise.
- **Shake gesture** (`ShakeConfig`): 6 peaks above ~2 g linear acceleration, 90–450 ms
  apart, within 2.5 s, plus wrist rotation ≥ 4 rad/s; 15 s cooldown. Uses a wake-up
  accelerometer with 1 s batching so it works with the screen off.
- The watch never creates, edits or deletes safe zones; it shows the status SafeTrack
  computes from the latest location.

See [TESTING.md](TESTING.md) for the test checklist.
