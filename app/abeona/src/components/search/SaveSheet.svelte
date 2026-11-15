<script lang="ts">
  import { ArrowRightLeft, CircleAlert, CircleCheck, Clock, Ticket, TriangleAlert, X } from "lucide-svelte";
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
  import Banner from "../common/Banner.svelte";
  import Fact from "../common/Fact.svelte";
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
    <form class="modal-box max-w-lg rounded-t-3xl bg-base-100 p-0 sm:rounded-3xl" onsubmit={save}>
      <div class="space-y-3 p-4">
        <!-- Route and times: what is being saved. -->
        <div>
          <div class="flex items-start justify-between gap-3">
            <div class="min-w-0">
              <p class="t-meta">{formatLongDate(s.departure.planned)}</p>
              <h2 class="t-title text-ink">{s.departure.name} → {s.arrival.name}</h2>
            </div>
            <button type="button" class="btn btn-circle btn-ghost btn-sm -mt-1 -mr-1" aria-label="Close" onclick={() => dialog?.close()}>
              <X class="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
          <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1">
            <span class="t-body tabular font-semibold text-ink">{formatTime(s.departure.planned)} → {formatTime(s.arrival.planned)}</span>
            <Fact icon={Clock} label="Duration" value={formatDuration(s.plannedMinutes)} />
            <Fact icon={ArrowRightLeft} label="Changes" value={s.changes} />
            <span class="flex flex-wrap gap-1">
              {#each s.rides as ride, i (i)}<LegChip leg={ride} />{/each}
            </span>
          </div>
        </div>

        {#if intake}
          <Banner
            tone={intake.match.status === "full" ? "ok" : "watch"}
            icon={intake.match.status === "full" ? CircleCheck : TriangleAlert}
            title="{intake.parsed.kind === 'ticket' ? 'Read from your ticket' : 'Read from DB Navigator'}{directionLabel(intake.parsed) ? ` · ${directionLabel(intake.parsed)}` : ''}"
          >
            {matchSummary(intake.match)}
            {#if more > 0}The return comes next, with the same PDF.{/if}
          </Banner>
        {/if}

        {#if ticketFile}
          <!-- The ticket the intake read: it goes into the trip's ticket wallet on save. -->
          <div class="flex min-h-10 items-center gap-3 rounded-2xl bg-card px-4">
            <Ticket class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
            <span class="sr-only">Ticket attached on save:</span>
            <span class="t-body min-w-0 flex-1 truncate text-ink">{ticketFile.name}</span>
          </div>
        {/if}

        {#if past}
          <!-- A journey that has already arrived is saved as it happened, not followed live. -->
          <label class="flex min-h-12 items-center gap-3 rounded-2xl bg-card px-4">
            <span class="t-body min-w-0 flex-1 text-ink">
              Arrived late by
              <span class="t-meta block">A past trip: not followed live</span>
            </span>
            <input class="input input-sm tabular h-9 w-16 rounded-xl border-transparent bg-well text-right" inputmode="numeric" placeholder="0" bind:value={delay} maxlength="4" />
            <span class="t-meta">min</span>
          </label>
        {/if}

        <input
          class="input t-body h-12 w-full rounded-2xl border-transparent bg-card"
          placeholder="Title (optional), e.g. Weekend in Berlin"
          aria-label="Title (optional)"
          bind:value={title}
          maxlength="80"
        />

        <TicketFields bind:fareType bind:trainBinding bind:bindingTouched bind:travelClass bind:price />

        <fieldset>
          <legend class="sr-only">
            Travellers and BahnCards{#if intake?.parsed.kind === "ticket"}, from the ticket{/if}
          </legend>
          <TicketNeedsEditor bind:travellerCount bind:discounts showDiscounts={showDiscount} />
        </fieldset>

        {#if error}
          <Banner tone="bad" icon={CircleAlert} title={error} />
        {/if}
      </div>

      <div class="sticky bottom-0 flex gap-2 border-t border-line bg-base-100 p-4">
        <button type="button" class="btn btn-ghost h-12 flex-1 rounded-full" onclick={() => dialog?.close()}>Cancel</button>
        <button type="submit" class="btn btn-primary h-12 flex-[2] rounded-full" disabled={saving}>
          {#if saving}<span class="loading loading-spinner loading-sm"></span>{/if}
          {past ? "Save past trip" : "Save trip"}{more > 0 ? " · then the return" : ""}
        </button>
      </div>
    </form>
  {/if}
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
