# ADR: Abeona, a train ride companion, scoped to a shared household

**Date:** 2026-11-02
**Status:** Accepted

## Context
A fourth app was wanted: a companion for train rides, mainly in Germany, with
reduced coverage in Austria, Switzerland, Italy, Denmark and France. Its five
use cases are upcoming trips, BahnCards for several people, a live travel
day, the record of a ride against its plan with compensation, and history.
Like the other apps it must cost nothing to run, and it gets a web client and
a native Android client.

The full feasibility review, with every feature graded feasible, risky,
deferred or impossible, is in the two GitHub issues that carry the remaining
work: #37 ("Abeona: ticket intake, BahnCards and the live travel day") and
#38 ("Abeona: after the ride, history and polish"). Keep it there, not here.

Trips are rarely made alone, and the app's user travels with family. Three
scoping models were weighed: per user (like Noctua), per-object sharing (like
Vibilia's cars) and a shared household (like Annona, but bounded).

## Decision
- **Name:** Abeona, the Roman goddess of outward journeys. That continues the
  naming scheme; Vibilia, goddess of journeys, was already taken.
  `app/abeona`, `android/abeona`, `/nexus/abeona`, tables `abeona_*`.
- **Scoping is a shared household.** `abeona_households` and
  `abeona_household_members`. A user is in at most one household; it is made
  on first use by `abeona_ensure_household()`. Trips, travellers (and later
  BahnCards and tickets) carry `household_id`, and every policy and RPC defers
  to `abeona_user_in_household()`. Unlike Annona this is bounded: strangers
  see nothing.
- **Joining merges.** Inviting someone by email whose household has no other
  members moves their trips and travellers over and deletes the empty
  household. Someone already sharing with others must leave first. Leaving
  leaves the trips behind and starts a fresh household.
- **Travellers are not users.** A traveller is a person who rides: a member,
  or a child or a partner without an account. BahnCards will belong to
  travellers, not to accounts.
- **Planned and current are separate columns.** A leg's planned times and
  platforms never change after saving; `departure`, `arrival` and the current
  platforms are overwritten by refreshes (`abeona_update_trip_live`). The
  statistics and, later, the compensation read the difference.
- **First slice (MVP):** search and save trips, trip detail with map, transfer
  confidence, the train-binding banner, a ticket wallet, an offline trip pack,
  a live notification and widget on Android, and the year in review. The
  rules shared by both clients (transfer confidence, binding lifted) are
  written twice, in TypeScript and Kotlin, with the binding rule also in SQL.
  Change them together.

## Consequences
- The live companion runs **on the phone**: a foreground service polls the
  Edge Function every two minutes during a trip. There is no server-side
  monitoring or push, so the web only knows what it sees while it is open.
  Server push (pg_cron plus FCM or Web Push) is deferred.
- Delays in the statistics are only as good as the last refresh. A trip
  nobody opened on the day keeps its planned times. Freezing actuals with a
  scheduled final snapshot is the next step (#38).
- The household is the unit of trust. There is no per-trip privacy inside a
  household.
- Distances are measured in SQL from the saved, thinned route line, or 1.1 ×
  the great-circle distance without one. That is accurate enough for totals,
  not for fares.
- (2026-11-04) The companion holds a **partial wake lock** while it follows a
  trip, capped at six hours. Without it the phone dozed with the screen off,
  the 30-second ticks stopped, and the notification froze until the screen
  came on. Its notification is a **Live Update** on Android 16+
  (`setRequestPromotedOngoing`, `POST_PROMOTED_NOTIFICATIONS`): pinned at the
  top, on the lock screen, with a status bar chip (`CompanionStatus.chip`:
  "12 min", "Pl. 7", "Missed"). Older Android shows it as an ordinary ongoing
  notification. The user can turn Live Updates off per app in Android's
  settings.
- (2026-11-07) The companion now refreshes every minute and starts from an
  exact alarm, on by default, with a dashboard on the trip page
  (`2026-11-07-abeona-companion-dashboard.md`).
