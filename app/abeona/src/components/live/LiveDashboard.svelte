<script lang="ts">
  /**
   * The live dashboard of a travel day — the web's copy of Android's
   * ui/live/LiveDashboard.kt: one live card, then the map with the traveller
   * on it. The card (abeona-design.md §8) holds everything live, most urgent
   * first: the scene strip, which is the ride's rail; what happens now with
   * its time, delay and countdown; under the hairline the steps ahead as
   * rows in the row grid; then how fresh it all is. How Abeona knows sits
   * behind the card's source pill.
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
    LogOut,
    GitCommitHorizontal,
    LocateFixed,
    MapPin,
    RefreshCw,
    Shuffle,
    TrainFront,
    Wifi,
  } from "lucide-svelte";
  import { type Snippet, untrack } from "svelte";
  import type { Approach, Leg, StopTime } from "../../lib/transit";
  import { fetchApproach, type Trip } from "../../lib/data";
  import { displayPlatform, formatTime, legLabel } from "../../lib/format";
  import { distanceMetres, distanceToLineMetres, NEAR_STOP_METRES, ON_LINE_METRES, type Position } from "../../lib/travelday";
  import type { LocationState } from "../../lib/position";
  import { type MapMarker, rideProgress } from "../../lib/progress";
  import { trainKind } from "../../lib/scene";
  import { effectiveTime, finalArrivalDelay, overallConfidence, trainBindingLifted, transfers } from "../../lib/transfer";
  import type { AlternativesReason, TravelDay } from "../../lib/travelday";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import Fact from "../common/Fact.svelte";
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
    /** How fresh the live values are, for the card's last line. */
    status?: { text: string; label: string; kind: "live" | "timetable" | "offline" | "done" } | null;
    /** What else is live about the trip (the binding lifted, another connection followed), inside the card. */
    notices?: Snippet;
    /**
     * The Trips screen's next-trip card: the same strip, state line, headline
     * and train line, then one row to the destination instead of the steps
     * ahead; no source, map, notices or status. Android's
     * LiveCard(summary = true).
     */
    summary?: boolean;
  }

  let { trip, day, now, stationMinutes, reason = null, position = null, location = "off", onLocation = () => {}, onAlternatives, settingsHref, status = null, notices, summary = false }: Props = $props();

  const legs = $derived(trip.legs);
  const leg = $derived(day.legIndex !== null ? legs[day.legIndex] : null);
  const progress = $derived(leg ? rideProgress(leg, now) : null);
  const change = $derived(
    day.legIndex === null
      ? null
      : (transfers(legs).find((t) => (day.kind === "on_board" ? t.fromIndex === day.legIndex : t.toIndex === day.legIndex)) ?? null),
  );
  const warn = $derived(day.missedChange || day.cancelled);
  const rides = $derived(legs.map((l, i) => ({ l, i })).filter(({ l }) => l.mode !== "walk"));
  const lastRide = $derived(rides[rides.length - 1]?.l ?? null);
  const arrived = $derived(day.kind === "arrived");

  const marker = $derived.by((): MapMarker | null => {
    if (position) return { lat: position.lat, lon: position.lon, kind: "location" };
    if (day.kind === "on_board" && progress?.estimate) return { ...progress.estimate, kind: "timetable" };
    return null;
  });

  // ------------------------------------------------------------ the train coming in

  /** While waiting for the train: where it is on its way here, asked every minute. */
  const waitingFor = $derived(
    !warn && leg && !leg.cancelled && leg.trip_ref && (day.kind === "at_station" || day.kind === "changing") ? leg : null,
  );
  const waitingKey = $derived(waitingFor ? `${waitingFor.trip_ref}|${waitingFor.from.id ?? waitingFor.from.name}` : null);
  let approach: Approach | null = $state(null);
  $effect(() => {
    const key = waitingKey;
    approach = null;
    const target = untrack(() => waitingFor);
    if (!key || !target) return;
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

  /** "Train left Aschaffenburg Hbf, 3 stops away": read out; on the card the stops are an icon and a number. */
  function approachText(a: Approach): string {
    return `${a.state === "at" ? "Train is at" : "Train left"} ${a.stop}, ${a.stops_away} ${a.stops_away === 1 ? "stop" : "stops"} away`;
  }

  // ------------------------------------------------------------ now

  const eyebrow = $derived.by(() => {
    if (day.missedChange) return "Looks like you missed it";
    if (day.cancelled) return "Change of plan";
    switch (day.kind) {
      case "before_leaving":
        return "Time to leave";
      case "at_station":
        return day.source === "location" ? "You're at the station" : "Head to the platform";
      case "on_board":
        // "to" and the train's own destination, as the departure board says it.
        return `On board${leg ? ` · ${legLabel(leg)}${leg.headsign ? ` to ${leg.headsign}` : ""}` : ""}`;
      case "changing":
        return `Change trains${leg ? ` · ${leg.from.name}` : ""}`;
      case "arrived":
        return "Arrived";
    }
  });

  /** How Abeona knows, behind the source pill. */
  let sourceDialog: HTMLDialogElement | undefined = $state();

  const sourceLabel = $derived(day.source === "onboard" ? "Train Wi-Fi" : day.source === "location" ? "Location" : "Timetable");


  /** What the card leads with: a name, the time that goes with it, what to do there, and the countdown. */
  const lead = $derived.by(() => {
    const base = {
      stop: null as StopTime | null,
      icon: null as "flag" | "change" | null,
      /** The platform beside the time (on board: where I get off), or as the headline itself (waiting for the train). */
      platformAt: null as StopTime | null,
      platformHead: null as StopTime | null,
      line: null as Leg | null,
      note: null as string | null,
      minutes: null as number | null,
      /** What broke, in a word: the headline and its time are struck through. */
      broken: null as "Missed" | "Cancelled" | "Not in the timetable" | null,
      lifted: false,
    };
    if (arrived || !leg) return { ...base, headline: trip.destination_name, stop: lastRide?.to ?? null, icon: "flag" as const };
    if (day.missedChange && day.kind !== "on_board") return { ...base, headline: legLabel(leg), stop: leg.from, note: "Your ticket may allow another train.", broken: "Missed" as const };
    if (leg.cancelled) {
      // The broken step struck through, with the train it was; whether the ticket now allows others.
      const lifted = trainBindingLifted(trip.fare_type, trip.train_binding, finalArrivalDelay(legs), true);
      return { ...base, headline: `${legLabel(leg)}${leg.headsign ? ` to ${leg.headsign}` : ""}`, stop: leg.from, lifted, broken: leg.dropped_at ? ("Not in the timetable" as const) : ("Cancelled" as const) };
    }
    const platform = displayPlatform(leg.from);
    switch (day.kind) {
      case "before_leaving":
        // The time to go is the headline; the train to catch is the first row under the hairline.
        return { ...base, headline: `Leave ${formatTime(day.leaveAt)}`, note: `${stationMinutes} min to ${leg.from.name}`, minutes: day.minutesToLeave };
      case "at_station":
      case "changing":
        return { ...base, headline: platform ? "Platform" : legLabel(leg), platformHead: platform ? leg.from : null, stop: leg.from, line: leg, minutes: day.minutesToDeparture };
      default:
        return { ...base, headline: leg.to.name, stop: leg.to, icon: change ? ("change" as const) : ("flag" as const), platformAt: leg.to, minutes: day.minutesToArrival };
    }
  });

  function countdown(minutes: number | null): { number: string; label: string } | null {
    if (minutes === null) return null;
    if (minutes <= 0) return { number: "now", label: "" };
    if (minutes < 60) return { number: String(minutes), label: "min" };
    // "16 h" over "54 min", never "16:54", which reads as a clock time.
    const hours = Math.floor(minutes / 60);
    return { number: `${hours} h`, label: minutes % 60 === 0 ? "" : `${minutes % 60} min` };
  }

  // ------------------------------------------------------------ the ride

  const showRide = $derived(
    !!leg && !!progress && !leg.cancelled && !(day.missedChange && day.kind !== "on_board") && (day.kind === "on_board" || day.kind === "at_station" || day.kind === "changing"),
  );
  const onBoard = $derived(day.kind === "on_board");
  /** How far along the rail the train is: 0 until on board. */
  const rail = $derived(onBoard && progress ? progress.fraction : 0);
  /** The scene: the land passes on board, the signal is red when the step broke, the flag is out on arrival. */
  const sceneState = $derived(arrived ? "arrived" : warn ? "stopped" : onBoard ? "riding" : "waiting");
  /** The train drawn: the ride the card is about, the last one once arrived. */
  const sceneRide = $derived(leg ?? lastRide);
  const sceneTrain = $derived(sceneRide ? trainKind(sceneRide.product, legLabel(sceneRide), sceneRide.mode === "bus") : undefined);
  /** Where the sky is reckoned: the device, else the stop the step is about. */
  const skyAt = $derived(position ?? (onBoard ? leg?.to : leg?.from) ?? lastRide?.to ?? null);
  const speed = $derived(position && position.speedKmh !== null && position.speedKmh >= 1 ? Math.round(position.speedKmh) : null);
  const standing = $derived(
    !!progress && progress.current !== null && progress.current > 0 && progress.current < progress.stops.length - 1,
  );
  /** The stop coming up on board (or the one standing at); none when only the stop the card leads with is left. */
  const nextStop = $derived.by(() => {
    if (!onBoard || !progress || progress.next === null) return null;
    const last = progress.stops.length - 1;
    if (!standing && progress.next === last) return null;
    const stop = standing ? progress.stops[progress.current ?? 0] : progress.stops[progress.next];
    const over = leg?.stopovers.find((o) => o.name === stop.name && (!o.id || o.id === stop.id));
    const planned = standing ? (over?.planned_departure ?? over?.planned_arrival) : (over?.planned_arrival ?? over?.planned_departure);
    const current = new Date(standing ? stop.departure : stop.arrival).toISOString();
    return { name: stop.name, planned: planned ?? current, current };
  });

  // ------------------------------------------------------------ the change

  /** After this ride: the next change, its train, and where the trip ends. */
  const then = $derived(warn || arrived || day.legIndex === null ? null : (transfers(legs).find((t) => t.fromIndex === day.legIndex) ?? null));
  const thenLeg = $derived(then ? legs[then.toIndex] : null);
  const arriving = $derived(change ? legs[change.fromIndex] : null);
  /** Something still lies ahead to show below the headline. */
  const ahead = $derived(!!leg && !warn && !arrived);
  const changeCount = $derived(Math.max(0, rides.length - 1));
  const worst = $derived(overallConfidence(transfers(legs)));

  // ------------------------------------------------------------ how Abeona knows

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
</script>

{#snippet way(from: StopTime, to: StopTime, walk: number)}
  <!-- Between two trains: the walk, else the platform left behind (where it changed is said where the train arrives; the next one is the row's own). -->
  {#if walk > 0}
    <Fact icon={Footprints} label="Walk" value={`${walk} min`} />
  {:else if displayPlatform(from) && displayPlatform(to)}
    <PlatformBox current={displayPlatform(from)} />
    <ArrowRight class="h-4 w-4 text-muted" aria-label="to" />
  {/if}
{/snippet}

{#snippet trainToCatch(train: Leg)}
  <!-- Before leaving: the train to catch, with all four facts. -->
  <StopRow planned={train.from.planned} current={train.from.current} name={train.headsign ? `to ${train.headsign}` : ""} platformPlanned={train.from.planned_platform} platformCurrent={train.from.platform}>
    {#snippet mark()}<TrainFront class="h-5 w-5" aria-label="Train" />{/snippet}
    {#snippet line()}<LegChip leg={train} />{/snippet}
  </StopRow>
{/snippet}

<section class="space-y-3" aria-label="Live">
  <div class="overflow-hidden rounded-3xl shadow-card {warn ? 'bg-bad-soft' : 'bg-brand-soft'}" aria-live="polite">
    <!-- The strip is the ride's rail: a dot per stop, the train where the ride is. -->
    <Scene
      state={sceneState}
      size={arrived ? "header" : "strip"}
      train={sceneTrain}
      stops={showRide && progress ? progress.stops.map((stop) => ({ fraction: stop.fraction, name: stop.name })) : null}
      fraction={rail}
      speedKmh={onBoard ? speed : null}
      at={now}
      lat={skyAt?.lat}
      lon={skyAt?.lon}
    />

    <div class="space-y-2 px-4 pb-4">
      <!-- What state, and how Abeona knows. -->
      <div class="flex min-h-7 items-center gap-2">
        <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full {warn ? 'bg-bad text-bad-content' : 'bg-primary text-primary-content'}" aria-hidden="true">
          {#if warn}<CircleAlert class="h-3.5 w-3.5" />
          {:else if day.kind === "before_leaving"}<DoorOpen class="h-3.5 w-3.5" />
          {:else if day.kind === "at_station"}<MapPin class="h-3.5 w-3.5" />
          {:else if day.kind === "on_board"}<TrainFront class="h-3.5 w-3.5" />
          {:else if day.kind === "changing"}<ArrowRightLeft class="h-3.5 w-3.5" />
          {:else}<Flag class="h-3.5 w-3.5" />{/if}
        </span>
        <p class="t-label min-w-0 flex-1 truncate {warn ? 'text-bad-ink' : 'text-brand'}">{eyebrow}</p>
        {#if !arrived && !summary}
          <button
            type="button"
            class="inline-flex h-7 shrink-0 items-center gap-0.5 rounded-full bg-card/70 pl-2 pr-1 text-ink transition hover:bg-card focus-visible:outline-2 focus-visible:outline-primary"
            aria-haspopup="dialog"
            aria-label={`How Abeona knows: ${sourceLabel}`}
            title={sourceLabel}
            onclick={() => sourceDialog?.showModal()}
          >
            {#if day.source === "onboard"}<Wifi class="h-4 w-4" />{:else if day.source === "location"}<LocateFixed class="h-4 w-4" />{:else}<Clock class="h-4 w-4" />{/if}
            <ChevronDown class="h-3.5 w-3.5 text-muted" />
          </button>
        {/if}
      </div>

      <!-- The one thing to know now. -->
      <div class="flex items-center gap-4">
        <div class="min-w-0 flex-1">
          <p class="t-headline flex items-center gap-2 text-ink">
            <span class="min-w-0 {lead.broken ? 'text-muted line-through' : ''}">{lead.headline}</span>
            {#if lead.platformHead}
              <PlatformBox planned={lead.platformHead.planned_platform} current={lead.platformHead.platform} size="lead" />
            {/if}
          </p>
          {#if lead.stop || lead.line || lead.broken}
            <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
              {#if lead.stop}
                <ShiftedTime planned={lead.stop.planned} current={lead.stop.current} cancelled={!!lead.broken} class="t-time text-ink" />
              {/if}
              {#if lead.icon === "flag"}<Flag class="h-5 w-5 text-muted" aria-label="Get off" />{:else if lead.icon === "change"}<ArrowRightLeft class="h-5 w-5 text-muted" aria-label="Change" />{/if}
              {#if lead.platformAt}
                <PlatformBox planned={lead.platformAt.planned_platform} current={lead.platformAt.platform} size="lead" />
              {/if}
              {#if lead.line}
                <span class="t-meta inline-flex min-w-0 items-center gap-2">
                  {#if lead.platformHead}<LegChip leg={lead.line} />{/if}
                  {#if lead.line.headsign}<span class="truncate">to {lead.line.headsign}</span>{/if}
                </span>
              {/if}
              {#if lead.broken}
                <StatusChip tone="bad" icon={lead.broken === "Missed" ? CircleAlert : Ban} label={lead.broken} />
              {/if}
              {#if lead.lifted}
                <StatusChip tone="watch" icon={ArrowRightLeft} label="Train binding lifted" />
              {/if}
            </div>
          {/if}
          {#if lead.note}
            <p class="t-meta mt-1 flex flex-wrap items-center gap-x-1">
              {#if day.kind === "before_leaving" && !lead.broken}<Footprints class="h-4 w-4" aria-hidden="true" />{/if}
              {lead.note}{#if day.kind === "before_leaving" && settingsHref && !summary}&nbsp;·
                <a href={settingsHref} class="text-brand underline-offset-2 hover:underline">change</a>{/if}
            </p>
          {/if}
          {#if approach && waitingFor}
            <!-- Where the train to board is on its way here. -->
            <p class="t-meta mt-1 flex flex-wrap items-center gap-x-1.5" aria-label={approachText(approach)}>
              <TrainFront class="h-4 w-4" aria-hidden="true" />
              <span aria-hidden="true">{approach.state === "at" ? "at" : "left"} {approach.stop}</span>
              <GitCommitHorizontal class="h-4 w-4" aria-hidden="true" />
              <span aria-hidden="true">{approach.stops_away}</span>
            </p>
          {/if}
        </div>
        {#if countdown(lead.minutes)}
          {@const c = countdown(lead.minutes)!}
          <div class="shrink-0 text-right" aria-label={`${c.number} ${c.label}`}>
            <p class="t-numeral text-brand">{c.number}</p>
            {#if c.label}<p class="t-meta">{c.label}</p>{/if}
          </div>
        {/if}
      </div>

      {#if summary}
        {#if ahead && !(onBoard && leg === lastRide) && lastRide}
          <!-- The Trips card's one row below the train: the arrival, the changes, how they look. -->
          <div class="border-t border-line pt-1">
            {#if day.kind === "before_leaving" && leg}{@render trainToCatch(leg)}{/if}
            <StopRow planned={lastRide.to.planned} current={lastRide.to.current} name={trip.title ? trip.destination_name : ""} muted>
              {#snippet mark()}<Flag class="h-5 w-5" aria-label="Destination" />{/snippet}
              {#snippet trailing()}
                <Fact icon={ArrowRightLeft} label={changeCount === 0 ? "Direct, changes" : "Changes"} value={changeCount} />
                {#if worst}<ConfidenceChip confidence={worst} />{/if}
              {/snippet}
            </StopRow>
          </div>
        {/if}
      {:else if ahead && leg}
        <!-- The steps still ahead, in travel order, each with its time and platform. -->
        {@const arrivingThere = then ? legs[then.fromIndex] : null}
        <div class="border-t border-line pt-1">
          {#if day.kind === "before_leaving"}
            {@render trainToCatch(leg)}
          {/if}
          {#if day.kind === "changing" && change && arriving}
            <!-- The change being made: its margin and the way between platforms. -->
            <StopRow platformPlanned={change.walkMinutes > 0 ? null : leg.from.planned_platform} platformCurrent={change.walkMinutes > 0 ? null : leg.from.platform}>
              {#snippet mark()}<ArrowRightLeft class="h-5 w-5" aria-label="Change" />{/snippet}
              {#snippet line()}<ConfidenceChip confidence={change.confidence} minutes={change.availableMinutes} />{/snippet}
              {#snippet trailing()}{@render way(arriving.to, leg.from, change.walkMinutes)}{/snippet}
            </StopRow>
          {/if}
          {#if nextStop && progress}
            <!-- The stop coming up, or the one the train stands at (then: when it leaves). -->
            <StopRow planned={nextStop.planned} current={nextStop.current} name={nextStop.name}>
              {#snippet mark()}
                {#if standing}<MapPin class="h-5 w-5" aria-label="Now at, leaves" />{:else}<ArrowRight class="h-5 w-5" aria-label="Next stop" />{/if}
              {/snippet}
              {#snippet trailing()}<Fact icon={GitCommitHorizontal} label="Stops left" value={progress.stopsLeft} />{/snippet}
            </StopRow>
          {/if}
          {#if then && thenLeg && arrivingThere}
            {#if !onBoard}
              <StopRow planned={arrivingThere.to.planned} current={arrivingThere.to.current} name={arrivingThere.to.name} platformPlanned={arrivingThere.to.planned_platform} platformCurrent={arrivingThere.to.platform}>
                {#snippet mark()}<LogOut class="h-5 w-5" aria-label="Get off" />{/snippet}
                {#snippet trailing()}
                  {#if showRide && progress && arrivingThere === leg}<Fact icon={GitCommitHorizontal} label="Stops" value={progress.stops.length - 1} />{/if}
                {/snippet}
              </StopRow>
            {/if}
            <StopRow platformPlanned={then.walkMinutes > 0 ? null : thenLeg.from.planned_platform} platformCurrent={then.walkMinutes > 0 ? null : thenLeg.from.platform}>
              {#snippet mark()}<ArrowRightLeft class="h-5 w-5" aria-label="Change" />{/snippet}
              {#snippet line()}<ConfidenceChip confidence={then.confidence} minutes={then.availableMinutes} />{/snippet}
              {#snippet trailing()}{@render way(arrivingThere.to, thenLeg.from, then.walkMinutes)}{/snippet}
            </StopRow>
            <StopRow planned={thenLeg.from.planned} current={thenLeg.from.current} name={thenLeg.headsign ? `to ${thenLeg.headsign}` : ""} platformPlanned={thenLeg.from.planned_platform} platformCurrent={thenLeg.from.platform}>
              {#snippet mark()}<TrainFront class="h-5 w-5" aria-label="Train" />{/snippet}
              {#snippet line()}<LegChip leg={thenLeg} />{/snippet}
            </StopRow>
          {/if}
          {#if lastRide && !(onBoard && leg === lastRide)}
            <!-- Where the trip ends (the headline, on the last ride). -->
            <StopRow planned={lastRide.to.planned} current={lastRide.to.current} name={trip.destination_name} muted={!!then}>
              {#snippet mark()}<Flag class="h-5 w-5" aria-label="Destination" />{/snippet}
              {#snippet trailing()}
                {#if !then && !onBoard && showRide && progress}<Fact icon={GitCommitHorizontal} label="Stops" value={progress.stops.length - 1} />{/if}
              {/snippet}
            </StopRow>
          {/if}
        </div>
      {/if}

      {#if day.missedChange && day.kind === "on_board"}
        <p class="t-meta flex items-center gap-1.5 text-bad-ink"><CircleAlert class="h-4 w-4 shrink-0" aria-hidden="true" /> You are still on this train after your connection left.</p>
      {/if}
      <!-- Filled only when something broke; a change merely at risk gets a quiet button (none on the Trips card). -->
      {#if reason && reason !== "at_risk"}
        {#if summary}
          <!-- Inside the Trips card's link: it opens the trip, whose card has the button. -->
          <span class="btn btn-sm rounded-full border-none bg-bad text-bad-content"><Shuffle class="h-4 w-4" aria-hidden="true" /> See alternatives</span>
        {:else if onAlternatives}
          <button class="btn btn-sm rounded-full border-none bg-bad text-bad-content hover:brightness-95" onclick={onAlternatives}>
            <Shuffle class="h-4 w-4" aria-hidden="true" /> See alternatives
          </button>
        {/if}
      {:else if reason && onAlternatives && !summary}
        <button class="btn btn-ghost btn-sm rounded-full border border-line text-ink" onclick={onAlternatives}>
          <Shuffle class="h-4 w-4" aria-hidden="true" /> Alternatives
        </button>
      {/if}

      {#if !summary}
        {@render notices?.()}

        {#if status || speed !== null}
          <!-- How fresh it all is: small, last. -->
          <div class="flex items-center justify-between gap-3">
            {#if status}
              <Fact
                icon={status.kind === "offline" ? CloudOff : status.kind === "timetable" ? Clock : status.kind === "done" ? CircleCheck : RefreshCw}
                label={status.label}
                value={status.text}
              />
            {/if}
            {#if speed !== null}<Fact icon={Gauge} label="Speed" value={`${speed} km/h`} class="ml-auto" />{/if}
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
<!-- What the source pill opens: each signal, the deciding one lit. -->
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
