# ADR: One Gradle convention for every Android app, and a CI build per app

**Date:** 2026-10-19
**Status:** Accepted

## Context
Vibilia was the only Android app, and its `build.gradle.kts` held everything:
SDK levels, the version scheme, signing, the Supabase BuildConfig fields, the
`config.yaml` reader. Noctua and Annona needed all of it. Copied three times it
would drift — the version scheme and the signing setup are exactly the parts
that must not — and one CI workflow for `android/**` would have built Vibilia
for a change to Annona.

## Decision
- **`android/build-logic`**, an included build with two convention plugins:
  `nexus.android.application` (namespace and application id from the module
  name, SDK levels, `0.0.<run number>` versioning, release signing,
  `SUPABASE_*` / `RELEASE_*` BuildConfig fields, compose, test setup, the
  shared modules as dependencies) and `nexus.android.library`. Build-time
  settings are read once, in `NexusBuildSettings`, exposed to app scripts as
  `nexus.setting(...)` and `nexus.configInt/configDouble(app, key, default)`.
- **What the apps share at runtime lives in `core/`**: `NexusCore` (client,
  session, update check), `NexusApp` (theme and session gate), `Loadable`, the
  settings cards. **`core/testing`** holds the fake Supabase and the screenshot
  robot, so each app's screenshot test is only its data and its walk.
- **One reusable workflow, `android-app.yml`, called by one small workflow per
  app**, each filtered to `android/<app>/**` plus the shared paths. One Gradle
  invocation per build runs the tests, the screenshots and the release APK;
  the Gradle cache is written from `main` only.

## Consequences
- A change to `core/`, `build-logic/` or the version catalog builds all three
  apps — by design, since all three ship it. A change to one app builds one.
- The per-app workflow file name is load-bearing: its run number is the
  version code. Renaming `android-vibilia.yml` would restart Vibilia at
  version code 1, below what phones have installed.
- `config.yaml` triggers all three; it is small and rarely changes, and
  scoping the trigger to one app's block is not something path filters can do.
- All apps share one signing key and one version prefix. An app that ever
  needs its own scheme overrides it in its own build file.
