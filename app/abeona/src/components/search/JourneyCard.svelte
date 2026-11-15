<script lang="ts">
  /**
   * One connection of a search, to compare at a glance: every card says the
   * same fact in the same place (abeona-design.md §9, Plan).
   */
  import { ArrowRightLeft, Ban, Clock } from "lucide-svelte";
  import { formatDuration } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import { transfers, type Confidence } from "../../lib/transfer";
  import type { Journey } from "../../lib/transit";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import Fact from "../common/Fact.svelte";
  import LegChip from "../common/LegChip.svelte";
  import PlatformBox from "../common/PlatformBox.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import StatusChip from "../common/StatusChip.svelte";

  interface Props {
    journey: Journey;
    /** The stations that were searched for: a connection names its own only where they differ. */
    fromName?: string;
    toName?: string;
    onChoose: () => void;
  }

  let { journey, fromName = "", toName = "", onChoose }: Props = $props();

  const s = $derived(summarise(journey.legs));
  const first = $derived(s.rides[0]?.from ?? s.departure);

  const RANK: Record<Confidence, number> = { safe: 0, tight: 1, at_risk: 2, impossible: 3 };
  /** The change most likely to fail: the worst confidence, then the least time. */
  const worst = $derived(
    [...transfers(journey.legs)].sort(
      (a, b) => RANK[b.confidence] - RANK[a.confidence] || a.availableMinutes - b.availableMinutes,
    )[0] ?? null,
  );
  /** "2 h 05": the clock icon is the word, and an hour needs no "min". */
  const duration = $derived(
    s.plannedMinutes >= 60 ? formatDuration(s.plannedMinutes).replace(" min", "") : formatDuration(s.plannedMinutes),
  );
  const otherStations = $derived(s.departure.name !== fromName || s.arrival.name !== toName);
</script>

<button
  type="button"
  onclick={onChoose}
  class="block w-full rounded-3xl bg-card p-4 text-left shadow-card transition hover:-translate-y-px focus-visible:outline-2 focus-visible:outline-primary"
>
  <span class="flex min-h-7 items-center justify-between gap-3">
    <span class="t-title flex items-baseline gap-2 text-ink {s.cancelled ? 'text-muted line-through' : ''}">
      <ShiftedTime planned={s.departure.planned} current={s.departure.current} block={false} />
      <span class="font-normal text-muted" aria-hidden="true">→</span>
      <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} block={false} />
    </span>
    {#if s.cancelled}
      <StatusChip tone="bad" icon={Ban} label="Cancelled" />
    {:else if worst}
      <ConfidenceChip confidence={worst.confidence} minutes={worst.availableMinutes} />
    {/if}
  </span>

  <!-- Duration and changes keep their width, so the trains start in the same place on every card. -->
  <span class="mt-2 grid grid-cols-[4.75rem_2.25rem_minmax(0,1fr)_auto] items-start gap-x-2">
    <Fact icon={Clock} label="Duration" value={duration} class="h-6" />
    <Fact icon={ArrowRightLeft} label="Changes" value={s.changes} class="h-6" />
    <span class="flex min-w-0 flex-wrap items-center gap-1">
      {#each s.rides as ride, i (i)}<LegChip leg={ride} />{/each}
    </span>
    <PlatformBox planned={first.planned_platform} current={first.platform} />
  </span>

  {#if otherStations}
    <span class="t-meta mt-2 block truncate">{s.departure.name} → {s.arrival.name}</span>
  {/if}
</button>
