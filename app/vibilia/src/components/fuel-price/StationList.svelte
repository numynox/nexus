<script lang="ts">
  import { ChevronDown, Clock3, Fuel } from "lucide-svelte";
  import { slide } from "svelte/transition";

  type OpeningTimeEntry = {
    text?: string;
    start?: string;
    end?: string;
  };
  let { stations } = $props();
  let openStationId = $state<string | number | null>(null);

  function getStationDiscount(station: any): number {
    const parsed = Number(station?.discount);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function getDiscountedCurrentPrice(station: any): number | undefined {
    if (typeof station?.currentPrice !== "number") return undefined;
    return Math.max(0, station.currentPrice - getStationDiscount(station));
  }

  const sortedStations = $derived.by(() => {
    return [...stations].sort((a: any, b: any) => {
      const aPrice = getDiscountedCurrentPrice(a);
      const bPrice = getDiscountedCurrentPrice(b);

      const aHasPrice = typeof aPrice === "number";
      const bHasPrice = typeof bPrice === "number";

      if (aHasPrice && bHasPrice) {
        if (aPrice !== bPrice) return aPrice - bPrice;
      } else if (aHasPrice) {
        return -1;
      } else if (bHasPrice) {
        return 1;
      }

      const aName = String(a?.brand || a?.name || "").toLowerCase();
      const bName = String(b?.brand || b?.name || "").toLowerCase();
      return aName.localeCompare(bName);
    });
  });

  const lowestCurrentPrice = $derived.by(() => {
    const prices = sortedStations
      .map((station: any) => getDiscountedCurrentPrice(station))
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

  function formatExtraCostFor50L(
    currentPrice: number,
    lowestPrice: number,
  ): string {
    const delta = Math.max(0, currentPrice - lowestPrice);
    const extraCost = delta * 50;
    return extraCost.toFixed(2);
  }

  function toggleStation(stationId: string | number) {
    openStationId = openStationId === stationId ? null : stationId;
  }

  function normalizeOpeningTimes(openingTimes: unknown): OpeningTimeEntry[] {
    if (Array.isArray(openingTimes)) {
      return openingTimes.filter(
        (entry): entry is OpeningTimeEntry =>
          typeof entry === "object" && entry !== null,
      );
    }

    if (typeof openingTimes === "string") {
      try {
        const parsed = JSON.parse(openingTimes);
        return Array.isArray(parsed)
          ? parsed.filter(
              (entry): entry is OpeningTimeEntry =>
                typeof entry === "object" && entry !== null,
            )
          : [];
      } catch {
        return [];
      }
    }

    return [];
  }

  function formatTime(time: string | undefined): string {
    if (!time) return "";
    return time.slice(0, 5);
  }

  function formatOpeningLabel(entry: OpeningTimeEntry): string {
    return (entry.text || "Opening Time").trim();
  }

  function formatOpeningTimeRange(entry: OpeningTimeEntry): string {
    const start = formatTime(entry.start);
    const end = formatTime(entry.end);

    return start && end ? `${start} - ${end}` : start || end || "";
  }

  function formatDisplayBrand(value: string): string {
    const trimmed = value.trim();
    if (trimmed.length <= 10) return trimmed;
    return `${trimmed.slice(0, 10)}...`;
  }
</script>

<div class="grid grid-cols-1 md:grid-cols-2 gap-4 items-start">
  {#each sortedStations as s (s.id)}
    {@const discount = getStationDiscount(s)}
    {@const discountedCurrentPrice = getDiscountedCurrentPrice(s)}
    {@const isLowest =
      lowestCurrentPrice !== null &&
      typeof discountedCurrentPrice === "number" &&
      Math.abs(discountedCurrentPrice - lowestCurrentPrice) < 0.000001}
    {@const displayBrand = formatDisplayBrand((s.brand || s.name || "").trim())}
    {@const displayPlace = (s.place || "").trim()}
    {@const openingTimes = normalizeOpeningTimes(
      s.opening_times ?? s.openingTimes,
    )}
    {@const isOpen = openStationId === s.id}

    <div
      class="card h-fit shadow-sm hover:shadow-md transition-all border {isLowest
        ? 'bg-secondary/10 border-secondary/25'
        : 'bg-base-200 border-base-content/5'}"
    >
      <button
        type="button"
        class="card-body p-4 w-full text-left cursor-pointer"
        aria-expanded={isOpen}
        onclick={() => toggleStation(s.id)}
      >
        <div class="flex flex-row items-center justify-between gap-3">
          <div class="flex items-start gap-3 min-w-0">
            <div class="shrink-0 self-center">
              <Fuel class="w-4 h-4 text-accent" />
            </div>
            <div class="min-w-0">
              <h3 class="font-bold text-base-content leading-tight">
                <span>{displayBrand}</span>
                {#if displayPlace}
                  <span class="text-base-content/55"> • {displayPlace}</span>
                {/if}
              </h3>
              <div
                class="flex flex-wrap items-center gap-1 mt-1 text-[10px] text-base-content/40"
              >
                {#if s.whole_day}
                  <span class="badge badge-secondary badge-xs text-[9px] h-3"
                    >24h</span
                  >
                {/if}
                {#if discount > 0}
                  <span class="badge badge-accent badge-xs text-[9px] h-3">
                    -{Math.round(discount * 100)}ct
                  </span>
                {/if}
                <span>
                  {s.street || ""}
                  {s.house_number || ""}
                </span>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-3 shrink-0 self-center">
            <div
              class="flex min-w-[6.75rem] items-center justify-end gap-2 text-right"
            >
              {#if typeof discountedCurrentPrice === "number"}
                {@const priceParts = formatPriceParts(discountedCurrentPrice)}
                {@const extraCostFor50L =
                  lowestCurrentPrice !== null && !isLowest
                    ? formatExtraCostFor50L(
                        discountedCurrentPrice,
                        lowestCurrentPrice,
                      )
                    : null}
                {#if extraCostFor50L !== null}
                  <div
                    class="text-[10px] font-semibold text-base-content/65 leading-tight text-center"
                  >
                    <div>+{extraCostFor50L} €</div>
                    <div>per 50L</div>
                  </div>
                {/if}
                <div
                  class="inline-flex items-center gap-0.5 rounded-md bg-base-100 p-1.5 pb-0.5 text-accent leading-none shadow-inner"
                >
                  <span
                    class="text-2xl font-black font-mono tabular-nums tracking-tight"
                    >{priceParts.base}</span
                  ><sup class="relative top-[-4px] text-xs font-mono"
                    >{priceParts.superscript}</sup
                  >
                </div>
              {:else}
                <div class="badge badge-ghost text-[10px]">No Price</div>
              {/if}
            </div>

            <ChevronDown
              class="w-4 h-4 text-base-content/40 transition-transform duration-200 {isOpen
                ? 'rotate-180'
                : ''}"
            />
          </div>
        </div>
      </button>

      {#if isOpen}
        <div class="px-4 pb-4 pt-0" transition:slide={{ duration: 180 }}>
          <div class="border-t border-base-content/10 pt-3">
            <div class="flex items-center justify-between gap-2 mb-2">
              <div
                class="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-base-content/45"
              >
                <Clock3 class="w-3 h-3" />
                Opening Hours
              </div>
            </div>

            {#if s.whole_day}
              <div class="text-sm font-semibold text-secondary">Open 24h</div>
            {:else if openingTimes.length > 0}
              <div class="space-y-2 max-h-44 overflow-y-auto pr-1">
                {#each openingTimes as openingTime, index (`${s.id}-${index}`)}
                  {@const openingLabel = formatOpeningLabel(openingTime)}
                  {@const openingTimeRange =
                    formatOpeningTimeRange(openingTime)}
                  {#if openingLabel || openingTimeRange}
                    <div
                      class="rounded-lg border border-base-content/10 bg-base-100/50 px-3 py-2 flex items-start justify-between gap-3"
                    >
                      <div
                        class="text-xs font-semibold text-base-content/80 leading-snug"
                      >
                        {openingLabel}
                      </div>
                      <div
                        class="text-sm text-base-content/70 whitespace-nowrap"
                      >
                        {openingTimeRange}
                      </div>
                    </div>
                  {/if}
                {/each}
              </div>
            {:else}
              <div class="text-sm text-base-content/50">
                No opening hours available
              </div>
            {/if}
          </div>
        </div>
      {/if}
    </div>
  {:else}
    <div
      class="col-span-full h-32 flex items-center justify-center text-base-content/40 italic"
    >
      No stations found
    </div>
  {/each}
</div>
