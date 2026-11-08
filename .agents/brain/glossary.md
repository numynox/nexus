# Glossary

The nouns of the three domains, as they appear in code and in the UI. All UI is
English only (`<html lang="en">`); the German text you will find in
`supabase/seed.sql` and in Tankerkoenig responses is *data*, not a translation
layer. Do not add a second language without an ADR.

Read this before naming a table, a column, or a label. Several of these words
mean something narrower than they look.

---

## Cross-app

| Code | UI | Notes |
| :--- | :--- | :--- |
| `profiles` | — | one row per `auth.users` row, shared by all three apps; holds `full_name` and `email` only |
| `session` | — | the Supabase session; the Svelte store every app gates its UI on |
| `base_url` / `getBaseUrl()` | — | the app's GitHub Pages prefix (`/nexus/<app>`); every internal link is built from it |

## Noctua (RSS reader)

| Code | UI | Notes |
| :--- | :--- | :--- |
| `feeds` | Feed | an RSS source. Global: the catalogue is shared by all users. `filter` holds per-feed keyword exclusions applied at fetch time |
| `articles` | Article | one entry from a feed, deduplicated on `(feed_id, url)`. `summary` is the feed's own description — **not** a generated summary. Visible only while one of *your* sections contains its feed, unlike the feed itself |
| `sections` | Section | a user's named, ordered grouping of feeds — the personal arrangement of the shared catalogue. `icon` is a key from `SECTION_ICONS`, not an emoji. Sections have no colour — see `decisions/2026-09-06-sections-have-no-colour.md` |
| `sections_feeds` | — | join table, carries the feed's `sort_order` within a section |
| `article_reads` | — | **read**: durable, per-user, in the database; drives statistics |
| `noctua_seen_articles` (localStorage) | — | **seen**: device-local, disposable; drives "hide what I've already looked at" |
| `article_stars` | Starred | **intent**, not history: what you meant to come back to. `/read` is where you have been, `/starred` is what you saved |
| `noctua_recommendations` | Recommend / Recommended by … | one reader marking an article for **everyone** — the one deliberately shared thing in Noctua. Pinned above the days ("Recommended") for the others, once per device, then just highlighted. The request called it "notify"; there is no push notification (`decisions/2026-10-31-noctua-recommendations.md`) |
| — | Library | Android only: the starred and read lists as one tab with two shelves; the web keeps `/starred` and `/read` |
| end of the feed (`EndOfFeed`, the end panel) | Nothing new / That's everything | the panel after the last article, where pulling up refreshes. "Nothing new" when no article is left to show, "That's everything" below a list. The owl lives here (`decisions/2026-10-27-the-owl-at-the-end-of-the-feed.md`) |

"Read" and "seen" are two different things tracked in two different places.
Clearing seen history does not touch statistics. On Android "seen" lives in
the app's DataStore instead of `localStorage` — still per device.

## Vibilia (fuel and vehicle costs)

