<script lang="ts">
  import { Check, FileText, X } from "lucide-svelte";
  import {
    describeError,
    ensureHousehold,
    fetchTravellers,
    isPastJourney,
    saveTrip,
    uploadTicket,
    type BahnCardKind,
    type FareTypeValue,
    type Traveller,
  } from "../../lib/data";
  import { BAHNCARD_DISCOUNTS, FARE_TYPES, formatDuration, formatLongDate, formatTime } from "../../lib/format";
  import { session } from "../../lib/stores";
  import { summarise } from "../../lib/journey";
  import { halfOf, matchSummary, type IntakeDraft } from "../../lib/intake/intake";
  import type { Journey } from "../../lib/transit";
  import LegChip from "../common/LegChip.svelte";

  interface Props {
    journey: Journey | null;
    /** Set when the journey came from a share text or a ticket: the fields start from what was read. */
    intake?: IntakeDraft | null;
    /** The ticket PDF the intake read; attached to the trip once it is saved. */
    ticketFile?: File | null;
    onClose: () => void;
    onSaved: (tripId: string) => void;
  }

  let { journey, intake = null, ticketFile = null, onClose, onSaved }: Props = $props();

  let dialog: HTMLDialogElement | undefined = $state();
  let title = $state("");
  let fareType = $state<FareTypeValue>("saver");
  let trainBinding = $state(true);
  let bindingTouched = $state(false);
  let travelClass = $state<1 | 2>(2);
  let price = $state("");
  let discount = $state<BahnCardKind | null>(null);
  let travellers = $state<Traveller[]>([]);
  /** Null until the travellers are loaded: then the RPC's default (the caller) applies. */
  let travellerIds = $state<ReadonlySet<string> | null>(null);
  let saving = $state(false);
  /** For a journey that has already arrived: how late it really was, typed by hand. */
  let delay = $state("");
  let error = $state("");
  /**
   * For a ticket that prints the whole booking's price: null until the user
   * says what this trip cost (half, all, or typed). Never halved silently.
   */
  let priceConfirmed = $state(true);
  let fromIntake = false;

  function euros(cents: number): string {
    return (cents / 100).toFixed(2);
  }

  function applyIntake(draft: IntakeDraft | null) {
    const p = draft?.prefill;
    title = "";
    fareType = p?.fare_type ?? "saver";
    trainBinding = p?.train_binding ?? fareType === "saver";
    bindingTouched = p?.train_binding != null;
    travelClass = p?.travel_class ?? 2;
    discount = p?.bahncard_discount ?? null;
    price = p?.price_cents != null ? euros(p.price_cents) : "";
    priceConfirmed = !(p?.price_cents != null && p.price_scope === "booking");
  }

  function choosePrice(cents: number) {
    price = euros(cents);
    priceConfirmed = true;
  }

  const s = $derived(journey ? summarise(journey.legs) : null);
  const past = $derived(journey ? isPastJourney(journey) : false);

  $effect(() => {
    if (!dialog) return;
    if (journey && !dialog.open) {
      error = "";
      delay = "";
      // A normal search keeps the last choices; an intake starts from what it read (and leaves none behind).
      if (intake || fromIntake) applyIntake(intake);
      fromIntake = !!intake;
      dialog.showModal();
      void loadTravellers();
    } else if (!journey && dialog.open) {
      dialog.close();
    }
  });

  async function loadTravellers() {
    try {
      travellers = await fetchTravellers();
      // Whoever saves the trip travels on it, unless they say otherwise.
      const me = $session?.user?.id;
      travellerIds = new Set(travellers.filter((t) => t.user_id === me).map((t) => t.id));
    } catch {
      // Without the list, the trip simply gets the caller's own traveller.
      travellers = [];
      travellerIds = null;
    }
  }

  function toggleTraveller(id: string) {
    const next = new Set(travellerIds ?? []);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    travellerIds = next;
  }

  const showDiscount = $derived(fareType !== "bahncard100" && fareType !== "deutschlandticket");

  function chooseFare(value: FareTypeValue) {
    fareType = value;
    // Saver fares are bound to their train; others are not, unless said so.
    if (!bindingTouched) trainBinding = value === "saver";
  }

  function parsePrice(raw: string): number | null | "invalid" {
    const cleaned = raw.trim().replace(/€/g, "").replace(/\s/g, "");
    if (cleaned === "") return null;
    const normalised = cleaned.includes(",") ? cleaned.replace(/\./g, "").replace(",", ".") : cleaned;
    const value = Number(normalised);
    if (!Number.isFinite(value) || value < 0) return "invalid";
    return Math.round(value * 100);
  }

  async function save(event: Event) {
    event.preventDefault();
    if (!journey || saving) return;
    if (!priceConfirmed) {
      error = "Say what this trip cost: the ticket prints the whole booking's price.";
      return;
    }
    const cents = parsePrice(price);
    if (cents === "invalid") {
      error = "Enter the price as a number, e.g. 29.90.";
      return;
    }
    let delayMinutes: number | null = null;
    if (past && delay.trim() !== "") {
      delayMinutes = Number(delay.trim());
      if (!Number.isInteger(delayMinutes) || delayMinutes < 0 || delayMinutes > 1440) {
        error = "Enter the delay in whole minutes, e.g. 12.";
        return;
      }
    }
    saving = true;
    error = "";
    try {
      const id = await saveTrip(journey, {
        ...(past ? { past: { arrival_delay_minutes: delayMinutes } } : {}),
        title,
        fare_type: fareType,
        train_binding: trainBinding,
        travel_class: travelClass,
        price_cents: cents,
        bahncard_discount: showDiscount ? discount : null,
        ...(travellerIds ? { traveller_ids: [...travellerIds] } : {}),
        ...(intake ? { source: intake.source } : {}),
      });
      if (ticketFile) {
        // The trip is saved either way; a failed upload leaves it without its ticket, to add by hand.
        await uploadTicket({ id, household_id: await ensureHousehold(), ticket_path: null }, ticketFile).catch(() => {});
      }
      onSaved(id);
    } catch (e) {
      error = describeError(e);
      saving = false;
    }
  }
