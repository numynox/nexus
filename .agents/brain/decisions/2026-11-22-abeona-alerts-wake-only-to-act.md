# ADR: Abeona's alerts wake the traveller only to act

**Date:** 2026-11-22
**Status:** Accepted

## Context
The Android companion alerted on a cancellation, a platform change, a change
at risk or impossible, the binding lifted, and every time a ride's delay grew
by the chosen step (5, 10, 15 or 20 minutes). Four things were missing or
wrong:
- No alert said when to get off. Citymapper, Transit, NAVITIME and DB's regional
  Wi-Fi portal all have one, and travellers rate it highly.
- A warning was never withdrawn. A change at risk that came good again left
  the red alert in the shade. DSB sends "back on schedule".
- The delay steps repeated. A train losing a minute at every stop alerted
  again and again, but the traveller can do nothing about the delay itself.
- Every alert used the same urgent channel.

Research says the same thing: people get about 63 notifications a day, and
one warning symbol used for everything gets ignored (Transport Focus 2026). It
also shows the cost of floods: the main complaints about Flighty and Rejseplanen.

## Decision
`CompanionText.kt` and `LiveCompanion.kt`:
- **One notification per subject.** Each alert names its subject: a change, a
  ride's platform, a ride's cancellation, a ride's delay, getting off, the
  binding, the delay at the destination, a missed connection. Its notification
  id comes from the subject, so a newer alert replaces the older one.
- **Two channels.** "Act now" (the existing `alerts` channel, high importance):
  platform change, change at risk or impossible, cancelled, missed, get off,
  you may take another train. "Updates" (`updates`, default importance):
  delays, back on track, compensation thresholds.
- **Get off.** Once per ride, 5 minutes before the expected arrival, while the
  companion sees the traveller on board: "Get off at Würzburg Hbf in 5 min",
  then the platform, the next train and its platform, and the exit side when
  the train's Wi-Fi gives it. It is worked out from stored times, so it works
  in a tunnel. A "Get off reminder" switch, on by default.
- **Back on track.** A change alerted at risk or impossible that is fine
  again, or a ride alerted as late that is on time again, replaces its warning
  without sound.
- **Delays by consequence.** A ride's delay alerts once, at the chosen
  threshold. After that, only the delay at the destination alerts, when it
  reaches the compensation thresholds (60 and 120 minutes). The train binding
  keeps its own alert at 20 minutes, now titled "You may take another train"
  like the banner.

## Consequences
- The delay setting now means "from", not "from and every further". An
  update changes its meaning without asking. The old repeats are what we
  chose to drop.
- The importance of a channel belongs to the user once it exists. The
  `alerts` id is kept so existing settings stay; renaming it to "Act now"
  only changes its label.
- The web has no live notification surface. Its card already shows each of
  these states as they happen.
