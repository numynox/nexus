<script lang="ts" module>
  import type { FareTypeValue } from "../../lib/data";

  /** A BahnCard discount means nothing on a Deutschlandticket or a BahnCard 100 fare. */
  export function discountsApply(fareType: FareTypeValue): boolean {
    return fareType !== "bahncard100" && fareType !== "deutschlandticket";
  }
</script>

<script lang="ts">
  /**
   * The ticket's facts — fare, binding, class, price — as the save sheet and
   * "Edit trip" both ask for them.
   */
  import { FARE_TYPES } from "../../lib/format";

  interface Props {
    fareType: FareTypeValue;
    trainBinding: boolean;
    /** Once the binding was set by hand (or read from a ticket), choosing a fare leaves it alone. */
    bindingTouched?: boolean;
    travelClass: 1 | 2;
    /** In euros, as typed. */
    price: string;
  }

  let {
    fareType = $bindable(),
    trainBinding = $bindable(),
    bindingTouched = $bindable(false),
    travelClass = $bindable(),
    price = $bindable(),
  }: Props = $props();

  function chooseFare(value: FareTypeValue) {
    fareType = value;
    // Saver fares are bound to their train; others are not, unless said so.
    if (!bindingTouched) trainBinding = value === "saver";
  }
</script>

<div class="space-y-5">
  <fieldset>
    <legend class="px-1 pb-2 text-xs font-semibold uppercase tracking-wider text-base-content/55">Ticket</legend>
    <div class="grid grid-cols-2 gap-2 sm:grid-cols-3">
      {#each FARE_TYPES as fare (fare.value)}
        <button
          type="button"
          class="rounded-2xl border px-3 py-2.5 text-sm font-medium transition
            {fareType === fare.value ? 'border-primary bg-mint text-primary' : 'border-base-300 bg-card hover:border-base-content/30'}"
          aria-pressed={fareType === fare.value}
          onclick={() => chooseFare(fare.value)}
        >
          {fare.label}
        </button>
      {/each}
    </div>
  </fieldset>

  <label class="flex cursor-pointer items-start gap-3 rounded-2xl bg-card p-4">
    <input
      type="checkbox"
      class="toggle toggle-primary mt-0.5"
      bind:checked={trainBinding}
      onchange={() => (bindingTouched = true)}
    />
    <span>
      <span class="block font-medium">Bound to these trains</span>
      <span class="block text-sm text-base-content/60">Abeona tells you when a delay of 20 minutes or more lifts the binding.</span>
    </span>
  </label>

  <div class="grid grid-cols-2 gap-4">
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
    <label class="block">
      <span class="block px-1 pb-1 text-xs font-semibold uppercase tracking-wider text-base-content/55">Price <span class="normal-case font-normal tracking-normal">(optional)</span></span>
      <span class="input w-full rounded-2xl bg-card">
        <span class="text-base-content/50">€</span>
        <input inputmode="decimal" placeholder="0.00" bind:value={price} class="tabular" />
      </span>
    </label>
  </div>
</div>
