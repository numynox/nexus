# Abeona design and UX system

How Abeona looks and what it shows when, on both clients. The reasons are in
`decisions/2026-11-15-abeona-design-system.md`; the words are in `glossary.md`.
Android is the reference; the web follows it with the same names.

Change this file in the same commit as a change to a rule below. A screen that
needs an exception gets the rule changed, not a one-off.

---

## 1. Principles

1. **One question per moment.** Every screen answers the question the
   traveller has *now* first, in the largest type on the screen. Everything
   else is smaller, lower, or behind a tap.
2. **Say a fact once.** A time, platform or delay appears once per screen.
   A card never repeats its title bar.
3. **Shape and place before words.** A boxed number is a platform. A time is
   on the left, a platform on the right. An icon from the vocabulary (§5)
   replaces its word. Words are for names (stations, trains), for instructions
   seen rarely, and for anything with legal weight (train binding, validity,
   compensation).
4. **Colour means status, burgundy means Abeona.** Green, yellow and red are
   only ever status, and always come with an icon. Burgundy is identity, the
   current step and the primary action; it never means "attention".
5. **Dense, in columns.** Rows are one line where they can be. Times, names
   and platforms sit in the same columns on every screen so the eye learns
   where to look.
6. **No card inside a card.** A card holds rows, insets and chips. Where a
   card would nest in another, the outer one becomes plain layout.
