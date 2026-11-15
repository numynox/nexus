<script lang="ts">
  import { CircleAlert, FileText, Info, Trash2, Upload, X } from "lucide-svelte";
  import {
    deleteBahnCard,
    describeError,
    removeBahnCardFile,
    saveBahnCard,
    TICKET_TYPES,
    uploadBahnCardFile,
    type BahnCard,
    type BahnCardKind,
  } from "../../lib/data";
  import { bahnCardName, toDay } from "../../lib/format";
  import Banner from "../common/Banner.svelte";

  interface Props {
    /** Shows the sheet; false closes it. */
    open: boolean;
    /** The card being edited, or null for a new one. */
    card: BahnCard | null;
    onClose: () => void;
    onSaved: (card: BahnCard) => void;
    onDeleted: (id: string) => void;
  }

  let { open, card, onClose, onSaved, onDeleted }: Props = $props();

  let dialog: HTMLDialogElement | undefined = $state();
  let name = $state("");
  let kind = $state<BahnCardKind>(25);
  let travelClass = $state<1 | 2>(2);
  let validFrom = $state("");
  let validTo = $state("");
  let cardNumber = $state("");
  let subscription = $state(true);
  let noticeWeeks = $state(4);
  let file = $state<File | null>(null);
  let removeFile = $state(false);
  let saving = $state(false);
  let error = $state("");

  let renewInfo = $state(false);

  // A compact choice in a row: the chosen side is the brand's soft surface.
  const segment = "t-meta flex h-9 min-w-10 items-center justify-center rounded-full px-3 transition-colors";
  const segmentOn = "bg-brand-soft font-semibold text-brand";
  const row = "flex min-h-12 items-center gap-3 px-4";
  const field = "input input-sm tabular h-9 rounded-xl border-transparent bg-well text-ink";

  const existingFile = $derived(card?.file_path ? card.file_path.split("/").pop()!.replace(/^\d+-/, "") : "");

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) {
      reset();
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  });

  function reset() {
    error = "";
    saving = false;
    file = null;
    removeFile = false;
    if (card) {
      name = card.name ?? "";
      kind = card.kind;
      travelClass = card.travel_class;
      validFrom = card.valid_from;
      validTo = card.valid_to;
      cardNumber = card.card_number ?? "";
      subscription = card.subscription;
      noticeWeeks = card.notice_weeks;
    } else {
      // A BahnCard runs for a year: today until the day before next year's date.
      const from = new Date();
      const to = new Date(from.getFullYear() + 1, from.getMonth(), from.getDate() - 1);
      name = "";
      kind = 25;
      travelClass = 2;
      validFrom = toDay(from);
      validTo = toDay(to);
      cardNumber = "";
      subscription = true;
      noticeWeeks = 4;
    }
  }

  function onFile(event: Event) {
    const chosen = (event.target as HTMLInputElement).files?.[0];
    (event.target as HTMLInputElement).value = "";
    if (chosen) {
      file = chosen;
      removeFile = false;
    }
  }

  async function save(event: Event) {
    event.preventDefault();
    if (!open || saving) return;
    if (!validFrom || !validTo) {
      error = "Enter when the card is valid.";
      return;
    }
    if (validTo < validFrom) {
      error = "The card cannot end before it starts.";
      return;
    }
    saving = true;
    error = "";
    try {
      let saved = await saveBahnCard(
        {
          // Blank means no name: the card is then called by its kind.
          name: name.trim() || null,
          kind,
          travel_class: travelClass,
          valid_from: validFrom,
          valid_to: validTo,
          card_number: cardNumber,
          subscription,
          notice_weeks: Math.max(0, Math.min(26, Math.round(noticeWeeks || 0))),
        },
        card?.id,
      );
      if (file) {
        saved = { ...saved, file_path: await uploadBahnCardFile(saved, file) };
      } else if (removeFile && saved.file_path) {
        await removeBahnCardFile(saved);
        saved = { ...saved, file_path: null };
      }
      onSaved(saved);
      dialog?.close();
    } catch (e) {
      error = describeError(e);
      saving = false;
    }
  }

  async function remove() {
    if (!card || !confirm(`Delete ${bahnCardName(card)}?`)) return;
    saving = true;
    try {
      await deleteBahnCard(card);
      onDeleted(card.id);
      dialog?.close();
    } catch (e) {
      error = describeError(e);
      saving = false;
    }
  }
</script>

