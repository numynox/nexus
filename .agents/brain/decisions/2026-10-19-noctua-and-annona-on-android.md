# ADR: Noctua and Annona on Android — what changed from the web, and why

**Date:** 2026-10-19
**Status:** Accepted — statistics amended by `2026-10-21-noctua-statistics-in-postgres.md`

## Context
`2026-10-19-native-android-apps.md` committed Noctua and Annona to native apps
that do at least everything the web apps do, free to restructure. Both are
used mostly on a phone: Noctua for reading on the go, Annona standing at the
fridge with groceries in hand.

## Decision
- **No schema change.** Both apps use the web's tables, RPCs and the
  `lookup-food-product` Edge Function as they are; RLS decides what each
  client sees, so nothing new needed reviewing.
- **Noctua** keeps "seen" device-local (DataStore, the Android `localStorage`)
  and "read" in Postgres, exactly the web's split. Starred and read become one
  Library tab. Articles open in a **Custom Tab** — the user's own browser with
  its logins, inside the app. Swipes replace the web's keyboard shortcuts.
  Statistics are computed on the phone from the web's queries, so both clients
  agree without a new RPC.
- **Annona** reads barcodes with **CameraX and zxing-cpp**, not ML Kit: it is
  open source, decodes on the device and needs nothing from Google Play
  services, in line with Vibilia's choice of `LocationManager`. The add form
  keeps the web's month-and-year expiry (the first of the month) and adds quick
  picks and an exact day. A **daily expiry reminder** and **launcher shortcuts**
  are new and Android-only: a website cannot notify reliably, so the web hides
  them rather than offering a half version. Reminders are per phone and off by
  default — the inventory is shared, nagging is personal.

## Consequences
- Row shapes for Noctua and Annona now exist in Kotlin too; a renamed column
  breaks both clients at runtime, like Vibilia's.
- The reminder runs as the signed-in user in WorkManager. A phone that has
  signed out gets no reminder; one offline at the hour retries when the
  network returns.
- Seen state on a phone and in a browser are separate, as they already were
  between two browsers.
