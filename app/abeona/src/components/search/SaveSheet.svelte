<script lang="ts">
  import { FileText, X } from "lucide-svelte";
  import {
    describeError,
    ensureHousehold,
    isPastJourney,
    saveTrip,
    uploadTicket,
    type BahnCardKind,
    type FareTypeValue,
  } from "../../lib/data";
  import { centsToEuros, formatDuration, formatLongDate, formatTime, parseEuros } from "../../lib/format";
  import { summarise } from "../../lib/journey";
  import { directionLabel, matchSummary, type IntakeDraft } from "../../lib/intake/intake";
  import type { Journey } from "../../lib/transit";
  import LegChip from "../common/LegChip.svelte";
  import { ticketSummary } from "../../lib/ticketNeeds";
  import TicketFields, { discountsApply } from "../trip/TicketFields.svelte";
  import TicketNeedsEditor from "../trip/TicketNeedsEditor.svelte";

  interface Props {
    journey: Journey | null;
    /** Set when the journey came from a share text or a ticket: the fields start from what was read. */
    intake?: IntakeDraft | null;
    /** The ticket PDF the intake read; attached to the trip once it is saved. */
    ticketFile?: File | null;
    /** How many trips from the same ticket follow this one (the return of a ticket for both directions). */
    more?: number;
    onClose: () => void;
    onSaved: (tripId: string) => void;
  }

  let { journey, intake = null, ticketFile = null, more = 0, onClose, onSaved }: Props = $props();

  let dialog: HTMLDialogElement | undefined = $state();
  let title = $state("");
  let fareType = $state<FareTypeValue>("saver");
  let trainBinding = $state(true);
  let bindingTouched = $state(false);
  let travelClass = $state<1 | 2>(2);
  let price = $state("");
  /** What the ticket needs: from a ticket what it printed, else one traveller without a BahnCard. */
  let travellerCount = $state<number | null>(1);
  let discounts = $state<BahnCardKind[]>([]);
  let saving = $state(false);
  /** For a journey that has already arrived: how late it really was, typed by hand. */
  let delay = $state("");
  let error = $state("");
  let fromIntake = false;

  function applyIntake(draft: IntakeDraft | null) {
    const p = draft?.prefill;
    title = "";
    fareType = p?.fare_type ?? "saver";
    trainBinding = p?.train_binding ?? fareType === "saver";
    bindingTouched = p?.train_binding != null;
    travelClass = p?.travel_class ?? 2;
    price = p?.price_cents != null ? centsToEuros(p.price_cents) : "";
    const needs = ticketSummary(p?.travellers ?? null, p?.discounts ?? []);
    travellerCount = needs.traveller_count ?? 1;
    discounts = needs.bahncard_discounts as BahnCardKind[];
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
      if (!intake) {
        // Who rides is not a habit worth keeping: every plain search starts at one, without BahnCards.
        travellerCount = 1;
        discounts = [];
      }
      dialog.showModal();
    } else if (!journey && dialog.open) {
      dialog.close();
    }
  });

  const showDiscount = $derived(discountsApply(fareType));

  async function save(event: Event) {
    event.preventDefault();
    if (!journey || saving) return;
    const cents = parseEuros(price);
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
        traveller_count: travellerCount,
        bahncard_discounts: showDiscount ? discounts : [],
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
              {intake.parsed.kind === "ticket" ? "Read from your ticket" : "Read from DB Navigator"}{#if directionLabel(intake.parsed)}
                · {directionLabel(intake.parsed)}{/if}
            </p>
            <p class="mt-1">{matchSummary(intake.match)}</p>
            {#if more > 0}
              <p class="mt-1">The ticket is for both directions: the return comes next, with the same PDF.</p>
            {/if}
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

        <TicketFields bind:fareType bind:trainBinding bind:bindingTouched bind:travelClass bind:price />

        <fieldset>
          <legend class="px-1 pb-2 text-xs font-semibold uppercase tracking-wider text-base-content/55">
            Travellers &amp; BahnCards
            {#if intake?.parsed.kind === "ticket"}<span class="normal-case font-normal tracking-normal">· from the ticket</span>{/if}
          </legend>
          <TicketNeedsEditor bind:travellerCount bind:discounts showDiscounts={showDiscount} />
        </fieldset>

        {#if error}
          <p class="rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">{error}</p>
        {/if}
      </div>

      <div class="flex gap-3 px-6 pb-6">
        <button type="button" class="btn btn-ghost flex-1 rounded-full" onclick={() => dialog?.close()}>Cancel</button>
        <button type="submit" class="btn btn-primary flex-[2] rounded-full" disabled={saving}>
          {#if saving}<span class="loading loading-spinner loading-sm"></span>{/if}
          {past ? "Save past trip" : "Save trip"}{more > 0 ? " · then the return" : ""}
        </button>
      </div>
    </form>
  {/if}
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
