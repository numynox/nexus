<script lang="ts">
  import { CreditCard, ExternalLink, Maximize2, Pencil } from "lucide-svelte";
  import type { BahnCard } from "../../lib/data";
  import { bahnCardLabel, formatDay, parseDay, toDay } from "../../lib/format";
  import { DB_NAVIGATOR_HOME } from "../../lib/journey";

  interface Props {
    card: BahnCard;
    onShow: () => void;
    onEdit: () => void;
  }

  let { card, onShow, onEdit }: Props = $props();

  const today = toDay(new Date());
  const status = $derived(
    card.valid_to < today ? "expired" : card.valid_from > today ? "upcoming" : "valid",
  );
  const deadline = $derived.by(() => {
    const d = parseDay(card.valid_to);
    d.setDate(d.getDate() - card.notice_weeks * 7);
    return toDay(d);
  });
</script>

<div class="rounded-2xl bg-base-100 p-3 {status === 'expired' ? 'opacity-60' : ''}">
  <div class="flex items-start gap-3">
    <span class="flex h-9 w-12 shrink-0 items-center justify-center rounded-lg bg-primary text-xs font-bold text-primary-content shadow-sm">
      {card.kind}
    </span>
    <div class="min-w-0 flex-1">
      <p class="font-medium text-ink">{bahnCardLabel(card)}</p>
      <p class="text-sm text-base-content/60">
        {#if status === "expired"}
          Expired {formatDay(card.valid_to)}
        {:else if status === "upcoming"}
          Valid from {formatDay(card.valid_from)}
        {:else}
          Valid until {formatDay(card.valid_to)}
        {/if}
        {#if card.subscription && status !== "expired"}
          · renews unless cancelled by {formatDay(deadline)}
        {/if}
      </p>
      {#if card.card_number}<p class="tabular text-xs text-base-content/45">{card.card_number}</p>{/if}
    </div>
    <button class="btn btn-ghost btn-circle btn-sm" aria-label="Edit {bahnCardLabel(card)}" onclick={onEdit}>
      <Pencil class="h-4 w-4" />
    </button>
  </div>
  <div class="mt-3 flex flex-wrap gap-2 pl-[3.75rem]">
    {#if card.file_path}
      <button class="btn btn-sm rounded-full border-base-300 bg-card" onclick={onShow}>
        <Maximize2 class="h-4 w-4" /> Show card
      </button>
    {:else}
      <button class="btn btn-ghost btn-sm rounded-full text-base-content/60" onclick={onEdit}>
        <CreditCard class="h-4 w-4" /> Add the card file
      </button>
    {/if}
    <a href={DB_NAVIGATOR_HOME} target="_blank" rel="noopener" class="btn btn-sm rounded-full border-base-300 bg-card">
      <ExternalLink class="h-4 w-4" /> Open in DB Navigator
    </a>
  </div>
</div>
