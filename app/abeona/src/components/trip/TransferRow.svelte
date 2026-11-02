<script lang="ts">
  import { Footprints, Repeat } from "lucide-svelte";
  import type { Transfer } from "../../lib/transfer";
  import type { Leg } from "../../lib/transit";
  import ConfidenceChip from "../common/ConfidenceChip.svelte";

  interface Props {
    transfer: Transfer;
    arriving: Leg;
    departing: Leg;
  }

  let { transfer, arriving, departing }: Props = $props();

  const fromPlatform = $derived(arriving.to.platform ?? arriving.to.planned_platform);
  const toPlatform = $derived(departing.from.platform ?? departing.from.planned_platform);
</script>

<div class="flex items-center gap-3 px-2 py-3 sm:px-4">
  <div class="flex w-[4.25rem] justify-end">
    <span class="flex h-9 w-9 items-center justify-center rounded-full bg-base-200 text-base-content/60">
      {#if transfer.walkMinutes > 0}<Footprints class="h-4 w-4" />{:else}<Repeat class="h-4 w-4" />{/if}
    </span>
  </div>
  <div class="min-w-0 flex-1 border-l-2 border-dashed border-base-300 py-1 pl-5">
    <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
      <p class="text-sm font-semibold text-ink">
        {transfer.availableMinutes} min to change
      </p>
      <ConfidenceChip confidence={transfer.confidence} />
    </div>
    <p class="mt-0.5 text-xs text-base-content/60">
      {#if transfer.walkMinutes > 0}
        Walk {transfer.walkMinutes} min to {departing.from.name}
      {:else if transfer.changesStation}
        To {departing.from.name}
      {:else if fromPlatform && toPlatform}
        Platform {fromPlatform} → {toPlatform}
      {:else}
        At {departing.from.name}
      {/if}
      · {transfer.requiredMinutes} min needed
    </p>
  </div>
</div>
