# ADR: On Android 16 the companion's notification draws the journey

**Date:** 2026-11-22
**Status:** Accepted; its bar and texts superseded by `2026-11-24-abeona-journey-header.md`

## Context
Since November the live card has been the journey drawn as one line
(`2026-11-20-abeona-live-journey-line.md`). The ongoing notification was
still four lines of text (`2026-11-20-abeona-notification-names-the-train.md`).

Android 16 promotes an ongoing notification to a Live Update: at the top of the
shade, on the lock screen and as a status-bar chip. In that form
`ProgressStyle` draws segments, points and a moving tracker. That is the
journey line in a form the system understands. Transit, Flighty and Google
Maps use the lock screen like this, and travellers single out these apps for it.

`ProgressStyle` has a cost: it allows only a title, one text line and the
header's subtext. The owner asked to keep the progress bar and still keep the
speed and the next stop. Anything else should be compacted or dropped.

## Decision
On Android 16 and newer, `LiveCompanion.ongoing()` uses `ProgressStyle`:
- **The bar.** The bar is the journey actually followed, measured in minutes
  from the first expected departure to the final expected arrival.
  - Each ride is a segment in the brand colour.
  - Each change is a segment in its confidence colour.
  - The change stations ahead are points (the system draws at most 4).
  - A train is the tracker and a flag is the end.
  - With more than 10 segments (the system's limit), the changes become points
    only.
- **The title** is unchanged: "ICE 728 arrives in 1 h 25 min · Pl. 7 · 10:19".
- **The text and the subtext.** The rest goes in order of importance:
  1. the change, as one line ("Change at Liège-Guillemins → IC 535 · Pl. 3 · 13:02"
     and its minutes and state when not safe);
  2. the seat before boarding;
  3. the next stop;
  4. the speed;
  5. how fresh the data is, only when it is not live.

  The first goes in the text, the others are joined in the subtext.
  "n stops left" is dropped because the bar shows it. "Live 12:37" is dropped
  because live is the normal case.
- **Actions.** "Ticket" opens the ticket directly when the trip has one.
  "Alternatives" appears when something broke. "Stop" stays.
- **Android 15 and older** keep the four-line notification of
  `2026-11-20-abeona-notification-names-the-train.md` and gain the same
  actions.
- **The widget** shows "in 12 min" only while the companion runs and refreshes
  it every minute. Otherwise it shows the day, so it never shows stale
  minutes; a widget refreshes only every 30 minutes on its own.

## Consequences
- On Android 16 the notification and the live card read alike: the same
  order, and the same colours for the same confidence.
- The system may adjust segment colours for contrast, and some manufacturers
  restrict promotion. The bar is a summary; the card stays the source of truth.
- Android 16 Live Updates are mirrored to Wear OS 7 watches where the
  manufacturer allows it. There is no watch app of our own.
