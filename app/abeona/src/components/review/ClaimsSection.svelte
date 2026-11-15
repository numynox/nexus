<script lang="ts">
  import { CalendarClock, CircleAlert, CircleCheck, Ban, HandCoins, Hourglass } from "lucide-svelte";
  import { getBaseUrl } from "../../lib/config";
  import { type ClaimEntry, type ClaimsOverview, describeError, fetchClaimsOverview } from "../../lib/data";
  import { claimDeadlineText } from "../../lib/compensation";
  import { formatDay, formatPrice, formatShortDate } from "../../lib/format";
  import Banner from "../common/Banner.svelte";
  import Fact from "../common/Fact.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";
  import StatusChip from "../common/StatusChip.svelte";

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

  /** A claim nobody made yet turns from quiet to watch in its last week. */
  const NEAR_DAYS = 7;
  const entries = $derived(overview ? [...overview.to_claim, ...overview.waiting] : []);

  function title(entry: ClaimEntry) {
    return entry.title ?? `${entry.origin_name} → ${entry.destination_name}`;
  }
</script>

{#if error}
  <Banner tone="bad" icon={CircleAlert} title={error} />
{:else if overview && !empty}
  <section class="space-y-2" aria-label="Compensation">
    <SectionHeader title="Compensation">
      <Fact
        icon={HandCoins}
        label="Earned back in {year}{overview.paid > 0 ? `, ${overview.paid} ${overview.paid === 1 ? 'claim' : 'claims'} paid` : ''}"
      >
        <span class="font-semibold text-ink">{formatPrice(overview.earned_cents)}</span>
        {#if overview.paid > 0}<span aria-hidden="true">· {overview.paid} paid</span>{/if}
      </Fact>
    </SectionHeader>

    {#if entries.length > 0}
      <ul class="divide-y divide-line rounded-3xl bg-card px-4 shadow-card">
        {#each entries as entry (entry.trip_id)}
          <li>
            <a href={`${base}/trip?id=${entry.trip_id}#compensation`} class="grid min-h-14 grid-cols-[minmax(0,1fr)_auto] items-center gap-x-3 py-2">
              <span class="t-body truncate text-ink">{title(entry)}</span>
              <span class="t-body tabular text-right font-semibold text-ink">
                {entry.amount_cents !== null ? formatPrice(entry.amount_cents) : "fare?"}
              </span>
              <span class="flex min-w-0 flex-wrap items-center gap-x-3">
                <span class="t-meta inline-flex gap-1.5">
                  <span>{formatShortDate(entry.planned_departure)}</span>
                  {#if entry.delay_minutes !== null}<span class="font-bold text-watch-ink">+{entry.delay_minutes}</span>{/if}
                </span>
                {#if entry.status === "open"}
                  <Fact icon={CalendarClock} label={claimDeadlineText(formatDay(entry.deadline), entry.days_left)} value={formatDay(entry.deadline)} />
                {/if}
              </span>
              <span class="justify-self-end">
                {#if entry.status === "paid"}
                  <StatusChip tone="ok" icon={CircleCheck} label="Paid" />
                {:else if entry.status === "rejected"}
                  <StatusChip tone="bad" icon={Ban} label="Rejected" />
                {:else if entry.status === "claimed"}
                  <StatusChip tone="watch" icon={Hourglass} label="Claimed" title="Claimed, waiting for DB" />
                {:else if entry.days_left <= NEAR_DAYS}
                  <StatusChip
                    tone="watch"
                    icon={CalendarClock}
                    label={entry.days_left < 0 ? "Too late" : entry.days_left === 0 ? "Today" : `${entry.days_left} d`}
                    title={claimDeadlineText(formatDay(entry.deadline), entry.days_left)}
                  />
                {:else}
                  <StatusChip tone="quiet" label="Not claimed" />
                {/if}
              </span>
            </a>
          </li>
        {/each}
      </ul>
    {/if}
  </section>
{/if}
