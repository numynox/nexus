# ADR: What Abeona's companion takes from other travel apps, and what it does not

**Date:** 2026-11-22
**Status:** Accepted

## Context
In November 2026 we studied how about 40 apps present a journey while it
happens:
- **Germany:** DB Navigator, ICE Portal, bahn.expert, Railwise, Träwelling, MVGO.
- **Rest of Europe:** SBB, NS, SJ, Vy, DSB, ÖBB, Trainline, LNER, Realtime
  Trains, SNCF Connect.
- **Japan and Korea:** JR East, Yahoo! Norikae, NAVITIME, Naver, KakaoMap,
  Korail.
- **North America:** Transit, Amtrak.
- **Map apps:** Citymapper, Google Maps, Apple Maps.
- **Adjacent domains:** Flighty, Uber, United.

We set them against traveller research:
- Grotenhuis 2007;
- Kay et al., CHI 2016;
- UK Department for Transport 2021;
- Transport Focus 2026;
- the Android and iOS guidance for live surfaces.

The study confirmed what Abeona already did well:
- one next action with one countdown;
- fields only while they matter;
- graded connection risk;
- data freshness shown;
- checking the ticket's validity on alternatives, which no other app does;
- the binding lifted as an active "Any train";
- compensation worked out on arrival;
- a live share link.

It also showed what is easy to copy and wrong for Abeona.

## Decision
Adopted, each in its own record:
- **Alerts:** wake only to act
  (`2026-11-22-abeona-alerts-wake-only-to-act.md`).
- **Lock screen:** draws the journey
  (`2026-11-22-abeona-lock-screen-journey-line.md`).
- **Seats:** the coach and seat from the ticket
  (`2026-11-22-abeona-coach-and-seat-from-the-ticket.md`).
- **Postcard:** the arrival postcard (`2026-11-22-abeona-arrival-postcard.md`).
- **Tray-table mode, after SJ:** two panes when the phone lies sideways on the
  tray table or is mounted on a wheelchair, and the ticket in landscape.
  Described in `abeona-design.md` §9.
- **The train's Wi-Fi:** the official delay reason (ICE Portal) and the exit
  side (ÖBB Railnet), where it gives them.
- **Inclusion:** one sentence per journey row for screen readers, a still
  scene under reduced motion, and nothing clipped at 200 % font.

Rejected:
- **Map first.** SBB's 2026 map-first redesign (rated 3.1★, "a massive step
  back"), Rejseplanen's and National Rail's all met commuter backlash. The map
  stays below the card.
- **Coach order, platform sectors and crowding.** There is no reliable,
  permitted source (see the seat record). Crowding forecasts also confuse, as
  DB's grey/grey/orange icon shows.
- **Delay causes beyond the ICE Portal, and Abeona's own delay predictions.**
  Transitous has no service messages for German trains, and a prediction
  without a model is a guess.
- **AI chat, entertainment, ordering food.** That is what the trains' own
  portals are for.
- **Points, leaderboards and public check-ins.** They expose movement
  patterns, and a household app has no audience for them.
- **Playful copy near a disruption.**
- **Web push and a watch app**, for now. Push needs server-side alert logic
  duplicated from the Android companion. Live Updates reach Wear OS 7 watches
  by themselves.

## Consequences
The next feature request that copies another app should be checked against
the rejected list first. The reasons above still hold unless the data or the
platforms change.
