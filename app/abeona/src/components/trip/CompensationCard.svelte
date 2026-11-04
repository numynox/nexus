<script lang="ts">
  import { ClipboardCheck, Copy, ExternalLink, HandCoins } from "lucide-svelte";
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
  const tone = $derived(
    comp?.verdict === "eligible" || comp?.verdict === "unknown_price"
      ? "bg-mint text-primary"
      : comp?.verdict === "unknown_arrival"
        ? "bg-amber-soft text-amber-ink"
        : "bg-base-200 text-base-content/70",
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

<section class="rounded-3xl bg-card p-5 shadow-card sm:p-6" id="compensation" aria-busy={loading}>
  <div class="flex items-center gap-2">
    <HandCoins class="h-5 w-5 text-primary" />
    <h2 class="font-semibold text-ink">Compensation</h2>
  </div>

  {#if loading}
    <div class="mt-4 h-20 animate-pulse rounded-2xl bg-base-200"></div>
  {:else if comp}
    <div class="mt-4 rounded-2xl px-4 py-3 {tone}">
      <p class="text-lg font-semibold">{verdictHeadline(comp.verdict, amount)}</p>
      {#if comp.delay_minutes !== null}
        <p class="mt-0.5 text-sm opacity-80">
          {comp.delay_minutes === 0 ? "On time" : `${comp.delay_minutes} min late`}
          {#if comp.actual_arrival}· arrived {formatTime(comp.actual_arrival)}{/if}
          {#if delaySourceText(comp.delay_source)}· {delaySourceText(comp.delay_source)}{/if}
        </p>
      {/if}
    </div>

    <ul class="mt-3 space-y-1 text-sm text-base-content/75">
      {#each comp.reasons as reason, i (i)}<li>{reason}</li>{/each}
    </ul>
    {#if comp.hints.length > 0}
      <ul class="mt-2 space-y-1 text-sm text-amber-ink">
        {#each comp.hints as hint, i (i)}<li>{hint}</li>{/each}
      </ul>
    {/if}

    {#if comp.verdict === "unknown_price"}
      <form class="mt-4 flex items-end gap-2" onsubmit={savePrice}>
        <label class="form-control flex-1">
          <span class="mb-1 text-xs font-semibold text-base-content/60">What the ticket cost (€)</span>
          <input class="input input-bordered rounded-xl" inputmode="decimal" placeholder="34.90" bind:value={price} />
        </label>
        <button class="btn btn-primary rounded-full" disabled={busy}>Save</button>
      </form>
    {/if}

    {#if comp.verdict !== "unknown_arrival" && (trip.force_majeure || (comp.delay_minutes ?? 0) >= (comp.threshold_minutes ?? 60))}
      <label class="mt-4 flex cursor-pointer items-start gap-3 text-sm">
        <input type="checkbox" class="checkbox checkbox-sm mt-0.5" checked={trip.force_majeure} onchange={toggleForceMajeure} disabled={busy} />
        <span>Caused by a storm, flooding or a person on the track</span>
      </label>
    {/if}

    {#if canClaim}
      <p class="mt-4 text-sm font-medium {comp.deadline_passed ? 'text-error' : 'text-ink'}">
        {claimDeadlineText(formatDay(comp.deadline), daysLeft)}
      </p>
      <div class="mt-3 flex flex-wrap gap-2">
        <button class="btn btn-primary btn-sm rounded-full" onclick={copy}>
          {#if copied}<ClipboardCheck class="h-4 w-4" /> Copied{:else}<Copy class="h-4 w-4" /> Copy for the form{/if}
        </button>
        <a class="btn btn-sm rounded-full border-base-300 bg-card" href={comp.claim_url} target="_blank" rel="noopener">
          <ExternalLink class="h-4 w-4" /> DB passenger rights
        </a>
      </div>
      <details class="mt-3 text-sm">
        <summary class="cursor-pointer text-base-content/60">The text, in German</summary>
        <pre class="mt-2 whitespace-pre-wrap rounded-2xl bg-base-200 p-3 font-sans text-xs leading-relaxed">{comp.summary_de}</pre>
      </details>
    {/if}

    {#if canClaim || claim}
      <div class="mt-4">
        <p class="text-xs font-semibold uppercase tracking-wider text-base-content/55">Your claim</p>
        <div class="mt-2 flex flex-wrap gap-1.5 rounded-full bg-base-200 p-1" role="radiogroup" aria-label="Claim status">
          {#each statuses as status (status)}
            {@const on = (claim?.status ?? "open") === status}
            <button
              role="radio"
              aria-checked={on}
              class="flex-1 rounded-full px-3 py-1.5 text-xs font-semibold transition {on ? 'bg-card text-primary shadow-card' : 'text-base-content/60'}"
              onclick={() => setStatus(status)}
              disabled={busy}
            >{CLAIM_STATUS_LABELS[status]}</button>
          {/each}
        </div>
        {#if claim?.status === "paid" && claim.paid_at}
          <p class="mt-2 text-xs text-base-content/60">{formatPrice(claim.amount_cents)} paid on {formatDay(claim.paid_at.slice(0, 10))}</p>
        {:else if claim?.status === "claimed" && claim.claimed_at}
          <p class="mt-2 text-xs text-base-content/60">Claimed on {formatDay(claim.claimed_at.slice(0, 10))}</p>
        {/if}
      </div>
    {/if}

    <p class="mt-4 text-xs leading-relaxed text-base-content/50">
      A summary of DB's passenger rights, not legal advice. Check fahrgastrechte.info or bahn.de before you claim.
    </p>
  {/if}

  {#if error}
    <p class="mt-3 text-sm text-error" role="alert">{error}</p>
  {/if}
</section>
