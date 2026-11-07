<script lang="ts">
  /**
   * The live dashboard of a travel day — the web's copy of Android's
   * ui/live/LiveDashboard.kt. The one thing to do now with its countdown,
   * then as far as they matter in this state: where on the ride the train
   * is (next stop, stops left), the map with the traveller on it, the next
   * change, the whole journey, and how Abeona knows which train you are on.
   */
  import {
    ArrowRight,
    ArrowRightLeft,
    CircleCheck,
    Clock,
    DoorOpen,
    Flag,
    Footprints,
    LocateFixed,
    MapPin,
    Shuffle,
    TrainFront,
    TriangleAlert,
    Wifi,
  } from "lucide-svelte";
  import type { Trip } from "../../lib/data";
  import { displayPlatform, formatDuration, formatTime, legLabel, platformChanged, stopDelay } from "../../lib/format";
  import { distanceMetres, distanceToLineMetres, NEAR_STOP_METRES, ON_LINE_METRES, type Position } from "../../lib/travelday";
  import type { LocationState } from "../../lib/position";
  import { type MapMarker, rideProgress } from "../../lib/progress";
  import { effectiveTime, transfers } from "../../lib/transfer";
  import type { AlternativesReason, TravelDay } from "../../lib/travelday";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import DelayBadge from "../common/DelayBadge.svelte";
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
  }

  let { trip, day, now, stationMinutes, reason = null, position = null, location, onLocation, onAlternatives, settingsHref }: Props = $props();

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
        return `On board${leg ? ` · ${legLabel(leg)}` : ""}`;
      case "changing":
        return "Change trains";
      case "arrived":
        return "Arrived";
    }
  });

  const sourceLabel = $derived(day.source === "onboard" ? "Train Wi-Fi" : day.source === "location" ? "Location" : "Timetable");

  /** Headline, the line under it, its delay, and the countdown with what it counts to. */
  const now_ = $derived.by(() => {
    if (arrived || !leg) {
      return {
        headline: trip.destination_name,
        detail: `Arrived at ${formatTime(lastRide ? effectiveTime(lastRide.to) : null)}`,
        delay: lastRide ? stopDelay(lastRide.to) : 0,
        minutes: null as number | null,
        unit: "",
      };
    }
    if (day.missedChange && day.kind !== "on_board") {
      return {
        headline: `${legLabel(leg)} has left`,
        detail: `It left ${leg.from.name} at ${formatTime(effectiveTime(leg.from))}. Your ticket may allow another train.`,
        delay: 0,
        minutes: null,
        unit: "",
      };
    }
    if (leg.cancelled) {
      return { headline: `${legLabel(leg)} is cancelled`, detail: `It was to leave ${leg.from.name} at ${formatTime(leg.from.planned)}.`, delay: 0, minutes: null, unit: "" };
    }
    const platform = displayPlatform(leg.from);
    switch (day.kind) {
      case "before_leaving":
        return {
          headline: `Leave for ${leg.from.name}`,
          detail: [legLabel(leg), platform ? `Pl. ${platform}` : null, formatTime(effectiveTime(leg.from)), `${stationMinutes} min to the station`]
            .filter(Boolean)
            .join(" · "),
          delay: stopDelay(leg.from),
          minutes: day.minutesToLeave,
          unit: "to leave",
        };
      case "at_station":
      case "changing":
        return {
          headline: platform ? `Platform ${platform}` : legLabel(leg),
          detail: `${legLabel(leg)}${leg.headsign ? ` to ${leg.headsign}` : ""} · ${formatTime(effectiveTime(leg.from))}`,
          delay: stopDelay(leg.from),
          minutes: day.minutesToDeparture,
          unit: "to departure",
        };
      default:
        return {
          headline: leg.to.name,
          detail: [
            change ? "Change here" : leg.to.name === trip.destination_name ? "Your stop" : "Get off here",
            formatTime(effectiveTime(leg.to)),
            displayPlatform(leg.to) ? `Pl. ${displayPlatform(leg.to)}` : null,
          ]
            .filter(Boolean)
            .join(" · "),
          delay: stopDelay(leg.to),
          minutes: day.minutesToArrival,
          unit: "to arrival",
        };
    }
  });

  function countdown(minutes: number | null): { number: string; label: string } | null {
    if (minutes === null) return null;
    if (minutes <= 0) return { number: "now", label: "" };
    if (minutes < 60) return { number: String(minutes), label: "min" };
    return { number: `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, "0")}`, label: "h" };
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
  const ahead = $derived.by(() => {
    if (!progress) return [] as number[];
    const from = progress.next ?? progress.stops.length;
    const all: number[] = [];
    for (let i = from; i < progress.stops.length; i++) all.push(i);
    return all;
  });
  const shownAhead = $derived(ahead.length > 5 ? [...ahead.slice(0, 3), ahead[ahead.length - 1]] : ahead);

  // ------------------------------------------------------------ the change

  const departing = $derived(change ? legs[change.toIndex] : null);
  const arriving = $derived(change ? legs[change.fromIndex] : null);
  const laterChanges = $derived(change ? transfers(legs).filter((t) => t.fromIndex > change.fromIndex).length : 0);
  const leavesIn = $derived.by(() => {
    const t = departing ? effectiveTime(departing.from) : null;
    return t ? Math.ceil((Date.parse(t) - now) / 60_000) : null;
  });

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
  <!-- Now: the one thing to do, and how long until it. -->
  <div
    class="rounded-[2rem] p-5 shadow-card sm:p-6 {warn ? 'bg-gradient-to-br from-amber-soft to-card' : 'bg-gradient-to-br from-mint to-card'}"
    aria-live="polite"
  >
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
        <span class="inline-flex items-center gap-1.5 rounded-full bg-card/80 px-2.5 py-1 text-[0.7rem] font-semibold text-ink" title="What placed you on your trip">
          {#if day.source === "onboard"}<Wifi class="h-3.5 w-3.5 text-primary" />{:else if day.source === "location"}<LocateFixed class="h-3.5 w-3.5 text-primary" />{:else}<Clock class="h-3.5 w-3.5 text-primary" />{/if}
          {sourceLabel}
        </span>
      {/if}
    </div>

    <div class="mt-4 flex items-end gap-4">
      <div class="min-w-0 flex-1">
        <p class="tabular text-3xl font-semibold tracking-tight text-ink sm:text-4xl">{now_.headline}</p>
        <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-base-content/70">
          <span>{now_.detail}</span>
          <DelayBadge minutes={now_.delay} />
          {#if day.kind === "before_leaving" && settingsHref}
            <a href={settingsHref} class="font-medium text-primary underline-offset-2 hover:underline">change</a>
          {/if}
        </p>
      </div>
      {#if countdown(now_.minutes)}
        {@const c = countdown(now_.minutes)!}
        <div class="shrink-0 text-right" aria-label={`${c.number} ${c.label} ${now_.unit}`}>
          <p class="tabular text-5xl font-semibold leading-none tracking-tighter sm:text-6xl {warn ? 'text-amber-ink' : 'text-primary'}">{c.number}</p>
          <p class="mt-1 text-xs font-medium text-base-content/60">{[c.label, now_.unit].filter(Boolean).join(" ")}</p>
        </div>
      {/if}
    </div>

    {#if day.kind === "changing" && change && !warn}
      <div class="mt-3 flex flex-wrap items-center gap-2 text-sm">
        <ConfidenceChip confidence={change.confidence} />
        <span class="tabular text-base-content/70">
          {change.availableMinutes} min ·
          {change.walkMinutes > 0
            ? `walk ${change.walkMinutes} min`
            : arriving && displayPlatform(arriving.to)
              ? `from Pl. ${displayPlatform(arriving.to)}${leg && displayPlatform(leg.from) ? ` → Pl. ${displayPlatform(leg.from)}` : ""}`
              : ""}
        </span>
      </div>
    {/if}
    {#if day.missedChange && day.kind === "on_board"}
      <p class="mt-3 text-sm font-medium text-amber-ink">You are still on this train after your connection left.</p>
    {/if}
    {#if reason && onAlternatives}
      <button class="btn btn-sm mt-4 rounded-full border-none bg-amber-soft text-amber-ink hover:brightness-95" onclick={onAlternatives}>
        <Shuffle class="h-4 w-4" /> See alternatives
      </button>
    {/if}
  </div>

  {#if !arrived}
    <div class="grid gap-4 lg:grid-cols-2 lg:items-start">
      <div class="space-y-4">
        {#if showRide && leg && progress}
          <!-- The ride: next stop, stops left, the train on its line. -->
          <div class="rounded-3xl bg-card p-5 shadow-card">
            <div class="flex items-center gap-2">
              <LegChip {leg} size="md" />
              <span class="min-w-0 flex-1 truncate text-sm font-medium text-ink">to {leg.headsign ?? leg.to.name}</span>
              <span class="tabular text-sm font-semibold text-primary">
                {#if progress.stopsLeft === 0}Arrived{:else if !onBoard}{progress.stops.length - 1} stops{:else if progress.stopsLeft === 1}Last stop{:else}{progress.stopsLeft} stops left{/if}
              </span>
            </div>

            {#if onBoard && progress.next !== null}
              {@const stop = standing ? progress.stops[progress.current ?? 0] : progress.stops[progress.next]}
              <div class="mt-4 flex items-end gap-3">
                <div class="min-w-0 flex-1">
                  <p class="text-[0.68rem] font-bold uppercase tracking-[0.12em] text-base-content/50">
                    {standing ? "Now at" : progress.next === progress.stops.length - 1 ? "Next stop · get off" : "Next stop"}
                  </p>
                  <p class="truncate text-xl font-semibold text-ink">{stop.name}</p>
                </div>
                <div class="text-right">
                  <p class="tabular text-xl font-semibold text-ink">{formatTime(new Date(standing ? stop.departure : stop.arrival).toISOString())}</p>
                  <p class="text-xs text-base-content/60">{standing ? "leaves" : inMinutes(progress.minutesToNext)}</p>
                </div>
              </div>
            {/if}

            <!-- Every stop on a line; behind filled, the train where it is. -->
            <div class="relative mx-2.5 mt-5 h-9" role="img" aria-label={`${Math.round(rail * 100)} % of the ride done`}>
              <div class="absolute inset-x-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-base-300"></div>
              <div class="absolute left-0 top-1/2 h-1 -translate-y-1/2 rounded-full bg-primary" style="width: {rail * 100}%"></div>
              {#each progress.stops as stop, i (i)}
                {@const end = i === 0 || i === progress.stops.length - 1}
                {@const passed = onBoard && stop.fraction <= rail}
                <span
                  class="absolute top-1/2 -translate-x-1/2 -translate-y-1/2 rounded-full ring-2 ring-card
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

            {#if ahead.length > 1 || !onBoard}
              <ol class="mt-3 space-y-1.5">
                {#each shownAhead as i, k (i)}
                  {#if k === 3 && ahead.length > 5}
                    <li class="pl-[3.75rem] text-xs text-base-content/50">· · · {ahead.length - 4} more</li>
                  {/if}
                  {@const stop = progress.stops[i]}
                  {@const last = i === progress.stops.length - 1}
                  <li class="flex items-center gap-3 text-sm">
                    <span class="tabular w-11 text-base-content/60">{formatTime(new Date(stop.arrival).toISOString())}</span>
                    <span class="rounded-full {last ? 'h-2.5 w-2.5 bg-primary' : 'h-2 w-2 bg-base-content/30'}"></span>
                    <span class="truncate {last ? 'font-semibold text-ink' : 'text-base-content/80'}">{stop.name}</span>
                  </li>
                {/each}
              </ol>
            {/if}
          </div>
        {/if}

        {#if onBoard && change && departing && arriving}
          <!-- The next change, from the train before it. -->
          <div class="rounded-3xl bg-card p-5 shadow-card">
            <div class="flex items-center gap-3">
              <ArrowRightLeft class="h-5 w-5 text-primary" />
              <div class="min-w-0 flex-1">
                <p class="text-[0.68rem] font-bold uppercase tracking-[0.12em] text-base-content/50">Your change</p>
                <p class="truncate font-semibold text-ink">{departing.from.name}</p>
              </div>
              <span class="flex items-center gap-1.5">
                <ConfidenceChip confidence={change.confidence} />
                <span class="tabular text-xs font-semibold text-base-content/70">{change.availableMinutes} min</span>
              </span>
            </div>
            <div class="mt-4 grid grid-cols-[1fr_auto_1fr] items-center gap-3">
              <div class="flex items-center gap-2.5">
                <div>
                  <p class="text-[0.68rem] font-bold uppercase tracking-[0.12em] text-base-content/50">Arrive</p>
                  <p class="tabular flex items-center gap-1.5 text-xl font-semibold text-ink">
                    {formatTime(effectiveTime(arriving.to))} <DelayBadge minutes={stopDelay(arriving.to)} />
                  </p>
                </div>
                {#if displayPlatform(arriving.to)}
                  <span class="tabular rounded-xl px-2.5 py-1 text-center text-sm font-bold {platformChanged(arriving.to) ? 'bg-amber-soft text-amber-ink' : 'bg-secondary text-secondary-content'}">
                    <span class="block text-[0.6rem] font-medium">Pl.</span>{displayPlatform(arriving.to)}
                  </span>
                {/if}
              </div>
              <div class="flex flex-col items-center text-base-content/50">
                {#if change.walkMinutes > 0}<Footprints class="h-5 w-5" />{:else}<ArrowRight class="h-5 w-5" />{/if}
                <span class="tabular text-[0.68rem]">{change.walkMinutes > 0 ? change.walkMinutes : change.requiredMinutes} min</span>
              </div>
              <div class="flex items-center justify-end gap-2.5">
                {#if displayPlatform(departing.from)}
                  <span class="tabular rounded-xl px-2.5 py-1 text-center text-sm font-bold {platformChanged(departing.from) ? 'bg-amber-soft text-amber-ink' : 'bg-secondary text-secondary-content'}">
                    <span class="block text-[0.6rem] font-medium">Pl.</span>{displayPlatform(departing.from)}
                  </span>
                {/if}
                <div class="text-right">
                  <p class="text-[0.68rem] font-bold uppercase tracking-[0.12em] text-base-content/50">Depart</p>
                  <p class="tabular flex items-center justify-end gap-1.5 text-xl font-semibold text-ink">
                    {formatTime(effectiveTime(departing.from))} <DelayBadge minutes={stopDelay(departing.from)} />
                  </p>
                </div>
              </div>
            </div>
            <div class="mt-4 flex items-center gap-2 border-t border-base-200 pt-3 text-sm text-base-content/70">
              <LegChip leg={departing} />
              <span class="truncate">
                {[departing.headsign ? `to ${departing.headsign}` : null, leavesIn !== null ? `leaves ${inMinutes(leavesIn)}` : null].filter(Boolean).join(" · ")}
              </span>
            </div>
            {#if laterChanges > 0}
              <p class="mt-2 text-xs text-base-content/60">{laterChanges === 1 ? "Then one more change" : `Then ${laterChanges} more changes`}</p>
            {/if}
          </div>
        {/if}
      </div>

      <div class="space-y-4">
        <TripMap {legs} {marker} focus={onBoard ? leg : null} />

        <!-- The whole journey: behind, now, ahead. -->
        <div class="rounded-3xl bg-card p-5 shadow-card">
          <div class="flex items-center gap-2">
            <p class="flex-1 text-[0.68rem] font-bold uppercase tracking-[0.12em] text-base-content/50">
              Your journey{rides.length === 1 ? " · direct" : day.legIndex !== null && rides.findIndex(({ i }) => i === day.legIndex) >= 0 ? ` · ride ${rides.findIndex(({ i }) => i === day.legIndex) + 1} of ${rides.length}` : ""}
            </p>
            <span class="tabular text-sm font-semibold text-ink">{trip.destination_name} {formatTime(lastRide ? effectiveTime(lastRide.to) : null)}</span>
            {#if lastRide}<DelayBadge minutes={stopDelay(lastRide.to)} />{/if}
          </div>
          <div class="mt-3 flex flex-wrap items-center gap-1.5">
            {#each rides as { l, i }, k (i)}
              {@const done = day.legIndex !== null && i < day.legIndex}
              {@const current = i === day.legIndex && onBoard}
              <span class="inline-flex items-center gap-1 rounded-full {current ? 'bg-primary/15 p-0.5' : ''}">
                {#if done}<CircleCheck class="h-4 w-4 text-primary" />{/if}
                <LegChip leg={l} />
              </span>
              {#if k < rides.length - 1}
                <span class="text-xs text-base-content/50">{l.to.name.split("(")[0].trim()}</span>
              {/if}
            {/each}
            <Flag class="h-4 w-4 text-base-content/50" />
          </div>
        </div>

        <!-- How Abeona knows: each signal, the deciding one lit. -->
        <div class="rounded-3xl bg-card p-5 shadow-card">
          <p class="text-[0.68rem] font-bold uppercase tracking-[0.12em] text-base-content/50">
            {onBoard ? "How Abeona knows your train" : "How Abeona knows where you are"}
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
        </div>
      </div>
    </div>
  {/if}
</section>
