<script lang="ts">
  import { ArrowRightLeft, DoorOpen, Flag, Footprints, MapPin, Shuffle, TrainFront, TriangleAlert } from "lucide-svelte";
  import { displayPlatform, formatDuration, formatTime, legLabel, stopDelay } from "../../lib/format";
  import { effectiveTime, transfers } from "../../lib/transfer";
  import type { AlternativesReason, TravelDay } from "../../lib/travelday";
  import type { Leg } from "../../lib/transit";
  import ConfidenceChip from "./ConfidenceChip.svelte";
  import DelayBadge from "./DelayBadge.svelte";

  interface Props {
    legs: Leg[];
    day: TravelDay;
    stationMinutes: number;
    /** Why alternatives are worth a look, if they are. */
    reason?: AlternativesReason | null;
    /** Where the time to the station is changed; omitted inside a link. */
    settingsHref?: string;
    onAlternatives?: () => void;
  }

  let { legs, day, stationMinutes, reason = null, settingsHref, onAlternatives }: Props = $props();

  const leg = $derived(day.legIndex !== null ? legs[day.legIndex] : null);
  const from = $derived(day.fromLegIndex !== null ? legs[day.fromLegIndex] : null);
  const last = $derived(legs.findLast((l) => l.mode !== "walk") ?? legs[legs.length - 1]);
  // The next change from here: after the ride on board, or the one being made.
  const change = $derived(
    day.legIndex === null
      ? null
      : (transfers(legs).find((t) => (day.kind === "on_board" ? t.fromIndex === day.legIndex : t.toIndex === day.legIndex)) ?? null),
  );
  const platform = $derived(leg ? displayPlatform(leg.from) : null);

  function inMinutes(minutes: number | null): string {
    if (minutes === null) return "";
    if (minutes <= 0) return "now";
    return `in ${formatDuration(minutes)}`;
  }

  const eyebrow = $derived.by(() => {
    if (day.source === "onboard") return "On board · from the train's Wi-Fi";
    if (day.missedChange) return "Looks like you missed it";
    switch (day.kind) {
      case "before_leaving":
        return "Time to leave";
      case "at_station":
        return day.source === "location" ? "Looks like you're at the station" : "Head to the platform";
      case "on_board":
        return day.source === "location" ? "Looks like you're on board" : "On board";
      case "changing":
        return day.source === "location" ? "Looks like you're changing trains" : "Change trains";
      case "arrived":
        return "Arrived";
    }
  });

  const tone = $derived(
    day.missedChange || day.cancelled ? "amber" : day.kind === "arrived" ? "calm" : "live",
  );
</script>

