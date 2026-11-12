# Changelog

All notable changes to RoutineFlow. Dates are in YYYY-MM-DD format.

## [1.0.0] - 2026-11-12

First release.

### Added

- **Rule engine** — pure-Kotlin, unit-tested: ANDed triggers (event triggers
  match within a 90-second window, state triggers match the live snapshot),
  routine-level conditions, IF/ELSE/END_IF control flow, delays up to
  5 minutes, `${battery}` / `${time}` / `${routine}` substitution, and a
  5-minute automatic re-fire cooldown per routine.
- **23 triggers** — time of day, RoutineFlow alarm, sunrise, sunset, battery
  level, charging state, Wi-Fi connected/disconnected, Bluetooth
  connected/disconnected, arrive/leave a place (geofence), app opened/closed,
  screen on/off, headphones, notification received, airplane mode, Do Not
  Disturb, device unlocked, call state, clipboard changed.
- **40 Play-safe actions** across settings, apps, notify &amp; speak, network
  (own webhook GET) and flow control — plus **6 badged Shizuku/root actions**
  (direct mobile-data, airplane-mode, Wi-Fi, dark-mode and GPS toggles,
  force-stop app) listed and disabled, never faked.
- **5 built-in Modes** — Sleep, Driving, Work, Focus, Relax — with
  apply-on-activate, revert-on-exit, and automatic activation from triggers.
- **12 ready-made templates** — bedtime wind-down, good morning, low battery
  saver, driving mode, work focus, welcome home, headphones in music up,
  silence for video calls, night charging care, leaving home reminder, pause
  media on calls, read clipboard aloud.
- **Quick Settings tile** and **home-screen widget** — one-tap run of the
  favourite routine.
- **Backup &amp; restore** — versioned JSON export/import, including opening
  shared `.json` backup files with the app.
- **Run history** with per-run status and detail.
- **Diagnostics screen** — every permission and grant, what is missing, and
  what breaks without it — plus a first-run **setup wizard** with per-brand
  OEM battery steps (incl. OnePlus/OxygenOS).
- **Approximate sunrise/sunset** calculated on-device (NOAA-style, no network).
- **CI** — GitHub Actions builds and unit-tests on every push/PR and
  publishes a debug APK artifact.
- Documentation: README (features, build, install, setup, full permissions
  table, honest limits), privacy policy, changelog.
