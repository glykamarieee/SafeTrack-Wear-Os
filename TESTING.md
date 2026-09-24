# SafeTrack Watch: testing checklist

Run on a Galaxy Watch8 LTE (or the Wear OS emulator) after applying `20260929_watch_rpc.sql`. Check backend
effects in the Supabase SQL Editor (queries at the end) and in the Guardian app.
Automated unit tests: `./gradlew testDebugUnitTest`.

## Device connection

- [ ] **Valid code**: guardian links the Watch ID and generates a code; enter it → "Connected / Connected Successfully" with the child's name → Continue → permission prompt → dashboard. `smartwatch_devices.paired_at`, `device_token_hash`, `device_model` set.
- [ ] **Invalid code** (wrong digits) → "Connection code is invalid or expired." Code field cleared.
- [ ] **Expired code** (wait > 10 min) → same message.
- [ ] **Already-used code** (enter a code that already paired) → same message.
- [ ] **Unregistered watch** (no `smartwatch_devices` row) → "This watch is not added yet. Ask your guardian to add Watch ID ST-WATCH-…".
- [ ] **Watch disabled by admin** before pairing → "This watch is not linked to a child profile…".
- [ ] **Already-linked watch**: pair again with a fresh code → succeeds; the old token stops working.
- [ ] **Watch relinked to another child / disabled after pairing** → at the next heartbeat the watch returns to the connection screen with "This watch was disconnected from SafeTrack…".
- [ ] **No internet** while connecting → "No internet connection. Please try again when communication is available."
- [ ] Dashboard is not reachable before pairing (relaunch app → connection screen).
- [ ] **Watch ID** is the same after app restart, reboot, and app update (`adb install -r`); never "UNKNOWN". A full uninstall may give a new ID, which must be linked again.

## Location

- [ ] **GPS available** (outdoors): within ~3 min a `location_logs` row appears with `source = smartwatch`, accuracy set; dashboard "Last Location: Updated just now"; `last_location_at` advances.
- [ ] **GPS unavailable** (indoors / location off): dashboard shows "Location currently unavailable."; no crash.
- [ ] **Internet available**: fixes upload immediately.
- [ ] **Internet unavailable** (airplane mode): no crash; the newest fix is kept.
- [ ] **Location recovery**: turn network back on → the kept fix uploads, `last_location_at` does not move backwards.
- [ ] Screen off for 15 min → location rows keep arriving.

## Safe zone

- [ ] **Inside**: guardian creates a zone around the watch → dashboard "🟢 INSIDE SAFE ZONE", safe-zone screen shows "Zone: <name>"; guardian receives an entry event.
- [ ] **Outside**: move out (or shrink/move the zone) → "🔴 OUTSIDE SAFE ZONE"; guardian receives an exit event.
- [ ] **Unavailable**: no zone configured → "⚪ UNAVAILABLE / No safe zone set yet"; no location yet → "Location unavailable".
- [ ] The watch offers no way to create, edit or delete zones.

## SOS

- [ ] **Tap-and-hold**: ring fills over 1.5 s; releasing early does nothing; completing opens the countdown (no alert yet).
- [ ] **Shake**: 6+ vigorous shakes in ~2 s → countdown opens (also with the screen off, via the full-screen notification). Walking, running, clapping, typing do **not** trigger it.
- [ ] **Countdown** shows 3 → 2 → 1 with a vibration each second; back/swipe does not skip it.
- [ ] **Cancel** (button, or "Cancel SOS" on the notification) → "SOS CANCELLED / Returning to monitoring…" → dashboard; **no** `sos_alerts` row.
- [ ] **Confirm** → "SENDING SOS…" → "SOS SENT / Emergency alert sent / Location included"; `sos_alerts` row with `status = active`, `activation_method = tap_and_hold` or `shake`, lat/long set; guardian gets the push.
- [ ] **SOS with location**: `sos_alerts.location_log_id` points to a `smartwatch` fix taken at confirmation.
- [ ] **SOS without location** (location off): "Location unavailable." shown; alert still created (server attaches the latest stored location if any).
- [ ] **SOS without network** (airplane mode) → "SOS PENDING / No connection…"; dashboard shows "SOS waiting to send".
- [ ] **Retry after network recovery**: turn network on → alert arrives within ~1 min; `triggered_at` equals the original activation time; banner disappears.
- [ ] **Pending SOS survives restart**: SOS offline → force-stop app (or reboot) → network on → alert still arrives.
- [ ] **No duplicates**: SOS on a flaky network (toggle airplane mode during "Sending") → exactly one `sos_alerts` row for that `triggered_at`.
- [ ] A second SOS cannot start while one is counting down or sending.

## Heartbeat

- [ ] **Heartbeat sent**: `last_seen_at` updates at least every ~2–3 min while the watch is idle.
- [ ] **Last seen updated** with battery %, network type and model on `smartwatch_devices`.
- [ ] **Watch disconnected**: airplane mode > 5 min → Status screen "🔴 Disconnected", "Monitoring will resume when communication returns."; guardian receives the "watch disconnected" notice from the scheduled scan.
- [ ] **Watch reconnects**: network on → "🟢 Connected", "Last Sync: Just now".

## Battery / network

- [ ] **Battery** % on the dashboard matches the system and updates while charging/discharging; `last_battery_percent` matches.
- [ ] **Wi-Fi** → "Wi-Fi" on watch and `last_network_type`.
- [ ] **LTE/mobile data** (Wi-Fi and Bluetooth off) → "LTE".
- [ ] **No network** → "No network"; connection turns Disconnected after the 5-minute window.

## Restart

- [ ] **Restart watch** → monitoring resumes without opening the app (requires "Allow all the time" location); `last_seen_at` updates.
- [ ] **Restart application** (force stop, reopen) → goes straight to the dashboard.
- [ ] **Reconnect after restart**: status returns to Connected after the first heartbeat.
- [ ] **Preserve device connection**: no re-pairing needed after restart, reboot or app update.

## Useful queries

```sql
select watch_id, child_id, is_active, paired_at, last_seen_at, last_location_at,
       last_battery_percent, last_network_type, device_model
from public.smartwatch_devices where watch_id = 'ST-WATCH-XXXXXXXX';

select id, source, latitude, longitude, accuracy_meters, recorded_at
from public.location_logs where child_id = '<child id>' order by recorded_at desc limit 10;

select id, activation_method, status, triggered_at, location_log_id, latitude, longitude
from public.sos_alerts where child_id = '<child id>' order by triggered_at desc limit 10;

select event_type, title, occurred_at
from public.geofence_events where child_id = '<child id>' order by occurred_at desc limit 10;
```
