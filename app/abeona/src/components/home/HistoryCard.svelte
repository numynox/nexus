<script lang="ts">
  /** The year so far as four figures in one row, and the past trips as one quiet row. */
  import { ChevronRight, History } from "lucide-svelte";
  import type { Statistics, Trip } from "../../lib/data";
  import { formatNumber, formatShortDate } from "../../lib/format";
  import SectionHeader from "../common/SectionHeader.svelte";

  interface Props {
    year: number;
    stats: Statistics | null;
    /** The trips last made: the latest one names the row to the past trips. */
    recent: Trip[];
    reviewHref: string;
    pastHref: string;
  }

  let { year, stats, recent, reviewHref, pastHref }: Props = $props();

  const figures = $derived([
    { label: "trips", value: stats ? formatNumber(stats.trips) : "–", late: false },
    { label: "km", value: stats ? formatNumber(stats.km) : "–", late: false },
    { label: "min late", value: stats ? formatNumber(stats.delay_minutes) : "–", late: !!stats && stats.delay_minutes > 0 },
    {
      label: "on time",
      value: stats?.punctual_share === null || stats?.punctual_share === undefined ? "–" : `${Math.round(stats.punctual_share * 100)}%`,
      late: false,
    },
  ]);
  const last = $derived(recent[0] ?? null);
</script>

<section class="space-y-2">
  <SectionHeader title={`${year} so far`} />
  <div class="overflow-hidden rounded-3xl bg-card shadow-card">
    <a href={reviewHref} class="flex min-h-12 items-center gap-2 p-4 transition-colors hover:bg-well" aria-label="Year in review">
      <dl class="grid flex-1 grid-cols-4 gap-2">
        {#each figures as f (f.label)}
          <div class="flex flex-col-reverse">
            <dt class="t-meta">{f.label}</dt>
            <dd class="tabular t-title {f.late ? 'text-watch-ink' : 'text-ink'}">{f.value}</dd>
          </div>
        {/each}
      </dl>
      <ChevronRight class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
    </a>
    <a href={pastHref} class="flex min-h-12 items-center gap-3 border-t border-line px-4 transition-colors hover:bg-well">
      <History class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
      <span class="t-body shrink-0 whitespace-nowrap text-ink">Past trips</span>
      {#if last}
        <span class="t-meta ml-auto min-w-0 truncate">{formatShortDate(last.planned_departure)} · {last.title ?? `${last.origin_name} → ${last.destination_name}`}</span>
      {/if}
      <ChevronRight class="h-5 w-5 shrink-0 text-muted {last ? '' : 'ml-auto'}" aria-hidden="true" />
    </a>
  </div>
</section>
