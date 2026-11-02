<script lang="ts">
  import { FileText, Maximize2, Ticket, Trash2, Upload } from "lucide-svelte";
  import { describeError, removeTicket, TICKET_TYPES, type Trip, uploadTicket } from "../../lib/data";

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

<section class="rounded-3xl bg-card p-5 shadow-card" id="ticket">
  <div class="flex items-center gap-2">
    <Ticket class="h-5 w-5 text-primary" />
    <h2 class="font-semibold text-ink">Ticket</h2>
  </div>

  <input bind:this={input} type="file" accept={TICKET_TYPES.join(",")} class="hidden" onchange={onFile} />

  {#if trip.ticket_path}
    <div class="mt-4 flex items-center gap-3 rounded-2xl bg-mint p-3">
      <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-card text-primary"><FileText class="h-5 w-5" /></span>
      <p class="min-w-0 flex-1 truncate text-sm font-medium">{fileName}</p>
    </div>
    <button class="btn btn-primary mt-3 w-full rounded-full" onclick={onShow} disabled={busy}>
      <Maximize2 class="h-4 w-4" /> Show ticket
    </button>
    <div class="mt-2 flex gap-2">
      <button class="btn btn-ghost btn-sm flex-1 rounded-full" onclick={choose} disabled={busy}>
        <Upload class="h-4 w-4" /> Replace
      </button>
      <button class="btn btn-ghost btn-sm flex-1 rounded-full text-error" onclick={remove} disabled={busy}>
        <Trash2 class="h-4 w-4" /> Remove
      </button>
    </div>
  {:else}
    <button
      class="mt-4 flex w-full flex-col items-center gap-2 rounded-2xl border-2 border-dashed border-base-300 px-4 py-6 text-center transition hover:border-primary hover:bg-mint/50"
      onclick={choose}
      disabled={busy}
    >
      {#if busy}
        <span class="loading loading-spinner text-primary"></span>
      {:else}
        <Upload class="h-6 w-6 text-primary" />
      {/if}
      <span class="font-medium">Add your ticket</span>
      <span class="text-xs text-base-content/55">PDF, PNG or JPEG, up to 10 MB</span>
    </button>
  {/if}

  {#if error}
    <p class="mt-3 text-sm text-error" role="alert">{error}</p>
  {/if}
</section>
