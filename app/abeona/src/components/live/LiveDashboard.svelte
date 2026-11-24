<script lang="ts">
  /**
   * The live card of a travel day (abeona-design.md §8) — the web's copy of
   * Android's ui/live/LiveDashboard.kt: the journey drawn as one vertical
   * line in the row grid. Done rides fold into one row; the ride in focus is
   * the band (the scene strip on its stretch of line, its train, where it
   * is); the next thing to do is the one large row with a verb; the rest
   * runs down to the destination, which is always last. Words only where
   * nothing shorter works (decisions/2026-11-20-abeona-live-journey-line.md).
   * The scene is the card's header: its bar under the track is the whole
   * journey, moving by the frame clock. A ride's stops between its ends are
   * there on request: "5 stops" under the band, the next train and the later
   * ones opens them on its line, where on board the line fills up to a
   * marker at the train, by the same clock.
   * How Abeona knows sits behind the footer's source button. The map with
   * the traveller on it is the trip page's (TripPanel), so that it can stand
   * beside the card in tray-table mode.
   *
   * To a screen reader each row of the line is one sentence, made of the
   * words the row shows ("Board ICE 25 to Wien Hbf at Frankfurt(Main)Hbf,
   * platform 8, 14:21, 7 minutes late, in 12 minutes. Coach 24, seat 66.");
   * its drawing is hidden, its buttons are not.
   */
  import {
    ArrowRight,
    ArrowRightLeft,
    Ban,
    ChevronDown,
    CircleAlert,
    CircleCheck,
    Clock,
    CloudOff,
    DoorOpen,
    Flag,
    Footprints,
    Gauge,
    LocateFixed,
    LogOut,
    MapPin,
    RefreshCw,
    Shuffle,
    TrainFront,
    Wifi,
  } from "lucide-svelte";
  import { untrack } from "svelte";
  import type { Approach, Leg, StopTime, Stopover } from "../../lib/transit";
  import { fetchApproach, type Trip } from "../../lib/data";
  import { formatDuration, formatTime, legLabel } from "../../lib/format";
  import { seatSpoken } from "../../lib/intake/seat";
  import { distanceMetres, distanceToLineMetres, NEAR_STOP_METRES, ON_LINE_METRES, type Position } from "../../lib/travelday";
  import type { LocationState } from "../../lib/position";
  import { frameClock } from "../../lib/frameClock.svelte";
  import { rideProgress, type ProgressStop } from "../../lib/progress";
  import { trainKind, type BarJourney, type BarRide } from "../../lib/scene";
  import { delayMinutes, finalArrivalDelay, normalisePlatform, trainBindingLifted, transfers, type Transfer } from "../../lib/transfer";
  import type { AlternativesReason, TravelDay } from "../../lib/travelday";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import Fact from "../common/Fact.svelte";
  import type { IconComponent } from "../common/icon";
  import LegChip from "../common/LegChip.svelte";
  import PlatformBox from "../common/PlatformBox.svelte";
  import Scene from "../common/Scene.svelte";
  import Seat from "../common/Seat.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import StatusChip from "../common/StatusChip.svelte";
  import StopRow from "../common/StopRow.svelte";
  import StopsToggle from "../common/StopsToggle.svelte";

  interface Props {
    trip: Trip;
    day: TravelDay;
    now: number;
    stationMinutes: number;
    reason?: AlternativesReason | null;
    position?: Position | null;
    location?: LocationState;
    onLocation?: (on: boolean) => void;
    onAlternatives?: () => void;
    settingsHref?: string;
    /** How fresh the live values are, for the footer: "Live 12:37", "Offline 12:37". */
    status?: { text: string; label: string; kind: "live" | "timetable" | "offline" | "done" } | null;
    /** The ticket's train binding is lifted (the caller works it out as the server does): "Any train". */
    bindingLifted?: boolean;
    /** A connection followed instead of the plan: "Back to the plan" under the destination. */
    onBackToPlan?: () => void;
    busy?: boolean;
    /**
     * The Trips screen's compact form: the band without its second line, the
     * active step and one row (the next change, the next train, the arrival);
     * no done row, footer or map. Android's LiveCard(summary = true).
     */
    summary?: boolean;
  }

  let {
    trip,
    day,
    now,
    stationMinutes,
    reason = null,
    position = null,
    location = "off",
    onLocation = () => {},
    onAlternatives,
    settingsHref,
    status = null,
    bindingLifted = false,
    onBackToPlan,
    busy = false,
    summary = false,
  }: Props = $props();

  // ------------------------------------------------------------ the journey

  const legs = $derived(trip.legs);
  const rides = $derived(legs.map((l, i) => ({ l, i })).filter(({ l }) => l.mode !== "walk"));
  const lastRideIdx = $derived(rides.length > 0 ? rides[rides.length - 1].i : null);
  const lastRide = $derived(lastRideIdx === null ? null : legs[lastRideIdx]);
  const allTransfers = $derived(transfers(legs));
  const changeInto = (i: number): Transfer | null => allTransfers.find((t) => t.toIndex === i) ?? null;

  const arrived = $derived(day.kind === "arrived" || day.legIndex === null);
  const onBoard = $derived(day.kind === "on_board");
  /** The ride the card is about: the one to catch, or the one on board. */
  const focusIdx = $derived(arrived ? null : day.legIndex);
  const leg = $derived(focusIdx === null ? null : legs[focusIdx]);
  const progress = $derived(leg ? rideProgress(leg, now) : null);
  const doneRides = $derived(
    arrived ? rides : focusIdx !== null && (onBoard || day.kind === "changing") ? rides.filter((r) => r.i < focusIdx) : [],
  );
  const aheadRides = $derived(focusIdx === null ? [] : rides.filter((r) => r.i > focusIdx));
  const replanned = $derived(trip.missed_leg_idx !== null && (trip.replacement_legs?.length ?? 0) > 0);

  type Broken = "Missed" | "Cancelled" | "Not running";
  /** What broke about ride i, in a word: missed (by the device's signals), cancelled, or no longer in the timetable. */
  function brokenOf(i: number): Broken | null {
    if (day.missedChange) {
      if (!onBoard && i === focusIdx) return "Missed";
      // Still on board after the connection left: the next train is the one missed.
      if (onBoard && i === aheadRides[0]?.i) return "Missed";
    }
    const l = legs[i];
    if (l.cancelled) return l.dropped_at ? "Not running" : "Cancelled";
    return null;
  }
  const focusBroken = $derived(focusIdx === null ? null : brokenOf(focusIdx));
  const brokenAhead = $derived(aheadRides.find((r) => brokenOf(r.i) !== null)?.i ?? null);
  /** The first change still ahead that is at risk or impossible, when nothing broke outright. */
  const riskyChange = $derived.by((): Transfer | null => {
    if (focusIdx === null || focusBroken || brokenAhead !== null) return null;
    return (
      allTransfers.find(
        (t) => (onBoard ? t.fromIndex >= focusIdx : t.toIndex >= focusIdx) && (t.confidence === "at_risk" || t.confidence === "impossible"),
      ) ?? null
    );
  });
  /** "Any train": the binding is lifted, by the caller's reckoning or because a train does not run. */
  const lifted = $derived(
    bindingLifted ||
      ([focusIdx, brokenAhead].some((i) => i !== null && legs[i].cancelled) &&
        trainBindingLifted(trip.fare_type, trip.train_binding, finalArrivalDelay(legs), true)),
  );

  // ------------------------------------------------------------ the band

  /** While waiting for the train: where it is on its way here, asked every minute. */
  const waitingFor = $derived(
    !focusBroken && leg && !leg.cancelled && leg.trip_ref && (day.kind === "at_station" || day.kind === "changing") ? leg : null,
  );
  const waitingKey = $derived(waitingFor ? `${waitingFor.trip_ref}|${waitingFor.from.id ?? waitingFor.from.name}` : null);
  let approach: Approach | null = $state(null);
  $effect(() => {
    const key = waitingKey;
    approach = null;
    const target = untrack(() => waitingFor);
    if (!key || !target || summary) return;
    let stopped = false;
    const load = () =>
      fetchApproach(target)
        .then((found) => {
          if (!stopped) approach = found;
        })
        .catch(() => {});
    load();
    const timer = setInterval(load, 60_000);
    return () => {
      stopped = true;
      clearInterval(timer);
    };
  });

  /** "Train left Aschaffenburg Hbf · 3 stops away", as Android says it. */
  function approachText(a: Approach): string {
    return `${a.state === "at" ? "Train is at" : "Train left"} ${a.stop} · ${a.stops_away} ${a.stops_away === 1 ? "stop" : "stops"} away`;
  }

  /** The scene: the land passes on board, the signal is red when the step broke, the flag is out on arrival. */
  const sceneState = $derived(arrived ? "arrived" : focusBroken ? "stopped" : onBoard ? "riding" : "waiting");
  const sceneRide = $derived(leg ?? lastRide);
  const sceneTrain = $derived(sceneRide ? trainKind(sceneRide.product, legLabel(sceneRide), sceneRide.mode === "bus") : undefined);
  /** Where the sky is reckoned: the device, else the stop the step is about. */
  const skyAt = $derived(position ?? (onBoard ? leg?.to : leg?.from) ?? lastRide?.to ?? null);
  const speed = $derived(onBoard && position && position.speedKmh !== null && position.speedKmh >= 1 ? Math.round(position.speedKmh) : null);

  const ms = (value: string | null | undefined) => (value ? Date.parse(value) : NaN);
  /** The expected time a stop between a ride's ends is reached, as rideProgress takes it: its arrival, else its departure. */
  const reachedAt = (s: Stopover) => [ms(s.arrival), ms(s.planned_arrival), ms(s.departure), ms(s.planned_departure)].find((t) => !Number.isNaN(t)) ?? NaN;
  /** The stops a ride serves, by their index among its stopovers: the ones rideProgress counts (not cancelled, with a time). */
  const served = (l: Leg) => l.stopovers.flatMap((s, k) => (!s.cancelled && !Number.isNaN(reachedAt(s)) ? [k] : []));

  /**
   * The header's bar: every ride's expected times, the ride in focus and
   * what the traveller is doing; the scene works out the rest on every frame.
   * None when the step broke (the signal is red) or a ride has no time.
   */
  const barJourney = $derived.by((): BarJourney | null => {
    if (arrived || focusIdx === null || focusBroken) return null;
    const list: BarRide[] = [];
    for (const { l } of rides) {
      const departure = ms(l.from.current ?? l.from.planned);
      const arrival = ms(l.to.current ?? l.to.planned);
      if (Number.isNaN(departure) || Number.isNaN(arrival)) return null;
      list.push({ departure, arrival, stops: served(l).map((k) => reachedAt(l.stopovers[k])) });
    }
    const current = rides.findIndex((r) => r.i === focusIdx);
    if (current < 0) return null;
    const t = day.kind === "changing" ? changeInto(focusIdx) : null;
    return {
      rides: list,
      current,
      state: day.kind,
      confidence: t && !legs[t.fromIndex].cancelled && !legs[t.toIndex].cancelled ? t.confidence : null,
    };
  });

  /**
   * On board, in the band: the stop the train stands at (and when it leaves
   * there), else the next one it comes to (and when). Nothing when that is
   * where the ride ends: the step below says so.
   */
  const here = $derived.by((): { at: boolean; stop: Stopover } | null => {
    if (!onBoard || !progress || !leg || focusBroken) return null;
    const ks = served(leg);
    const last = progress.stops.length - 1;
    if (ks.length !== last - 1) return null;
    const inside = (i: number | null): i is number => i !== null && i >= 1 && i < last;
    if (inside(progress.current)) return { at: true, stop: leg.stopovers[ks[progress.current - 1]] };
    if (inside(progress.next)) return { at: false, stop: leg.stopovers[ks[progress.next - 1]] };
    return null;
  });

  // ------------------------------------------------------------ the rows

  /** The line below a row (above it, the one of the row before): this ride, a ride ahead, done, on foot between trains. */
  type Seg = "cur" | "ahead" | "plain" | "past" | "walk" | "bad" | "none";
  type NodeKind = "plain" | "done" | "through" | "active" | "bad" | "flag";

  interface Step {
    verb: string;
    icon: IconComponent;
    bad: boolean;
    /** The one large row; a broken ride further ahead is a small one. */
    large: boolean;
    time: { planned: string | null; current: string | null; cancelled: boolean } | null;
    node: NodeKind;
    /** Leave: none; broken: the train ("IC 535 to Ostende"). */
    title: string | null;
    /** "in 12 min", on the active step only. */
    countdown: string | null;
    platform: StopTime | null;
    leave: boolean;
    /** A broken step: where it leaves from. */
    station: string | null;
    /** The Alternatives button and "Any train" go here (when the anchor says so). */
    actions: boolean;
    /** Board: the seat the ticket reserves on that train (not on the Trips card). */
    seat: { coach: string | null; seats: string | null } | null;
    /** The row as one sentence, for screen readers. */
    spoken: string;
  }

  type Row =
    | { kind: "done"; seg: Seg }
    | { kind: "doneRide"; index: number; first: boolean; seg: Seg }
    | { kind: "stop"; stop: StopTime; name: string; node: NodeKind; muted: boolean; platform: boolean; seg: Seg; boarded?: boolean }
    /** One of a ride's stops between its ends, opened from its "n stops"; on board the ones left behind are done (muted). */
    | { kind: "stopover"; stop: Stopover; done: boolean; seg: Seg; rail?: string }
    | { kind: "band"; seg: Seg }
    | { kind: "step"; step: Step; seg: Seg }
    | { kind: "change"; t: Transfer; seg: Seg }
    | { kind: "train"; index: number; seg: Seg }
    | { kind: "later"; indices: number[]; seg: Seg }
    | { kind: "destination"; seg: Seg };

  /** The stretch of the ride in focus: burgundy, red when it does not run, plain when it left without you. */
  const rideSeg = $derived<Seg>(focusBroken === "Missed" ? "plain" : focusBroken ? "bad" : "cur");

  let doneOpen = $state(false);
  let laterOpen = $state(false);
  /**
   * The rides whose stops are open, by index: closed at first, kept through
   * the minute's tick and the refreshes (another trip is another page).
   */
  let stopsOpen = $state<ReadonlySet<number>>(new Set());

  function toggleStops(i: number) {
    const next = new Set(stopsOpen);
    if (!next.delete(i)) next.add(i);
    stopsOpen = next;
  }

  /**
   * Ride i's stops between its ends, for the rows. On board the ones the
   * train has left are done (muted): up to the last one whose expected
   * departure is behind; the one it stands at is not. A cancelled stop goes
   * with the ones around it. Each stop's dot carries its index, for the
   * marker on the rail.
   */
  function stopoverRows(i: number, line: Seg): Extract<Row, { kind: "stopover" }>[] {
    const stops = legs[i].stopovers ?? [];
    const left =
      onBoard && i === focusIdx
        ? stops.findLastIndex((s) => {
            const at = [ms(s.departure), ms(s.planned_departure), ms(s.arrival), ms(s.planned_arrival)].find((t) => !Number.isNaN(t));
            return !s.cancelled && at !== undefined && now >= at;
          })
        : -1;
    return stops.map((stop, k) => ({ kind: "stopover", stop, done: k <= left, seg: line, rail: i === focusIdx && onBoard ? String(k) : undefined }));
  }

  // ------------------------------------------------------------ the train on the band's rail

  let journeyList: HTMLDivElement | undefined = $state();
  /** On board with the ride's stops open, the line fills up to a marker where the train is. */
  const railShown = $derived(!summary && onBoard && !focusBroken && focusIdx !== null && stopsOpen.has(focusIdx));
  /** Where the ride's stops sit on the line, in px within the list: its start, each stop between (by index), its end. */
  let railAt = $state<{ x: number; start: number; end: number; stops: (number | null)[] } | null>(null);

  $effect(() => {
    void rows;
    const list = journeyList;
    if (!railShown || !list || focusIdx === null) {
      railAt = null;
      return;
    }
    const count = legs[focusIdx].stopovers.length;
    const measure = () => {
      const box = list.getBoundingClientRect();
      const centre = (el: Element | null) => (el ? el.getBoundingClientRect().top + el.getBoundingClientRect().height / 2 - box.top : null);
      const start = list.querySelector('[data-rail="start"]');
      const end = list.querySelector('[data-rail="end"]');
      if (!start || !end) {
        railAt = null;
        return;
      }
      const r = start.getBoundingClientRect();
      railAt = {
        x: r.left + r.width / 2 - box.left,
        start: centre(start)!,
        end: centre(end)!,
        stops: Array.from({ length: count }, (_, k) => centre(list.querySelector(`[data-rail="${k}"]`))),
      };
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(list);
    return () => observer.disconnect();
  });

  $effect(() => {
    if (railShown) return frameClock.use();
  });

  /** Where the train is among a ride's stops at t: at one (from = to), or a share of the way from one to the next, by the expected times. */
  function among(stops: ProgressStop[], t: number): { from: number; to: number; share: number } {
    const last = stops.length - 1;
    if (t < stops[0].departure) return { from: 0, to: 0, share: 0 };
    for (let i = 1; i <= last; i++) {
      const s = stops[i];
      if (t < s.arrival) {
        const a = stops[i - 1].departure;
        return { from: i - 1, to: i, share: Math.max(0, Math.min(1, (t - a) / Math.max(s.arrival - a, 1))) };
      }
      if (i === last || t < s.departure) return { from: i, to: i, share: 0 };
    }
    return { from: last, to: last, share: 0 };
  }

  /** The marker and the filled line, on every frame of the shared clock: only these two read it. */
  const railTrain = $derived.by(() => {
    if (!railShown || !railAt || !progress || !leg) return null;
    const ks = served(leg);
    const last = progress.stops.length - 1;
    if (ks.length !== last - 1) return null;
    const at = railAt;
    const y = (i: number) => (i === 0 ? at.start : i === last ? at.end : at.stops[ks[i - 1]]);
    const p = among(progress.stops, frameClock.now);
    const a = y(p.from);
    const b = y(p.to);
    if (a === null || b === null) return null;
    return { x: at.x, top: at.start, y: a + (b - a) * p.share };
  });

  /** A station name that may wrap after its brackets and slashes: "Frankfurt(Main)Hbf" has no space to break at. */
  function breakable(name: string | null): string | null {
    return name?.replace(/([)/])(?=\S)/g, "$1\u200b") ?? null;
  }

  /** "in 12 min", "in 1 h 25 min", "now" (Android's TripFormat.relative). */
  function relative(minutes: number | null): string | null {
    if (minutes === null) return null;
    return minutes <= 0 ? "now" : `in ${formatDuration(minutes)}`;
  }

  const brokenIcon: Record<Broken, IconComponent> = { Missed: CircleAlert, Cancelled: Ban, "Not running": Ban };

  // ------------------------------------------------------------ what a screen reader hears

  /** "7 minutes", "1 hour 25 minutes". */
  function spokenMinutes(minutes: number): string {
    const m = Math.max(0, Math.round(minutes));
    const unit = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;
    const h = Math.floor(m / 60);
    if (h === 0) return unit(m, "minute");
    return m % 60 === 0 ? unit(h, "hour") : `${unit(h, "hour")} ${unit(m % 60, "minute")}`;
  }

  /** "14:21, 7 minutes late"; on time, the time alone. */
  function spokenTime(stop: { planned: string | null; current: string | null } | null): string | null {
    if (!stop || !(stop.planned ?? stop.current)) return null;
    const late = delayMinutes(stop.planned, stop.current);
    return late > 0 ? `${formatTime(stop.current)}, ${spokenMinutes(late)} late` : formatTime(stop.planned ?? stop.current);
  }

  /** "platform 8", "platform 6, changed from 5", as the box says it. */
  function spokenPlatform(stop: Pick<StopTime, "platform" | "planned_platform"> | null): string | null {
    const shown = stop?.platform || stop?.planned_platform;
    if (!stop || !shown) return null;
    return stop.platform && stop.planned_platform && stop.platform !== stop.planned_platform
      ? `platform ${shown}, changed from ${stop.planned_platform}`
      : `platform ${shown}`;
  }

  function spokenCountdown(minutes: number | null): string | null {
    if (minutes === null) return null;
    return minutes <= 0 ? "now" : `in ${spokenMinutes(minutes)}`;
  }

  /** " Coach 24, seat 66." after a sentence, or nothing. */
  function spokenSeat(ride: Pick<Leg, "coach" | "seats">): string {
    const seat = seatSpoken(ride.coach, ride.seats);
    return seat ? ` ${seat}.` : "";
  }

  /** The parts that have something to say, as one sentence. */
  function sentence(...parts: (string | null | undefined | false)[]): string {
    const text = parts.filter(Boolean).join(", ");
    return text ? `${text}.` : "";
  }

  const confidenceWord = { safe: "safe", tight: "tight", at_risk: "at risk", impossible: "impossible" } as const;

  function spokenChange(t: Transfer): string {
    const from = legs[t.fromIndex].to;
    const to = legs[t.toIndex].from;
    const a = from.platform ?? from.planned_platform;
    const b = to.platform ?? to.planned_platform;
    const way = t.walkMinutes > 0 ? `walk ${spokenMinutes(t.walkMinutes)}` : a && b ? `platform ${a} to platform ${b}` : null;
    return sentence(t.confidence === "impossible" ? "Change impossible" : `Change, ${spokenMinutes(t.availableMinutes)}, ${confidenceWord[t.confidence]}`, way);
  }

  function spokenStop(r: Extract<Row, { kind: "stop" }>): string {
    return sentence(r.boarded ? `Boarded at ${r.name}` : r.name, r.platform && spokenPlatform(r.stop), spokenTime(r.stop));
  }

  /** "Stuttgart Hbf, 21:52, 2 minutes late"; reached: "Passed …". */
  function spokenStopover(r: Extract<Row, { kind: "stopover" }>): string {
    const s = r.stop;
    const name = r.done ? `Passed ${s.name}` : s.name;
    const planned = s.planned_arrival ?? s.planned_departure;
    if (s.cancelled) return sentence(name, planned && formatTime(planned), "cancelled");
    return sentence(name, spokenTime({ planned, current: s.arrival ?? s.departure }));
  }

  function spokenTrain(i: number): string {
    const t = legs[i];
    const from = changeInto(i)?.changesStation || !t.headsign ? `from ${t.from.name}` : null;
    return sentence(`${legLabel(t)}${t.headsign ? ` to ${t.headsign}` : ""}`, from, spokenPlatform(t.from), spokenTime(t.from)) + spokenSeat(t);
  }

  function spokenDoneRide(i: number, first: boolean): string {
    const ride = legs[i];
    return sentence(`${legLabel(ride)}${ride.headsign ? ` to ${ride.headsign}` : ""}`, spokenPlatform(ride.from), !first && spokenTime(ride.from));
  }

  function brokenStep(i: number, why: Broken, large: boolean): Step {
    const l = legs[i];
    // The step that broke is the train's departure, except under someone already on it.
    const at = onBoard && i === focusIdx ? l.to : l.from;
    return {
      verb: why,
      icon: brokenIcon[why],
      bad: true,
      large,
      time: { planned: at.planned, current: at.current, cancelled: true },
      node: "bad",
      title: `${legLabel(l)}${l.headsign ? ` to ${l.headsign}` : ""}`,
      countdown: null,
      platform: null,
      leave: false,
      station: large ? at.name : null,
      // The alternatives and "Any train" go with the first thing that broke.
      actions: i === (focusBroken ? focusIdx : brokenAhead),
      seat: null,
      spoken: sentence(`${why}: ${legLabel(l)}${l.headsign ? ` to ${l.headsign}` : ""} at ${at.name}`, formatTime(at.planned)),
    };
  }

  function activeStep(verb: "Board" | "Get off" | "Arrive" | "Arrived", stop: StopTime, name: string, minutes: number | null, ride: Leg | null = null): Step {
    const icon = verb === "Board" ? TrainFront : verb === "Get off" ? LogOut : Flag;
    const train = ride ? `${legLabel(ride)}${ride.headsign ? ` to ${ride.headsign}` : ""}` : null;
    return {
      verb,
      icon,
      bad: false,
      large: true,
      time: { planned: stop.planned, current: stop.current, cancelled: false },
      node: verb === "Arrived" ? "flag" : "active",
      title: name,
      countdown: relative(minutes),
      platform: stop,
      leave: false,
      station: null,
      // On the last ride the destination is this step: "Any train" goes here.
      actions: verb === "Arrive",
      seat: ride && !summary ? { coach: ride.coach ?? null, seats: ride.seats ?? null } : null,
      spoken:
        sentence(train ? `${verb} ${train} at ${name}` : `${verb} at ${name}`, spokenPlatform(stop), spokenTime(stop), spokenCountdown(minutes)) +
        (ride ? spokenSeat(ride) : ""),
    };
  }

  const leaveStep = $derived<Step>({
    verb: "Leave",
    icon: DoorOpen,
    bad: false,
    large: true,
    time: { planned: day.leaveAt, current: null, cancelled: false },
    node: "active",
    title: null,
    countdown: relative(day.minutesToLeave),
    platform: null,
    leave: true,
    station: null,
    actions: false,
    seat: null,
    spoken: sentence(`Leave at ${formatTime(day.leaveAt)}`, spokenCountdown(day.minutesToLeave)),
  });

  /** The step to show large: the verb and its stop, or what broke there. */
  const active = $derived.by((): Step | null => {
    if (arrived) return lastRide ? activeStep("Arrived", lastRide.to, trip.destination_name, null) : null;
    if (!leg || focusIdx === null) return null;
    if (focusBroken) return brokenStep(focusIdx, focusBroken, true);
    switch (day.kind) {
      case "before_leaving":
        return leaveStep;
      case "at_station":
      case "changing":
        return activeStep("Board", leg.from, leg.from.name, day.minutesToDeparture, leg);
      default:
        return focusIdx === lastRideIdx
          ? activeStep("Arrive", leg.to, trip.destination_name, day.minutesToArrival)
          : activeStep("Get off", leg.to, leg.to.name, day.minutesToArrival);
    }
  });

  /** Rides beyond the next train fold into "Later", unless something there broke or is at risk. */
  const laterFolds = $derived(
    aheadRides.length > 1 &&
      !(brokenAhead !== null && brokenAhead !== aheadRides[0].i) &&
      !(riskyChange !== null && riskyChange.toIndex > aheadRides[0].i),
  );

  const rows = $derived.by((): Row[] => {
    const out: Row[] = [];
    if (doneRides.length > 0) {
      const tail: Seg = day.kind === "changing" ? "walk" : "past";
      out.push({ kind: "done", seg: doneOpen ? "past" : tail });
      if (doneOpen) {
        doneRides.forEach((r, k) => {
          const last = k === doneRides.length - 1;
          out.push({ kind: "doneRide", index: r.i, first: k === 0, seg: "past" });
          if (!(arrived && last)) {
            out.push({ kind: "stop", stop: r.l.to, name: r.l.to.name, node: "done", muted: true, platform: true, seg: last ? tail : "past" });
          }
        });
      }
    }
    if (arrived) {
      if (active) out.push({ kind: "step", step: active, seg: "none" });
      return out;
    }
    if (!leg || focusIdx === null || !active) return out;
    // On board with the ride's stops open, its line is faded: the marker's fill draws the part behind the train over it.
    const ride = onBoard && !focusBroken && !summary && stopsOpen.has(focusIdx) ? "ahead" : rideSeg;
    /** A change to or from a train that does not run says nothing: the broken row does. */
    const changeTo = (i: number) => {
      const t = changeInto(i);
      return t && !legs[t.fromIndex].cancelled && !legs[t.toIndex].cancelled ? t : null;
    };

    switch (day.kind) {
      case "before_leaving":
        if (focusBroken) out.push({ kind: "step", step: active, seg: ride });
        else {
          out.push({ kind: "step", step: active, seg: "walk" });
          out.push({ kind: "stop", stop: leg.from, name: leg.from.name, node: "plain", muted: false, platform: true, seg: ride });
        }
        break;
      case "at_station":
        out.push({ kind: "step", step: active, seg: ride });
        break;
      case "changing": {
        const t = changeTo(focusIdx);
        if (t) out.push({ kind: "change", t, seg: "walk" });
        out.push({ kind: "step", step: active, seg: ride });
        break;
      }
      default:
        out.push({ kind: "stop", stop: leg.from, name: leg.from.name, node: "done", muted: true, platform: false, seg: ride, boarded: true });
    }
    // The ride in focus: its train and where it is; its stops on request, right under it. Nothing when it broke: the step says so.
    if (!focusBroken) {
      out.push({ kind: "band", seg: ride });
      if (stopsOpen.has(focusIdx)) out.push(...stopoverRows(focusIdx, ride));
    }

    const more = aheadRides.length > 0;
    if (onBoard) out.push({ kind: "step", step: active, seg: more ? "walk" : "none" });
    else if (!more) out.push({ kind: "destination", seg: "none" });
    else out.push({ kind: "stop", stop: leg.to, name: leg.to.name, node: "plain", muted: false, platform: true, seg: "walk" });

    for (let k = 0; k < aheadRides.length; k++) {
      const r = aheadRides[k];
      const t = changeTo(r.i);
      if (t) out.push({ kind: "change", t, seg: "walk" });
      const why = brokenOf(r.i);
      if (why) out.push({ kind: "step", step: brokenStep(r.i, why, false), seg: "plain" });
      else {
        out.push({ kind: "train", index: r.i, seg: "plain" });
        if (stopsOpen.has(r.i)) out.push(...stopoverRows(r.i, "plain"));
      }
      if (k === 0 && laterFolds) {
        out.push({ kind: "later", indices: aheadRides.slice(1).map((x) => x.i), seg: "plain" });
        if (!laterOpen) break;
      }
      if (r.i !== lastRideIdx) out.push({ kind: "stop", stop: r.l.to, name: r.l.to.name, node: "plain", muted: false, platform: true, seg: "walk" });
    }
    if (more) out.push({ kind: "destination", seg: "none" });
    return out;
  });

  /** Rows after what broke (or the change at risk) are dimmed; the active step and the band never are. */
  const dimFrom = $derived(rows.findIndex((r) => (r.kind === "step" && r.step.bad) || (r.kind === "change" && r.t === riskyChange)));
  const dimmed = (k: number, r: Row) =>
    dimFrom >= 0 && k > dimFrom && r.kind !== "band" && !(r.kind === "step" && r.step.large && !r.step.bad);

  /** Where the Alternatives button and "Any train" go: what broke first, else the change at risk, else the destination. */
  const anchor = $derived<"broken" | "risky" | "destination">(focusBroken || brokenAhead !== null ? "broken" : riskyChange ? "risky" : "destination");

  const laterChanges = $derived.by(() => {
    if (aheadRides.length < 2) return { count: 0, worst: null as Transfer | null };
    const list = aheadRides
      .slice(1)
      .map((r) => changeInto(r.i))
      .filter((t): t is Transfer => t !== null && !legs[t.fromIndex].cancelled && !legs[t.toIndex].cancelled);
    const order = { safe: 0, tight: 1, at_risk: 2, impossible: 3 } as const;
    const worst = list.reduce<Transfer | null>((w, t) => (!w || order[t.confidence] > order[w.confidence] || (order[t.confidence] === order[w.confidence] && t.availableMinutes < w.availableMinutes) ? t : w), null);
    return { count: aheadRides.length - 1, worst: worst && worst.confidence !== "safe" ? worst : null };
  });

  /** The band in words: the train, how far, the stops passed and next, or where the train to catch is. */
  const bandSpoken = $derived.by(() => {
    if (!leg || focusBroken) return "";
    const parts = [sentence(`${legLabel(leg)}${leg.headsign ? ` to ${leg.headsign}` : ""}`, speed !== null && `${speed} km/h`)];
    if (onBoard && progress) parts.push(`${Math.round(progress.fraction * 100)} % of the ride done.`);
    const leaves = here?.at ? spokenTime({ planned: here.stop.planned_departure, current: here.stop.departure }) : null;
    if (here?.at) parts.push(sentence(`At ${here.stop.name}`, leaves && `leaves ${leaves}`));
    else if (here) parts.push(sentence(`Next stop ${here.stop.name}`, spokenTime({ planned: here.stop.planned_arrival, current: here.stop.arrival })));
    if (!here && approach && waitingFor) parts.push(`${approachText(approach).replace(" · ", ", ")}.`);
    return parts.join(" ");
  });

  const doneLabel = $derived.by(() => {
    const first = doneRides[0]?.l;
    if (!first) return "";
    const n = doneRides.length;
    return `${n} ${n === 1 ? "ride" : "rides"} done, from ${first.from.name} at ${formatTime(first.from.current ?? first.from.planned)}${replanned ? ", re-planned" : ""}`;
  });

  const laterLabel = $derived.by(() => {
    const lines = aheadRides.slice(1).map((r) => legLabel(r.l));
    const worst = laterChanges.worst;
    const n = laterChanges.count;
    return `Later: ${lines.join(", ")}, ${n} ${n === 1 ? "change" : "changes"}${worst ? `, a ${confidenceWord[worst.confidence]} change of ${spokenMinutes(worst.availableMinutes)}` : ""}`;
  });

  /**
   * A new step said once, as it comes (Board → Get off): the rows are not a
   * live region, or the countdown would be read out every minute.
   */
  let news = $state("");
  let lastStep: string | null = null;
  $effect(() => {
    const key = active ? `${active.verb}|${active.title ?? ""}` : "";
    if (lastStep !== null && key !== lastStep && active) news = untrack(() => active?.spoken ?? "");
    lastStep = key;
  });

  // ------------------------------------------------------------ the compact form's one row

  const compactChange = $derived.by(() => {
    const t = aheadRides.length > 0 ? changeInto(aheadRides[0].i) : null;
    return t && !legs[t.fromIndex].cancelled && !legs[t.toIndex].cancelled ? t : null;
  });
  const compactNext = $derived(aheadRides.length > 0 ? aheadRides[0].l : null);
  /** The destination is the active step itself on the last ride. */
  const destinationActive = $derived(arrived || (onBoard && focusIdx === lastRideIdx));

  // ------------------------------------------------------------ the footer: how fresh, how Abeona knows

  let sourceDialog: HTMLDialogElement | undefined = $state();
  const sourceLabel = $derived(day.source === "onboard" ? "Train Wi-Fi" : day.source === "location" ? "Location" : "Timetable");
  const freshness = $derived.by(() => {
    if (!status) return null;
    const word = { live: "Live", offline: "Offline", timetable: "Timetable", done: "" }[status.kind];
    return `${word} ${status.text}`.trim();
  });

  type State = "decides" | "agrees" | "silent" | "off";

  const locationEvidence = $derived.by((): { state: State; detail: string } => {
    if (location === "unsupported") return { state: "off", detail: "This browser cannot share its position" };
    if (location === "denied") return { state: "off", detail: "The browser refused the position: allow it in the site settings" };
    if (location === "off") return { state: "off", detail: "Off · use it to see where you are on the map" };
    if (location === "asking" || !position) return { state: "silent", detail: "Waiting for a position fix" };
    const state: State = day.source === "location" ? "decides" : "agrees";
    const speed = position.speedKmh !== null && position.speedKmh >= 1 ? ` · ${Math.round(position.speedKmh)} km/h` : "";
    const stops = rides.flatMap(({ l }) => [l.from, l.to, ...l.stopovers]);
    let near: { name: string; d: number } | null = null;
    for (const s of stops) {
      if (typeof s.lat !== "number" || typeof s.lon !== "number") continue;
      const d = distanceMetres(position.lat, position.lon, s.lat, s.lon);
      if (!near || d < near.d) near = { name: s.name, d };
    }
    let line: number | null = null;
    for (const { l } of rides) {
      if (!l.polyline || l.polyline.length === 0) continue;
      const d = distanceToLineMetres(position.lat, position.lon, l.polyline);
      if (d !== null && (line === null || d < line)) line = d;
    }
    const km = (m: number) => (m < 1000 ? `${Math.round(m / 10) * 10} m` : `${(m / 1000).toFixed(1)} km`);
    if (near && near.d <= NEAR_STOP_METRES) return { state, detail: `At ${near.name}${speed}` };
    if (line !== null && line <= ON_LINE_METRES) return { state, detail: `On your route${speed}` };
    if (line !== null) return { state, detail: `${km(line)} from your route${speed}` };
    if (near) return { state, detail: `${km(near.d)} from ${near.name}${speed}` };
    return { state, detail: `Position known${speed}` };
  });

  const timetableEvidence = $derived.by((): { state: State; detail: string } => {
    const state: State = day.source === "time" ? "decides" : "agrees";
    if (arrived) return { state, detail: "The last ride has arrived" };
    const live = legs.some((l) => l.realtime);
    if (live && trip.refreshed_at) return { state, detail: `Live times, updated ${formatTime(trip.refreshed_at)}` };
    if (live) return { state, detail: "Live times" };
    return { state, detail: "Planned times: no live data for these trains" };
  });

  const tone: Record<State, string> = {
    decides: "bg-primary text-primary-content",
    agrees: "bg-brand-soft text-brand",
    silent: "bg-well text-muted",
    off: "bg-well text-muted",
  };

  const segClass: Record<Exclude<Seg, "none">, string> = {
    cur: "w-1 bg-brand",
    ahead: "w-0.5 bg-brand/35",
    plain: "w-0.5 bg-ink/30",
    past: "w-0.5 bg-ink/15",
    walk: "w-0 border-l-2 border-dashed border-ink/40",
    bad: "w-0.5 bg-bad",
  };
  /** The row grid of the card's own rows; `lc-grid` lets them narrow with the card (the style below). */
  const GRID = "lc-grid grid grid-cols-[3.25rem_1.5rem_minmax(0,1fr)_auto] gap-x-2";
