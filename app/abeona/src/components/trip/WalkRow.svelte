<script lang="ts">
  import { Footprints } from "lucide-svelte";
  import { formatDuration, minutesBetween } from "../../lib/format";
  import type { Leg } from "../../lib/transit";

  interface Props {
    leg: Leg;
  }

  let { leg }: Props = $props();
</script>

<div class="flex items-center gap-3 px-2 py-2 text-sm text-base-content/60 sm:px-4">
  <div class="flex w-[4.25rem] justify-end">
    <span class="flex h-8 w-8 items-center justify-center rounded-full bg-base-200"><Footprints class="h-4 w-4" /></span>
  </div>
  <p class="border-l-2 border-dotted border-base-300 py-1 pl-5">
    Walk {formatDuration(minutesBetween(leg.from.planned, leg.to.planned))} to {leg.to.name}
  </p>
</div>
