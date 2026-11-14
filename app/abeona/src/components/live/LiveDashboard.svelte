<script lang="ts">
  /**
   * The live dashboard of a travel day — the web's copy of Android's
   * ui/live/LiveDashboard.kt: one live card, then the map with the traveller
   * on it. The card holds everything live, most urgent first: what happens
   * now with its time, delay and countdown; the ride on a rail; the next
   * stop; the change after this ride and where the trip ends; then how fresh
   * it all is. How Abeona knows sits behind the card's source pill.
   */
  import {
    ArrowRight,
    ArrowRightLeft,
    ChevronDown,
    Clock,
    CloudOff,
    DoorOpen,
    Flag,
    Footprints,
    GitCommitHorizontal,
    LocateFixed,
    MapPin,
    RefreshCw,
    Shuffle,
    TrainFront,
    TriangleAlert,
    Wifi,
  } from "lucide-svelte";
  import type { Snippet } from "svelte";
  import type { Leg, StopTime } from "../../lib/transit";
  import type { Trip } from "../../lib/data";
  import { displayPlatform, formatDuration, formatTime, legLabel, platformChanged, stopDelay } from "../../lib/format";
  import { distanceMetres, distanceToLineMetres, NEAR_STOP_METRES, ON_LINE_METRES, type Position } from "../../lib/travelday";
  import type { LocationState } from "../../lib/position";
  import { type MapMarker, rideProgress } from "../../lib/progress";
  import { effectiveTime, transfers } from "../../lib/transfer";
  import type { AlternativesReason, TravelDay } from "../../lib/travelday";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import LegChip from "../common/LegChip.svelte";
  import TripMap from "../trip/TripMap.svelte";

  interface Props {
    trip: Trip;
    day: TravelDay;
    now: number;
    stationMinutes: number;
    reason?: AlternativesReason | null;
    position?: Position | null;
    location: LocationState;
    onLocation: (on: boolean) => void;
    onAlternatives?: () => void;
    settingsHref?: string;
    /** How fresh the live values are, for the card's last line. */
    status?: { text: string; offline?: boolean } | null;
    /** What else is live about the trip (the binding lifted, another connection followed), inside the card. */
    notices?: Snippet;
  }

  let { trip, day, now, stationMinutes, reason = null, position = null, location, onLocation, onAlternatives, settingsHref, status = null, notices }: Props = $props();

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
        return `On board${leg ? ` · ${legLabel(leg)}${leg.headsign ? ` → ${short(leg.headsign)}` : ""}` : ""}`;
      case "changing":
        return "Change trains";
      case "arrived":
        return "Arrived";
    }
  });

  /** How Abeona knows, behind the source pill. */
  let sourceDialog: HTMLDialogElement | undefined = $state();

  const sourceLabel = $derived(day.source === "onboard" ? "Train Wi-Fi" : day.source === "location" ? "Location" : "Timetable");

  /** "Frankfurt(Main)Hbf" → "Frankfurt": a station's town, for tight labels. */
  function short(name: string): string {
    return name.split("(")[0].split(" Hbf")[0].split(" Hauptbahnhof")[0].trim();
  }


  /** What the card leads with: a name, the time that goes with it, what to do there, and the countdown. */
  const lead = $derived.by(() => {
    const base = { stop: null as StopTime | null, icon: null as "flag" | "change" | null, platformAt: null as StopTime | null, line: null as Leg | null, note: null as string | null, minutes: null as number | null, cancelled: false };
    if (arrived || !leg) return { ...base, headline: trip.destination_name, stop: lastRide?.to ?? null, icon: "flag" as const };
    if (day.missedChange && day.kind !== "on_board") return { ...base, headline: `${legLabel(leg)} has left`, stop: leg.from, note: "Your ticket may allow another train." };
    if (leg.cancelled) return { ...base, headline: `${legLabel(leg)} ${leg.dropped_at ? "is not in the timetable" : "is cancelled"}`, stop: leg.from, cancelled: true };
    const platform = displayPlatform(leg.from);
    switch (day.kind) {
      case "before_leaving":
        // The station is the trip's start, in the title above; the time to go fits one line.
        return { ...base, headline: `Leave at ${formatTime(day.leaveAt)}`, stop: leg.from, line: leg, note: `${stationMinutes} min to the station`, minutes: day.minutesToLeave };
      case "at_station":
      case "changing":
        return { ...base, headline: platform ? `Platform ${platform}` : legLabel(leg), stop: leg.from, line: leg, minutes: day.minutesToDeparture };
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

  function inMinutes(minutes: number | null): string {
    if (minutes === null) return "";
    if (minutes <= 0) return "now";
    return `in ${formatDuration(minutes)}`;
  }

  // ------------------------------------------------------------ the ride

  const showRide = $derived(
    !!leg && !!progress && !leg.cancelled && !(day.missedChange && day.kind !== "on_board") && (day.kind === "on_board" || day.kind === "at_station" || day.kind === "changing"),
  );
  const onBoard = $derived(day.kind === "on_board");
  /** How far along the rail the train is: 0 until on board. */
  const rail = $derived(onBoard && progress ? progress.fraction : 0);
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
    agrees: "bg-secondary text-secondary-content",
    silent: "bg-base-200 text-base-content/50",
    off: "bg-base-200 text-base-content/40",
  };
</script>

<section class="space-y-4" aria-label="Live">
  <div
    class="space-y-4 rounded-[2rem] p-5 shadow-card sm:p-6 {warn ? 'bg-gradient-to-br from-amber-soft to-card' : 'bg-gradient-to-br from-mint to-card'}"
    aria-live="polite"
  >
    <!-- What state, and how Abeona knows. -->
    <div class="flex items-center gap-3">
      <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full {warn ? 'bg-amber text-amber-ink' : 'bg-primary text-primary-content'}">
        {#if warn}<TriangleAlert class="h-[18px] w-[18px]" />
        {:else if day.kind === "before_leaving"}<DoorOpen class="h-[18px] w-[18px]" />
        {:else if day.kind === "at_station"}<MapPin class="h-[18px] w-[18px]" />
        {:else if day.kind === "on_board"}<TrainFront class="h-[18px] w-[18px]" />
        {:else if day.kind === "changing"}<ArrowRightLeft class="h-[18px] w-[18px]" />
        {:else}<Flag class="h-[18px] w-[18px]" />{/if}
      </span>
      <p class="min-w-0 flex-1 truncate text-xs font-bold uppercase tracking-[0.14em] {warn ? 'text-amber-ink' : 'text-primary'}">{eyebrow}</p>
      {#if !arrived}
        <button
          type="button"
          class="inline-flex items-center gap-1.5 rounded-full bg-card/80 py-1 pl-2.5 pr-1.5 text-[0.7rem] font-semibold text-ink transition hover:bg-card focus-visible:outline-2 focus-visible:outline-primary"
          aria-haspopup="dialog"
          aria-label={`How Abeona knows: ${sourceLabel}`}
          onclick={() => sourceDialog?.showModal()}
        >
          {#if day.source === "onboard"}<Wifi class="h-3.5 w-3.5 text-primary" />{:else if day.source === "location"}<LocateFixed class="h-3.5 w-3.5 text-primary" />{:else}<Clock class="h-3.5 w-3.5 text-primary" />{/if}
          {sourceLabel}
          <ChevronDown class="h-3.5 w-3.5 text-base-content/50" />
        </button>
      {/if}
    </div>

    <!-- The one thing to know now. -->
    <div class="flex items-end gap-4">
      <div class="min-w-0 flex-1">
        <p class="text-2xl font-semibold tracking-tight text-ink sm:text-3xl">{lead.headline}</p>
        <div class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1.5">
          {#if lead.stop}
            <ShiftedTime planned={lead.stop.planned} current={lead.stop.current} cancelled={lead.cancelled} class="text-3xl font-semibold tracking-tight text-ink sm:text-4xl" />
          {/if}
          {#if lead.icon === "flag"}<Flag class="h-[18px] w-[18px] text-base-content/60" aria-label="Get off" />{:else if lead.icon === "change"}<ArrowRightLeft class="h-[18px] w-[18px] text-base-content/60" aria-label="Change" />{/if}
          {#if lead.platformAt && displayPlatform(lead.platformAt)}
            {@const p = lead.platformAt}
            <span class="tabular rounded-lg px-2 py-0.5 text-sm font-bold {platformChanged(p) ? 'bg-amber-soft text-amber-ink' : 'bg-secondary text-secondary-content'}" title="Platform">Pl. {displayPlatform(p)}</span>
          {/if}
          {#if lead.line}
            <span class="inline-flex items-center gap-1.5 text-sm text-base-content/70">
              <LegChip leg={lead.line} />
              {#if lead.line.headsign}→ {short(lead.line.headsign)}{/if}
            </span>
          {/if}
        </div>
        {#if lead.note}
          <p class="mt-1 text-sm text-base-content/70">
            {lead.note}{#if day.kind === "before_leaving" && settingsHref}&nbsp;·
              <a href={settingsHref} class="font-medium text-primary underline-offset-2 hover:underline">change</a>{/if}
          </p>
        {/if}
      </div>
      {#if countdown(lead.minutes)}
        {@const c = countdown(lead.minutes)!}
        <div class="shrink-0 text-right" aria-label={`${c.number} ${c.label}`}>
          <p class="tabular text-5xl font-semibold leading-none tracking-tighter sm:text-6xl {warn ? 'text-amber-ink' : 'text-primary'}">{c.number}</p>
          {#if c.label}<p class="mt-1 text-xs font-medium text-base-content/60">{c.label}</p>{/if}
        </div>
      {/if}
    </div>

    {#if day.kind === "changing" && change && !warn}
      <!-- The change being made: its margin and the way between platforms. -->
      <div class="flex flex-wrap items-center gap-2 text-sm">
        <ConfidenceChip confidence={change.confidence} />
        <span class="tabular text-base-content/70">
          {change.availableMinutes} min ·
          {#if change.walkMinutes > 0}<Footprints class="inline h-4 w-4" aria-label="Walk" /> {change.walkMinutes} min{:else if arriving && displayPlatform(arriving.to)}Pl. {displayPlatform(arriving.to)}{leg && displayPlatform(leg.from) ? ` → ${displayPlatform(leg.from)}` : ""}{/if}
        </span>
      </div>
    {/if}

    {#if showRide && leg && progress}
      <!-- The ride on a rail: every stop, behind filled, the train where it is. -->
      <div>
        <div class="relative mx-2.5 h-9" role="img" aria-label={`${Math.round(rail * 100)} % of the ride done`}>
          <div class="absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-base-content/15"></div>
          <div class="absolute left-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-primary" style="width: {rail * 100}%"></div>
          {#each progress.stops as stop, i (i)}
            {@const end = i === 0 || i === progress.stops.length - 1}
            {@const passed = onBoard && stop.fraction <= rail}
            <span
              class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full ring-2 ring-mint
                {end ? 'h-3.5 w-3.5' : 'h-2.5 w-2.5'}
                {passed ? 'bg-primary' : end ? 'border-2 border-primary bg-card' : 'border-2 border-base-content/30 bg-card'}"
              style="left: {stop.fraction * 100}%"
              title={stop.name}
            ></span>
          {/each}
          {#if onBoard}
            <span
              class="absolute top-1/2 flex h-7 w-7 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-primary text-primary-content shadow-card transition-[left] duration-700"
              style="left: {rail * 100}%"
              aria-hidden="true"
            >
              <TrainFront class="h-4 w-4" />
            </span>
          {/if}
        </div>
        <div class="mt-0.5 flex items-start justify-between gap-3 text-xs text-base-content/70">
          <span class="min-w-0">
            <span class="block truncate">{short(leg.from.name)}</span>
            <ShiftedTime planned={leg.from.planned} current={leg.from.current} block={false} />
          </span>
          <span class="text-right">
            {#if !onBoard}
              <span class="block truncate">{short(leg.to.name)}</span>
              <ShiftedTime planned={leg.to.planned} current={leg.to.current} block={false} />
            {/if}
            <span class="flex items-center justify-end gap-1" aria-label={`${onBoard ? progress.stopsLeft : progress.stops.length - 1} stops${onBoard ? " left" : ""}`}>
              <GitCommitHorizontal class="h-3.5 w-3.5" aria-hidden="true" />
              <span class="tabular" aria-hidden="true">{onBoard ? progress.stopsLeft : progress.stops.length - 1}</span>
            </span>
          </span>
        </div>
      </div>

      {#if nextStop}
        <!-- The stop coming up. -->
        <div class="flex items-center gap-3 rounded-2xl bg-card/60 px-4 py-3">
          {#if standing}<MapPin class="h-[18px] w-[18px] shrink-0 text-base-content/60" aria-label="Now at" />{:else}<ArrowRight class="h-[18px] w-[18px] shrink-0 text-base-content/60" aria-label="Next stop" />{/if}
          <span class="min-w-0 flex-1 truncate text-lg font-semibold text-ink">{nextStop.name}</span>
          <span class="text-right">
            <ShiftedTime planned={nextStop.planned} current={nextStop.current} block={false} class="text-lg font-semibold text-ink" />
            <span class="block text-xs text-base-content/60">{standing ? "leaves" : inMinutes(progress.minutesToNext)}</span>
          </span>
        </div>
      {/if}
    {/if}

    {#if then && thenLeg}
      <!-- After this ride: the next train, and where the trip ends. -->
      <div class="space-y-2 rounded-2xl bg-card/60 px-4 py-3 text-sm">
        <div class="flex items-center gap-2">
          {#if then.walkMinutes > 0}<Footprints class="h-[18px] w-[18px] text-base-content/60" aria-label="Then" />{:else}<ArrowRightLeft class="h-[18px] w-[18px] text-base-content/60" aria-label="Then" />{/if}
          <span class="min-w-0 flex-1 truncate text-base-content/70">{onBoard ? "" : thenLeg.from.name}</span>
          <ConfidenceChip confidence={then.confidence} />
          <span class="tabular text-xs font-semibold text-base-content/70">{then.availableMinutes} min</span>
        </div>
        <div class="flex items-center gap-2">
          <LegChip leg={thenLeg} />
          {#if displayPlatform(thenLeg.from)}
            <span class="tabular rounded-lg px-2 py-0.5 text-xs font-bold {platformChanged(thenLeg.from) ? 'bg-amber-soft text-amber-ink' : 'bg-secondary text-secondary-content'}">Pl. {displayPlatform(thenLeg.from)}</span>
          {/if}
          <span class="flex-1"></span>
          <ShiftedTime planned={thenLeg.from.planned} current={thenLeg.from.current} block={false} class="font-semibold text-ink" />
        </div>
        {#if lastRide}
          <div class="flex items-center gap-2 text-base-content/70">
            <Flag class="h-4 w-4" aria-label="Destination" />
            <span class="min-w-0 flex-1 truncate">{trip.destination_name}</span>
            <ShiftedTime planned={lastRide.to.planned} current={lastRide.to.current} block={false} class="text-ink" />
          </div>
        {/if}
      </div>
    {/if}

    {#if day.missedChange && day.kind === "on_board"}
      <p class="text-sm font-medium text-amber-ink">You are still on this train after your connection left.</p>
    {/if}
    {#if reason && onAlternatives}
      <button class="btn btn-sm rounded-full border-none bg-amber-soft text-amber-ink hover:brightness-95" onclick={onAlternatives}>
        <Shuffle class="h-4 w-4" /> See alternatives
      </button>
    {/if}

    {@render notices?.()}

    {#if status}
      <!-- How fresh it all is: small, last. -->
      <p class="flex items-center gap-1.5 text-xs text-base-content/60">
        {#if status.offline}<CloudOff class="h-3.5 w-3.5" aria-hidden="true" />{:else}<RefreshCw class="h-3.5 w-3.5 text-primary" aria-hidden="true" />{/if}
        {status.text}
      </p>
    {/if}
  </div>

  {#if !arrived}
    <TripMap {legs} {marker} focus={onBoard ? leg : null} />
  {/if}
</section>

<!-- What the source pill opens: each signal, the deciding one lit. -->
<dialog bind:this={sourceDialog} class="modal modal-bottom sm:modal-middle" aria-labelledby="how-abeona-knows">
  <div class="modal-box rounded-t-[2rem] sm:rounded-[2rem]">
    <h3 id="how-abeona-knows" class="text-lg font-semibold text-ink">
      {onBoard ? "How Abeona knows your train" : "How Abeona knows where you are"}
    </h3>
    <p class="mt-1 text-sm text-base-content/70">
      The strongest signal there is decides: the train's Wi-Fi, then your location, then the timetable with the live times.
    </p>
    <ul class="mt-3 space-y-3">
        <li class="flex items-center gap-3">
          <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full {tone.off}"><Wifi class="h-[18px] w-[18px]" /></span>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-ink">Train Wi-Fi</p>
            <p class="text-xs text-base-content/60">Read by the Android app on an ICE or Railjet: it names the train</p>
          </div>
        </li>
        <li class="flex items-center gap-3">
          <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full {tone[locationEvidence.state]}"><LocateFixed class="h-[18px] w-[18px]" /></span>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-ink">Location</p>
            <p class="text-xs text-base-content/60">{locationEvidence.detail}</p>
          </div>
          {#if locationEvidence.state === "decides"}
            <span class="rounded-full bg-mint px-2 py-0.5 text-[0.68rem] font-bold text-primary">Decides</span>
          {/if}
          {#if location === "off"}
            <button class="btn btn-xs rounded-full border-none bg-mint text-primary" onclick={() => onLocation(true)}>Use</button>
          {:else if location === "on" || location === "asking"}
            <button class="btn btn-ghost btn-xs rounded-full text-base-content/60" onclick={() => onLocation(false)}>Stop</button>
          {/if}
        </li>
        <li class="flex items-center gap-3">
          <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full {tone[timetableEvidence.state]}"><Clock class="h-[18px] w-[18px]" /></span>
          <div class="min-w-0 flex-1">
            <p class="text-sm font-medium text-ink">Timetable</p>
            <p class="text-xs text-base-content/60">{timetableEvidence.detail}</p>
          </div>
          {#if timetableEvidence.state === "decides"}
            <span class="rounded-full bg-mint px-2 py-0.5 text-[0.68rem] font-bold text-primary">Decides</span>
          {/if}
        </li>
      </ul>
    <div class="modal-action">
      <form method="dialog"><button class="btn btn-ghost rounded-full">Close</button></form>
    </div>
  </div>
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
