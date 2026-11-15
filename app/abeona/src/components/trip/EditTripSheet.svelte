<script lang="ts">
  /**
   * "Edit trip" from the trip page's ⋮ menu: the title and what the save sheet
   * asked about the ticket, saved in one update (data.ts: updateTrip).
   */
  import { X } from "lucide-svelte";
  import { describeError, updateTrip, type BahnCardKind, type FareTypeValue, type Trip } from "../../lib/data";
  import { centsToEuros, parseEuros } from "../../lib/format";
  import TicketFields, { discountsApply } from "./TicketFields.svelte";
  import TicketNeedsEditor from "./TicketNeedsEditor.svelte";

  interface Props {
    /** The trip to edit; null closes the sheet. */
    trip: Trip | null;
    onClose: () => void;
    /** After a save: the page loads the trip again. */
    onSaved: () => void | Promise<void>;
  }

  let { trip, onClose, onSaved }: Props = $props();

  let dialog: HTMLDialogElement | undefined = $state();
  let title = $state("");
  let fareType = $state<FareTypeValue>("other");
  let trainBinding = $state(false);
  let travelClass = $state<1 | 2>(2);
  let price = $state("");
  let travellerCount = $state<number | null>(null);
  let discounts = $state<BahnCardKind[]>([]);
  let saving = $state(false);
  let error = $state("");

  const showDiscounts = $derived(discountsApply(fareType));

  $effect(() => {
    if (!dialog) return;
    if (trip && !dialog.open) {
      title = trip.title ?? "";
      fareType = trip.fare_type;
      trainBinding = trip.train_binding;
      travelClass = trip.travel_class;
      price = trip.price_cents !== null ? centsToEuros(trip.price_cents) : "";
      travellerCount = trip.traveller_count;
      discounts = [...(trip.bahncard_discounts ?? [])];
      error = "";
      saving = false;
      dialog.showModal();
    } else if (!trip && dialog.open) {
      dialog.close();
    }
  });

  async function save(event: Event) {
    event.preventDefault();
    if (!trip || saving) return;
    const cents = parseEuros(price);
    if (cents === "invalid") {
      error = "Enter the price as a number, e.g. 29.90.";
      return;
    }
    saving = true;
    error = "";
    try {
      await updateTrip(trip.id, {
        title: title.trim() || null,
        fare_type: fareType,
        train_binding: trainBinding,
        travel_class: travelClass,
        price_cents: cents,
        traveller_count: travellerCount,
        bahncard_discounts: showDiscounts ? discounts : [],
      });
      await onSaved();
      dialog?.close();
    } catch (e) {
      error = describeError(e);
    } finally {
      saving = false;
    }
  }
</script>

<dialog bind:this={dialog} class="modal modal-bottom sm:modal-middle" onclose={onClose}>
  {#if trip}
    <form class="modal-box max-w-lg rounded-t-3xl bg-base-100 p-0 sm:rounded-3xl" onsubmit={save}>
      <div class="flex items-start justify-between gap-3 px-4 pt-4">
        <div class="min-w-0">
          <p class="t-meta truncate">{trip.origin_name} → {trip.destination_name}</p>
          <h2 class="t-title text-ink">Edit trip</h2>
        </div>
        <button type="button" class="btn btn-circle btn-ghost btn-sm" aria-label="Close" onclick={() => dialog?.close()}>
          <X class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>

      <div class="space-y-4 p-4">
        <label class="block">
          <span class="t-meta block px-1 pb-1">Title (optional)</span>
          <input class="input w-full rounded-2xl border-line bg-well" placeholder={`${trip.origin_name} → ${trip.destination_name}`} bind:value={title} maxlength="80" />
        </label>

        <!-- The binding was chosen once already: a new fare leaves it as it is. -->
        <TicketFields bind:fareType bind:trainBinding bindingTouched={true} bind:travelClass bind:price />

        <fieldset>
          <legend class="t-meta px-1 pb-2">Travellers &amp; BahnCards</legend>
          <TicketNeedsEditor bind:travellerCount bind:discounts {showDiscounts} disabled={saving} />
        </fieldset>

        {#if error}
          <p class="t-meta rounded-2xl bg-bad-soft px-4 py-3 text-bad-ink" role="alert">{error}</p>
        {/if}
      </div>

      <div class="flex gap-3 px-4 pb-4">
        <button type="button" class="btn btn-ghost flex-1 rounded-full" onclick={() => dialog?.close()}>Cancel</button>
        <button type="submit" class="btn btn-primary flex-[2] rounded-full" disabled={saving}>
          {#if saving}<span class="loading loading-spinner loading-sm"></span>{/if}
          Save
        </button>
      </div>
    </form>
  {/if}
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
