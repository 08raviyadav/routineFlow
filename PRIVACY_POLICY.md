# RoutineFlow — Privacy Policy

**Last updated: 12 November 2026**

RoutineFlow is an Android automation app. This policy explains, in plain
language, what data the app touches and what it never does.

## The short version

- **Everything is stored on your device.** Your routines, modes, run history
  and settings live in a local database on your phone. There is no account,
  no server, and no cloud copy.
- **We collect nothing.** No analytics, no ads, no tracking, no crash
  reporting to any third party.
- **Nothing leaves your device** except in the two cases listed below, both
  of which you control.

## What stays on your device

- Your routines (triggers, conditions, actions), modes, templates you have
  copied, run history and app preferences — stored in a local Room database
  and Android preferences on your phone.
- Places you save for geofence triggers (name, latitude, longitude, radius)
  and locations you enter for sunrise/sunset — stored locally only.
- Clipboard text and notification text used by triggers are read only to
  decide whether a routine should fire. They are not stored, logged or sent
  anywhere.
- App-usage information (which app is in the foreground) is read only to
  match app-opened / app-closed triggers. It is not stored or sent anywhere.
- Backups you export are plain JSON files you choose where to save. Android's
  built-in Auto Backup may also back up the app's local data to your own
  Google account, under your device's backup settings — RoutineFlow itself
  sends nothing.

## The only two ways data leaves your device

1. **Your own webhook action.** If you add a "Web request (GET)" action,
   RoutineFlow sends an HTTP GET to the URL you typed, when that routine
   runs. That is the only network request the app ever makes, and it goes
   only to URLs you configure yourself.
2. **Geofencing via Google Play Services.** Arrive/leave-a-place triggers use
   Android's geofencing APIs, which are provided by Google Play Services on
   the device. The place coordinates you enter are stored locally; the
   geofence itself is registered with Play Services on your phone so it can
   wake the app when you cross it. RoutineFlow sends your location to no
   server of its own.

## Permissions and why each is used

RoutineFlow requests permissions only to power the features that need them,
and every one is optional — the app works without them, with the matching
features unavailable. The full table (permission → purpose → what breaks
without it) is in the README under "Permissions".

In particular:

- **Location** is used only for geofence triggers and for calculating
  sunrise/sunset from a location you enter. It is never sent to any server
  operated by RoutineFlow (there is none).
- **Notification access** is used only to match notification triggers and to
  run the clear-notifications and play/pause-media actions.
- **Usage access** is used only to detect when an app comes to the
  foreground, for app triggers.
- **Device admin** is requested with the lock-screen policy only — no
  password, camera or wipe policies.
- **Camera** is used only to switch the flashlight (torch) on and off.

## Children

RoutineFlow is not directed at children under 13 and collects no data from
anyone.

## Changes to this policy

If this policy changes, the updated version will be published with the app
release it applies to, with the "Last updated" date above changed.

## Contact

Questions about this policy: open an issue on the RoutineFlow GitHub
repository.
