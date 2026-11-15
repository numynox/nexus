<script lang="ts">
  /** The ticket and what was booked: fare, class, price, who it is for. Never above the fold on the travel day. */
  import { Armchair, CreditCard, Euro, FileText, Lock, Maximize2, Tag, Trash2, Upload, User } from "lucide-svelte";
  import { describeError, removeTicket, TICKET_TYPES, type Trip, uploadTicket } from "../../lib/data";
  import { bahnCardsShort, fareLabel, formatPrice, ticketNeedsLabel } from "../../lib/format";
  import Fact from "../common/Fact.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";

  interface Props {
    trip: Trip;
    onChanged: (ticketPath: string | null) => void;
    onShow: () => void;
  }

  let { trip, onChanged, onShow }: Props = $props();

  let busy = $state(false);
  let error = $state("");
  let input: HTMLInputElement | undefined = $state();

  const fileName = $derived(
    trip.ticket_path ? trip.ticket_path.split("/").pop()!.replace(/^\d+-/, "") : "",
  );

  export function choose() {
    input?.click();
  }

  async function onFile(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    (event.target as HTMLInputElement).value = "";
    if (!file) return;
    busy = true;
    error = "";
    try {
      onChanged(await uploadTicket(trip, file));
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }

  async function remove() {
    if (!confirm("Remove the ticket from this trip?")) return;
    busy = true;
    error = "";
    try {
      await removeTicket(trip);
      onChanged(null);
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = false;
    }
  }
</script>

<section class="space-y-2" id="ticket">
  <SectionHeader title="Ticket" />
  <div class="space-y-3 rounded-3xl bg-card p-4 shadow-card">
    <div class="flex flex-wrap items-center gap-x-4 gap-y-2" aria-label={ticketNeedsLabel(trip.traveller_count, trip.bahncard_discounts)}>
      <Fact icon={Tag} label="Fare" value={fareLabel(trip.fare_type)} />
      <Fact icon={Armchair} label="Class" value={trip.travel_class === 1 ? "1st" : "2nd"} />
      {#if trip.price_cents !== null}<Fact icon={Euro} label="Price" value={formatPrice(trip.price_cents)} />{/if}
      {#if trip.traveller_count !== null}<Fact icon={User} label="Travellers" value={trip.traveller_count} />{/if}
      {#if trip.bahncard_discounts.length > 0}<Fact icon={CreditCard} label="BahnCards" value={bahnCardsShort(trip.bahncard_discounts)} />{/if}
      {#if trip.train_binding}<Fact icon={Lock} label="Only on the booked trains" value="Train-bound" />{/if}
    </div>

    <input bind:this={input} type="file" accept={TICKET_TYPES.join(",")} class="hidden" onchange={onFile} />

    {#if trip.ticket_path}
      <div class="flex min-h-12 items-center gap-2 rounded-2xl bg-well pl-3 pr-1">
        <FileText class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
        <p class="t-body min-w-0 flex-1 truncate text-ink">{fileName}</p>
        <button class="btn btn-ghost btn-circle btn-sm" onclick={choose} disabled={busy} aria-label="Replace the ticket" title="Replace">
          <Upload class="h-5 w-5" aria-hidden="true" />
        </button>
        <button class="btn btn-ghost btn-circle btn-sm text-bad-ink" onclick={remove} disabled={busy} aria-label="Remove the ticket" title="Remove">
          <Trash2 class="h-5 w-5" aria-hidden="true" />
        </button>
      </div>
      <button class="btn btn-primary w-full rounded-full" onclick={onShow} disabled={busy}>
        <Maximize2 class="h-4 w-4" aria-hidden="true" /> Show ticket
      </button>
    {:else}
      <button class="flex min-h-12 w-full items-center gap-3 rounded-2xl bg-well px-3 text-left transition hover:bg-brand-soft" onclick={choose} disabled={busy}>
        {#if busy}
          <span class="loading loading-spinner loading-sm text-brand"></span>
        {:else}
          <Upload class="h-5 w-5 shrink-0 text-brand" aria-hidden="true" />
        {/if}
        <span class="t-body shrink-0 font-semibold text-ink">Add your ticket</span>
        <span class="t-meta ml-auto min-w-0 truncate" title="PDF, PNG or JPEG, up to 10 MB">PDF, PNG, JPEG · 10 MB</span>
      </button>
    {/if}

    {#if error}
      <p class="t-meta text-bad-ink" role="alert">{error}</p>
    {/if}
  </div>
</section>
