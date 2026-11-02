<script lang="ts">
  import { Bus, Footprints, TrainFront } from "lucide-svelte";
  import { legLabel, productFamily } from "../../lib/format";
  import type { Leg } from "../../lib/transit";

  interface Props {
    leg: Pick<Leg, "mode" | "line_name" | "product" | "cancelled">;
    size?: "sm" | "md";
  }

  let { leg, size = "sm" }: Props = $props();

  const family = $derived(productFamily(leg));

  const tone: Record<ReturnType<typeof productFamily>, string> = {
    long: "bg-neutral text-neutral-content",
    regional: "bg-mint text-primary",
    urban: "bg-sky text-ink",
    bus: "bg-base-200 text-base-content",
    walk: "bg-base-200 text-base-content/70",
  };
</script>

<span
  class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full font-semibold tracking-tight
    {size === 'md' ? 'px-3 py-1 text-sm' : 'px-2.5 py-0.5 text-xs'}
    {tone[family]} {leg.cancelled ? 'line-through opacity-60' : ''}"
>
  {#if family === "walk"}
    <Footprints class="h-3.5 w-3.5" />
  {:else if family === "bus"}
    <Bus class="h-3.5 w-3.5" />
  {:else if size === "md"}
    <TrainFront class="h-3.5 w-3.5" />
  {/if}
  {legLabel(leg)}
</span>
