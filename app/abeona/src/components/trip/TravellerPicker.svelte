<script lang="ts">
  /**
   * Who travels, and the BahnCard discount of each one's share: one ticket can
   * carry a BahnCard 25, a BahnCard 50 and someone without. Used by the save
   * sheet and the trip page; Android's TravellerPicker is the same.
   */
  import { Check } from "lucide-svelte";
  import type { BahnCardKind, Traveller, TripTraveller } from "../../lib/data";
  import { BAHNCARD_DISCOUNTS } from "../../lib/format";

  interface Props {
    travellers: Traveller[];
    selected: TripTraveller[];
    /** False for a fare without discounts (Deutschlandticket, BahnCard 100). */
    showDiscount: boolean;
    disabled?: boolean;
    onToggle: (traveller: Traveller) => void;
    onDiscount: (travellerId: string, discount: BahnCardKind | null) => void;
  }

  let { travellers, selected, showDiscount, disabled = false, onToggle, onDiscount }: Props = $props();

  const byId = $derived(new Map(selected.map((s) => [s.traveller_id, s])));
  const rows = $derived(travellers.filter((t) => byId.has(t.id)));
</script>

{#if travellers.length > 1}
  <div class="flex flex-wrap gap-2">
    {#each travellers as t (t.id)}
      {@const on = byId.has(t.id)}
      <button
        type="button"
        class="inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition
          {on ? 'border-primary bg-mint text-primary' : 'border-base-300 bg-card text-base-content/70 hover:border-base-content/30'}"
        aria-pressed={on}
        {disabled}
        onclick={() => onToggle(t)}
      >
        {#if on}<Check class="h-3.5 w-3.5" />{/if}
        {t.name}
      </button>
    {/each}
  </div>
{/if}

{#if showDiscount && rows.length > 0}
  <div class="mt-3 space-y-2">
    {#each rows as t (t.id)}
      {@const current = byId.get(t.id)?.bahncard_discount ?? null}
      <div class="flex items-center gap-3">
        <span class="w-20 shrink-0 truncate text-sm font-medium text-ink" title={t.name}>{t.name}</span>
        <div class="join flex-1" role="group" aria-label={`BahnCard discount for ${t.name}`}>
          {#each BAHNCARD_DISCOUNTS as d (d.label)}
            <button
              type="button"
              class="btn btn-sm join-item flex-1 {current === d.value ? 'btn-primary' : 'bg-card'}"
              aria-pressed={current === d.value}
              {disabled}
              onclick={() => onDiscount(t.id, d.value)}
            >{d.value === null ? "None" : `BC${d.value}`}</button>
          {/each}
        </div>
      </div>
    {/each}
  </div>
{/if}