</script>

<!-- The line through a row's mark column: above its node, below it, or through a row without one. -->
{#snippet seg(style: Seg, part: "above" | "below" | "through")}
  {#if style !== "none"}
    <span
      class="absolute left-1/2 -translate-x-1/2 {part === 'above' ? 'top-0 bottom-1/2' : part === 'below' ? 'top-1/2 bottom-0' : 'inset-y-0'} {segClass[style]}"
      aria-hidden="true"
    ></span>
  {/if}
{/snippet}

<!-- A node on the line; `rail` names it for the marker (the ride's start, a stop between by index, its end). -->
{#snippet node(kind: NodeKind, rail?: string)}
  {#if kind === "flag"}
    <span data-rail={rail} class="relative flex h-5 w-5 items-center justify-center rounded-full bg-brand-soft {arrived ? 'text-brand' : 'text-ink'}">
      <Flag class="h-4 w-4" aria-label="Destination" />
    </span>
  {:else if kind === "active"}
    <span data-rail={rail} class="relative h-3.5 w-3.5 rounded-full border-[3px] border-brand bg-brand-soft ring-4 ring-brand/25" aria-hidden="true"></span>
  {:else if kind === "bad"}
    <span data-rail={rail} class="relative h-3.5 w-3.5 rounded-full border-[3px] border-bad bg-brand-soft ring-4 ring-bad/30" aria-hidden="true"></span>
  {:else if kind === "done"}
    <!-- On the card's colour, so a line running through it does not show. -->
    <span data-rail={rail} class="relative h-2.5 w-2.5 rounded-full bg-brand-soft" aria-hidden="true"><span class="absolute inset-0 rounded-full bg-muted/60"></span></span>
  {:else if kind === "through"}
    <span data-rail={rail} class="relative h-2 w-2 rounded-full border-2 border-ink bg-brand-soft" aria-hidden="true"></span>
  {:else}
    <span data-rail={rail} class="relative h-2.5 w-2.5 rounded-full border-2 border-ink bg-brand-soft" aria-hidden="true"></span>
  {/if}
{/snippet}

<!-- Under what broke or the change at risk: the alternatives sheet, and whether any train will do. -->
{#snippet actions(filled: boolean, inline: boolean)}
  {#if (reason && (onAlternatives || summary)) || lifted}
    <div class="flex flex-wrap items-center gap-2 {inline ? '' : 'pt-1.5'}">
      {#if reason && summary && filled}
        <!-- Inside the Trips card's link: it opens the trip, whose card has the button. -->
        <span class="btn btn-sm rounded-full border-none bg-bad text-bad-content"><Shuffle class="h-4 w-4" aria-hidden="true" /> Alternatives</span>
      {:else if reason && onAlternatives && !summary}
        <button
          type="button"
          class="btn btn-sm rounded-full {filled ? 'border-none bg-bad text-bad-content hover:brightness-95' : 'border-bad-ink/60 bg-transparent text-bad-ink hover:bg-bad-soft'}"
          onclick={onAlternatives}
        >
          <Shuffle class="h-4 w-4" aria-hidden="true" /> Alternatives
        </button>
      {/if}
      {#if lifted}
        <StatusChip tone="watch" icon={ArrowRightLeft} label="Any train" title="Any train: the train binding is lifted" />
      {/if}
    </div>
  {/if}
{/snippet}

<!-- A step with its verb: the active one (large), or a broken ride further ahead. -->
{#snippet stepRow(s: Step, above: Seg, below: Seg, dim: boolean, rail?: string)}
  {@const Icon = s.icon}
  <!-- Off the Trips card a row of the list: its words as one sentence, the drawing hidden; the links and buttons stay. -->
  {@const hide = summary ? undefined : "true"}
  <div class="step {GRID} {dim ? 'opacity-50' : ''}" role={summary ? undefined : "listitem"}>
    {#if !summary}<span class="sr-only">{s.spoken}</span>{/if}
    <span class="s-seg1 relative col-start-2 row-start-1 self-stretch">{@render seg(above, "through")}</span>
    <p class="s-verb t-label col-start-3 row-start-1 flex items-center gap-1.5 pt-2.5 {s.bad ? 'text-bad-ink' : 'text-brand'}" aria-hidden={hide}>
      <Icon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{s.verb}
    </p>

    <span class="s-time col-start-1 row-span-2 row-start-2 self-start pt-0.5 font-semibold text-ink {s.large ? 't-title' : 't-body'}" aria-hidden={hide}>
      {#if s.time}
        <ShiftedTime planned={s.time.planned} current={s.time.current} cancelled={s.time.cancelled} block={false} class="flex-wrap gap-y-0" />
      {/if}
    </span>
    <span class="s-node relative col-start-2 row-start-2 flex items-center justify-center self-stretch">
      {@render seg(above, "above")}
      {@render seg(below, "below")}
      {@render node(s.node, rail)}
    </span>
    {#if s.leave}
      <p class="s-title col-start-3 row-start-2 text-[1.375rem] leading-tight font-semibold text-brand tabular" aria-hidden={hide}>{s.countdown}</p>
    {:else}
      <p class="s-title col-start-3 row-start-2 min-w-0 font-semibold text-ink [overflow-wrap:anywhere] {s.large ? 'text-[1.375rem] leading-tight' : 't-title'}" aria-hidden={hide}>{breakable(s.title)}</p>
    {/if}

    <span class="s-seg3 relative col-start-2 row-start-3 self-stretch">{@render seg(below, "through")}</span>
    <div class="s-more col-start-3 row-start-3 min-w-0 pb-2.5">
      {#if s.leave}
        {#if settingsHref && !summary}
          <a href={settingsHref} class="t-meta inline-flex min-h-8 items-center gap-1 hover:text-brand" title="Time to the station, in Settings">
            <Footprints class="h-4 w-4" aria-hidden="true" /><span class="sr-only">To the station</span>{stationMinutes} min
          </a>
        {:else}
          <Fact icon={Footprints} label="To the station" value={`${stationMinutes} min`} />
        {/if}
      {:else if s.countdown}
        <p class="t-body font-bold text-brand tabular" aria-hidden={hide}>{s.countdown}</p>
      {:else if s.station}
        <p class="t-meta truncate" aria-hidden={hide}>{s.station}</p>
      {/if}
      {#if s.seat}
        <!-- Where to sit, under the countdown: the coach to look for on the platform. -->
        <span class="mt-0.5 flex" aria-hidden={hide}><Seat coach={s.seat.coach} seats={s.seat.seats} class="text-ink" /></span>
      {/if}
      {#if s.actions && anchor === (s.bad ? "broken" : "destination")}{@render actions(!!reason && reason !== "at_risk", false)}{/if}
    </div>
    {#if s.platform}
      <span class="s-platform col-start-4 row-span-3 row-start-1 self-center" aria-hidden={hide}>
        <PlatformBox planned={s.platform.planned_platform} current={s.platform.platform} size={s.large ? "step" : "row"} />
      </span>
    {/if}
  </div>
{/snippet}

<!-- The ride in focus on its stretch of line: its train, where it is among its stops, the stops on request. Its picture is the card's header. -->
{#snippet band(through: Seg, first: boolean)}
  {@const spoken = !summary && bandSpoken !== ""}
  {@const toggle = !summary && !!leg && leg.stopovers.length > 0}
  {#if leg && !focusBroken}
    <div class="band grid grid-cols-[3.25rem_1.5rem_minmax(0,1fr)] gap-x-2" role={spoken ? "listitem" : undefined} aria-hidden={summary || spoken ? undefined : "true"}>
      {#if spoken}<span class="sr-only">{bandSpoken}</span>{/if}
      <span class="b-seg relative col-start-2 self-stretch">
        <!-- First in the card (the Trips card on board): the line starts here, under the header. -->
        <span class="absolute inset-x-0 bottom-0 {first ? 'top-3' : 'top-0'}">{@render seg(through, "through")}</span>
      </span>
      <div class="b-body min-w-0 py-1.5">
        <div class="b-cap flex min-h-8 min-w-0 items-center gap-2" aria-hidden={spoken ? "true" : undefined}>
          <LegChip {leg} size="md" />
          {#if leg.headsign}<span class="b-head t-body min-w-0 truncate font-semibold text-ink">to {leg.headsign}</span>{/if}
          {#if speed !== null}<Fact icon={Gauge} label="Speed" value={`${speed} km/h`} class="ml-auto font-semibold text-ink" />{/if}
        </div>
        {#if !summary && !here && approach && waitingFor}
          <p class="t-meta mt-1" aria-hidden={spoken ? "true" : undefined}>{approachText(approach)}</p>
        {/if}
        {#if !summary && (here || toggle)}
          <!--
            One line: the stop the train stands at or comes to next, then the ride's stops on request. It wraps
            where it must; each part brings its " · " along, and the one that would start a line is cut off.
          -->
          <div class="overflow-hidden {toggle ? '-mb-1.5' : ''}">
            <div class="b-here t-meta -ml-3 flex min-h-8 flex-wrap items-center">
              {#if here}
                <span class="inline-flex min-w-0 max-w-full items-center gap-1 pl-3" aria-hidden={spoken ? "true" : undefined}>
                  {#if here.at}<MapPin class="h-4 w-4 shrink-0" aria-hidden="true" />{:else}<ArrowRight class="h-4 w-4 shrink-0" aria-hidden="true" />{/if}
                  <span class="min-w-0 truncate">{here.stop.name}</span>
                  <ShiftedTime
                    planned={here.at ? here.stop.planned_departure : here.stop.planned_arrival}
                    current={here.at ? here.stop.departure : here.stop.arrival}
                    block={false}
                    class="shrink-0"
                  />
                </span>
              {/if}
              {#if toggle && focusIdx !== null}
                {@const i = focusIdx}
                <span class="inline-flex items-center">
                  <span class="w-3 text-center" aria-hidden="true">·</span>
                  <StopsToggle count={leg.stopovers.length} open={stopsOpen.has(i)} ride={legLabel(leg)} ontoggle={() => toggleStops(i)} />
                </span>
              {/if}
            </div>
          </div>
        {/if}
      </div>
    </div>
  {/if}
{/snippet}

{#snippet platforms(t: Transfer)}
  <!-- Between two trains: on foot with its minutes, else the platform arrived at → the one to leave from. -->
  {@const from = legs[t.fromIndex].to}
  {@const to = legs[t.toIndex].from}
  {#if t.walkMinutes > 0}
    <Fact icon={Footprints} label="Walk" value={`${t.walkMinutes} min`} />
  {:else if (from.platform ?? from.planned_platform) && (to.platform ?? to.planned_platform)}
    {@const a = from.platform ?? from.planned_platform}
    {@const b = to.platform ?? to.planned_platform}
    {@const moved = (s: StopTime) => !!s.platform && !!s.planned_platform && normalisePlatform(s.platform) !== normalisePlatform(s.planned_platform)}
    <span class="tabular inline-flex items-center gap-1 text-sm font-semibold text-ink" role="img" aria-label={`Platform ${a} to platform ${b}`}>
      <span class={moved(from) ? "rounded bg-watch-soft px-1 text-watch-ink" : ""} aria-hidden="true">{a}</span>
      <span class="text-muted" aria-hidden="true">→</span>
      <span class={moved(to) ? "rounded bg-watch-soft px-1 text-watch-ink" : ""} aria-hidden="true">{b}</span>
    </span>
  {/if}
{/snippet}

{#snippet changeRow(t: Transfer, above: Seg, dim: boolean)}
  {#if t === riskyChange}
    <!-- At risk or impossible: the change turns red, with the alternatives right there. -->
    <div class="{GRID} {dim ? 'opacity-50' : ''}" role="listitem">
      <span class="sr-only">{spokenChange(t)}</span>
      <span class="relative col-start-2 self-stretch">{@render seg(above, "through")}</span>
      <div class="col-span-2 col-start-3 my-1 rounded-2xl bg-bad-soft px-3 py-2 text-bad-ink ring-1 ring-bad/40 ring-inset">
        <div class="flex flex-wrap items-center gap-2" aria-hidden="true">
          <ConfidenceChip confidence={t.confidence} minutes={t.availableMinutes} word />
          {@render platforms(t)}
        </div>
        {#if anchor === "risky"}{@render actions(t.confidence === "impossible", false)}{/if}
      </div>
    </div>
  {:else}
    <div role="listitem">
      <span class="sr-only">{spokenChange(t)}</span>
      <div aria-hidden="true">
        <StopRow class={dim ? "opacity-50" : ""}>
          {#snippet mark()}{@render seg(above, "through")}{/snippet}
          {#snippet line()}
            <ConfidenceChip confidence={t.confidence} minutes={t.availableMinutes} word />
            {@render platforms(t)}
          {/snippet}
        </StopRow>
      </div>
    </div>
  {/if}
{/snippet}

{#snippet destinationRow(above: Seg, dim: boolean)}
  {#if lastRide}
    <div role="listitem">
      <span class="sr-only">{sentence(`Destination ${trip.destination_name}`, spokenPlatform(lastRide.to), spokenTime(lastRide.to))}</span>
      <div aria-hidden="true">
        <StopRow
          planned={lastRide.to.planned}
          current={lastRide.to.current}
          name={trip.destination_name}
          platformPlanned={lastRide.to.planned_platform}
          platformCurrent={lastRide.to.platform}
          class={dim ? "opacity-50" : ""}
        >
          {#snippet mark()}{@render seg(above, "above")}{@render node("flag")}{/snippet}
        </StopRow>
      </div>
      <!-- Under the destination's name, outside the hidden drawing: what to do now that the binding is lifted, and the way back to the plan. -->
      {#if anchor === "destination" && ((reason && onAlternatives) || lifted)}
        <div class="{GRID} -mt-1.5 pb-1.5 {dim ? 'opacity-50' : ''}">
          <span class="col-start-3">{@render actions(!!reason && reason !== "at_risk", true)}</span>
        </div>
      {/if}
      {#if onBackToPlan}
        <div class={GRID}>
          <span class="col-start-3">
            <button type="button" class="btn btn-ghost btn-sm -ml-3 rounded-full text-brand" onclick={onBackToPlan} disabled={busy}>Back to the plan</button>
          </span>
        </div>
      {/if}
    </div>
  {/if}
{/snippet}

<section class="space-y-3" aria-label="Live">
  <div class="overflow-hidden rounded-3xl bg-brand-soft shadow-card">
    {#if arrived && !summary}
      <!-- Arrived: the train at the platform with its flag, header size. -->
      <Scene state="arrived" size="header" train={sceneTrain} at={now} lat={skyAt?.lat} lon={skyAt?.lon} />
    {:else if !arrived}
      <!-- The header: the scene over the card's whole width, its bar the whole journey. On the full card the band says it in words. -->
      <div aria-hidden={summary ? undefined : "true"}>
        <Scene state={sceneState} size="strip" train={sceneTrain} journey={barJourney} speedKmh={speed} at={now} lat={skyAt?.lat} lon={skyAt?.lon} />
      </div>
    {/if}

    <div class="@container px-4 pb-1 pt-1.5">
      {#if summary}
        <!-- The Trips card: the band, the active step, then one row. -->
        <!-- In time order, as on the full card: before boarding the step comes first, on board the band. -->
        {#if active && !arrived && !onBoard}
          {@render stepRow(active, "none", focusBroken ? "none" : day.kind === "before_leaving" ? "walk" : rideSeg, false)}
          {@render band(rideSeg, false)}
        {:else}
          {#if !arrived}{@render band(rideSeg, true)}{/if}
          {#if active}{@render stepRow(active, arrived || focusBroken ? "none" : rideSeg, "none", false)}{/if}
        {/if}
        {#if !arrived && (compactChange || compactNext || !destinationActive)}
          <div class="{GRID} min-h-10 items-center pb-1.5 {focusBroken ? 'opacity-50' : ''}">
            <span class="col-start-3 flex min-w-0 flex-wrap items-center gap-2">
              {#if compactChange}<ConfidenceChip confidence={compactChange.confidence} minutes={compactChange.availableMinutes} word />{/if}
              {#if compactNext}<LegChip leg={compactNext} />{/if}
              {#if lastRide && !destinationActive}
                <span class="t-meta inline-flex items-center gap-1 text-ink">
                  <Flag class="h-4 w-4" aria-label="Destination" />
                  <ShiftedTime planned={lastRide.to.planned} current={lastRide.to.current} block={false} />
                </span>
              {/if}
              <!-- What broke further ahead, or the binding lifted: the step itself is not on this card. -->
              {#if !focusBroken}{@render actions(!!reason && reason !== "at_risk", true)}{/if}
            </span>
          </div>
        {/if}
      {:else}
        <!-- A new step, said once. -->
        <p class="sr-only" aria-live="polite">{news}</p>
        <div role="list" aria-label="Journey" class="relative" bind:this={journeyList}>
        {#if railTrain}
          <!-- On board, the ride's stops open: the line done, from where the ride began to the train (under the stops' dots). -->
          <span
            class="pointer-events-none absolute w-1 -translate-x-1/2 rounded-full bg-brand"
            style="left: {railTrain.x}px; top: {railTrain.top}px; height: {Math.max(0, railTrain.y - railTrain.top)}px"
            aria-hidden="true"
          ></span>
        {/if}
        {#each rows as r, k (k)}
          {@const above = rows[k - 1]?.seg ?? "none"}
          {@const dim = dimmed(k, r)}
          {#if r.kind === "done"}
            {@const first = doneRides[0].l}
            <div role="listitem">
              <button type="button" class="block w-full text-left" aria-expanded={doneOpen} aria-label={doneLabel} onclick={() => (doneOpen = !doneOpen)}>
                <StopRow planned={first.from.planned} current={first.from.current} name={first.from.name} muted class="min-h-12">
                  {#snippet mark()}{@render seg(r.seg, "below")}{@render node("done")}{/snippet}
                  <span class="t-meta">{doneRides.length} {doneRides.length === 1 ? "ride" : "rides"} done{replanned ? " · re-planned" : ""}</span>
                  {#snippet trailing()}
                    <ChevronDown class="h-5 w-5 text-muted transition-transform motion-reduce:transition-none {doneOpen ? 'rotate-180' : ''}" aria-hidden="true" />
                  {/snippet}
                </StopRow>
              </button>
            </div>
          {:else if r.kind === "doneRide"}
            {@const ride = legs[r.index]}
            <div role="listitem">
              <span class="sr-only">{spokenDoneRide(r.index, r.first)}</span>
              <div aria-hidden="true">
                <StopRow
                  planned={r.first ? null : ride.from.planned}
                  current={r.first ? null : ride.from.current}
                  name={ride.headsign ? `to ${ride.headsign}` : ""}
                  muted
                  platformPlanned={ride.from.planned_platform}
                  platformCurrent={ride.from.platform}
                >
                  {#snippet mark()}{@render seg(r.seg, "through")}{/snippet}
                  {#snippet line()}<LegChip leg={ride} />{/snippet}
                </StopRow>
              </div>
            </div>
          {:else if r.kind === "stop"}
            <div role="listitem">
              <span class="sr-only">{spokenStop(r)}</span>
              <div aria-hidden="true">
                <StopRow
                  planned={r.stop.planned}
                  current={r.stop.current}
                  name={r.name}
                  muted={r.muted}
                  platformPlanned={r.platform ? r.stop.planned_platform : null}
                  platformCurrent={r.platform ? r.stop.platform : null}
                  class={dim ? "opacity-50" : ""}
                >
                  {#snippet mark()}{@render seg(above, "above")}{@render seg(r.seg, "below")}{@render node(r.node, r.boarded ? "start" : undefined)}{/snippet}
                </StopRow>
              </div>
            </div>
          {:else if r.kind === "stopover"}
            <div role="listitem">
              <span class="sr-only">{spokenStopover(r)}</span>
              <div aria-hidden="true">
                <StopRow
                  planned={r.stop.planned_arrival ?? r.stop.planned_departure}
                  current={r.stop.arrival ?? r.stop.departure}
                  cancelled={r.stop.cancelled}
                  name={r.stop.name}
                  muted={r.done}
                  class="min-h-8 {dim ? 'opacity-50' : ''}"
                >
                  {#snippet mark()}{@render seg(above, "above")}{@render seg(r.seg, "below")}{@render node("through", r.rail)}{/snippet}
                </StopRow>
              </div>
            </div>
          {:else if r.kind === "band"}
            {@render band(r.seg, k === 0)}
          {:else if r.kind === "step"}
            {@render stepRow(r.step, above, r.seg, dim, onBoard && r.step === active ? "end" : undefined)}
          {:else if r.kind === "change"}
            {@render changeRow(r.t, above, dim)}
          {:else if r.kind === "train"}
            {@const train = legs[r.index]}
            {@const into = changeInto(r.index)}
            <div role="listitem">
              <span class="sr-only">{spokenTrain(r.index)}</span>
              <div aria-hidden="true">
                <StopRow
                  planned={train.from.planned}
                  current={train.from.current}
                  name={train.headsign ? `to ${train.headsign}` : train.from.name}
                  platformPlanned={train.from.planned_platform}
                  platformCurrent={train.from.platform}
                  class={dim ? "opacity-50" : ""}
                >
                  {#snippet mark()}{@render seg(above, "above")}{@render seg(r.seg, "below")}{@render node("plain")}{/snippet}
                  {#snippet line()}<LegChip leg={train} />{/snippet}
                  {#if into?.changesStation && train.headsign}<span class="t-meta truncate">{train.from.name}</span>{/if}
                  <Seat coach={train.coach} seats={train.seats} />
                </StopRow>
              </div>
              {#if train.stopovers.length > 0}
                <!-- Under the row, on its line: the train's stops, which open below. Drawn close to it; its line starts where the row's ends. -->
                <div class="{GRID} -mt-2 {dim ? 'opacity-50' : ''}">
                  <span class="relative col-start-2 self-stretch"><span class="absolute inset-x-0 top-2 bottom-0">{@render seg(r.seg, "through")}</span></span>
                  <span class="col-start-3 flex">
                    <StopsToggle count={train.stopovers.length} open={stopsOpen.has(r.index)} ride={legLabel(train)} ontoggle={() => toggleStops(r.index)} />
                  </span>
                </div>
              {/if}
            </div>
          {:else if r.kind === "later"}
            <div role="listitem">
            <button type="button" class="block w-full text-left {dim ? 'opacity-50' : ''}" aria-expanded={laterOpen} aria-label={laterLabel} onclick={() => (laterOpen = !laterOpen)}>
              <StopRow class="min-h-12">
                {#snippet mark()}{@render seg(r.seg, "through")}{/snippet}
                {#snippet line()}
                  <span class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
                    <span class="t-body mr-0.5 text-muted">Later</span>
                    {#each r.indices as i (i)}<LegChip leg={legs[i]} />{/each}
                    <span class="t-meta ml-0.5">{laterChanges.count} {laterChanges.count === 1 ? "change" : "changes"}</span>
                    {#if laterChanges.worst}
                      <ConfidenceChip confidence={laterChanges.worst.confidence} minutes={laterChanges.worst.availableMinutes} word />
                    {/if}
                  </span>
                {/snippet}
                {#snippet trailing()}
                  <ChevronDown class="h-5 w-5 text-muted transition-transform motion-reduce:transition-none {laterOpen ? 'rotate-180' : ''}" aria-hidden="true" />
                {/snippet}
              </StopRow>
            </button>
            </div>
          {:else if r.kind === "destination"}
            {@render destinationRow(above, dim)}
          {/if}
        {/each}
        {#if railTrain}
          <!-- Where the train is: between the stop it left and the next, or on the one it stands at. -->
          <span
            class="pointer-events-none absolute top-0 left-0 h-2.5 w-2.5 rounded-full bg-brand ring-[3px] ring-brand/25"
            style="transform: translate({railTrain.x - 5}px, {railTrain.y - 5}px)"
            aria-hidden="true"
          ></span>
        {/if}
        </div>

        <!-- How fresh it all is, and how Abeona knows: the last, smallest line. -->
        {#if freshness || !arrived}
        <div class="mt-1 flex min-h-11 items-center justify-between gap-3 border-t border-line">
          {#if status && freshness}
            <span class="t-meta inline-flex items-center gap-1.5 {status.kind === 'offline' ? 'text-watch-ink' : ''}" title={status.label}>
              {#if status.kind === "offline"}<CloudOff class="h-4 w-4" aria-hidden="true" />
              {:else if status.kind === "timetable"}<Clock class="h-4 w-4" aria-hidden="true" />
              {:else if status.kind === "done"}<CircleCheck class="h-4 w-4" aria-hidden="true" />
              {:else}<RefreshCw class="h-4 w-4" aria-hidden="true" />{/if}
              {freshness}
            </span>
          {:else}
            <span></span>
          {/if}
          {#if !arrived}
            <button
              type="button"
              class="btn btn-ghost btn-circle btn-sm -mr-2 h-10 w-10 text-muted"
              aria-haspopup="dialog"
              aria-label={`How Abeona knows: ${sourceLabel}`}
              title={`How Abeona knows: ${sourceLabel}`}
              onclick={() => sourceDialog?.showModal()}
            >
              {#if day.source === "onboard"}<Wifi class="h-5 w-5" aria-hidden="true" />{:else if day.source === "location"}<LocateFixed class="h-5 w-5" aria-hidden="true" />{:else}<Clock class="h-5 w-5" aria-hidden="true" />{/if}
            </button>
          {/if}
        </div>
        {/if}
      {/if}
    </div>
  </div>

</section>

{#if !summary}
<!-- What the footer's source button opens: each signal, the deciding one lit. -->
<dialog bind:this={sourceDialog} class="modal modal-bottom sm:modal-middle" aria-labelledby="how-abeona-knows">
  <div class="modal-box rounded-t-3xl bg-card sm:rounded-3xl">
    <h3 id="how-abeona-knows" class="t-title text-ink">
      {onBoard ? "How Abeona knows your train" : "How Abeona knows where you are"}
    </h3>
    <p class="t-meta mt-1">
      The strongest signal there is decides: the train's Wi-Fi, then your location, then the timetable with the live times.
    </p>
    <ul class="mt-3 space-y-2">
      <li class="flex min-h-12 items-center gap-3">
        <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full {tone.off}"><Wifi class="h-5 w-5" aria-hidden="true" /></span>
        <div class="min-w-0 flex-1">
          <p class="t-body font-semibold text-ink">Train Wi-Fi</p>
          <p class="t-meta">Read by the Android app on an ICE or Railjet: it names the train</p>
        </div>
      </li>
      <li class="flex min-h-12 items-center gap-3">
        <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full {tone[locationEvidence.state]}"><LocateFixed class="h-5 w-5" aria-hidden="true" /></span>
        <div class="min-w-0 flex-1">
          <p class="t-body font-semibold text-ink">Location</p>
          <p class="t-meta">{locationEvidence.detail}</p>
        </div>
        {#if locationEvidence.state === "decides"}
          <span class="t-label rounded-full bg-brand-soft px-2 py-0.5 text-brand">Decides</span>
        {/if}
        {#if location === "off"}
          <button class="btn btn-primary btn-sm rounded-full" onclick={() => onLocation(true)}>Use</button>
        {:else if location === "on" || location === "asking"}
          <button class="btn btn-ghost btn-sm rounded-full text-muted" onclick={() => onLocation(false)}>Stop</button>
        {/if}
      </li>
      <li class="flex min-h-12 items-center gap-3">
        <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full {tone[timetableEvidence.state]}"><Clock class="h-5 w-5" aria-hidden="true" /></span>
        <div class="min-w-0 flex-1">
          <p class="t-body font-semibold text-ink">Timetable</p>
          <p class="t-meta">{timetableEvidence.detail}</p>
        </div>
        {#if timetableEvidence.state === "decides"}
          <span class="t-label rounded-full bg-brand-soft px-2 py-0.5 text-brand">Decides</span>
        {/if}
      </li>
    </ul>
    <div class="modal-action">
      <form method="dialog"><button class="btn btn-ghost rounded-full">Close</button></form>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
{/if}

<style>
  /*
   * A narrow card (a phone at 150 % text and more): four columns of large
   * text leave the names no room, so the time moves above the name, the
   * platform beside the verb, and the name takes the whole width under them.
   * StopRow does the same for its rows.
   */
  @container (width < 16rem) {
    .lc-grid {
      grid-template-columns: 1.5rem minmax(0, 1fr) auto;
    }
    .lc-grid > :global(.col-start-2) {
      grid-column: 1;
    }
    .lc-grid > :global(.col-start-3) {
      grid-column: 2 / -1;
    }
    .step > .s-seg1 {
      grid-column: 1;
      grid-row: 1;
    }
    .step > .s-verb {
      grid-column: 2;
      grid-row: 1;
    }
    .step > .s-time {
      grid-column: 2;
      grid-row: 2;
      align-self: center;
    }
    .step > .s-node {
      grid-column: 1;
      grid-row: 2 / span 2;
    }
    .step > .s-title {
      grid-column: 2 / -1;
      grid-row: 3;
    }
    .step > .s-seg3 {
      grid-column: 1;
      grid-row: 4;
    }
    .step > .s-more {
      grid-column: 2 / -1;
      grid-row: 4;
    }
    .step > .s-platform {
      grid-column: 3;
      grid-row: 1 / span 2;
    }
    .band {
      grid-template-columns: 1.5rem minmax(0, 1fr);
    }
    .band > .b-seg {
      grid-column: 1;
    }
    .band > .b-body {
      grid-column: 2;
    }
    .b-cap,
    .b-here > :global(span) {
      flex-wrap: wrap;
    }
    .b-head,
    .b-here :global(.truncate) {
      white-space: normal;
      overflow-wrap: anywhere;
    }
  }
</style>
