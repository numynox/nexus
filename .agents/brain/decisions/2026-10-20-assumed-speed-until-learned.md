# ADR: Assume a driving speed until the drive model has learned one

**Date:** 2026-10-20
**Status:** Accepted

## Context
The drive model (`2026-10-19-bluetooth-drive-tracking.md`) learned only from
tracked legs, and a leg is tracked only if tracking ran from its first refuel.
A car whose tracking began mid-tank therefore had no model until the *second*
refuel after that. The fuel level stayed on the calendar method for two tanks,
and the first tank's drives were not used at all.

Of the two numbers the model needs, only one actually needs tracking. Litres
per 100 km is already known from the refuel history. Kilometres per driving
hour is the only number that does.

## Decision
- **Assumed speed.** `get_car_drive_model` starts every tracked car at
  50 km/h (the owner's typical speed), counted as 3 hours of driving, and
  blends in tracked legs by their real hours:
  `(learned × H + 50 × 3) / (H + 3)`. A typical tank of ~10 driving hours
  outweighs the assumption about 3 : 1. H is not age-weighted, so old legs
  do not hand the model back to the assumption.
  `sample_count = 0` means the speed is assumed, and both clients mark it as
  such.
- **Litres per hour = km per hour × litres per 100 km**, rather than a
  separate ratio, so the two can never disagree.
- **The tank in which tracking began** uses the drive method too: calendar
  litres per day up to the first recorded drive, driving time after.
  `get_car_fuel_level_estimate` reports the parts as `calendar_liters` and
  `driving_since`.
- **A partly tracked leg still teaches nothing.** Its odometer distance
  includes driving nobody timed, so km over tracked hours would overstate
  the speed. Inferring the missing hours from calendar days would bring back
  the calendar error tracking exists to remove.

## Consequences
- Gauge, range and the open leg's estimated km move with driving from the
  first recorded drive. Until the first tracked tank they are only as right
  as 50 km/h is for that car. Litres are less exposed than kilometres, since
  litres per 100 km is measured.
- The constants are global and live in SQL. A second user with a very
  different driving profile would want them per car.
- Measured distance (the daily attribution) is unchanged: it still spreads
  only tracked legs' exact odometer figures. Only the separately reported
  estimates use the assumption.
- A car with no drives at all still has no model. Untracked cars are
  unaffected.