<div class="rounded-3xl bg-card p-5 shadow-card sm:p-6" aria-live="polite">
  <div class="flex items-start gap-4">
    <span
      class="flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl
        {tone === 'amber' ? 'bg-amber-soft text-amber-ink' : 'bg-mint text-primary'}"
    >
      {#if day.missedChange || day.cancelled}
        <TriangleAlert class="h-5 w-5" />
      {:else if day.kind === "before_leaving"}
        <DoorOpen class="h-5 w-5" />
      {:else if day.kind === "at_station"}
        <MapPin class="h-5 w-5" />
      {:else if day.kind === "on_board"}
        <TrainFront class="h-5 w-5" />
      {:else if day.kind === "changing"}
        <ArrowRightLeft class="h-5 w-5" />
      {:else}
        <Flag class="h-5 w-5" />
      {/if}
    </span>

    <div class="min-w-0 flex-1">
      <p class="text-xs font-semibold uppercase tracking-[0.12em] {tone === 'amber' ? 'text-amber-ink' : 'text-primary'}">{eyebrow}</p>

      {#if day.kind === "before_leaving" && leg}
        <p class="tabular mt-1 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
          {day.minutesToLeave !== null && day.minutesToLeave <= 180 ? `Leave ${inMinutes(day.minutesToLeave)}` : `Leave at ${formatTime(day.leaveAt)}`}
        </p>
        <p class="mt-1 text-sm text-base-content/70">
          {legLabel(leg)} leaves {leg.from.name} at {formatTime(effectiveTime(leg.from))}{platform ? ` from platform ${platform}` : ""}.
          {stationMinutes} min to the station{#if settingsHref}&nbsp;·
            <a href={settingsHref} class="font-medium text-primary underline-offset-2 hover:underline">change</a>{/if}
        </p>
      {:else if (day.kind === "at_station" || day.kind === "changing") && leg}
        {#if day.missedChange}
          <p class="mt-1 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">{legLabel(leg)} has left</p>
          <p class="mt-1 text-sm text-base-content/70">
            It left {leg.from.name} at {formatTime(effectiveTime(leg.from))}. Your ticket may allow another train.
          </p>
        {:else if day.cancelled}
          <p class="mt-1 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">{legLabel(leg)} is cancelled</p>
          <p class="mt-1 text-sm text-base-content/70">It was to leave {leg.from.name} at {formatTime(leg.from.planned)}.</p>
        {:else}
          <p class="tabular mt-1 text-3xl font-semibold tracking-tight text-ink sm:text-4xl">
            {platform ? `Platform ${platform}` : legLabel(leg)}
          </p>
          <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-base-content/70">
            <span>
              {legLabel(leg)}{leg.headsign ? ` to ${leg.headsign}` : ""} leaves {day.kind === "changing" ? `${leg.from.name} ` : ""}{inMinutes(day.minutesToDeparture)}, {formatTime(effectiveTime(leg.from))}
            </span>
            <DelayBadge minutes={stopDelay(leg.from)} />
          </p>
          {#if day.kind === "changing" && change}
            <div class="mt-3 flex flex-wrap items-center gap-2 text-sm">
              <ConfidenceChip confidence={change.confidence} />
              <span class="text-base-content/70">
                {change.walkMinutes > 0 ? `Walk ${change.walkMinutes} min` : from ? `From platform ${displayPlatform(from.to) ?? "–"}` : ""}
              </span>
            </div>
          {/if}
        {/if}
      {:else if day.kind === "on_board" && leg}
        <p class="mt-1 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">
          {legLabel(leg)}{leg.headsign ? ` to ${leg.headsign}` : ""}
        </p>
        <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-base-content/70">
          <span>
            {change ? "Get off at" : "Arrives at"} {leg.to.name} {inMinutes(day.minutesToArrival)}, {formatTime(effectiveTime(leg.to))}{displayPlatform(leg.to) ? ` · platform ${displayPlatform(leg.to)}` : ""}
          </span>
          <DelayBadge minutes={stopDelay(leg.to)} />
        </p>
        {#if change}
          {@const next = legs[change.toIndex]}
          <div class="mt-3 flex flex-wrap items-center gap-2 rounded-2xl bg-base-200/70 px-3 py-2 text-sm">
            {#if change.walkMinutes > 0}<Footprints class="h-4 w-4 text-base-content/60" />{:else}<Shuffle class="h-4 w-4 text-base-content/60" />{/if}
            <span class="min-w-0 flex-1">
              Then {legLabel(next)} at {formatTime(effectiveTime(next.from))}{displayPlatform(next.from) ? `, platform ${displayPlatform(next.from)}` : ""} · {change.availableMinutes} min to change
            </span>
            <ConfidenceChip confidence={change.confidence} />
          </div>
        {/if}
        {#if day.missedChange}
          <p class="mt-2 text-sm font-medium text-amber-ink">You are still on this train after your connection left.</p>
        {/if}
      {:else if day.kind === "arrived" && last}
        <p class="mt-1 text-2xl font-semibold tracking-tight text-ink sm:text-3xl">{last.to.name}</p>
        <p class="mt-1 flex items-center gap-2 text-sm text-base-content/70">
          <span>At {formatTime(effectiveTime(last.to))}</span>
          <DelayBadge minutes={stopDelay(last.to)} onTimeLabel={last.realtime} />
        </p>
      {/if}

      {#if reason && onAlternatives}
        <button class="btn btn-sm mt-4 rounded-full border-none bg-amber-soft text-amber-ink hover:brightness-95" onclick={onAlternatives}>
          <Shuffle class="h-4 w-4" /> See alternatives
        </button>
      {/if}
    </div>
  </div>
</div>
