<script lang="ts">
  /**
   * The journey as rows in the row grid on one continuous rail
   * (abeona-design.md §9, Trip): a ride is its first and last stop with its
   * line between them, the stops folded behind "n stops"; a change is one
   * row; a walk to the first train or from the last one is one row. A
   * ride's seat, when the ticket gave one, goes with its line.
   */
  import { ArrowRightLeft, Ban, Clock, Footprints } from "lucide-svelte";
  import { formatDuration, legLabel, minutesBetween } from "../../lib/format";
  import { transfers } from "../../lib/transfer";
  import type { Leg } from "../../lib/transit";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import Fact from "../common/Fact.svelte";
  import LegChip from "../common/LegChip.svelte";
  import Seat from "../common/Seat.svelte";
  import StatusChip from "../common/StatusChip.svelte";
  import StopRow from "../common/StopRow.svelte";
  import StopsToggle from "../common/StopsToggle.svelte";

  interface Props {
    legs: Leg[];
  }

  let { legs }: Props = $props();

  const transferByTarget = $derived(new Map(transfers(legs).map((t) => [t.toIndex, t])));
  const firstRide = $derived(legs.findIndex((l) => l.mode !== "walk"));
  const lastRide = $derived(legs.findLastIndex((l) => l.mode !== "walk"));
  /** The rides whose stops are unfolded, by index. */
  let open = $state<ReadonlySet<number>>(new Set());

  function toggle(i: number) {
    const next = new Set(open);
    if (!next.delete(i)) next.add(i);
    open = next;
  }
</script>

<!-- The rail through a row's mark column: solid along a ride (red when it does not run), dotted between rides. -->
{#snippet rail(part: "start" | "through" | "end", kind: "ride" | "bad" | "between")}
  <span
    class="absolute left-1/2 -translate-x-1/2
      {part === 'start' ? 'top-1/2 bottom-0' : part === 'end' ? 'top-0 bottom-1/2' : 'inset-y-0'}
      {kind === 'between' ? 'border-l-2 border-dotted border-muted' : kind === 'bad' ? 'w-0.5 bg-bad' : 'w-0.5 bg-ink'}"
    aria-hidden="true"
  ></span>
{/snippet}

{#snippet dot(small: boolean, bad: boolean)}
  <span
    class="relative rounded-full bg-card {small ? 'h-2 w-2 border-2' : 'h-3.5 w-3.5 border-[3px]'} {bad ? 'border-bad' : 'border-ink'}"
    aria-hidden="true"
  ></span>
{/snippet}

{#snippet badge(Icon: typeof Footprints, label: string)}
  <span class="relative flex h-6 w-6 items-center justify-center rounded-full bg-card text-muted"><Icon class="h-4 w-4" aria-label={label} /></span>
{/snippet}

<section aria-label="Journey" class="rounded-3xl bg-card px-4 py-2 shadow-card">
  {#each legs as leg, i (i)}
    {#if leg.mode === "walk"}
      {#if i < firstRide || i > lastRide}
        <!-- The way to the first train, or on from the last one. -->
        <StopRow name={i > lastRide ? leg.to.name : ""} muted>
          {#snippet mark()}
            {@render rail(i < firstRide ? "start" : "end", "between")}
            {@render badge(Footprints, "Walk")}
          {/snippet}
          {#snippet trailing()}<Fact icon={Clock} label="Walk, duration" value={formatDuration(minutesBetween(leg.from.planned, leg.to.planned))} />{/snippet}
        </StopRow>
      {/if}
    {:else}
      {@const transfer = transferByTarget.get(i)}
      {@const kind = leg.cancelled ? "bad" : "ride"}
      {@const stops = leg.stopovers ?? []}
      {#if transfer}
        <!-- The change: its margin, the walk if there is one, what it needs. The platforms are the rows around it. -->
        <StopRow>
          {#snippet mark()}
            {@render rail("through", "between")}
            {@render badge(transfer.walkMinutes > 0 ? Footprints : ArrowRightLeft, transfer.walkMinutes > 0 ? "Change, on foot" : "Change")}
          {/snippet}
          {#snippet line()}<ConfidenceChip confidence={transfer.confidence} minutes={transfer.availableMinutes} />{/snippet}
          {#snippet trailing()}
            {#if transfer.walkMinutes > 0}<Fact icon={Footprints} label="Walk" value={`${transfer.walkMinutes} min`} />{/if}
            <span class="t-meta">needs {transfer.requiredMinutes} min</span>
          {/snippet}
        </StopRow>
      {/if}

      <StopRow
        planned={leg.from.planned}
        current={leg.from.current}
        cancelled={leg.cancelled}
        name={leg.from.name}
        platformPlanned={leg.from.planned_platform}
        platformCurrent={leg.from.platform}
        strong
      >
        {#snippet mark()}
          {#if i > firstRide || legs[i - 1]?.mode === "walk"}{@render rail("end", "between")}{/if}
          {@render rail("start", kind)}
          {@render dot(false, leg.cancelled)}
        {/snippet}
      </StopRow>

      <!-- The train: its line and where it is bound, then how long, how many stops, who runs it. -->
      <StopRow name={leg.headsign ? `to ${leg.headsign}` : ""} muted>
        {#snippet mark()}{@render rail("through", kind)}{/snippet}
        {#snippet line()}<LegChip {leg} size="md" />{/snippet}
        {#if leg.cancelled}
          <StatusChip tone="bad" icon={Ban} label={leg.dropped_at ? "Not in the timetable" : "Cancelled"} />
        {/if}
        <Fact icon={Clock} label="Duration" value={formatDuration(minutesBetween(leg.from.planned, leg.to.planned))} />
        {#if stops.length > 0}
          <StopsToggle count={stops.length} open={open.has(i)} ride={legLabel(leg)} ontoggle={() => toggle(i)} />
        {:else}
          <span class="t-meta">non-stop</span>
        {/if}
        {#if leg.operator}<span class="t-meta truncate">{leg.operator}</span>{/if}
        <Seat coach={leg.coach} seats={leg.seats} class="text-ink" />
      </StopRow>

      {#if open.has(i)}
        {#each stops as stop, n (n)}
          <StopRow
            planned={stop.planned_arrival ?? stop.planned_departure}
            current={stop.arrival ?? stop.departure}
            cancelled={stop.cancelled}
            name={stop.name}
            muted
            class="min-h-8"
          >
            {#snippet mark()}
              {@render rail("through", kind)}
              {@render dot(true, leg.cancelled)}
            {/snippet}
          </StopRow>
        {/each}
      {/if}

      <StopRow
        planned={leg.to.planned}
        current={leg.to.current}
        cancelled={leg.cancelled}
        name={leg.to.name}
        platformPlanned={leg.to.planned_platform}
        platformCurrent={leg.to.platform}
        strong
      >
        {#snippet mark()}
          {@render rail("end", kind)}
          {#if i < lastRide || legs[i + 1]?.mode === "walk"}{@render rail("start", "between")}{/if}
          {@render dot(false, leg.cancelled)}
        {/snippet}
      </StopRow>
    {/if}
  {/each}
</section>
