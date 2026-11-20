<script lang="ts">
  /**
   * The live card of a travel day (abeona-design.md §8) — the web's copy of
   * Android's ui/live/LiveDashboard.kt: the journey drawn as one vertical
   * line in the row grid, then the map with the traveller on it. Done rides
   * fold into one row; the ride in focus is the band (the scene strip on its
   * stretch of line, its train, where it is); the next thing to do is the one
   * large row with a verb; the rest runs down to the destination, which is
   * always last. Words only where nothing shorter works
   * (decisions/2026-11-20-abeona-live-journey-line.md). How Abeona knows sits
   * behind the footer's source button.
   */
  import {
    ArrowRightLeft,
    Ban,
    Check,
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
    RefreshCw,
    Shuffle,
    TrainFront,
    Wifi,
  } from "lucide-svelte";
  import { untrack } from "svelte";
  import type { Approach, StopTime } from "../../lib/transit";
  import { fetchApproach, type Trip } from "../../lib/data";
  import { formatDuration, formatTime, legLabel } from "../../lib/format";
  import { distanceMetres, distanceToLineMetres, NEAR_STOP_METRES, ON_LINE_METRES, type Position } from "../../lib/travelday";
  import type { LocationState } from "../../lib/position";
  import { type MapMarker, rideProgress } from "../../lib/progress";
  import { trainKind } from "../../lib/scene";
  import { finalArrivalDelay, normalisePlatform, trainBindingLifted, transfers, type Transfer } from "../../lib/transfer";
  import type { AlternativesReason, TravelDay } from "../../lib/travelday";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import Fact from "../common/Fact.svelte";
  import type { IconComponent } from "../common/icon";
  import LegChip from "../common/LegChip.svelte";
  import PlatformBox from "../common/PlatformBox.svelte";
  import Scene from "../common/Scene.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import StatusChip from "../common/StatusChip.svelte";
  import StopRow from "../common/StopRow.svelte";
  import TripMap from "../trip/TripMap.svelte";

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

  const marker = $derived.by((): MapMarker | null => {
    if (position) return { lat: position.lat, lon: position.lon, kind: "location" };
    if (onBoard && progress?.estimate) return { ...progress.estimate, kind: "timetable" };
    return null;
  });

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

  const showRail = $derived(!!progress && !focusBroken && (onBoard || day.kind === "at_station" || day.kind === "changing"));
  /** The scene: the land passes on board, the signal is red when the step broke, the flag is out on arrival. */
  const sceneState = $derived(arrived ? "arrived" : focusBroken ? "stopped" : onBoard ? "riding" : "waiting");
  const sceneRide = $derived(leg ?? lastRide);
  const sceneTrain = $derived(sceneRide ? trainKind(sceneRide.product, legLabel(sceneRide), sceneRide.mode === "bus") : undefined);
  /** Where the sky is reckoned: the device, else the stop the step is about. */
  const skyAt = $derived(position ?? (onBoard ? leg?.to : leg?.from) ?? lastRide?.to ?? null);
  const speed = $derived(onBoard && position && position.speedKmh !== null && position.speedKmh >= 1 ? Math.round(position.speedKmh) : null);

  /**
   * On board, under the train: the stop it last reached (stands at, else last
   * left) and the next one, as Android's PassingStops. Not the ride's first
   * stop (the row above has it), and no next when that is the step below.
   */
  const passing = $derived.by(() => {
    if (!onBoard || !progress) return null;
    const stops = progress.stops;
    const last = stops.length - 1;
    const inside = (i: number | null | undefined) => (i !== null && i !== undefined && i >= 1 && i < last ? i : null);
    const current = inside(progress.current);
    const reached = current ?? inside(stops.findLastIndex((s) => s.passed));
    const next = progress.next !== null && progress.next < last ? progress.next : null;
    if (reached === null && next === null) return null;
    return {
      reached: reached === null ? null : { name: stops[reached].name, time: formatTime(new Date(reached === current ? stops[reached].arrival : stops[reached].departure).toISOString()) },
      next: next === null ? null : { name: stops[next].name, time: formatTime(new Date(stops[next].arrival).toISOString()) },
    };
  });

  // ------------------------------------------------------------ the rows

  /** The line below a row (above it, the one of the row before): this ride, a ride ahead, done, on foot between trains. */
  type Seg = "cur" | "plain" | "past" | "walk" | "bad" | "none";
  type NodeKind = "plain" | "done" | "active" | "bad" | "flag";

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
  }

  type Row =
    | { kind: "done"; seg: Seg }
    | { kind: "doneRide"; index: number; first: boolean; seg: Seg }
    | { kind: "stop"; stop: StopTime; name: string; node: NodeKind; muted: boolean; platform: boolean; seg: Seg }
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
    };
  }

  function activeStep(verb: "Board" | "Get off" | "Arrive" | "Arrived", stop: StopTime, name: string, minutes: number | null): Step {
    const icon = verb === "Board" ? TrainFront : verb === "Get off" ? LogOut : Flag;
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
        return activeStep("Board", leg.from, leg.from.name, day.minutesToDeparture);
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
    const ride = rideSeg;
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
        out.push({ kind: "stop", stop: leg.from, name: leg.from.name, node: "done", muted: true, platform: false, seg: ride });
    }
    out.push({ kind: "band", seg: ride });

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
      else out.push({ kind: "train", index: r.i, seg: "plain" });
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
    plain: "w-0.5 bg-ink/30",
    past: "w-0.5 bg-ink/15",
    walk: "w-0 border-l-2 border-dashed border-ink/40",
    bad: "w-0.5 bg-bad",
  };
  const GRID = "grid grid-cols-[3.25rem_1.5rem_minmax(0,1fr)_auto] gap-x-2";
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

