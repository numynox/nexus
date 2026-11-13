<script lang="ts">
  import { FileText, Trash2, Upload, X } from "lucide-svelte";
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
    <form class="modal-box max-w-lg rounded-t-[2rem] bg-base-100 p-0 sm:rounded-[2rem]" onsubmit={save}>
      <div class="flex items-start justify-between gap-3 bg-sky px-6 pt-6 pb-5">
        <div>
          {#if card}<p class="text-xs font-semibold uppercase tracking-[0.12em] text-ink/70">{bahnCardName(card)}</p>{/if}
          <h2 class="mt-1 text-xl font-semibold text-ink">{card ? "Edit BahnCard" : "Add a BahnCard"}</h2>
        </div>
        <button type="button" class="btn btn-circle btn-ghost btn-sm" aria-label="Close" onclick={() => dialog?.close()}>
          <X class="h-4 w-4" />
        </button>
      </div>

      <div class="space-y-5 px-6 py-6">
        <label class="block">
          <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Name <span class="normal-case font-normal tracking-normal">(optional)</span></span>
          <input class="input w-full rounded-2xl bg-card" bind:value={name} maxlength="60" placeholder={`e.g. Mia, or leave empty for "BahnCard ${kind}"`} />
        </label>

        <div class="grid grid-cols-[minmax(0,3fr)_minmax(0,2fr)] gap-4">
          <fieldset>
            <legend class="px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Card</legend>
            <div class="join w-full">
              {#each [25, 50, 100] as const as k (k)}
                <button
                  type="button"
                  class="btn join-item flex-1 {kind === k ? 'btn-primary' : 'bg-card'}"
                  aria-pressed={kind === k}
                  onclick={() => (kind = k)}
                >{k}</button>
              {/each}
            </div>
          </fieldset>
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
        </div>

        <div class="grid grid-cols-2 gap-4">
          <label class="block">
            <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Valid from</span>
            <input type="date" class="input w-full rounded-2xl bg-card" bind:value={validFrom} required />
          </label>
          <label class="block">
            <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Valid until</span>
            <input type="date" class="input w-full rounded-2xl bg-card" bind:value={validTo} min={validFrom} required />
          </label>
        </div>

        <label class="block">
          <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Card number <span class="normal-case font-normal tracking-normal">(optional)</span></span>
          <input class="input tabular w-full rounded-2xl bg-card" inputmode="numeric" bind:value={cardNumber} maxlength="40" placeholder="7081 4111 1234 5678" />
        </label>

        <div class="rounded-2xl bg-card p-4">
          <label class="flex cursor-pointer items-start gap-3">
            <input type="checkbox" class="toggle toggle-primary mt-0.5" bind:checked={subscription} />
            <span>
              <span class="block font-medium">Renews automatically</span>
              <span class="block text-sm text-base-content/60">A subscription runs on unless it is cancelled in time. Turn this off once it is cancelled.</span>
            </span>
          </label>
          {#if subscription}
            <label class="mt-3 flex items-center gap-3 pl-[3.25rem]">
              <span class="text-sm">Notice period</span>
              <input type="number" class="input input-sm tabular w-20 rounded-full bg-base-100" min="0" max="26" bind:value={noticeWeeks} />
              <span class="text-sm text-base-content/60">weeks</span>
            </label>
          {/if}
        </div>

        <div>
          <p class="px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Card file <span class="normal-case font-normal tracking-normal">(optional)</span></p>
          {#if file || (existingFile && !removeFile)}
            <div class="flex items-center gap-3 rounded-2xl bg-mint p-3">
              <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-card text-primary"><FileText class="h-5 w-5" /></span>
              <p class="min-w-0 flex-1 truncate text-sm font-medium">{file?.name ?? existingFile}</p>
              <button type="button" class="btn btn-ghost btn-circle btn-sm text-error" aria-label="Remove file" onclick={() => { file = null; removeFile = true; }}>
                <Trash2 class="h-4 w-4" />
              </button>
            </div>
          {:else}
            <label class="flex w-full cursor-pointer items-center gap-3 rounded-2xl border-2 border-dashed border-base-300 px-4 py-3 transition hover:border-primary hover:bg-mint/50">
              <Upload class="h-5 w-5 text-primary" />
              <span class="text-sm"><span class="font-medium">Add the PDF or a screenshot</span><br /><span class="text-base-content/55">PDF, PNG or JPEG, up to 10 MB</span></span>
              <input type="file" accept={TICKET_TYPES.join(",")} class="hidden" onchange={onFile} />
            </label>
          {/if}
        </div>

        {#if error}
          <p class="rounded-2xl bg-error/10 px-4 py-3 text-sm text-error" role="alert">{error}</p>
        {/if}
      </div>

      <div class="flex gap-3 px-6 pb-6">
        {#if card}
          <button type="button" class="btn btn-ghost rounded-full text-error" onclick={remove} disabled={saving}>
            <Trash2 class="h-4 w-4" /> Delete
          </button>
        {:else}
          <button type="button" class="btn btn-ghost flex-1 rounded-full" onclick={() => dialog?.close()}>Cancel</button>
        {/if}
        <button type="submit" class="btn btn-primary flex-[2] rounded-full" disabled={saving}>
          {#if saving}<span class="loading loading-spinner loading-sm"></span>{/if}
          Save card
        </button>
      </div>
    </form>
  {/if}
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
