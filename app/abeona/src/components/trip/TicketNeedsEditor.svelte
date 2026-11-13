<script lang="ts">
  /**
   * What a ticket needs (issue #62): how many travellers, and the BahnCards it
   * was booked with, one chip per card. Used by the save sheet and by the
   * trip page's "Edit trip" sheet.
   */
  import { CreditCard, Minus, Plus, User, X } from "lucide-svelte";
  import type { BahnCardKind } from "../../lib/data";

  interface Props {
    /** Null while unknown (an older trip); the stepper starts at 1 from there. */
    travellerCount: number | null;
    discounts: BahnCardKind[];
    /** Hidden for fares a BahnCard discount does not apply to (Deutschlandticket, BahnCard 100). */
    showDiscounts?: boolean;
    disabled?: boolean;
  }

  let {
    travellerCount = $bindable(),
    discounts = $bindable(),
    showDiscounts = true,
    disabled = false,
  }: Props = $props();

  const uid = $props.id();

  /** DB books up to 9 people online; a larger count already stored stays reachable. */
  const max = $derived(Math.max(9, travellerCount ?? 0));

  function step(by: 1 | -1) {
    travellerCount = travellerCount === null ? 1 : Math.min(max, Math.max(1, travellerCount + by));
  }

  function add(kind: BahnCardKind) {
    discounts = [...discounts, kind].sort((a, b) => a - b);
  }

  function remove(index: number) {
    discounts = discounts.filter((_, i) => i !== index);
  }
</script>

<div class="space-y-4">
  <div class="flex items-center justify-between gap-3 rounded-2xl bg-card p-3 pl-4">
    <span class="flex items-center gap-2 font-medium" id="{uid}-travellers">
      <User class="h-4 w-4 text-base-content/60" aria-hidden="true" /> Travellers
    </span>
    <div class="flex items-center gap-1" role="group" aria-labelledby="{uid}-travellers">
      <button
        type="button"
        class="btn btn-circle btn-ghost btn-sm"
        aria-label="One traveller fewer"
        disabled={disabled || travellerCount === null || travellerCount <= 1}
        onclick={() => step(-1)}
      ><Minus class="h-4 w-4" /></button>
      <span class="tabular min-w-[4.5rem] text-center text-lg font-semibold" aria-live="polite">
        {#if travellerCount === null}<span class="text-sm font-normal text-base-content/55">Not known</span>{:else}{travellerCount}{/if}
      </span>
      <button
        type="button"
        class="btn btn-circle btn-ghost btn-sm"
        aria-label="One traveller more"
        disabled={disabled || (travellerCount !== null && travellerCount >= max)}
        onclick={() => step(1)}
      ><Plus class="h-4 w-4" /></button>
    </div>
  </div>

  {#if showDiscounts}
    <div>
      <p class="flex items-center gap-2 px-1 pb-2 text-sm font-medium">
        <CreditCard class="h-4 w-4 text-base-content/60" aria-hidden="true" /> Booked with BahnCards
      </p>
      <div class="flex flex-wrap items-center gap-2">
        {#each discounts as d, i (i)}
          <span class="inline-flex items-center gap-1 rounded-full bg-mint py-1 pl-3 pr-1 text-sm font-medium text-primary">
            BahnCard {d}
            <button
              type="button"
              class="btn btn-circle btn-ghost btn-xs"
              aria-label="Remove BahnCard {d}"
              {disabled}
              onclick={() => remove(i)}
            ><X class="h-3.5 w-3.5" /></button>
          </span>
        {:else}
          <span class="px-1 text-sm text-base-content/55">None</span>
        {/each}
        {#each [25, 50] as const as kind (kind)}
          <button
            type="button"
            class="btn btn-sm rounded-full border-base-300 bg-card font-normal"
            aria-label="Add BahnCard {kind}"
            {disabled}
            onclick={() => add(kind)}
          ><Plus class="h-3.5 w-3.5" /> BC{kind}</button>
        {/each}
      </div>
    </div>
  {/if}
</div>
