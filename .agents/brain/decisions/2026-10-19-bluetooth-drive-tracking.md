# ADR: Learn driving from Bluetooth connections, and attribute distance to the days driven

**Date:** 2026-10-19
**Status:** Accepted — the model's starting point and the fuel level for a
tank in which tracking began amended by `2026-10-20-assumed-speed-until-learned.md`

## Context
Vibilia only knew the odometer at refuels. Two things followed from that.
Every kilometre of a tank was booked on the day of the refuel that closed it,
so with refuels less often than monthly one month showed nothing and the next
showed everything. And the fuel level estimate divided by calendar days, so a
week of holiday at home drained the gauge as fast as a week of commuting.

A phone connects to the car's Bluetooth whenever the car is on, which is a
cheap, battery-free proxy for "driving now" — and only the native Android app
can observe it (`2026-10-19-native-android-apps.md`).

## Decision
- **Linking.** A car is linked to one or more Bluetooth adapter addresses in
  `vibilia_car_bluetooth_devices`. The link is per car, not per phone: every
  member's phone recognises the car once that member turns tracking on (a
  device-local switch). The web shows the link read-only.
- **Recording.** Each phone records connect/disconnect pairs and uploads them to
  `vibilia_drive_intervals` with a client-generated id, so a retried upload is a
  no-op. A disconnect followed by a reconnect within two minutes is treated as one
  drive. Intervals under a minute are dropped on the phone.
- **Deduplication happens on read, by time.** Several phones in one car report
  overlapping intervals; `get_car_drive_periods` merges them into
  non-overlapping periods (gaps and islands), so every minute counts once
  however many phones saw it. Nothing is deduplicated on write, which keeps the
  upload free of coordination between phones.
- **The model** (`get_car_drive_model`) is a ratio of sums over *tracked legs* —
  refuel-to-refuel intervals that began after tracking started, so their driving
  time is complete: km per driving hour and litres per driving hour, each leg
  weighted by its length and by age with a 180-day half-life. Legs averaging
  below 5 or above 150 km/h are excluded as evidence of a phone left connected or
  left behind.
- **Attribution** (`get_car_daily_distance`): a tracked leg's *exact* odometer
  distance and fuel are spread over the Europe/Berlin days it was driven, in
  proportion to each day's driving time. Every other leg stays on its refuel day,
  as before. The open leg since the last refuel is converted with the model and
  returned as separate `estimated_*` columns, replaced by exact figures once the
  next refuel is logged.
- **Fuel level** (`get_car_fuel_level_estimate`): driving hours since the last
  refuel times the model's litres per hour when the model is ready; otherwise the
  calendar method the web app used to compute in the browser.
- Statistics follow: this-year / last-year distance and fuel in
  `get_car_refuel_statistics` sum the daily attribution by calendar year. Costs
  stay on the day they were paid.
- All of this is SQL, so the web app shows the same estimate and the same
  monthly chart as the phone.

## Consequences
- **Everything degrades to the old behaviour.** A car with no drives gets
  refuel-day attribution and the calendar estimate; `get_car_km_per_month`
  remains for clients built before `get_car_distance_per_month`. Pinned by
  `supabase/tests/drive_tracking.test.sql`.
- "Connected" is not "driving". Sitting in the car with the radio on counts.
  The speed bounds keep the worst of it out of the model; nothing keeps it out of
  attribution, which only redistributes the odometer's true total.
- A drive made without any tracked phone in the car is invisible: the leg's
  distance lands on the days the phone did see. The total stays exact; only the
  daily split suffers.
- Tracking coverage is inferred, not recorded: a leg counts as tracked if any
  phone reported anything before it began. Turning tracking off for a month
  quietly skews that month's split — acceptable for a personal app.
- If a phone reboots mid-drive, it closes the drive at its last heartbeat (every
  15 minutes while connected), so up to 15 minutes can be lost.
- A Bluetooth address identifies the car's head unit. Replacing the head unit
  means linking the new one.
