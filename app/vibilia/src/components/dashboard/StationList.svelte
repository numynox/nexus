<script lang="ts">
  import { MapPin } from "lucide-svelte";

  let { stations } = $props();

  const lowestCurrentPrice = $derived.by(() => {
    const prices = stations
      .map((station: any) => station.currentPrice)
      .filter((price: unknown) => typeof price === "number");

    if (prices.length === 0) return null;
    return Math.min(...prices);
  });

  function formatPriceParts(price: number): {
    base: string;
    superscript: string;
  } {
    const fixed = price.toFixed(3);
    return {
      base: fixed.slice(0, -1),
      superscript: fixed.slice(-1),
    };
  }
</script>

<div class="grid grid-cols-1 md:grid-cols-2 gap-4">
  {#each stations as s (s.id)}
    {@const isLowest =
      lowestCurrentPrice !== null &&
      typeof s.currentPrice === "number" &&
      s.currentPrice === lowestCurrentPrice}
    {@const displayBrand = (s.brand || s.name || "").trim()}
    {@const displayPlace = (s.place || "").trim()}
    <div
      class="card shadow-sm hover:shadow-md transition-all border {isLowest
        ? 'bg-secondary/10 border-secondary/25'
        : 'bg-base-200 border-base-content/5'}"
    >
      <div class="card-body p-4 flex flex-row items-center justify-between">
        <div class="flex items-start gap-3">
          <div class="mt-1">
            <MapPin class="w-4 h-4 text-accent" />
          </div>
          <div>
            <h3 class="font-bold text-base-content leading-tight">
              {displayBrand}{displayPlace ? ` • ${displayPlace}` : ""}
            </h3>
            <div
              class="flex items-center gap-1 mt-1 text-[10px] text-base-content/40"
            >
              {#if s.whole_day}
                <span class="badge badge-secondary badge-xs text-[9px] h-3"
                  >24h</span
                >
              {/if}
              <span>
                {s.street || ""}
                {s.house_number || ""}
              </span>
            </div>
          </div>
        </div>
        <div
          class="flex items-center justify-end shrink-0 min-w-[6.75rem] text-right self-center"
        >
          {#if s.currentPrice}
            {@const priceParts = formatPriceParts(s.currentPrice)}
            <div
              class="text-2xl font-black text-accent whitespace-nowrap leading-none"
            >
              {priceParts.base}<sup class="text-xs align-super"
                >{priceParts.superscript}</sup
              >
              <span class="text-xs font-normal ml-0.5">€</span>
            </div>
          {:else}
            <div class="badge badge-ghost text-[10px]">No Price</div>
          {/if}
        </div>
      </div>
    </div>
  {:else}
    <div
      class="col-span-full h-32 flex items-center justify-center text-base-content/40 italic"
    >
      No stations found
    </div>
  {/each}
</div>
