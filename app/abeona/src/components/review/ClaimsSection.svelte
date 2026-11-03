<script lang="ts">
  import { HandCoins } from "lucide-svelte";
  import { getBaseUrl } from "../../lib/config";
  import { type ClaimEntry, type ClaimsOverview, describeError, fetchClaimsOverview } from "../../lib/data";
  import { claimDeadlineText } from "../../lib/compensation";
  import { formatDay, formatPrice, formatShortDate } from "../../lib/format";

  interface Props {
    year: number;
  }

  let { year }: Props = $props();

  const base = getBaseUrl();
  let overview = $state<ClaimsOverview | null>(null);
  let error = $state("");

  $effect(() => {
    const y = year;
    fetchClaimsOverview(y)
      .then((o) => {
        overview = o;
        error = "";
      })
      .catch((e) => (error = describeError(e)));
  });

  const empty = $derived(!!overview && overview.to_claim.length === 0 && overview.waiting.length === 0 && overview.earned_cents === 0);

  function title(entry: ClaimEntry) {
    return entry.title ?? `${entry.origin_name} → ${entry.destination_name}`;
  }
</script>

{#if error}
  <p class="rounded-2xl bg-error/10 px-5 py-4 text-sm text-error" role="alert">{error}</p>
{:else if overview && !empty}
  <section class="rounded-[2rem] bg-card p-6 shadow-card sm:p-8" aria-labelledby="claims-heading">
    <div class="flex flex-wrap items-end justify-between gap-4">
      <div class="flex items-center gap-2">
        <HandCoins class="h-5 w-5 text-primary" />
        <h2 id="claims-heading" class="text-lg font-semibold text-ink">Compensation</h2>
      </div>
      <div class="text-right">
        <p class="text-xs font-semibold uppercase tracking-wider text-base-content/55">Earned back in {year}</p>
        <p class="tabular text-2xl font-semibold text-primary">{formatPrice(overview.earned_cents)}</p>
        {#if overview.paid > 0}<p class="text-xs text-base-content/55">{overview.paid} {overview.paid === 1 ? "claim" : "claims"} paid</p>{/if}
      </div>
    </div>

    {#if overview.to_claim.length > 0}
      <p class="mt-5 text-xs font-semibold uppercase tracking-wider text-base-content/55">To claim</p>
      <ul class="mt-2 divide-y divide-base-300/60">
        {#each overview.to_claim as entry (entry.trip_id)}
          <li>
            <a href={`${base}/trip?id=${entry.trip_id}#compensation`} class="flex items-center gap-3 py-3 hover:text-primary">
              <div class="min-w-0 flex-1">
                <p class="truncate font-medium text-ink">{title(entry)}</p>
                <p class="text-sm {entry.days_left <= 7 ? 'text-amber-ink' : 'text-base-content/60'}">
                  {formatShortDate(entry.planned_departure)} · +{entry.delay_minutes} min · {claimDeadlineText(formatDay(entry.deadline), entry.days_left)}
                </p>
              </div>
              <span class="tabular font-semibold">{entry.amount_cents !== null ? formatPrice(entry.amount_cents) : "fare?"}</span>
            </a>
          </li>
        {/each}
      </ul>
    {/if}

    {#if overview.waiting.length > 0}
      <p class="mt-5 text-xs font-semibold uppercase tracking-wider text-base-content/55">Waiting for DB</p>
      <ul class="mt-2 divide-y divide-base-300/60">
        {#each overview.waiting as entry (entry.trip_id)}
          <li>
            <a href={`${base}/trip?id=${entry.trip_id}#compensation`} class="flex items-center gap-3 py-3 hover:text-primary">
              <div class="min-w-0 flex-1">
                <p class="truncate font-medium text-ink">{title(entry)}</p>
                <p class="text-sm text-base-content/60">{formatShortDate(entry.planned_departure)} · claimed</p>
              </div>
              <span class="tabular font-semibold">{formatPrice(entry.amount_cents)}</span>
            </a>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
{/if}
