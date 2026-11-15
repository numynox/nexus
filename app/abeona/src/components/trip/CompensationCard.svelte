<script lang="ts">
  import { CalendarClock, CircleCheck, CircleHelp, ClipboardCheck, Clock, Copy, ExternalLink, Flag, Minus } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    type Claim,
    type ClaimStatus,
    type Compensation,
    describeError,
    fetchClaim,
    fetchCompensation,
    setClaimStatus,
    setForceMajeure,
    setTripPrice,
    type Trip,
  } from "../../lib/data";
  import { CLAIM_STATUS_LABELS, claimDeadlineText, delaySourceText, verdictHeadline } from "../../lib/compensation";
  import { formatDay, formatPrice, formatTime } from "../../lib/format";
  import Fact from "../common/Fact.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";

  interface Props {
    trip: Trip;
    /** After the price or the force-majeure flag changed on the trip. */
    onTripChanged: (trip: Trip) => void;
  }

  let { trip, onTripChanged }: Props = $props();

  let comp = $state<Compensation | null>(null);
  let claim = $state<Claim | null>(null);
  let loading = $state(true);
  let busy = $state(false);
  let error = $state("");
  let copied = $state(false);
  let price = $state("");

  const amount = $derived(comp?.amount_cents !== null && comp?.amount_cents !== undefined ? formatPrice(comp.amount_cents) : null);
  const canClaim = $derived(!!comp?.summary_de);
  const statuses: ClaimStatus[] = ["open", "claimed", "paid", "rejected"];
  const daysLeft = $derived.by(() => {
    if (!comp) return 0;
    const [y, m, d] = comp.deadline.split("-").map(Number);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return Math.round((new Date(y!, m! - 1, d!).getTime() - today.getTime()) / 86_400_000);
  });
  /** The verdict's status: money to claim is ok, something to fill in is watch, nothing owed is quiet. */
  const tone = $derived(
    comp?.verdict === "eligible"
      ? ({ box: "bg-ok-soft text-ok-ink", icon: CircleCheck } as const)
      : comp?.verdict === "unknown_price" || comp?.verdict === "unknown_arrival"
        ? ({ box: "bg-watch-soft text-watch-ink", icon: CircleHelp } as const)
        : ({ box: "bg-well text-ink", icon: Minus } as const),
  );

  export async function reload() {
    try {
      [comp, claim] = await Promise.all([fetchCompensation(trip.id), fetchClaim(trip.id)]);
      error = "";
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  onMount(() => {
    void reload();
  });

  async function savePrice(event: SubmitEvent) {
    event.preventDefault();
    const cents = Math.round(Number(price.replace(",", ".")) * 100);
    if (!Number.isFinite(cents) || cents <= 0) {
      error = "Enter what the ticket cost, like 34.90.";
      return;
    }
    busy = true;
    try {
      await setTripPrice(trip.id, cents);
      onTripChanged({ ...trip, price_cents: cents });
      await reload();
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }

  async function toggleForceMajeure(event: Event) {
    const value = (event.target as HTMLInputElement).checked;
    busy = true;
    try {
      await setForceMajeure(trip.id, value);
      onTripChanged({ ...trip, force_majeure: value });
      await reload();
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }

  async function setStatus(status: ClaimStatus) {
    if (busy || claim?.status === status || (!claim && status === "open")) return;
    busy = true;
    try {
      claim = await setClaimStatus(trip.id, status, claim?.amount_cents ?? comp?.amount_cents ?? null);
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }

  async function copy() {
    if (!comp?.summary_de) return;
    try {
      await navigator.clipboard.writeText(comp.summary_de);
      copied = true;
      setTimeout(() => (copied = false), 2500);
    } catch {
      error = "The browser did not allow copying. Select the text below instead.";
    }
  }
</script>

<section class="space-y-2" id="compensation" aria-busy={loading}>
  <SectionHeader title="Compensation" />
  <div class="space-y-3 rounded-3xl bg-card p-4 shadow-card">
  {#if loading}
    <div class="h-16 animate-pulse rounded-2xl bg-well"></div>
  {:else if comp}
    <!-- The verdict and the amount first; how late, when and by whose word as facts. -->
    <div class="flex items-start gap-3 rounded-2xl px-4 py-3 {tone.box}">
      <tone.icon class="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
      <div class="min-w-0">
        <p class="t-title">{verdictHeadline(comp.verdict, amount)}</p>
        {#if comp.delay_minutes !== null}
          <p class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
            <Fact icon={Clock} label="Delay" value={comp.delay_minutes === 0 ? "On time" : `+${comp.delay_minutes} min`} class="text-current" />
            {#if comp.actual_arrival}<Fact icon={Flag} label="Arrived" value={formatTime(comp.actual_arrival)} class="text-current" />{/if}
            {#if delaySourceText(comp.delay_source)}<span class="t-meta text-current">{delaySourceText(comp.delay_source)}</span>{/if}
          </p>
        {/if}
      </div>
    </div>

    <ul class="t-meta space-y-1">
      {#each comp.reasons as reason, i (i)}<li>{reason}</li>{/each}
    </ul>
    {#if comp.hints.length > 0}
      <ul class="t-meta space-y-1 text-watch-ink">
        {#each comp.hints as hint, i (i)}<li>{hint}</li>{/each}
      </ul>
    {/if}

    {#if comp.verdict === "unknown_price"}
      <form class="flex items-end gap-2" onsubmit={savePrice}>
        <label class="flex-1">
          <span class="t-meta mb-1 block">What the ticket cost (€)</span>
          <input class="input w-full rounded-xl border-line bg-well" inputmode="decimal" placeholder="34.90" bind:value={price} />
        </label>
        <button class="btn btn-primary rounded-full" disabled={busy}>Save</button>
      </form>
    {/if}

    {#if comp.verdict !== "unknown_arrival" && (trip.force_majeure || (comp.delay_minutes ?? 0) >= (comp.threshold_minutes ?? 60))}
      <label class="t-body flex min-h-12 cursor-pointer items-center gap-3 text-ink">
        <input type="checkbox" class="checkbox checkbox-sm" checked={trip.force_majeure} onchange={toggleForceMajeure} disabled={busy} />
        <span>Caused by a storm, flooding or a person on the track</span>
      </label>
    {/if}

    {#if canClaim}
      <Fact icon={CalendarClock} label="Claim deadline" value={claimDeadlineText(formatDay(comp.deadline), daysLeft)} class={comp.deadline_passed ? "text-bad-ink" : "text-ink"} />
      <div class="flex flex-wrap gap-2">
        <button class="btn btn-primary btn-sm rounded-full" onclick={copy}>
          {#if copied}<ClipboardCheck class="h-4 w-4" aria-hidden="true" /> Copied{:else}<Copy class="h-4 w-4" aria-hidden="true" /> Copy for the form{/if}
        </button>
        <a class="btn btn-ghost btn-sm rounded-full border border-line text-ink" href={comp.claim_url} target="_blank" rel="noopener">
          <ExternalLink class="h-4 w-4" aria-hidden="true" /> DB passenger rights
        </a>
      </div>
      <details>
        <summary class="t-meta cursor-pointer">The text, in German</summary>
        <pre class="mt-2 whitespace-pre-wrap rounded-2xl bg-well p-3 font-sans text-xs leading-relaxed text-ink">{comp.summary_de}</pre>
      </details>
    {/if}

    {#if canClaim || claim}
      <div>
        <div class="flex flex-wrap gap-1 rounded-full bg-well p-1" role="radiogroup" aria-label="Your claim">
          {#each statuses as status (status)}
            {@const on = (claim?.status ?? "open") === status}
            <button
              role="radio"
              aria-checked={on}
              class="min-h-8 flex-1 rounded-full px-1.5 text-xs leading-tight font-semibold transition {on ? 'bg-brand-soft text-brand' : 'text-muted'}"
              onclick={() => setStatus(status)}
              disabled={busy}
            >{CLAIM_STATUS_LABELS[status]}</button>
          {/each}
        </div>
        {#if claim?.status === "paid" && claim.paid_at}
          <p class="t-meta mt-2">{formatPrice(claim.amount_cents)} paid on {formatDay(claim.paid_at.slice(0, 10))}</p>
        {:else if claim?.status === "claimed" && claim.claimed_at}
          <p class="t-meta mt-2">Claimed on {formatDay(claim.claimed_at.slice(0, 10))}</p>
        {/if}
      </div>
    {/if}

    <!-- The rule, one tap away. -->
    <details>
      <summary class="t-meta cursor-pointer">About this estimate</summary>
      <p class="t-meta mt-1">A summary of DB's passenger rights, not legal advice. Check fahrgastrechte.info or bahn.de before you claim.</p>
    </details>
  {/if}

  {#if error}
    <p class="t-meta text-bad-ink" role="alert">{error}</p>
  {/if}
  </div>
</section>