7. **The landscape gives way.** The scene (§7) is large when nothing is
   urgent and shrinks to a strip that carries information (the ride's rail)
   on the travel day. It never pushes a fact below the fold.

---

## 2. User stories: the moments

The moments are the travel day's states (`travelday.ts`, `TravelDay.kt`) plus
what lies before and after. "First" is the headline; "then" follows in order;
"behind a tap" is one tap away; anything not listed is not on that screen.

| # | Moment | As a traveller I want to … | First | Then | Behind a tap | Scene |
| :- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | Nothing planned | add my next trip quickly | the scene, "Plan a trip", "Add from a ticket" | year so far | past trips | full, idle |
| 2 | Planning (Search) | compare connections at a glance | per connection: departure → arrival, duration, changes, trains, worst change | price or validity if known | stops, save sheet | none |
| 3 | Days before | know it is still on, and when | day, departure time and platform | arrival, changes, trains, ticket attached | the trip | header, parked |
| 4 | Timetable changed | know my plan broke before the day | what broke, struck through | "Find another connection" | alternatives | header, stopped |
| 5 | Time to leave | know when to walk out | "Leave 12:55" and the countdown | the train to catch: time, line, direction, platform | ticket | strip, waiting |
| 6 | At the station | find my train | the platform, countdown to departure | my coach and seat; the train line; where it is now; steps ahead | ticket, alternatives | strip, waiting |
| 7 | On board | know when and where to get off | the stop I get off at, arrival, platform there, countdown | next stop; the change after; destination | all stops, map, ticket | strip, riding (the rail) |
| 8 | Changing | make the connection | next platform, countdown | margin and way between platforms; the train | alternatives | strip, waiting |
| 9 | Something broke | know my options at once | what broke, struck through; "See alternatives" | whether the binding is lifted | the rules | strip, stopped |
| 10 | Inspection | show the ticket in two taps | the ticket, full brightness | BahnCards the ticket needs | DB Navigator | none |
| 11 | Arrived | know I am done, and how late | where, when, the delay | compensation if any; the postcard | what happened; share the postcard | header, arrived |
| 12 | Afterwards | get my money back with little typing | the verdict and amount | deadline, text for the form | the rule | none |
| 13 | Looking back | enjoy the year | one figure per story card | its comparison | — | full, per card |
| 14 | Setting up | change one thing and leave | groups by moment: before, on the day, after, household | — | each sheet | none |

Rules that follow from the table:

- The **ticket** is one tap away from every travel-day screen (top bar).
- **Alternatives** are a filled button only in moments 4 and 9; outlined when
  a change is at risk; otherwise in ⋮.
- **Freshness** ("updated 17:44", offline) is always the last, smallest line.
- What was true yesterday (price, fare, travellers) is never above the fold on
  the travel day.

---

## 3. Colour

### Brand and surfaces

| Token | Light | Dark | Use |
| :--- | :--- | :--- | :--- |
| `burgundy` | `#8C1D40` | `#F08CA8` | fills: primary button, rail progress, state disc, active nav. Content on it: `#FFFFFF` / `#3D0A1A` |
| `brand` (burgundy as text) | `#8C1D40` | `#F08CA8` | burgundy text and icons on a surface: eyebrow, countdown, links, active tab. The same value as the fill: burgundy is dark enough for both |
| `brand-soft` | `#F7E1E7` | `#452033` | the live card, selected chips, active nav pill |
| `paper` (background) | `#FBF7F2` | `#1A1512` | the screen |
| `card` | `#FFFFFF` | `#26201C` | raised surfaces |
| `well` | `#F4EEE6` | `#322A24` | insets, platform boxes, inputs, quiet chips |
| `ink` | `#2A211C` | `#F3EAE2` | text |
| `muted` | `#6B5F57` | `#B5A79C` | secondary text and icons |
| `line` | ink at 12 % | ink at 14 % | hairlines |

### Status: always with its icon

| Status | Means | Fill / content | Soft / ink (light) | Soft / ink (dark) | Icon |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `ok` | on time, safe change, covered by the ticket, paid | — | `#DDF0E6` / `#1B5A40` | `#1D4634` / `#A6E3C4` | check circle |
| `watch` | delay, tight change, platform changed, binding lifted, deadline near, check the terms | `#F2C230` / `#3A2A00` | `#FCEFC2` / `#6B4D00` | `#4A3A00` / `#FFE08A` | timelapse (tight), swap (binding), event (deadline) |
| `bad` | cancelled, missed, impossible, at risk, not in the timetable, needs another ticket | `#C4372B` / `#FFFFFF` | `#FBDAD5` / `#9E241A` | `#5C1A12` / `#FFC2B8` | error circle (at risk), block (impossible, cancelled) |

- A **delay** is `+6` in `watch` ink beside the new time (`ShiftedTime`,
  glossary: delay). It is never red; red is for a ride that does not run.
- **Soft** is the default (chips, banners). **Fill** is for the one thing on
  the screen that broke: the "See alternatives" button, a changed platform.
- Nothing else is coloured. Line chips are ink (long-distance), outlined ink
  (regional) or well with a bus icon; platform boxes are well. No blue.
- Every pair above is ≥ 4.5 : 1.

The shared daisyUI themes stay selectable on the web; there the tokens derive
from the theme's own `primary`, `success`, `warning` and `error`.

---

## 4. Type, spacing, shape

One scale, named the same on both clients (`AbeonaType` in Kotlin, `t-*`
classes in `global.css`). Numbers are always tabular.

| Name | Size / weight | Use |
| :--- | :--- | :--- |
| `numeral` | 48 / 600, tight | the countdown; one per screen |
| `time` | 28 / 600 | the time that goes with the headline |
| `headline` | 24 / 600 | the answer to the moment's question; one per screen |
| `title` | 17 / 600 | card titles, a stop's name in a row that matters |
| `body` | 15 / 400 | rows, sentences |
| `meta` | 13 / 500, muted | supporting facts, captions |
| `label` | 11 / 700, caps, tracked | the state line of the live card and column heads only |

Spacing is a 4 dp grid: 4, 8, 12, 16, 24. Screen gutter 16. Card padding 16.
Between cards 12. Between rows in a card 8. A row is at least 40 high, 48 if
it is tappable.

The maps follow the theme: in a dark one the basemap is dark too, lifted so roads, water and names stay readable (`decisions/2026-11-15-abeona-dark-map.md`). The route is burgundy, stations ink, a ride that does not run `bad`.

Shapes: card 24, inset 16, line chip and platform box 8 (squared, like a
board), status chip and buttons fully round. No shadows on Android; one soft
shadow for cards on the web.

Icons: 16 beside meta text, 20 leading a row, 24 for actions.

### The row grid

Every row about a stop or a train uses the same four columns:

```
| time 52 | mark 24 | name ……………………… | platform |
  14:16      ●        Stockholm C           [10]
```

Time left (tabular), the mark (rail dot, mode icon or line chip), the name
taking the room, the platform boxed at the right edge. A column with nothing
to say stays empty; it does not collapse. This holds for the trip timeline,
the steps ahead, the next stop, alternatives, search results and the widget.

---

## 5. Icon vocabulary

One concept, one icon, on both clients (Material Symbols Rounded / lucide).
A new concept gets a row here first.

| Concept | Android | Web | Replaces the words |
| :--- | :--- | :--- | :--- |
| leave home | `MeetingRoom` | `DoorOpen` | — |
| at a station, stop | `Place` | `MapPin` | "at" |
| on board, train, board (the verb) | `Train` | `TrainFront` | — |
| bus | `DirectionsBus` | `Bus` | — |
| walk | `DirectionsWalk` | `Footprints` | "walk" |
| change, number of changes | `SwapHoriz` + n | `ArrowRightLeft` + n | "2 changes", "direct" (n = 0) |
| get off | `Logout` | `LogOut` | "get off at" |
| destination, arrival | `Flag` | `Flag` | "Arrives", "Destination" |
| next stop | `ArrowForward` | `ArrowRight` | "Next stop" |
| stops left | `LinearScale` + n | `GitCommitHorizontal` + n | "n stops left" |
| duration | `Schedule` + "2 h 14" | `Clock` | "Duration" |
| platform | the boxed number | the boxed number | "Pl.", "Platform", "Track" |
| ticket | `ConfirmationNumber` | `Ticket` | "Ticket attached" |
| travellers | `Person` + n | `User` + n | "Travellers" |
| BahnCard | `CreditCard` + 25/50 | `CreditCard` | — |
| alternatives | `Shuffle` | `Shuffle` | — |
| updated / live | `Sync` | `RefreshCw` | "Live" |
| offline | `CloudOff` | `CloudOff` | — |
| speed, top speed | `Speed` | `Gauge` | "top speed" on the postcard |
| coach and seat | `EventSeat` | `Armchair` | "Coach 24 · Seat 66" |
| exit side (Android, from the train's Wi-Fi) | `DoorSliding` | — | "Exit left" |
| delay reason (Android, ICE Portal) | `Info` | — | DB's words, one line |
| distance travelled | `Route` | `Route` | "travelled" |
| CO₂ saved | `Eco` | `Leaf` | "CO₂ saved" |
| source: Wi-Fi / location / timetable | `Wifi` / `MyLocation` / `Schedule` | `Wifi` / `LocateFixed` / `Clock` | the source's name on the pill |
| safe / tight / at risk / impossible | `CheckCircle` / `Timelapse` / `ErrorOutline` / `Block` | `CircleCheck` / `Timer` / `CircleAlert` / `Ban` | — (the chip keeps the minutes) |
| share, edit, delete, more | `Share`, `Edit`, `Delete`, `MoreVert` | `Share2`, `Pencil`, `Trash2`, `EllipsisVertical` | — |

An icon that stands alone carries the replaced words as its accessibility
label (`contentDescription`, `aria-label`). A status chip is icon + minutes
("✓ 12 min"); the word (Safe, Tight …) is its label for screen readers and
shows only where the chip stands alone without a number.

---

## 6. Components

Named the same on both clients. A screen is built from these; a new need is a
new component here, not a local one.

| Component | What it is |
| :--- | :--- |
| `ShiftedTime` | a time with its delay (glossary: delay) |
| `PlatformBox` | the boxed platform; `watch` fill with the old number struck when changed; sizes row (default) and lead |
| `LineChip` | "ICE 597" squared; ink / outlined / well+bus |
| `StatusChip` | icon + short value in `ok`, `watch` or `bad` soft. `ConfidenceChip` is a `StatusChip` |
| `Fact` | icon + value in `meta`: ⇄ 2, 🕓 2 h 14, 👤 2, € 39.90 |
| `StopRow` | the row grid (§4): time, mark, name, platform |
| `Banner` | a full-width status message: icon, one bold line, at most one sentence, optional action. `watch` or `bad` soft |
| `LiveCard` | the travel day's card (§8) |
| `Scene` | the landscape (§7) |
| `SectionHeader` | `title` + optional `Fact`s on the right; no supporting sentence |
| `Sheet` | every form and detail; title, content, one primary button at the bottom |
| `PrimaryButton` | burgundy fill, white label (dark theme: rose fill, deep burgundy label); one per screen or sheet |
| `EmptyState` | `Scene` full, one `headline`, the primary button; no paragraph |
| `Segmented` | two or three choices side by side in a well track, the chosen one brand-soft with brand text (Depart/Arrive, class, card, theme, year) |
| `ChoicePills` | more choices than fit a segmented control, as wrapping pills (the fare) |
| `WellField` | a borderless text field on `well`, the placeholder as its label; one line unless it holds pasted text |
| `FieldRow` | one line of a form or of Settings: icon, name, optional ⓘ that opens the explanation in place, the value or switch at the right edge |

---

## 7. The scene

A landscape with a railway through it, drawn in code (Compose `Canvas`,
inline SVG) in the same units on both clients: 360 wide, the track 16 above
the bottom edge, the sky growing upwards with the box. It replaces every
static illustration.

- **Sky**: the real time of day where the trip is (sun times at the current
  stop, Frankfurt when there is none). The sun crosses left to right; at
  night the moon in its real phase and a few fixed stars. Around sunrise and
  sunset the horizon glows orange. The sky fades into the surface behind it
  at its top edge, so the scene has no box, and its lightness follows the
  theme (a dark theme never gets a bright band).
- **Land**: far hills, near hills with trees, masts and wire, the track.
  Parallax 0.08 / 0.25 / 1 / 1.
- **Train**: the one the ride really is, several cars long, in its real
  livery (`trainKind`): an ICE for long-distance (white, red stripe, long
  nose), a red double-deck set for regional trains, a shorter red set with
  many doors for the S-Bahn, a bus for a bus. Every train has an end car at
  both ends, as the real sets do. Its nose marks the place and the cars trail
  off behind; off the rail the whole set shows when the box has the room. Windows are lit at night. This is the one place
  where red is not a status.
- **Motion**: clouds always drift. The land scrolls only while riding, faster
  with the train's speed when its Wi-Fi tells it. Animations off (animator
  scale 0, `prefers-reduced-motion`) means a still picture.

| State | Shows | When |
| :--- | :--- | :--- |
| `idle` | empty track, a signal at green; now and then a small train crosses far away | nothing planned |
| `parked` | the train at a platform, lights off | a trip, not today |
| `waiting` | the train at a platform, headlight on | time to leave, at the station, changing |
| `riding` | the land scrolling past the train | on board |
| `stopped` | the signal at red, nothing moves | cancelled, missed, not in the timetable |
| `arrived` | the train at a platform, a flag | arrived |

Sizes:

| Size | Height | Where |
| :--- | :--- | :--- |
| full | 200 | empty Trips, year-in-review cards, the status page |
| header | 96 | top of the next-trip card off the travel day; arrived |
| strip | 64 | the live card's header, flush with the card's top, and its compact form's |

**The strip is the whole journey.** It is the live card's header, flush with
the card's top and sides. Under its track runs the journey bar, in a frame
that never moves: 16 dp from both edges, in every state from before leaving
to the last ride. Its parts are always all the journey's rides and changes;
the part in focus takes the room, the rest are short:
- **dots mean the stops of the train in focus, and only that**: its line,
  the burgundy fill up to the train's nose, a dot per stop and one at each
  end (the train in the scene above moves in sync);
- **another train is a capsule**: a short rounded line without dots,
  burgundy once done, muted ahead;
- **a change is the gap between two capsules**; while changing it is the part
  in focus: a dashed line in its confidence colour with a walker badge moving
  through the change time;
- **the destination** is always the right end: the end ring, or a ring cap on
  the last capsule;
- before leaving the walker badge waits on the first dot.

A figure on a bar is always a **badge**: a disc in the surface colour, a ring
and the glyph in the colour of the part it sits on, a soft shadow; the Live
Update's train and walker trackers are the same badges. The bar lies in the
ground below the train, so the train never hides it.

**It moves, never jumps.** The train, the bar, the walker and the stop
rail's marker (below) move from one frame clock, from the expected times, not
the minute tick; a new live time glides in (1 s), a new state morphs (the
parts take their new widths, dots and dashes fading in and out, 600 ms)
while the old train drives out and the next drives in. With less motion asked for,
it all stands still and updates in place
(`decisions/2026-11-24-abeona-journey-header.md`).

Small things, both clients: tapping the train flashes its headlight; tapping
the sun or moon makes it swell for a moment (its accessibility label names
the time of day or the moon's phase). Nothing waits for an animation.

The model (`lib/scene.ts`, `ui/scene/SceneModel.kt`) is tested from
`scene.fixtures.json` on both clients, like the travel day.

---

## 8. The live card

The live card is the journey drawn as one vertical line, in the row grid
(§4): time left, the line with its nodes, the content, the platform boxed
right. Every fact has one row and is shown once on the screen
(`decisions/2026-11-20-abeona-live-journey-line.md`; the design canvas
"Abeona live companion redesign" has every situation drawn). Text is the
minimum needed to understand: no sentence where a word, a number or an
icon does.

```
┌────────────────────────────────────────────┐
│ 06:29 ○  Erlangen               ⌄          │  done (muted): "2 rides done"
│ 10:52 ○  Frankfurt Flughafen               │  where you boarded (muted)
│      ┃  [ICE 16] to Bruxelles-Midi 197 km/h│  band: your train
│      ┃  → Aachen 12:21 · 3 stops ⌄         │
│ 12:47 ◉  GET OFF                    ┌──┐   │  the active step: verb,
│  +4      Liège-Guillemins           │ 3│   │  station, countdown,
│          in 49 min                  └──┘   │  platform
│      ┆  (✓ 15 min)  3 → 3                 │  the change
│ 13:02 ●  [IC 535] to Ostende          [3]  │  the next train
│      │  Later: [ICE 26] [RE 1] · 2 changes │  folded beyond it
│ 13:32 ⚑  Louvain                      [1]  │  destination, always last
│ ⟳ Live 12:37                         (wifi)│  freshness; source button
└────────────────────────────────────────────┘
```

Rows, top to bottom; a row with nothing to say is left out:

| Row | When | Shows |
| :--- | :--- | :--- |
| Done | a ride is done | first departure time and station, muted; "n rides done"; expands to the done rows |
| Boarded | on board | the current ride's departure (time, station), muted |
| Band | always, except arrived or broken | the current ride (the scene strip above it is the card's header, §7): the line chip, "to" its direction and, from the train's Wi-Fi, the speed; under it one left-aligned line: → the next stop with its time and delay (standing at a stop: 📍 it and its departure), then "· n stops ⌄", which opens the ride's stops on its rail: the same dot for each, the rail full burgundy down to a marker where the train is and faded after it, the stops passed muted; waiting for the train, where it is ("Left Aschaffenburg Hbf · 3 stops"); on board an ICE at least 5 min late, DB's delay reason from the train's Wi-Fi as one muted line with an info icon |
| Active step | always, exactly one | the verb (Leave · Board · Get off · Arrive · Arrived), the time with its delay, the station (Leave: none), "in 12 min", the platform big; Board adds the seat ("Coach 24 · Seat 66"), Get off and Arrive the exit side when the train's Wi-Fi gives it ("Exit left") |
| Change | after a Get off, or before a Board at a change | the status chip with the minutes (word only when not safe: "6 min · tight"), "3 → 5" or the walk with its minutes |
| Next train | after the change | time, line chip, "to" direction, platform; the seat under it when known; "n stops ⌄" opens its stops |
| Later | rides beyond the next train | their line chips, "n changes", the worst change's chip unless all are safe; expands to their rows |
| Destination | unless it is the active step | time with delay, name, platform |
| Footer | always | where the times come from and when: "Live 12:37", "Timetable 12:37" (no live data for the trip), "Offline 12:37" (`watch`); the source as an icon button that opens How Abeona knows |

- **Sizes grow as the step comes close:** the active step is the large row;
  at a change the change row and the next train move up as soon as you get
  off. No new element appears anywhere else.
- **One countdown** ("in 12 min"), on the active step.
- **Something broke:** the broken step's row turns `bad` (red node, the verb
  "Missed", "Cancelled" or "Not running", the train, the time struck), a
  filled "Alternatives" button opens the alternatives sheet, and "Any train"
  (`watch`) says the binding is lifted. "Any train" stands once: with what
  broke, else the change at risk, else the destination (on the last ride, the
  Arrive step). The rows after it are dimmed. A
  change at risk is a red change row with an outlined "Alternatives".
- **Notices are rows, not banners:** a re-planned trip says "re-planned" in
  the done row; a connection followed instead adds "Back to the plan" under
  the destination.
- **While the card is live, the trip's own timeline is not shown** below the
  map: the card is the journey. It returns once arrived.
- **Arrived:** the arrival scene (header size), the done row and "Arrived"
  with the time, the delay and the platform. Compensation stays in its own
  card below, and below that the **postcard**: the route and the day, then
  figures as icon + value + label (⇢ "220 km travelled", 🚆 "2 h 29 on
  board", 🍃 "33 kg CO₂ saved", ⏲ "287 km/h top speed", ⚑ "+22 min at the
  end" in `watch` or "On time" in `ok`), a figure left out when it is 0 or
  unknown, and a Share icon button that makes a 1080×1350 image of the
  arrival scene, the route and the figures. Never while something is broken
  (`decisions/2026-11-22-abeona-arrival-postcard.md`).
- **Compact form** (the Trips card on the travel day): the header, the band
  without its second line and the active step, in time order (before boarding the step
  comes first), and one row: the change chip, the next line chip, "⚑ 13:32",
  "Any train" when the binding is lifted. Nothing else, and no banner under
  it.
- **Words only where an icon or a number cannot say it.** No instructions
  the traveller knows already (walking to the platform, waiting for the
  train); the platform box, the countdown and the chips carry the step.
- **The notification** follows the same order: your train and the active step
  as the title, then the change, the next train, the next stop, and the band's
  line (`decisions/2026-11-20-abeona-notification-names-the-train.md`). On
  Android 16 and newer it is a Live Update that draws the journey: one equal
  burgundy segment per ride, the changes as points on their boundaries in
  their confidence colour, a train moving along the ride (a walker on the
  point while changing or walking to the station), a flag at the end. Under
  the title two lines: the change ("Würzburg Hbf → RE 10 · Pl. 5 · 6 min ·
  tight") or the seat before boarding; then the next stop and the speed. How
  fresh only when not live, in the header. Actions: Ticket, Alternatives (only
  when broken), Stop (`decisions/2026-11-24-abeona-journey-header.md`).
- **Alerts wake the traveller only to act** ("Act now": platform change,
  change at risk or impossible, cancelled, missed, get off in 5 min, you may
  take another train). Delays, "back on track" and the compensation thresholds
  are quiet "Updates". One notification per subject: the newer replaces the
  older (`decisions/2026-11-22-abeona-alerts-wake-only-to-act.md`).
- **For everyone:** each row reads as one sentence to a screen reader; the
  scene stands still when the system asks for less motion; nothing clips at
  200 % font.

---

## 9. Screens

Top bar on every screen: title left, at most two icon actions, then ⋮.
Bottom navigation: Trips, Plan, Year, Settings (icons with labels).

| Screen | Order, top to bottom |
| :--- | :--- |
| **Trips** | `Banner`s (BahnCard deadline, update). Next trip: on the travel day its day and title as plain text, then the live card compact; otherwise the next-trip card (scene header; day and title; departure `time` + `PlatformBox`; one line of `Fact`s: ⚑ arrival, 🕓 duration, ⇄ n, chips, ticket). "Upcoming" as dense rows (day + time left, title and line chips, ticket icon right). The year so far as four figures in one row. "Past trips" as a quiet row. Empty: `EmptyState`. |
| **Trip, live** | Top bar: title = "A → B", ticket, refresh, ⋮. `LiveCard` (§8). Map. Then the ticket and the rest, without the timeline (the card is the journey). The screen stays on at the station and while changing. |
| **Trip, not live** | Header: day, departure → arrival as `time`, facts line. `Banner`s. Timeline as `StopRow`s on one continuous rail: a ride is its first and last stop with the line chip between, stops folded behind "n stops"; a change is one row (chip, platforms). Ticket card (fare, class, price, travellers as `Fact`s; "Show ticket"). After the ride: compensation, the postcard (§8), what happened. Ride rows show the seat when known. |
| **Trip, tray-table mode** | When the window is at least 600 dp wide (tablets, foldables) or a phone lies in landscape (height under 480): two panes. Left the header, the live card or the timeline and everything else; right the map, the full height, standing still while the left scrolls. For a phone on the tray table or mounted on a wheelchair. |
| **Alternatives** | Sheet. Each connection: departure → arrival, 🕓, ⇄ n, line chips, validity chip (`ok` / `watch` / `bad`). The hint once, under the list. |
| **Ticket** | White, full brightness. The ticket. Cards the ticket needs as tabs. DB Navigator in the top bar. The rides' seats. In landscape the code large on the left, the details on the right. |
| **Plan (Search)** | From, to (with swap), when, one primary button. "Add from a ticket or share" as a quiet row. Results: one card per connection in the row grid: departure → arrival `title`, facts line, chips; earlier / later at the ends. |
| **Save sheet** | Route and times, ticket fields as compact rows, travellers and BahnCards, attach ticket, Save. |
| **Year** | A pager of story cards: scene or chart on top, one `numeral`, one line under it (among them "Top speed": "287 km/h", the train and the day). Claims bar under the pager. |
| **Past trips** | Dense rows by month: day, title, delay as `StatusChip`. |
| **Claims** | Rows: trip, amount, status chip, deadline `Fact`. |
| **BahnCards** | Cards as rows: name, class, valid-to, deadline chip. Sheet to edit. |
| **Settings** | Groups in the order of the moments: Before the trip (time to the station, reminder), On the day (companion, location, alerts, get off reminder), Afterwards (claims reminder), Household (members, BahnCards), App (theme, update, account). One line per setting: name, value or switch; the explanation behind an info tap only where it is not obvious. |
| **Status page** (web, shared link) | Scene header, who and where, arrival `time`, the rail. No navigation. |
| **Widget** | The day ("Today", "Tomorrow", "Sun 22 Nov"), or the countdown while the companion runs and refreshes it; departure `time` + platform, title, ⚑ arrival ⇄ n. |
| **Notification** | On board: the train, when it arrives, the platform and the time as the title; then where to change, the next train and the change (its state only when not safe), the next stop, and last the stops left, the speed and how fresh (`decisions/2026-11-20-abeona-notification-names-the-train.md`); on Android 16+ the journey as a bar with the title, one line and the header (§8). Actions Ticket, Alternatives when broken, Stop. The accent colour is burgundy. |
