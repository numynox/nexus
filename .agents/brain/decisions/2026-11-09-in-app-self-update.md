# ADR: The Android apps download and install their own updates

**Date:** 2026-11-09
**Status:** Accepted

## Context
Every update meant the banner's link, a browser download, the Downloads
folder and Android's package installer — four apps, every release. Updates
were skipped, and an installed APK that lags behind is exactly the version
skew `2026-10-19-native-android-apps.md` warns about.

## Decision
- **`android/core/update`**, shared by all apps: `AppUpdates` (in each
  app's container), two workers, one non-exported `UpdateReceiver`. Its
  manifest brings `REQUEST_INSTALL_PACKAGES`, the foreground-service
  permissions and WorkManager's `SystemForegroundService` typed `dataSync`.
- **The check** stays `core/data`'s `UpdateChecker` (unauthenticated GitHub
  API, highest version code among this app's tags). It runs on app start
  and daily (`PeriodicWorkRequest`, any network). A newer release is
  notified once: the version code notified is stored only when posting
  succeeded, so a swiped notification does not return and a phone that
  allows notifications later still hears of it.
- **The download** is a foreground worker (unique, `REPLACE`), started by
  the banner, the settings row, or the notification's tap — a broadcast to
  the receiver, since Android 12 forbids notification trampolines. Progress
  in an ongoing notification, at most once a second.
- **Before installing** the APK must be this package, newer than the
  installed build, and signed by the same certificates. "Not newer" ends
  quietly: the install replaces the process, and WorkManager may run the
  worker again in the new version. Where the platform cannot read an
  archive's signers the key check is left to Android, which refuses a
  foreign key anyway.
- **The install** is a `PackageInstaller` session with
  `USER_ACTION_NOT_REQUIRED`. Its answer reaches the receiver: the system's
  confirmation as a notification (and opened at once if the app is in
  front), success and cancel quietly, anything else "Installation failed".
- **Release builds only.** A debug build is another package and key; its
  banner keeps opening the release page.
- English labels only, like every other string in the apps (glossary.md),
  where the approach this followed used German ones.

## Consequences
- The first install stays manual. The first self-update asks to allow
  "Install unknown apps" for the app and to confirm, because the browser
  installed it. After that the app is its own installer and Android may
  skip the prompt — unverified on a device; check it on the two releases
  after this ships.
- The version code must only grow: the workflow file names stay
  (`2026-10-19-shared-android-build-and-per-app-ci.md`), and a local build
  installed over a release uses the same code, never a higher one.
- The signing key is now also what the app checks itself against; losing it
  stops self-updates as well as manual ones.
