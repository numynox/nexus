<script lang="ts">
  /**
   * The next trip. On the travel day its day and title as plain text over
   * the live card's compact form; before it, the departure overview under
   * the scene (abeona-design.md §9, Trips).
   */
  import { ArrowRightLeft, CalendarX2, Clock, Flag, RefreshCw, Shuffle, Ticket } from "lucide-svelte";
  import type { Trip } from "../../lib/data";
  import { formatDuration, formatRelativeDay, formatShortDate, legLabel } from "../../lib/format";
  import { trainKind } from "../../lib/scene";
  import { summarise } from "../../lib/journey";
  import { trainBindingLifted } from "../../lib/transfer";
  import { alternativesOrigin, alternativesReason, type TravelDay } from "../../lib/travelday";
  import LiveDashboard from "../live/LiveDashboard.svelte";
  import BindingBanner from "../common/BindingBanner.svelte";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import Fact from "../common/Fact.svelte";
  import LegChip from "../common/LegChip.svelte";
  import PlatformBox from "../common/PlatformBox.svelte";
  import Scene from "../common/Scene.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import StatusChip from "../common/StatusChip.svelte";

  interface Props {
    trip: Trip;
    href: string;
    refreshing?: boolean;
    /** Where the traveller is on the trip, while it is live. */
    day?: TravelDay | null;
    stationMinutes?: number;
    /** The clock the day was worked out at. */
    now?: number;
  }

  let { trip, href, refreshing = false, day = null, stationMinutes = 15, now = Date.now() }: Props = $props();

  const s = $derived(summarise(trip.legs));
  const firstRide = $derived(s.rides[0] ?? trip.legs[0]);

  // Offered by themselves only while there is somewhere to search from (as on the trip page).
  const reason = $derived(day && alternativesOrigin(trip.legs, day, now) ? alternativesReason(trip.legs, day) : null);
  const bindingLifted = $derived(trainBindingLifted(trip.fare_type, trip.train_binding, s.arrivalDelay));
  const dayName = $derived(formatRelativeDay(trip.planned_departure));
  const date = $derived(formatShortDate(trip.planned_departure));
  /** A ride no longer in the timetable, before the travel day: the plan broke. */
  const dropped = $derived(trip.legs.some((l) => l.mode !== "walk" && !!l.dropped_at));
  const route = $derived(`${trip.origin_name} → ${trip.destination_name}`);
</script>

{#snippet when()}
  <p class="t-label flex items-center gap-2 text-brand">
    <span>{dayName}{dayName !== date ? `, ${date}` : ""}</span>
    {#if refreshing}
      <RefreshCw class="h-3.5 w-3.5 animate-spin" aria-label="Updating live times" />
    {:else if s.hasRealtime}
      <RefreshCw class="h-3.5 w-3.5" aria-label="Live" />
    {/if}
  </p>
{/snippet}

{#if day}
  <section class="space-y-2">
    <a {href} class="block px-1">
      {@render when()}
      <h2 class="t-title mt-0.5 text-ink">{trip.title ?? route}</h2>
    </a>
    <a {href} class="block rounded-3xl focus-visible:outline-2 focus-visible:outline-primary">
      <LiveDashboard summary {trip} {day} {now} {stationMinutes} {reason} />
    </a>
    {#if bindingLifted}<BindingBanner delay={s.arrivalDelay} />{/if}
  </section>
{:else}
  <a {href} class="block overflow-hidden rounded-3xl bg-card shadow-card transition hover:brightness-[0.99] focus-visible:outline-2 focus-visible:outline-primary">
    <Scene state={dropped || s.cancelled ? "stopped" : "parked"} size="header"
      train={firstRide ? trainKind(firstRide.product, legLabel(firstRide), firstRide.mode === "bus") : undefined}
      lat={firstRide?.from.lat} lon={firstRide?.from.lon} />
    <div class="space-y-2 p-4 pt-2">
      <div>
        {@render when()}
        <h2 class="t-title mt-0.5 text-ink">{trip.title ?? route}</h2>
      </div>

      <div class="flex items-center gap-3">
        <ShiftedTime planned={s.departure.planned} current={s.departure.current} cancelled={s.cancelled} class="t-time text-ink" />
        <span class="t-body min-w-0 flex-1 truncate text-ink">{s.departure.name}</span>
        {#if firstRide}
          <PlatformBox planned={firstRide.from.planned_platform} current={firstRide.from.platform} size="lead" />
        {/if}
      </div>

      <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
        <Fact icon={Flag} label="Arrives">
          <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} block={false} />
          {#if trip.title}<span>{s.arrival.name}</span>{/if}
        </Fact>
        <Fact icon={Clock} label="Duration" value={formatDuration(s.plannedMinutes)} />
        <Fact icon={ArrowRightLeft} label={s.changes === 0 ? "Direct, changes" : "Changes"} value={s.changes} />
        <span class="flex flex-wrap items-center gap-1">
          {#each s.rides as ride, i (i)}<LegChip leg={ride} />{/each}
        </span>
        {#if s.confidence && s.changes > 0}<ConfidenceChip confidence={s.confidence} />{/if}
        {#if trip.ticket_path}
          <span class="text-muted" role="img" aria-label="Ticket attached" title="Ticket attached"><Ticket class="h-4 w-4" aria-hidden="true" /></span>
        {/if}
      </div>

      {#if dropped && trip.missed_leg_idx === null}
        <!-- The plan broke before the day: what broke is struck above; the trip has the connections. -->
        <div class="flex flex-wrap items-center gap-2 pt-1">
          <StatusChip tone="bad" icon={CalendarX2} label="Timetable changed" />
          <span class="btn btn-sm rounded-full border-none bg-bad text-bad-content"><Shuffle class="h-4 w-4" aria-hidden="true" /> Find another connection</span>
        </div>
      {/if}

      {#if bindingLifted}<BindingBanner delay={s.arrivalDelay} />{/if}
    </div>
  </a>
{/if}
