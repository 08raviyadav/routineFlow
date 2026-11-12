# RoutineFlow

**Universal Android automation: Routines + Modes, on any Android phone.**

RoutineFlow brings iPhone Shortcuts–style routines and Samsung Modes &amp; Routines–style
modes to every Android phone — no Samsung, no Pixel, no brand lock-in. Build a
routine once ("when this happens, do that"), and the engine runs it for you in
the background. Modes bundle a whole set of settings (Do Not Disturb, ringer,
brightness, volumes, screen timeout) into one tap, and can switch themselves on
and off automatically.

Everything runs on your phone. No account, no cloud, no analytics — see
[PRIVACY_POLICY.md](PRIVACY_POLICY.md).

> **Status:** v1.0.0 source. No APK is shipped in this repo — CI builds the
> debug APK on every push (see [Get the APK](#get-the-apk)).

## What it does

| Feature | v1.0 |
|---|---|
| Triggers | **23** |
| Actions (Play-safe) | **40** |
| Actions needing Shizuku / root | **6**, listed and badged, never faked |
| Built-in Modes | **5** (Sleep, Driving, Work, Focus, Relax) |
| Ready-made templates | **12** |
| Conditions, IF/ELSE, delays, cooldowns | Yes |

### Triggers (23)

Time of day · Alarm (RoutineFlow) · Sunrise · Sunset · Battery level ·
Charging state · Wi-Fi connected · Wi-Fi disconnected · Bluetooth connected ·
Bluetooth disconnected · Arrive at a place · Leave a place · App opened ·
App closed · Screen on · Screen off · Headphones · Notification received ·
Airplane mode · Do Not Disturb · Device unlocked · Call state ·
Clipboard changed.

### Actions (40 Play-safe + 6 badged)

- **Settings:** Wi-Fi settings panel, Bluetooth on/off, Do Not Disturb,
  ringer mode, brightness, auto-brightness, screen timeout, solid wallpaper,
  ring/media/alarm/notification volume, auto-rotate, flashlight, lock screen,
  play/pause media, clear notifications, battery-saver and location settings.
- **Apps:** launch app, open link, set alarm, start timer, NFC settings,
  mobile-data settings.
- **Notify &amp; speak:** show notification, speak text / time / battery,
  vibrate, show a message, play a sound, copy to clipboard.
- **Network:** web request (GET) to your own URL — e.g. a Home Assistant
  webhook or smart plug.
- **Flow control:** run another routine, enable/disable a routine, wait
  (delay), If / Else / End if.
- **Badged (Shizuku / root only):** direct mobile-data toggle, direct
  airplane-mode toggle, direct Wi-Fi toggle, direct dark mode, direct GPS
  toggle, force-stop an app. These are **listed but disabled** in v1 — Android
  does not allow them from a normal app, and RoutineFlow would rather show
  them honestly than pretend to run them.

### Modes (5 built-in)

Sleep, Driving, Work, Focus and Relax. A Mode applies its settings when
activated and reverts them when it exits. Modes can also switch themselves
automatically — Driving, for example, turns on when your car's Bluetooth
connects.

### Templates (12)

Bedtime wind-down · Good morning · Low battery saver · Driving mode ·
Work focus · Welcome home · Headphones in, music up · Silence for video calls ·
Night charging care · Leaving home reminder · Pause media on calls ·
Read my clipboard aloud. Tapping a template copies it into your routines —
the gallery itself is never edited.

### Also in v1.0

- **Quick Settings tile** — run your favourite routine from the notification shade.
- **Home-screen widget** — one-tap run of your favourite routine.
- **Backup &amp; restore** — export routines and modes as a versioned JSON file,
  import it back, or open a shared `.json` file with RoutineFlow.
- **Run history** — every run logged with success / partial / failed status.
- **Diagnostics** — a health screen showing every permission and grant, what is
  missing, and exactly what breaks without it.
- **Setup wizard** — first-run walkthrough for permissions, battery exemption
  and per-brand OEM steps.

## How it works

A routine is **triggers + conditions + actions**:

1. **Triggers are ANDed.** Every trigger in a routine must match for it to
   fire. Event triggers (time, app opened, notification…) match if the event
   arrived within the last 90 seconds; state triggers (battery below 20%,
   Wi-Fi connected, charging…) match against the live state of the phone.
2. **Conditions must also hold** at fire time (e.g. only between 23:00 and
   06:00, only when charging).
3. **Actions run in order**, with IF/ELSE blocks, delays (up to 5 minutes)
   and `${battery}` / `${time}` / `${routine}` text substitution.
4. **Cooldown:** a routine does not re-fire automatically within 5 minutes of
   its last run, so a state trigger cannot spam actions.

The engine is pure Kotlin with no Android dependencies, so the matching and
control-flow logic is fully unit-tested on the JVM. The Android layer
(`EngineService`) owns the foreground service, alarms, geofences, notification
listening and app-usage polling, and hands snapshots and events to the engine.

## Build

### Android Studio (recommended)

1. Open this folder in Android Studio (Ladybug or newer).
2. Let it sync Gradle (JDK 17, Android SDK 36 / compileSdk 36).
3. Press **Run** — or **Build → Build App Bundle(s) / APK(s) → Build APK(s)**
   for an APK.

### Command line

Requires JDK 17 and the Android SDK (API 36) with `ANDROID_HOME` set:

```sh
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Get the APK

No Android Studio? Push this repo to GitHub and CI does the build:

1. Push to GitHub (any branch).
2. Open the repo's **Actions** tab → the latest **Build** run.
3. Download the **routineflow-debug-apk** artifact (kept 30 days) and unzip it.

Or download Gradle 8.13 directly from services.gradle.org, unzip it, and run
`gradle :app:assembleDebug` from the project root (this is what CI does).

## Install on your phone

1. Copy the APK to your phone (or download it straight onto the phone).
2. Tap it. Android will ask you to allow installing from that source
   (browser / Files) — allow it once.
3. Open RoutineFlow and follow the **setup wizard**: it walks you through the
   permissions below, the battery exemption, and the OEM steps for your phone
   brand.

## First-run setup

RoutineFlow works with zero permissions, but each permission unlocks the
triggers and actions that need it. The setup wizard and the Diagnostics screen
show exactly what is missing and what breaks without it.

### Battery exemption (important)

Android (and especially OEM skins) kills background apps aggressively. For
routines to fire reliably:

- Grant **"Don't optimise"** when the wizard asks (battery optimisation exemption).
- **OnePlus / OxygenOS:** Settings → Battery → Battery optimisation →
  RoutineFlow → Don't optimise; also lock RoutineFlow in the Recents screen
  (tap the lock icon) so swiping it away does not kill the engine, and turn off
  any "deep optimisation" / "sleep standby optimisation" for the app.
- **Samsung / One UI:** Settings → Battery → Background usage limits → Never
  sleeping apps → add RoutineFlow.
- **Xiaomi / MIUI:** Settings → Battery → App battery saver → RoutineFlow →
  No restrictions; also enable Autostart.
- **Huawei / EMUI:** Settings → Battery → App launch → RoutineFlow → Manage
  manually (allow auto-launch, secondary launch, run in background).
- **Other brands:** the wizard links to the right screen where it can; the
  Diagnostics screen tells you if the exemption is missing.

### Other grants (all optional, all in Settings — never silently taken)

- **Notification access** — notification trigger, clear-notifications and
  play/pause media actions.
- **Usage access** — app-opened / app-closed triggers.
- **Do Not Disturb access** — DND actions and Modes.
- **Modify system settings** — brightness, auto-brightness, screen timeout,
  auto-rotate.
- **Device admin (lock only)** — the lock-screen action. RoutineFlow requests
  no password, camera or wipe policies.
- **Exact alarms** — time / alarm / sunrise / sunset triggers firing on time.
- **Location (incl. background)** — geofence triggers and sunrise/sunset.
- **Camera** — the flashlight action only (the torch is part of the camera).

## Permissions

Every permission in the manifest, why it exists, and what breaks without it:

| Permission | Why | Without it |
|---|---|---|
| INTERNET | Only for the user's own webhook (HTTP GET) action | Webhook action fails; nothing else uses the network |
| ACCESS_NETWORK_STATE | Check connectivity for webhooks | Webhook may fail silently |
| ACCESS_WIFI_STATE | Read current Wi-Fi SSID | Wi-Fi triggers/conditions never match |
| BLUETOOTH / BLUETOOTH_CONNECT / BLUETOOTH_SCAN | Bluetooth triggers and Bluetooth on/off action | Bluetooth triggers/actions unavailable |
| VIBRATE | Vibrate action | Vibrate action does nothing |
| WAKE_LOCK | Keep the engine awake briefly while a routine runs | Long routines may be cut short |
| RECEIVE_BOOT_COMPLETED | Re-arm alarms and geofences after reboot | Routines do not resume after a restart |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_SPECIAL_USE | The engine foreground service | Engine cannot run in the background |
| POST_NOTIFICATIONS | Routine notifications, engine notification | No notifications at all |
| SCHEDULE_EXACT_ALARM | Time / alarm / sun triggers at exact times | Time triggers fire late or not at all |
| ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION | Geofences, sunrise/sunset location | Location triggers unavailable |
| ACCESS_BACKGROUND_LOCATION | Geofences firing while the app is closed | Arrive/leave triggers unreliable |
| ACCESS_NOTIFICATION_POLICY | Do Not Disturb actions and Modes | DND actions/Modes fail |
| WRITE_SETTINGS | Brightness, auto-brightness, timeout, auto-rotate | Those settings actions fail |
| PACKAGE_USAGE_STATS | App-opened / app-closed triggers | App triggers never fire |
| CAMERA | Flashlight (torch) action only | Flashlight action fails |
| READ_PHONE_STATE | Call-state trigger | Call trigger never fires |
| SET_WALLPAPER | Solid-colour wallpaper action | Wallpaper action fails |
| MODIFY_AUDIO_SETTINGS | Volume and ringer actions | Volume/ringer actions fail |
| REQUEST_IGNORE_BATTERY_OPTIMIZATIONS | Ask for the battery exemption | Engine may be killed by the OS |

## Honest limits

RoutineFlow does not fake capabilities. These are real platform limits:

- **Wi-Fi direct toggle is blocked on Android 10+.** The Play-safe action
  opens the Wi-Fi settings panel instead; the direct toggle is a badged
  Shizuku action.
- **Mobile data, airplane mode and GPS direct toggles need Shizuku or root**
  and are badged, not faked. (GPS needs root even with Shizuku on Android 10+.)
- **Exact alarms can be delayed by Doze** and by OEM battery killers; the
  battery exemption and OEM steps above are the mitigation.
- **OEM battery killers** (OnePlus, Samsung, Xiaomi, Huawei…) can still kill
  the engine despite every exemption. Diagnostics reports the engine state so
  you can see it.
- **No Accessibility Service in v1**, so there is no UI automation inside
  other apps (no tapping buttons in other apps, no reading other apps' screens).
- **App-open detection needs Usage access and polls roughly every 3 seconds**,
  so app triggers fire within a few seconds, not instantly.
- **Sunrise/sunset is approximate** (a few minutes off) — calculated on-device
  from your location, with no network.
- **Android does not broadcast other apps' alarms**, so the Alarm trigger only
  fires for alarms set through RoutineFlow itself.

## Privacy

Local-only. No account, no analytics, no ads, no data collection. The only
network traffic RoutineFlow ever makes is the user's own webhook action to
URLs they configure themselves. Full policy:
[PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Roadmap

- **v1.5:** loops and variables, JavaScript actions, HTTP server (receive
  webhooks), plugin API, routine gallery.
- **v2.0:** AI routine builder, Shizuku action pack (the badged actions become
  real), Wear OS companion, optional sync.

## Tech

Kotlin 2.1 · Jetpack Compose (Material 3) · Room · DataStore · WorkManager ·
Kotlin Serialization · kotlinx-coroutines · Play Services Location (geofencing)
· minSdk 26 (Android 8.0) · targetSdk 36 (Android 16).

## License

MIT. Copyright © 2026 Ravi Yadav. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
If you fork or redistribute RoutineFlow, keep the copyright notice intact and
give visible credit: "Created by Ravi Yadav (https://github.com/08raviyadav)".