</script>

<dialog bind:this={dialog} class="modal modal-bottom sm:modal-middle" onclose={onClose}>
  {#if journey && s}
    <form class="modal-box max-w-lg rounded-t-[2rem] bg-base-100 p-0 sm:rounded-[2rem]" onsubmit={save}>
      <div class="bg-mint px-6 pt-6 pb-5">
        <div class="flex items-start justify-between gap-3">
          <div>
            <p class="text-xs font-semibold uppercase tracking-[0.12em] text-primary">{formatLongDate(s.departure.planned)}</p>
            <h2 class="mt-1 text-xl font-semibold text-ink">{s.departure.name} → {s.arrival.name}</h2>
          </div>
          <button type="button" class="btn btn-circle btn-ghost btn-sm" aria-label="Close" onclick={() => dialog?.close()}>
            <X class="h-4 w-4" />
          </button>
        </div>
        <p class="tabular mt-2 text-sm text-base-content/70">
          {formatTime(s.departure.planned)} – {formatTime(s.arrival.planned)} · {formatDuration(s.plannedMinutes)} ·
          {s.changes === 0 ? "direct" : s.changes === 1 ? "1 change" : `${s.changes} changes`}
        </p>
        <div class="mt-3 flex flex-wrap gap-1.5">
          {#each s.rides as ride, i (i)}<LegChip leg={ride} />{/each}
        </div>
      </div>

      <div class="space-y-5 px-6 py-6">
        {#if intake}
          <div
            class="rounded-2xl p-4 text-sm {intake.match.status === 'full' ? 'bg-mint text-primary' : 'bg-amber-soft text-amber-ink'}"
            role="status"
          >
            <p class="font-semibold">
              {intake.parsed.kind === "ticket" ? "Read from your ticket" : "Read from DB Navigator"}
            </p>
            <p class="mt-1">{matchSummary(intake.match)}</p>
            {#if ticketFile}
              <p class="mt-2 inline-flex items-center gap-1.5"><FileText class="h-4 w-4" /> The PDF goes into the trip's ticket wallet.</p>
            {/if}
          </div>
        {/if}
        {#if past}
          <div class="rounded-2xl bg-amber-soft p-4 text-amber-ink">
            <p class="font-semibold">A past trip</p>
            <p class="mt-1 text-sm">It is saved as it happened and not followed live. Say how late you arrived, if you were.</p>
            <label class="mt-3 block">
              <span class="block pb-1 text-xs font-semibold uppercase tracking-wider opacity-80">Arrived late by <span class="normal-case font-normal tracking-normal">(minutes, optional)</span></span>
              <input class="input tabular w-full rounded-2xl bg-card text-base-content" inputmode="numeric" placeholder="0" bind:value={delay} maxlength="4" />
            </label>
          </div>
        {/if}
        <label class="block">
          <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Title <span class="normal-case font-normal tracking-normal">(optional)</span></span>
          <input class="input w-full rounded-2xl bg-card" placeholder="e.g. Weekend in Berlin" bind:value={title} maxlength="80" />
        </label>

        <fieldset>
          <legend class="px-1 pb-2 text-xs font-semibold uppercase tracking-wider text-base-content/55">Ticket</legend>
          <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
            {#each FARE_TYPES as fare (fare.value)}
              <button
                type="button"
                class="rounded-2xl border px-3 py-2.5 text-sm font-medium transition
                  {fareType === fare.value ? 'border-primary bg-mint text-primary' : 'border-base-300 bg-card hover:border-base-content/30'}"
                aria-pressed={fareType === fare.value}
                onclick={() => chooseFare(fare.value)}
              >
                {fare.label}
              </button>
            {/each}
          </div>
        </fieldset>

        <label class="flex cursor-pointer items-start gap-3 rounded-2xl bg-card p-4">
          <input
            type="checkbox"
            class="toggle toggle-primary mt-0.5"
            bind:checked={trainBinding}
            onchange={() => (bindingTouched = true)}
          />
          <span>
            <span class="block font-medium">Bound to these trains</span>
            <span class="block text-sm text-base-content/60">Abeona tells you when a delay of 20 minutes or more lifts the binding.</span>
          </span>
        </label>

        <div class="grid grid-cols-2 gap-4">
          <fieldset>
            <legend class="px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Class</legend>
            <div class="join w-full">
              {#each [1, 2] as const as c (c)}
                <button
                  type="button"
                  class="btn join-item flex-1 {travelClass === c ? 'btn-primary' : 'bg-card'}"
                  aria-pressed={travelClass === c}
                  onclick={() => (travelClass = c)}
                >{c === 1 ? "1st" : "2nd"}</button>
              {/each}
            </div>
          </fieldset>
          <label class="block">
            <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Price <span class="normal-case font-normal tracking-normal">(optional)</span></span>
            <span class="input w-full rounded-2xl bg-card">
              <span class="text-base-content/50">€</span>
              <input inputmode="decimal" placeholder="0.00" bind:value={price} class="tabular" oninput={() => (priceConfirmed = true)} />
            </span>
          </label>
        </div>

        {#if intake?.prefill.price_cents != null && intake.prefill.price_scope === "booking"}
          {@const whole = intake.prefill.price_cents}
          <div class="rounded-2xl p-4 text-sm {priceConfirmed ? 'bg-card' : 'bg-amber-soft text-amber-ink'}">
            <p class="font-semibold">€{euros(whole)} is likely the whole booking</p>
            <p class="mt-1">
              This ticket is one direction of a return booking, and DB prints the booking's total. What did this trip cost?
              Compensation is worked out from it.
            </p>
            <div class="mt-3 flex flex-wrap gap-2">
              <button type="button" class="btn btn-sm rounded-full {priceConfirmed && price === euros(halfOf(whole)) ? 'btn-primary' : 'bg-card'}" onclick={() => choosePrice(halfOf(whole))}>
                Half · €{euros(halfOf(whole))}
              </button>
              <button type="button" class="btn btn-sm rounded-full {priceConfirmed && price === euros(whole) ? 'btn-primary' : 'bg-card'}" onclick={() => choosePrice(whole)}>
                All of it · €{euros(whole)}
              </button>
            </div>
            <p class="mt-2 text-xs opacity-80">Or type the price above.</p>
          </div>
        {/if}

        {#if showDiscount}
          <fieldset>
            <legend class="px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">BahnCard discount</legend>
            <div class="join w-full">
              {#each BAHNCARD_DISCOUNTS as d (d.label)}
                <button
                  type="button"
                  class="btn join-item flex-1 {discount === d.value ? 'btn-primary' : 'bg-card'}"
                  aria-pressed={discount === d.value}
                  onclick={() => (discount = d.value)}
                >{d.label}</button>
              {/each}
            </div>
          </fieldset>
        {/if}

        {#if travellers.length > 1}
          <fieldset>
            <legend class="px-1 pb-2 text-xs font-semibold uppercase tracking-wider text-base-content/55">
              Who travels
              {#if intake?.prefill.travellers}
                <span class="normal-case font-normal tracking-normal">· the ticket is for {intake.prefill.travellers === 1 ? "1 person" : `${intake.prefill.travellers} people`}</span>
              {/if}
            </legend>
            <div class="flex flex-wrap gap-2">
              {#each travellers as t (t.id)}
                {@const on = travellerIds?.has(t.id) ?? false}
                <button
                  type="button"
                  class="inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition
                    {on ? 'border-primary bg-mint text-primary' : 'border-base-300 bg-card text-base-content/70 hover:border-base-content/30'}"
                  aria-pressed={on}
                  onclick={() => toggleTraveller(t.id)}
                >
                  {#if on}<Check class="h-3.5 w-3.5" />{/if}
                  {t.name}
                </button>
              {/each}
            </div>
          </fieldset>
        {/if}

        {#if error}
          <p class="rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">{error}</p>
        {/if}
      </div>

      <div class="flex gap-3 px-6 pb-6">
        <button type="button" class="btn btn-ghost flex-1 rounded-full" onclick={() => dialog?.close()}>Cancel</button>
        <button type="submit" class="btn btn-primary flex-[2] rounded-full" disabled={saving}>
          {#if saving}<span class="loading loading-spinner loading-sm"></span>{/if}
          {past ? "Save past trip" : "Save trip"}
        </button>
      </div>
    </form>
  {/if}
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
