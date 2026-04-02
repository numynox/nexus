<script lang="ts">
  import { MapPin } from "lucide-svelte";
  let { stations } = $props();
</script>

<div class="grid grid-cols-1 md:grid-cols-2 gap-4">
  {#each stations as s (s.id)}
    <div
      class="card bg-base-200 shadow-sm hover:shadow-md transition-all border border-base-content/5"
    >
      <div class="card-body p-4 flex flex-row items-center justify-between">
        <div class="flex items-start gap-3">
          <div class="mt-1">
            <MapPin class="w-4 h-4 text-primary" />
          </div>
          <div>
            <h3 class="font-bold text-base-content leading-tight">{s.name}</h3>
            <p class="text-xs text-base-content/60">{s.brand} • {s.place}</p>
            <p class="text-[10px] text-base-content/40 mt-1">
              {s.street}
              {s.house_number}
            </p>
          </div>
        </div>
        <div class="flex flex-col items-end gap-1">
          {#if s.currentPrice}
            <div class="text-2xl font-black text-primary">
              {s.currentPrice.toFixed(3)}
              <span class="text-xs font-normal">€</span>
            </div>
          {:else}
            <div class="badge badge-ghost text-[10px]">No Price</div>
          {/if}
          {#if s.whole_day}
            <div class="badge badge-success badge-xs text-[9px] h-3">24h</div>
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
