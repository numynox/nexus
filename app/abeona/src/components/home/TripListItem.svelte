<script lang="ts">
  /** An upcoming trip as one dense row: day and time left, title and trains, what is wrong and the ticket right. */
  import { Ban, CalendarClock, CalendarX2, Clock, Flag, Ticket } from "lucide-svelte";
  import type { Trip } from "../../lib/data";
  import { formatDuration } from "../../lib/format";
  import { tripFollowedLegs } from "../../lib/followed";
  import { summarise } from "../../lib/journey";
  import Fact from "../common/Fact.svelte";
  import LegChip from "../common/LegChip.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import StatusChip from "../common/StatusChip.svelte";

  interface Props {
    trip: Trip;
    href: string;
  }

  let { trip, href }: Props = $props();

  // As travelled: a connection followed instead (taken, or adjusted for a timetable change) counts.
  const s = $derived(summarise(tripFollowedLegs(trip)));
  /** A ride no longer in the timetable: adjusted when Abeona or someone took a connection instead. */
  const changed = $derived(
    trip.legs.some((l) => !!l.dropped_at) ? (trip.missed_leg_idx !== null ? "Adjusted" : "Timetable changed") : null,
  );
  const when = $derived(new Date(trip.planned_departure));
</script>

<a {href} class="grid min-h-12 grid-cols-[3.25rem_minmax(0,1fr)_auto] items-center gap-x-3 px-4 py-2 transition-colors hover:bg-well">
  <span class="leading-tight">
    <span class="t-meta block">{when.toLocaleDateString("en-GB", { day: "numeric", month: "short" })}</span>
    <ShiftedTime planned={s.departure.planned} current={s.departure.current} block={false} cancelled={s.cancelled} class="t-body flex-wrap gap-y-0 font-semibold text-ink" />
  </span>

  <span class="min-w-0">
    <span class="flex min-w-0 items-baseline gap-2">
      <span class="t-body truncate font-semibold text-ink">{trip.title ?? `${trip.origin_name} → ${trip.destination_name}`}</span>
      {#if trip.title}<span class="t-meta hidden truncate sm:inline">{trip.origin_name} → {trip.destination_name}</span>{/if}
    </span>
    <span class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1">
      {#each s.rides as ride, i (i)}<LegChip leg={ride} />{/each}
      <Fact icon={Flag} label="Arrives">
        <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} block={false} cancelled={s.cancelled} />
      </Fact>
      <Fact icon={Clock} label="Duration" value={formatDuration(s.plannedMinutes)} />
    </span>
  </span>

  <span class="flex items-center gap-2">
    {#if changed === "Adjusted"}
      <StatusChip tone="watch" icon={CalendarClock} title="Adjusted to the new timetable" />
    {:else if changed}
      <StatusChip tone="bad" icon={CalendarX2} title="Timetable changed" />
    {:else if s.cancelled}
      <StatusChip tone="bad" icon={Ban} title="Cancelled" />
    {/if}
    {#if trip.ticket_path}
      <span class="text-muted" role="img" aria-label="Ticket attached" title="Ticket attached"><Ticket class="h-4 w-4" aria-hidden="true" /></span>
    {/if}
  </span>
</a>
