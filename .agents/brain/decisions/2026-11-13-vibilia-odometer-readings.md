# ADR: Odometer readings, a logbook entry without a cost

**Date:** 2026-11-13
**Status:** Accepted

## Context
A service record gave dated odometer values from before the car was bought.
They should extend the odometer chart, but neither existing entry fits:

- A **back-dated refuel** makes the gap between the old reading and the first
  real refuel count as distance driven, with consumption across it.
- A **0 € expense** (the forms required more than 0 €, the column did not)
  stretches "expenses per year": `expense_span` divides by the years between
  the first and the last expense. The chart only read refuels anyway.

## Decision
- A third entry, **odometer reading** (`vibilia_odometer_readings`: car, date,
  mileage, notes). Shared with the car's members like the rest of it; anyone
  with access may add, correct and delete.
- Readings are **plotted, never counted**. `get_car_odometer_plot` merges them
  with the refuels for the odometer chart on both clients;
  `get_car_refuel_year_bounds` reaches back to the oldest one, so "All" and
  the year options cover them. Every statistics RPC still reads refuels and
  expenses only.
- In the logbook they are entries of their own, counted in no month's spend: a
  month with only readings shows no amount and no comparison, and does not
  start the comparisons early.
- `get_car_refuel_plot_events` keeps its mileage column for APKs that predate
  this; they simply do not see readings.

## Consequences
- The statistics card's odometer (`latest_mileage`) is still the highest
  refuel. A reading newer than the last refuel does not move it.
- Readings are not checked against their neighbours: a typo shows up as a
  kink in the chart, and is fixed by editing the reading.
- Anything that should *count* the distance before the first refuel (a "total
  driven since manufacture", say) would need its own decision — readings were
  kept out of the statistics deliberately.