| Code | UI | Notes |
| :--- | :--- | :--- |
| `cars` | Vehicle | the sidebar and routes say "Vehicle"; the tables, columns and RPCs all say `car`. Keep the split: `car_*` in SQL, "Vehicle" in labels |
| `car_access` | Members / Sharing | grants a non-owner full access to a car; owner plus these rows are what `list_car_members` returns |
| `refuel_events` | Refuel | one fill-up: mileage, litres, total, price per litre, station, plus `is_full_refuel` and `missed_previous_refuel`. `missed_previous_refuel` means a fill went unrecorded before this one, so the statistics RPCs return `null` consumption for that interval instead of a wrong number |
| `car_expenses` | Expense | non-fuel cost against a vehicle |
| `fuel_stations` | Station | a Tankerkoenig station. Its `id` **is** the Tankerkoenig id, not ours |
| `fuel_prices` | Price | one price reading for one fuel type at one station at one `checked_at`. Only the last 21 days are kept |
| `fuel_prices_daily` | — | min/max/avg per station, fuel type and **Europe/Berlin day**; the long-term history behind the "weekly market low" line once raw readings are pruned |
| `fuel_type` | E5 / E10 / Diesel | stored capitalised; the Tankerkoenig API wants lowercase — mapped inside the Edge Function |
| `opening_times`, `overrides`, `whole_day` | Open now | Tankerkoenig's opening-hours shape, stored raw as JSONB and interpreted in SQL |
| fuel level estimate | Fuel Level | derived, never stored (`get_car_fuel_level_estimate`): last refuel minus driving hours × litres per hour (`drive`), or minus days × litres per day (`time`) |
| `vibilia_car_bluetooth_devices` | Bluetooth | the car's head-unit address; linked in the Android app, per car not per phone |
| `vibilia_drive_intervals` | — | one phone's report of one connection: **interval**. Raw, overlapping between phones |
| drive period | Drive | intervals merged across phones (`get_car_drive_periods`) — what the UI calls a drive |
| leg | — | the stretch between two consecutive refuels; **tracked** when drive tracking was already running at its start |
| drive model | Driving | per car: km per drive from an assumed average speed by drive duration, scaled by tracked legs; litres at the car's L/100 km (`get_car_drive_model`) |
| `vibilia_commutes` | Commute | one person's commute in one car: weekdays, a window each way, home and work with a radius. Private to that person |
| `to_work` / `to_home` | To work / Home | the two directions of a commute, in columns (`to_work_from`), the RPC's `direction` and the Android code (`CommuteDirection`) |
| expected travel time | Travel time | median of your own drives that started in the window (`get_vibilia_commute_expectation`) |
| `sms_number`, `sms_to_work`, `sms_to_home` | Text someone | one contact the phone texts the arrival time to, on the chosen ways |
| announce commute | Say the arrival time in the car | device-local switch; the phone speaks the arrival time through the car (Android only) |
| track drives | Record drives on this phone | device-local switch; without it the phone still remembers the connected car for the refuel form |

## Annona (food expiration)

| Code | UI | Notes |
| :--- | :--- | :--- |
| `annona_products` | Product | the *kind* of thing — "Barilla Farfalle 200g". Identified by `ean` where known |
| `annona_items` | Item | one physical package of a product, with its own `expiration_date` and location. Products are catalogue; items are stock |
| `is_consumed` | Consume | items are never deleted when used up, they are flagged — so the log survives |
| `annona_storage_locations` | Location | Fridge, Pantry, Freezer… |
| `annona_categories` | Category | shared list, seeded from `DEFAULT_CATEGORIES` on first login; each has a `color` and `icon` key resolved through `lib/categoryMeta.ts` |
| `annona_item_log` | Activity / Comments | append-only trail per item (`created`, `consumed`, `expiration_changed`, `location_changed`, `comment`, …) with the acting `user_id` — the only record of *who* in a shared database |
| `ean` | Barcode | scanned with the camera; the scan path is Product-first: find by EAN, else look it up in Open Food Facts, then create the product and add an item |
| `energy_kcal_100g` … `salt_100g` | Nutrition | the EU nutrition declaration per 100 g/ml, as printed on the packet |
| `nutrition_source` | — | `openfoodfacts` when the values came from a lookup; NULL when typed by hand |
| — | Pantry | Android's home tab: the dashboard figures, then everything in stock grouped Expired / This week / This month / Later / No date |
| `annona_storage_locations` (Android) | Places | the Android tab and settings group for locations; the rows are the same "Location" |
| reminders | Remind me before things expire | Android only, device-local: a daily notification of items expiring within 1–14 days |

Products, categories and locations in Annona are **shared by every user**, not
owned. There is no "my products".

## Abeona (train rides)