{#snippet node(kind: NodeKind)}
  {#if kind === "flag"}
    <span class="relative flex h-5 w-5 items-center justify-center rounded-full bg-brand-soft {arrived ? 'text-brand' : 'text-ink'}">
      <Flag class="h-4 w-4" aria-label="Destination" />
    </span>
  {:else if kind === "active"}
    <span class="relative h-3.5 w-3.5 rounded-full border-[3px] border-brand bg-brand-soft ring-4 ring-brand/25" aria-hidden="true"></span>
  {:else if kind === "bad"}
    <span class="relative h-3.5 w-3.5 rounded-full border-[3px] border-bad bg-brand-soft ring-4 ring-bad/30" aria-hidden="true"></span>
  {:else if kind === "done"}
    <span class="relative h-2.5 w-2.5 rounded-full bg-muted/60" aria-hidden="true"></span>
  {:else}
    <span class="relative h-2.5 w-2.5 rounded-full border-2 border-ink bg-brand-soft" aria-hidden="true"></span>
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
{#snippet stepRow(s: Step, above: Seg, below: Seg, dim: boolean)}
  {@const Icon = s.icon}
  <div class="{GRID} {dim ? 'opacity-50' : ''}" aria-live={s.large ? "polite" : undefined}>
    <span class="relative col-start-2 row-start-1 self-stretch">{@render seg(above, "through")}</span>
    <p class="t-label col-start-3 row-start-1 flex items-center gap-1.5 pt-2.5 {s.bad ? 'text-bad-ink' : 'text-brand'}">
      <Icon class="h-3.5 w-3.5" aria-hidden="true" />{s.verb}
    </p>

    <span class="col-start-1 row-span-2 row-start-2 self-start pt-0.5 font-semibold text-ink {s.large ? 't-title' : 't-body'}">
      {#if s.time}
        <ShiftedTime planned={s.time.planned} current={s.time.current} cancelled={s.time.cancelled} block={false} class="flex-wrap gap-y-0" />
      {/if}
    </span>
    <span class="relative col-start-2 row-start-2 flex items-center justify-center self-stretch">
      {@render seg(above, "above")}
      {@render seg(below, "below")}
      {@render node(s.node)}
    </span>
    {#if s.leave}
      <p class="col-start-3 row-start-2 text-[1.375rem] leading-tight font-semibold text-brand tabular">{s.countdown}</p>
    {:else}
      <p class="col-start-3 row-start-2 min-w-0 font-semibold text-ink [overflow-wrap:anywhere] {s.large ? 'text-[1.375rem] leading-tight' : 't-title'}">{breakable(s.title)}</p>
    {/if}

    <span class="relative col-start-2 row-start-3 self-stretch">{@render seg(below, "through")}</span>
    <div class="col-start-3 row-start-3 min-w-0 pb-2.5">
      {#if s.leave}
        {#if settingsHref && !summary}
          <a href={settingsHref} class="t-meta inline-flex min-h-8 items-center gap-1 hover:text-brand" title="Time to the station, in Settings">
            <Footprints class="h-4 w-4" aria-hidden="true" /><span class="sr-only">To the station</span>{stationMinutes} min
          </a>
        {:else}
          <Fact icon={Footprints} label="To the station" value={`${stationMinutes} min`} />
        {/if}
      {:else if s.countdown}
        <p class="t-body font-bold text-brand tabular">{s.countdown}</p>
      {:else if s.station}
        <p class="t-meta truncate">{s.station}</p>
      {/if}
      {#if s.actions && anchor === (s.bad ? "broken" : "destination")}{@render actions(!!reason && reason !== "at_risk", false)}{/if}
    </div>
    {#if s.platform}
      <span class="col-start-4 row-span-3 row-start-1 self-center">
        <PlatformBox planned={s.platform.planned_platform} current={s.platform.platform} size={s.large ? "step" : "row"} />
      </span>
    {/if}
  </div>
{/snippet}

<!-- The ride in focus on its stretch of line: the scene, the train, where it is. -->
{#snippet band(through: Seg, first: boolean)}
  {@const caption = !!leg && !focusBroken}
  <div class="grid grid-cols-[3.25rem_1.5rem_minmax(0,1fr)] gap-x-2">
    <span class="relative col-start-2 self-stretch">
      <!-- First in the card (the Trips card): the line starts with the scene. -->
      <span class="absolute inset-x-0 bottom-0 {first ? 'top-3' : 'top-0'}">{@render seg(through, "through")}</span>
    </span>
    <div class="min-w-0 py-1.5">
      <div class="overflow-hidden rounded-xl">
        <Scene
          state={sceneState}
          size="strip"
          train={sceneTrain}
          stops={showRail && progress ? progress.stops.map((stop) => ({ fraction: stop.fraction, name: stop.name })) : null}
          fraction={onBoard && progress ? progress.fraction : 0}
          speedKmh={speed}
          at={now}
          lat={skyAt?.lat}
          lon={skyAt?.lon}
        />
      </div>
      {#if caption && leg}
        <div class="mt-2 flex min-w-0 items-center gap-2">
          <LegChip {leg} size="md" />
          {#if leg.headsign}<span class="t-body min-w-0 truncate font-semibold text-ink">to {leg.headsign}</span>{/if}
          {#if speed !== null}<Fact icon={Gauge} label="Speed" value={`${speed} km/h`} class="ml-auto font-semibold text-ink" />{/if}
        </div>
      {/if}
      {#if caption && !summary}
        {#if passing}
          <div class="t-meta mt-1 flex flex-wrap items-center justify-between gap-x-3 gap-y-0.5">
            {#if passing.reached}
              <span class="inline-flex min-w-0 max-w-full items-center gap-1">
                <Check class="h-3.5 w-3.5 shrink-0" aria-hidden="true" /><span class="sr-only">Passed</span>
                <span class="truncate">{passing.reached.name}</span>
                <span class="tabular shrink-0">{passing.reached.time}</span>
              </span>
            {:else}
              <span></span>
            {/if}
            {#if passing.next}
              <span class="ml-auto inline-flex min-w-0 max-w-full items-center gap-1">
                <span class="shrink-0">Next</span>
                <span class="truncate">{passing.next.name}</span>
                <span class="tabular shrink-0">{passing.next.time}</span>
              </span>
            {/if}
          </div>
        {:else if approach && waitingFor}
          <p class="t-meta mt-1">{approachText(approach)}</p>
        {/if}
      {/if}
    </div>
  </div>
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
    <div class="{GRID} {dim ? 'opacity-50' : ''}">
      <span class="relative col-start-2 self-stretch">{@render seg(above, "through")}</span>
      <div class="col-span-2 col-start-3 my-1 rounded-2xl bg-bad-soft px-3 py-2 text-bad-ink ring-1 ring-bad/40 ring-inset">
        <div class="flex flex-wrap items-center gap-2">
          <ConfidenceChip confidence={t.confidence} minutes={t.availableMinutes} word />
          {@render platforms(t)}
        </div>
        {#if anchor === "risky"}{@render actions(t.confidence === "impossible", false)}{/if}
      </div>
    </div>
  {:else}
    <StopRow class={dim ? "opacity-50" : ""}>
      {#snippet mark()}{@render seg(above, "through")}{/snippet}
      {#snippet line()}
        <ConfidenceChip confidence={t.confidence} minutes={t.availableMinutes} word />
        {@render platforms(t)}
      {/snippet}
    </StopRow>
  {/if}
{/snippet}

{#snippet destinationRow(above: Seg, dim: boolean)}
  {#if lastRide}
    <StopRow
      planned={lastRide.to.planned}
      current={lastRide.to.current}
      name={trip.destination_name}
      platformPlanned={lastRide.to.planned_platform}
      platformCurrent={lastRide.to.platform}
      class={dim ? "opacity-50" : ""}
    >
      {#snippet mark()}{@render seg(above, "above")}{@render node("flag")}{/snippet}
      {#if anchor === "destination"}{@render actions(!!reason && reason !== "at_risk", false)}{/if}
    </StopRow>
    {#if onBackToPlan && !summary}
      <div class={GRID}>
        <span class="col-start-3">
          <button type="button" class="btn btn-ghost btn-sm -ml-3 rounded-full text-brand" onclick={onBackToPlan} disabled={busy}>Back to the plan</button>
        </span>
      </div>
    {/if}
  {/if}
{/snippet}

<section class="space-y-3" aria-label="Live">
  <div class="overflow-hidden rounded-3xl bg-brand-soft shadow-card">
    {#if arrived && !summary}
      <!-- Arrived: the train at the platform with its flag, header size. -->
      <Scene state="arrived" size="header" train={sceneTrain} at={now} lat={skyAt?.lat} lon={skyAt?.lon} />
    {/if}

    <div class="px-4 pb-1 pt-1.5">
      {#if summary}
        <!-- The Trips card: the band, the active step, then one row. -->
        <!-- In time order, as on the full card: before boarding the step comes first, on board the band. -->
        {#if active && !arrived && !onBoard}
          {@render stepRow(active, "none", day.kind === "before_leaving" && !focusBroken ? "walk" : rideSeg, false)}
          {@render band(rideSeg, false)}
        {:else}
          {#if !arrived}{@render band(rideSeg, true)}{/if}
          {#if active}{@render stepRow(active, arrived ? "none" : rideSeg, "none", false)}{/if}
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
        {#each rows as r, k (k)}
          {@const above = rows[k - 1]?.seg ?? "none"}
          {@const dim = dimmed(k, r)}
          {#if r.kind === "done"}
            {@const first = doneRides[0].l}
            <button type="button" class="block w-full text-left" aria-expanded={doneOpen} onclick={() => (doneOpen = !doneOpen)}>
              <StopRow planned={first.from.planned} current={first.from.current} name={first.from.name} muted class="min-h-12">
                {#snippet mark()}{@render seg(r.seg, "below")}{@render node("done")}{/snippet}
                <span class="t-meta">{doneRides.length} {doneRides.length === 1 ? "ride" : "rides"} done{replanned ? " · re-planned" : ""}</span>
                {#snippet trailing()}
                  <ChevronDown class="h-5 w-5 text-muted transition-transform {doneOpen ? 'rotate-180' : ''}" aria-label={doneOpen ? "Hide what is done" : "Show what is done"} />
                {/snippet}
              </StopRow>
            </button>
          {:else if r.kind === "doneRide"}
            {@const ride = legs[r.index]}
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
          {:else if r.kind === "stop"}
            <StopRow
              planned={r.stop.planned}
              current={r.stop.current}
              name={r.name}
              muted={r.muted}
              platformPlanned={r.platform ? r.stop.planned_platform : null}
              platformCurrent={r.platform ? r.stop.platform : null}
              class={dim ? "opacity-50" : ""}
            >
              {#snippet mark()}{@render seg(above, "above")}{@render seg(r.seg, "below")}{@render node(r.node)}{/snippet}
            </StopRow>
          {:else if r.kind === "band"}
            {@render band(r.seg, k === 0)}
          {:else if r.kind === "step"}
            {@render stepRow(r.step, above, r.seg, dim)}
          {:else if r.kind === "change"}
            {@render changeRow(r.t, above, dim)}
          {:else if r.kind === "train"}
            {@const train = legs[r.index]}
            {@const into = changeInto(r.index)}
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
            </StopRow>
          {:else if r.kind === "later"}
            <button type="button" class="block w-full text-left {dim ? 'opacity-50' : ''}" aria-expanded={laterOpen} onclick={() => (laterOpen = !laterOpen)}>
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
                  <ChevronDown class="h-5 w-5 text-muted transition-transform {laterOpen ? 'rotate-180' : ''}" aria-label={laterOpen ? "Hide the later rides" : "Show the later rides"} />
                {/snippet}
              </StopRow>
            </button>
          {:else if r.kind === "destination"}
            {@render destinationRow(above, dim)}
          {/if}
        {/each}

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

  {#if !arrived && !summary}
    <TripMap {legs} {marker} focus={onBoard ? leg : null} />
  {/if}
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