<dialog bind:this={dialog} class="modal modal-bottom sm:modal-middle" onclose={onClose}>
  {#if open}
    <form class="modal-box max-w-lg rounded-t-3xl bg-base-100 p-0 sm:rounded-3xl" onsubmit={save}>
      <div class="space-y-3 p-4">
        <div class="flex items-center justify-between gap-3">
          <h2 class="t-title text-ink">{card ? `Edit ${bahnCardName(card)}` : "Add a BahnCard"}</h2>
          <button type="button" class="btn btn-circle btn-ghost btn-sm -mr-1" aria-label="Close" onclick={() => dialog?.close()}>
            <X class="h-5 w-5" aria-hidden="true" />
          </button>
        </div>

        <!-- One line per fact of the card: its name on the left, the value on the right. -->
        <div class="divide-y divide-line rounded-2xl bg-card">
          <label class={row}>
            <span class="t-body shrink-0 text-ink">Name</span>
            <input class="{field} min-w-0 flex-1 text-right" bind:value={name} maxlength="60" placeholder={`optional, e.g. Mia`} />
          </label>
          <div class={row}>
            <span class="t-body flex-1 text-ink" id="bc-kind">Card</span>
            <div class="inline-flex rounded-full bg-well p-0.5" role="group" aria-labelledby="bc-kind">
              {#each [25, 50, 100] as const as k (k)}
                <button type="button" class="{segment} tabular {kind === k ? segmentOn : ''}" aria-pressed={kind === k} onclick={() => (kind = k)}>{k}</button>
              {/each}
            </div>
          </div>
          <div class={row}>
            <span class="t-body flex-1 text-ink" id="bc-class">Class</span>
            <div class="inline-flex rounded-full bg-well p-0.5" role="group" aria-labelledby="bc-class">
              {#each [1, 2] as const as c (c)}
                <button type="button" class="{segment} {travelClass === c ? segmentOn : ''}" aria-pressed={travelClass === c} onclick={() => (travelClass = c)}>{c === 1 ? "1st" : "2nd"}</button>
              {/each}
            </div>
          </div>
          <label class={row}>
            <span class="t-body flex-1 text-ink">Valid from</span>
            <input type="date" class="{field} w-40" bind:value={validFrom} required />
          </label>
          <label class={row}>
            <span class="t-body flex-1 text-ink">Valid until</span>
            <input type="date" class="{field} w-40" bind:value={validTo} min={validFrom} required />
          </label>
          <label class={row}>
            <span class="t-body shrink-0 text-ink">Card number</span>
            <input class="{field} min-w-0 flex-1 text-right" inputmode="numeric" bind:value={cardNumber} maxlength="40" placeholder="optional" />
          </label>
        </div>

        <div class="divide-y divide-line rounded-2xl bg-card">
          <div>
            <div class={row}>
              <label class="t-body flex-1 text-ink" for="bc-renews">Renews automatically</label>
              <button type="button" class="btn btn-circle btn-ghost btn-sm text-muted" aria-label="About renewing" aria-expanded={renewInfo} onclick={() => (renewInfo = !renewInfo)}>
                <Info class="h-5 w-5" aria-hidden="true" />
              </button>
              <input id="bc-renews" type="checkbox" class="toggle toggle-primary" bind:checked={subscription} />
            </div>
            {#if renewInfo}
              <p class="t-meta px-4 pb-3">A subscription runs on unless it is cancelled in time. Turn this off once it is cancelled.</p>
            {/if}
          </div>
          {#if subscription}
            <label class={row}>
              <span class="t-body flex-1 text-ink">Notice period</span>
              <input type="number" class="{field} w-16 text-right" min="0" max="26" bind:value={noticeWeeks} />
              <span class="t-meta">weeks</span>
            </label>
          {/if}
          {#if file || (existingFile && !removeFile)}
            <div class={row}>
              <FileText class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
              <span class="sr-only">Card file:</span>
              <span class="t-body min-w-0 flex-1 truncate text-ink">{file?.name ?? existingFile}</span>
              <button type="button" class="btn btn-ghost btn-circle btn-sm text-bad-ink" aria-label="Remove file" onclick={() => { file = null; removeFile = true; }}>
                <Trash2 class="h-5 w-5" aria-hidden="true" />
              </button>
            </div>
          {:else}
            <label class="{row} cursor-pointer">
              <Upload class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
              <span class="t-body flex-1 text-ink">Add the card file</span>
              <span class="t-meta">PDF, PNG or JPEG, up to 10 MB</span>
              <input type="file" accept={TICKET_TYPES.join(",")} class="hidden" onchange={onFile} />
            </label>
          {/if}
        </div>

        {#if error}
          <Banner tone="bad" icon={CircleAlert} title={error} />
        {/if}
      </div>

      <div class="sticky bottom-0 flex gap-2 border-t border-line bg-base-100 p-4">
        {#if card}
          <button type="button" class="btn btn-ghost h-12 rounded-full text-bad-ink" onclick={remove} disabled={saving}>
            <Trash2 class="h-5 w-5" aria-hidden="true" /> Delete
          </button>
        {:else}
          <button type="button" class="btn btn-ghost h-12 flex-1 rounded-full" onclick={() => dialog?.close()}>Cancel</button>
        {/if}
        <button type="submit" class="btn btn-primary h-12 flex-[2] rounded-full" disabled={saving}>
          {#if saving}<span class="loading loading-spinner loading-sm"></span>{/if}
          Save card
        </button>
      </div>
    </form>
  {/if}
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
