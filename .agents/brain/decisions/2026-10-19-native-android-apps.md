# ADR: Native Android apps in Kotlin, beside the web apps, on the same Supabase

**Date:** 2026-10-19
**Status:** Accepted

## Context
The three apps are installable PWAs, but a browser tab cannot do the things
that make them most useful on a phone: notice that the phone connected to the
car, run work in the background, keep a session without a spinner on every
page, or feel like a native app. Three directions were weighed: wrapping the
existing Svelte build (Capacitor, a Trusted Web Activity), moving the backend to
Firebase for its offline sync, or writing native apps against the backend we
already have.

Firebase was rejected outright. Most of the logic in Nexus lives in Postgres —
the statistics RPCs, trigram story grouping, RLS with helper functions, pg_cron
scheduling, the price rollup — and none of it has a Firestore equivalent. Its
scheduled functions and outbound calls also need a paid plan.

Wrappers were rejected because the point is a native look and native
capabilities, not the web UI in a frame.

## Decision
- **Supabase stays the only backend.** Firebase is avoided as far as possible;
  if push notifications are ever wanted, FCM is the one piece that may be used,
  as a transport only.
- **Each web app gets its own native Android app**, written in **Kotlin with
  Jetpack Compose and Material 3** — fully native, not a wrapper. Vibilia is the
  first, as the test of the approach; Noctua and Annona follow.
- **Android code lives in `android/`**, a Gradle project separate from the npm
  workspace. Shared code is split the same way the web apps share
  `packages/`: `android/core/data` (Supabase client, auth, build config, update
  check) and `android/core/ui` (theme, motion, components, sign-in), with one
  application module per app (`android/vibilia`).
- **Distribution is sideloading from GitHub Releases.** CI builds a signed APK
  on every push to `main` that touches `android/` and attaches it to a release.
  There is no Play Store listing.
- **The web apps keep working and stay first-class.** A feature added on Android
  is also built on the web where the browser allows it; where it does not (Bluetooth),
  the web shows the result read-only or hides the control gracefully. Server-side
  logic goes into Postgres RPCs, so both clients get it for free.
- The Android app does **not** have to mirror the web app screen by screen.
  It is free to restructure navigation and data access where that makes a
  better app.

## Consequences
- **Version skew is now permanent.** A website is replaced on the next page
  load; an installed APK can lag for months. Schema changes must keep old
  clients working: add columns and functions, deprecate before dropping. The
  in-app update banner (it reads the latest GitHub release) shortens, but does
  not close, that window.
- Row shapes now exist twice: in each `lib/data.ts` and in the app's Kotlin
  models. Both are hand-written (no generated types,
  `2026-09-04-permissive-supabase-client-generics.md`), so a renamed column
  breaks at runtime in two places.
- The Supabase URL and anon key are baked into the APK at build time from the
  same repository variables the websites use. Changing them needs a new build
  *and* every phone to update.
- Merging to `main` now releases an APK as well as the websites
  (`2026-09-04-main-is-the-release-branch.md`). Releases are tagged
  `vibilia-android-v<version>` because sideloaded updates need something to
  download; the websites still have no tags.
- Every update must be signed with the same key or Android refuses to install it
  over the old one. The keystore lives only in repository secrets and must be
  backed up outside GitHub — losing it means every user uninstalls and
  reinstalls.
- iOS is not covered and not planned.
