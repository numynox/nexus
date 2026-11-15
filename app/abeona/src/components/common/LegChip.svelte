<script lang="ts">
  /**
   * The line chip ("ICE 597"), squared like a departure board. Ink only
   * (abeona-design.md §3): filled for long-distance, outlined for regional
   * and urban, well with its icon for a bus or a walk.
   */
  import { Bus, Footprints } from "lucide-svelte";
  import { legLabel, productFamily } from "../../lib/format";
  import type { Leg } from "../../lib/transit";

  interface Props {
    leg: Pick<Leg, "mode" | "line_name" | "product" | "cancelled">;
    size?: "sm" | "md";
  }

  let { leg, size = "sm" }: Props = $props();

  const family = $derived(productFamily(leg));

  const tone: Record<ReturnType<typeof productFamily>, string> = {
    long: "bg-ink text-card",
    regional: "text-ink ring-1 ring-inset ring-ink/60",
    urban: "text-ink ring-1 ring-inset ring-ink/60",
    bus: "bg-well text-ink",
    walk: "bg-well text-muted",
  };
</script>

<span
  class="tabular inline-flex shrink-0 items-center gap-1 whitespace-nowrap rounded-lg font-semibold tracking-tight
    {size === 'md' ? 'h-7 px-2 text-sm' : 'h-6 px-1.5 text-xs'}
    {tone[family]} {leg.cancelled ? 'line-through opacity-60' : ''}"
>
  {#if family === "walk"}
    <Footprints class="h-3.5 w-3.5" aria-hidden="true" />
  {:else if family === "bus"}
    <Bus class="h-3.5 w-3.5" aria-hidden="true" />
  {/if}
  {legLabel(leg)}
</span>
