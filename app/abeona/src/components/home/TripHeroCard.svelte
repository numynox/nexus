<script lang="ts">
  import { ArrowRight, Ticket } from "lucide-svelte";
  import type { Trip } from "../../lib/data";
  import {
    displayPlatform,
    formatDuration,
    formatRelativeDay,
    formatShortDate,
    formatTime,
    platformChanged,
  } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import { trainBindingLifted } from "../../lib/transfer";
  import type { TravelDay } from "../../lib/travelday";
  import TravelDayHero from "../common/TravelDayHero.svelte";
  import BindingBanner from "../common/BindingBanner.svelte";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import DelayBadge from "../common/DelayBadge.svelte";
  import Illustration from "../common/Illustration.svelte";
  import LegChip from "../common/LegChip.svelte";

  interface Props {
    trip: Trip;
    href: string;
    refreshing?: boolean;
    /** Where the traveller is on the trip, while it is live. */
    day?: TravelDay | null;
    stationMinutes?: number;
  }

  let { trip, href, refreshing = false, day = null, stationMinutes = 15 }: Props = $props();

  const s = $derived(summarise(trip.legs));
  const firstRide = $derived(s.rides[0] ?? trip.legs[0]);
  const platform = $derived(firstRide ? displayPlatform(firstRide.from) : null);
  const bindingLifted = $derived(trainBindingLifted(trip.fare_type, trip.train_binding, s.arrivalDelay));
  const dayName = $derived(formatRelativeDay(trip.planned_departure));
  const date = $derived(formatShortDate(trip.planned_departure));
</script>

<a
  {href}
  class="group relative block overflow-hidden rounded-[2rem] bg-mint text-ink transition hover:brightness-[0.99] focus-visible:outline-2 focus-visible:outline-primary"
>
  <div class="relative z-10 p-6 sm:p-8">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <p class="text-xs font-semibold sm:text-sm uppercase tracking-[0.12em] text-primary">
        Next trip · {dayName}{dayName !== date ? `, ${date}` : ""}
      </p>
      <div class="flex items-center gap-2">
        {#if refreshing}
          <span class="loading loading-dots loading-xs text-primary" aria-label="Updating live times"></span>
        {:else if s.hasRealtime}
          <span class="inline-flex items-center gap-1.5 text-xs font-medium text-primary">
            <span class="relative flex h-2 w-2">
              <span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-50"></span>
              <span class="relative inline-flex h-2 w-2 rounded-full bg-primary"></span>
            </span>
            Live
          </span>
        {/if}
        {#if s.confidence && s.changes > 0}
          <ConfidenceChip confidence={s.confidence} label={s.changes === 1 ? "Change" : "Changes"} />
        {/if}
      </div>
    </div>

    {#if trip.title}
      <h2 class="mt-2 text-lg font-semibold">{trip.title}</h2>
    {/if}

    {#if day}
      <div class="mt-5"><TravelDayHero legs={trip.legs} {day} {stationMinutes} /></div>
    {/if}

    <div class="mt-6 grid grid-cols-[1fr_auto_1fr] items-start gap-3 sm:gap-6">
      <div class="min-w-0">
        <div class="flex flex-col items-start gap-1 sm:flex-row sm:items-center sm:gap-2">
          <span class="tabular text-[2rem] leading-none font-semibold tracking-tight sm:text-6xl">{formatTime(s.departure.planned)}</span>
          <DelayBadge minutes={s.departureDelay} cancelled={s.cancelled} size="md" />
        </div>
        <p class="mt-2 text-sm leading-snug font-medium sm:truncate sm:text-lg">{s.departure.name}</p>
      </div>

      <div class="flex flex-col items-center pt-3 text-base-content/55 sm:pt-5">
        <span class="text-xs font-medium whitespace-nowrap">{formatDuration(s.plannedMinutes)}</span>
        <span class="mt-1 flex items-center">
          <span class="h-px w-4 bg-current sm:w-12"></span>
          <ArrowRight class="h-4 w-4" />
        </span>
        <span class="mt-1 text-xs whitespace-nowrap">
          {s.changes === 0 ? "Direct" : s.changes === 1 ? "1 change" : `${s.changes} changes`}
        </span>
      </div>

      <div class="min-w-0 text-right">
        <div class="flex flex-col-reverse items-end gap-1 sm:flex-row sm:items-center sm:justify-end sm:gap-2">
          <DelayBadge minutes={s.arrivalDelay} size="md" />
          <span class="tabular text-[2rem] leading-none font-semibold tracking-tight sm:text-6xl">{formatTime(s.arrival.planned)}</span>
        </div>
        <p class="mt-2 text-sm leading-snug font-medium sm:truncate sm:text-lg">{s.arrival.name}</p>
      </div>
    </div>

    <div class="mt-6 flex flex-wrap items-center gap-3">
      <div class="rounded-2xl bg-card px-4 py-2.5 shadow-card">
        <p class="text-[0.7rem] font-semibold uppercase tracking-[0.12em] text-base-content/55">Platform</p>
        <p class="tabular text-2xl font-semibold leading-tight {firstRide && platformChanged(firstRide.from) ? 'text-amber-ink' : ''}">
          {platform ?? "–"}
        </p>
      </div>
      <div class="flex min-w-0 flex-1 flex-wrap items-center gap-1.5">
        {#each s.rides as ride, i (i)}
          {#if i > 0}<span class="text-base-content/35">›</span>{/if}
          <LegChip leg={ride} size="md" />
        {/each}
      </div>
      {#if trip.ticket_path}
        <span class="inline-flex items-center gap-1.5 rounded-full bg-card px-3 py-1.5 text-xs font-semibold text-primary shadow-card">
          <Ticket class="h-3.5 w-3.5" /> Ticket
        </span>
      {/if}
    </div>

    {#if bindingLifted}
      <div class="mt-5"><BindingBanner delay={s.arrivalDelay} /></div>
    {/if}
  </div>

  <div class="pointer-events-none -mt-2 flex justify-end overflow-hidden">
    <Illustration
      name="train-hero"
      class="w-72 max-w-none translate-x-16 transition-transform duration-700 group-hover:translate-x-10 sm:w-[26rem] sm:translate-x-20"
    />
  </div>
</a>
