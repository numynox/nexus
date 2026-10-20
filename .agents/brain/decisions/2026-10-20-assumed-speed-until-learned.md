# ADR: Convert drives with an assumed speed by duration that tracked tanks scale

**Date:** 2026-10-20
**Status:** Accepted

## Context
The drive model (`2026-10-19-bluetooth-drive-tracking.md`) learned one figure,
km per driving hour, only from tracked legs. A leg is tracked only if
tracking ran from its first refuel. So a car whose tracking began mid-tank had
no model until the *second* refuel after that. The fuel level stayed on the
calendar method for two tanks, and the first tank's drives were not used at
all.

Of the two numbers the model needs, only one actually needs tracking. Litres
per 100 km is already known from the refuel history; distance per driving
time is not. A single speed also fits badly: a 10-minute errand and a
motorway hour differ by a factor of three.

## Decision
- **An assumed average speed by drive duration**, set by the owner: 30 km/h
  for drives up to 10 minutes, rising linearly to 50 km/h at 20 minutes and
  to 100 km/h at 100 minutes, 100 km/h beyond. It applies to the whole
  drive: 20 minutes is 16.7 km, an hour 75 km (`vibilia_assumed_drive_km`).
  A drive is a merged period (`get_car_drive_periods`). The phone already
  joins reconnects within two minutes into one drive.
  The first version (`20261020120000`) read the owner's breakpoints as
  distances (10, 20, 100 km). That made a 20-minute drive 10 km, against
  what was meant; `20261020200000` replaced it.
- **Tracked tanks scale the curve rather than replace it.** Each tracked leg
  compares its odometer km with the curve's km for its drives.
  `speed_factor` is the age-weighted ratio of sums, blended with 1 (the curve
  as given) counted as 3 hours of driving:
  `(learned × H + 1 × 3) / (H + 3)`. H is the real, unweighted hours, so
  old legs do not hand the model back to the assumption. A typical tank of
  ~10 driving hours outweighs the assumption about 3 : 1.
  `sample_count = 0` means the curve is still unscaled, and both clients
  say so.
- **Litres follow distance** at the car's litres per 100 km.
  `km_per_hour` / `liters_per_hour` remain for display and for clients built
  before the curve: the factor times the curve's average over the last 90
  days of drives.
- **The tank in which tracking began** uses the drive method too: calendar
  litres per day up to the first recorded drive, driving after.
  `get_car_fuel_level_estimate` reports the parts as `calendar_liters` and
  `driving_since`.
- **A partly tracked leg still teaches nothing.** Its odometer distance
  includes driving nobody timed. Inferring the missing hours from calendar
  days would bring back the calendar error tracking exists to remove.

## Consequences
- Gauge, range and the open leg's estimated km move with driving from the
  first recorded drive. Until the first tracked tank they are only as right
  as the curve is for that car. Litres are less exposed than kilometres,
  because litres per 100 km is measured.
- The curve is global and lives in SQL. A second user with a very different
  driving profile would want it per car. The Android app (`DriveCurve.kt`)
  and the web app (`assumedDriveKm`) carry copies to estimate a single drive.
  Change all three together.
- A drive spanning a refuel is split there, and each part goes on the curve
  separately. Both parts are slightly underestimated; rare enough to accept.
- The Android gauge's drive-in-progress top-up (`plusLocalDriving`) stays
  linear at `liters_per_hour`. It is replaced by the server figure once the
  drive is uploaded.
- Measured distance (the daily attribution) is unchanged: it still spreads
  only tracked legs' exact odometer figures. Only the separately reported
  estimates use the curve.
- A car with no drives at all still has no model. Untracked cars are
  unaffected.
