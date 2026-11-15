<script lang="ts">
  import { CalendarClock, CreditCard, Maximize2, Pencil, Upload } from "lucide-svelte";
  import { BAHNCARD_DEADLINE_DAYS, type BahnCard } from "../../lib/data";
  import { bahnCardLabel, bahnCardName, formatDay, parseDay, toDay } from "../../lib/format";
  import StatusChip from "../common/StatusChip.svelte";

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
  /** Yellow only once the deadline is as near as the dashboard's banner looks ahead. */
  const near = $derived((parseDay(deadline).getTime() - parseDay(today).getTime()) / 86_400_000 <= BAHNCARD_DEADLINE_DAYS);
</script>

<!-- One row: the card (icon and kind), whose it is, class and validity, the renewal deadline; show and edit at the end. -->
<div class="grid min-h-14 grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-x-3 py-2 {status === 'expired' ? 'opacity-60' : ''}">
  <span class="tabular inline-flex h-7 items-center gap-1 rounded-lg bg-ink px-1.5 text-xs font-bold text-card" role="img" aria-label="BahnCard {card.kind}">
    <CreditCard class="h-4 w-4" aria-hidden="true" />{card.kind}
  </span>
  <div class="min-w-0">
    <p class="t-body truncate font-medium text-ink">
      {card.name ? bahnCardName(card) : bahnCardLabel(card)}
    </p>
    <p class="flex flex-wrap items-center gap-x-2 gap-y-1">
      <span class="t-meta">
        {#if card.name}{card.travel_class === 1 ? "1st" : "2nd"} class ·{/if}
        {#if status === "expired"}
          expired {formatDay(card.valid_to)}
        {:else if status === "upcoming"}
          from {formatDay(card.valid_from)}
        {:else}
          until {formatDay(card.valid_to)}
        {/if}
      </span>
      {#if card.subscription && status !== "expired"}
        <StatusChip tone={near ? "watch" : "quiet"} icon={CalendarClock} label={formatDay(deadline)} title="Renews unless cancelled by {formatDay(deadline)}" />
      {/if}
    </p>
    {#if card.card_number}<p class="t-meta truncate text-xs">{card.card_number}</p>{/if}
  </div>
  <div class="flex items-center">
    {#if card.file_path}
      <button class="btn btn-ghost btn-circle" aria-label="Show card" title="Show card" onclick={onShow}>
        <Maximize2 class="h-5 w-5" aria-hidden="true" />
      </button>
    {:else}
      <button class="btn btn-ghost btn-circle text-muted" aria-label="Add the card file" title="Add the card file" onclick={onEdit}>
        <Upload class="h-5 w-5" aria-hidden="true" />
      </button>
    {/if}
    <button class="btn btn-ghost btn-circle" aria-label="Edit {card.name ? `${bahnCardName(card)}, ${bahnCardLabel(card)}` : bahnCardLabel(card)}" onclick={onEdit}>
      <Pencil class="h-5 w-5" aria-hidden="true" />
    </button>
  </div>
</div>
