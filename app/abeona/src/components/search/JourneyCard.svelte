<script lang="ts">
  import { formatDuration, formatTime } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import type { Journey } from "../../lib/transit";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";
  import ShiftedTime from "../common/ShiftedTime.svelte";
  import LegChip from "../common/LegChip.svelte";

  interface Props {
    journey: Journey;
    onChoose: () => void;
  }

  let { journey, onChoose }: Props = $props();

  const s = $derived(summarise(journey.legs));
</script>

<button
  type="button"
  onclick={onChoose}
  class="block w-full rounded-3xl bg-card p-5 text-left shadow-card transition hover:-translate-y-px hover:shadow-md focus-visible:outline-2 focus-visible:outline-primary {s.cancelled ? 'opacity-70' : ''}"
>
  <div class="flex items-start justify-between gap-4">
    <div class="tabular flex items-baseline gap-2 text-2xl font-semibold tracking-tight text-ink sm:text-[1.7rem]">
      <ShiftedTime planned={s.departure.planned} current={s.departure.current} block={false} cancelled={s.cancelled} />
      <span class="px-1 text-base-content/30">→</span>
      <ShiftedTime planned={s.arrival.planned} current={s.arrival.current} block={false} cancelled={s.cancelled} />
    </div>
    <div class="text-right">
      <p class="tabular text-sm font-semibold text-ink">{formatDuration(s.plannedMinutes)}</p>
      <p class="text-xs text-base-content/60">
        {s.changes === 0 ? "Direct" : s.changes === 1 ? "1 change" : `${s.changes} changes`}
      </p>
    </div>
  </div>

  <div class="mt-3 flex flex-wrap items-center gap-1.5">
    {#each s.rides as ride, i (i)}
      {#if i > 0}<span class="text-xs text-base-content/30">›</span>{/if}
      <LegChip leg={ride} />
    {/each}
    {#if s.confidence && s.changes > 0 && s.confidence !== "safe"}
      <span class="ml-auto"><ConfidenceChip confidence={s.confidence} /></span>
    {/if}
  </div>

  <p class="mt-3 truncate text-xs text-base-content/55">
    {s.departure.name} → {s.arrival.name}
  </p>
</button>