| Code | UI | Notes |
| :--- | :--- | :--- |
| `abeona_households` | Household | the people who see each other's trips. Bounded, unlike Annona's everyone-sees-everything; one per user, made on first use |
| `abeona_household_members` | Members | accounts in a household. Inviting someone who is alone in their own household **merges** it in |
| `abeona_travellers` | Traveller | a person who rides — a member, or someone without an account (a child). BahnCards belong to travellers, not to accounts |
| `abeona_trip_travellers` | Travellers (on a trip) | who rides on a trip. A new trip gets its creator's own traveller unless the save says otherwise |
| `abeona_bahncards` | BahnCard | DB's discount card (25, 50) or flat rate (100), per traveller, with class, validity and an optional card file. Shown as "BahnCard 50 · 2nd class". Only the digital card **inside DB Navigator** counts as proof; Abeona's copy is a reference, so "Open in DB Navigator" always sits next to it |
| `subscription` | Renews automatically | the card renews unless cancelled before its deadline. Turned off once it is cancelled, which also silences the reminder |
| `notice_weeks` | Notice period | weeks before `valid_to` by which a subscription must be cancelled. Default 4 (DB shortened it from 6); editable per card |
| cancellation deadline | Cancel by … | derived: `valid_to` − notice period (`abeona_bahncard_deadlines`). The Trips banner (both clients) and the Android reminder look 14 days ahead |
| `bahncard_discount` | BahnCard discount | per traveller on a trip (`abeona_trip_travellers`): the discount (25, 50) their share of the fare was bought with; one ticket can mix them ("1 BC25, 1 BC50"). `abeona_trips.bahncard_discount` only keeps the one everybody shares, for older APKs. A BahnCard 100 is the fare type `bahncard100`, not a discount |
| trip warning | Check the BahnCards | derived, never stored (`abeona_trip_warnings`): a traveller with a discount has no card of that kind, or it ends before / starts after the trip, or is 2nd class on a 1st-class ticket. A BahnCard 50 covers a 25 fare. The message is written in SQL and shown as is by both clients |
| `abeona_trips` | Trip | one journey from origin to destination, with the ticket facts the app knows (`fare_type`, `train_binding`, class, price) and the stored ticket file |
| `abeona_legs` | Leg / ride | one train, bus or walk within a trip. `planned_*` never change after saving; `departure`, `arrival` and the current platforms are the live values. Not Vibilia's "leg" between refuels |
| stopover | Stops | an intermediate stop of a leg, stored as JSON on the leg |
| transfer confidence | Safe / Tight / At risk | derived, never stored: minutes available for a change against minutes needed (same platform 2, other platform 5, walk + 3). Computed identically in both clients |
| train binding lifted | You may take another train | derived: a bound ticket (Sparpreis and similar) at ≥ 20 min expected delay at the destination. Never for regional tickets, the Deutschlandticket or a BahnCard 100 |
| `fare_type` | Ticket | `flex`, `saver`, `regional`, `deutschlandticket`, `bahncard100`, `other` |
| ticket wallet | Ticket | the stored ticket file shown full-screen at full brightness; next to it always the "Open DB Navigator" button, because some proofs are only valid in DB's app |
| live companion | Live companion | Android only: a foreground service during a trip that refreshes every minute and keeps an ongoing notification (a Live Update on Android 16+), with alerts. Starts by itself (setting on by default) at the time to the station plus 15 min before departure, at least 30, or when a trip under way is opened; stopped by hand, it stays off for that trip. "Start live companion" shows only when it will not start by itself, on the travel day (from the evening before for departures before 03:00); otherwise small print says when it starts. With the setting on, it also follows the phone's position while it runs |
| live dashboard | (the trip page while live) | both clients: the travel day's one thing to do with a big countdown, then the ride (next stop, stops left), the map with the traveller on it, the next change and the journey. The first card's source pill opens "How Abeona knows" |
| ride progress | Next stop · n stops left | derived, never stored (`progress.ts`, `domain/RideProgress.kt`): a ride's stops (cancelled stopovers left out), the one the train stands at or heads for, stops left up to and including where the ride ends, and where the timetable puts the train on its line (the hollow dot on the map) |
| evidence | How Abeona knows | behind the source pill (Train Wi-Fi · Location · Timetable) on the dashboard's first card: what each signal says right now: Train Wi-Fi, Location, Timetable; the one that placed the traveller "Decides". The web has no train Wi-Fi, and its location is the browser's, only while the page is open and asked for (`abeona_trip_location`) |
| travel day | (the hero card) | derived, never stored (`travelday.ts`, `domain/TravelDay.kt`): where the traveller is on a live trip. States `before_leaving` "Time to leave", `at_station` "Head to the platform", `on_board` "On board", `changing` "Change trains", `arrived` "Arrived". One hero card per state on both clients and in the live notification |
| time to the station | Time to the station | per device (web `abeona_station_minutes`, Android DataStore), default 15 min: door to platform. The **time to leave** is the first ride's expected departure minus it |
| time to leave reminder | Time to leave reminder | Android only, device-local, off by default: one notification at the time to leave, moved if the train is late |
| signals | (none; the copy says "Looks like …") | Android only, never stored: the train's Wi-Fi (**on-board Wi-Fi**: ICE Portal, ÖBB Railnet; names the train, gives speed and next stop) and the phone's position. They confirm or correct the travel day; "missed" means a signal says the traveller is not on the ride the clock expects |
| alerts | Alerts | Android, device-local: delay from X min (default 5, again for each further X), connection at risk or missed, platform change, cancellation. The binding-lifted alert is always on |
| alternatives | Alternatives | connections from the current train's next stop or the current station to the destination (`abeona-transit` action `alternatives`), ranked by arrival then changes. Offered by themselves when a change is at risk or missed or a train cancelled, and on demand |
| `abeona_fare_rules` | (the hint under the alternatives) | per fare type: long-distance covered, products never covered, train-bound, binding liftable, and an English hint ending in "check the terms". Reference data, read-only through the API |
| validity | Covered by your ticket / Check the terms / Needs another ticket | an alternative's mark (`valid`, `check`, `invalid`) from `abeona_allowed_products` and the rules, with a note in English |
| year in review | Year in review | the statistics of one calendar year as story cards (`abeona_statistics`) |
| final | Final times recorded | a leg whose live values the snapshot job (`abeona-refresh-trips`) froze 15–90 min after arrival (`abeona_legs.final`); the trip's `finalized_at` is set once every ride is final. Nothing overwrites final values |
| real arrival | arrived … | derived (`abeona_trip_arrival`): the recorded replacement's arrival, else the final, live or planned one. The statistics and the compensation read it |
| missed connection | What happened · Record a missed connection | `missed_leg_idx` (the ride missed or cancelled) and `replacement_legs` (the trains taken instead, picked from the connections at that stop or entered by hand) on the trip |
| `force_majeure` | Caused by a storm, flooding or a person on the track | ticked by the traveller; excludes compensation |
| compensation | Compensation | derived (`abeona_compensation`) from `abeona_compensation_rules`: verdict `eligible` "… to claim", `below_minimum` "under DB's €4 minimum", `unknown_price` "add the fare", `unknown_arrival` "When did you arrive?", `not_eligible` "No compensation"; with a German text for DB's form ("Copy for the form") and the claim deadline |
| `abeona_compensation_rules` | (the reasons and hints on the card) | passenger-rights figures as rows: tiers (60 min 25 %, 120 min 50 %), flat rates (Deutschlandticket, BahnCard 100), the €4 minimum, the three-month deadline, the night taxi, force majeure. Reference data; verify against fahrgastrechte.info |
| `abeona_claims` | Your claim / Claims | one per trip: `open` "Open" / "Not claimed yet", `claimed`, `paid`, `rejected`. "Earned back" is what was paid in a year |
| claim reminder | Remind me to claim | Android only, device-local, off by default: the day after a late trip and 28, 7 and 1 days before the deadline |
| past trip | Save past trip · A past trip | a trip saved after it happened (`source = 'import'`): from the search for a past date, Träwelling or the CSV script. Final from the start, never refreshed; the delay is typed by hand |
| `from_country` / `to_country` | Countries | ISO code of a ride's ends, from the stop's time zone or id. The year in review counts them |
| CO₂ saved | Footprint · kg CO₂ saved against the car | derived (`abeona_statistics`): km × (car − rail or bus) per passenger-km, one traveller, from `abeona_co2_factors` (UBA, approximate) |
| travel map | Where you went | derived (`abeona_travel_map`): one line per pair of stations ridden in a year, with a count |
| `abeona_share_links` | Share · Live status · Calendar | a token link to one trip, `status` (a page for whoever picks you up, until six hours after arrival) or `ics` (a calendar that follows delays, 30 days past the trip). Revoke deletes it. Opened by `abeona-public` |
| Träwelling import | Import from Träwelling | Settings, both clients: a personal access token pasted once, check-ins read for a date range and grouped into trips, the chosen ones saved as past trips. The token is not kept |
| share text | Read from DB Navigator | what DB Navigator's "Teilen" gives: route, date, then per ride the line, "Nach …", "Ab"/"An" with time, station and platform, and a bahn.de link whose `vbid` is kept only as a reference (`intake/shareText.ts`, `ShareText.kt`) |
| ticket intake | Add from a ticket or share · Read from your ticket | a DB ticket PDF's text read on the device (pdf.js, PdfBox), never on a server: fare, class, travellers, BahnCard, binding, rides, price. Saved as `source = 'share'` or `'pdf'`; the PDF goes into the wallet (`decisions/2026-11-04-abeona-intake.md`) |
| matching | Found in the timetable · n of m rides found | parsed rides looked up through `abeona-transit`, whole journey first, then ride by ride; a ride not found is saved as printed, without live updates |
