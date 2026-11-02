<script lang="ts">
  import { formatDelay } from "../../lib/format";

  interface Props {
    minutes: number;
    cancelled?: boolean;
    /** Show "on time" in green when there is live data and no delay. */
    onTimeLabel?: boolean;
    size?: "sm" | "md" | "lg";
  }

  let { minutes, cancelled = false, onTimeLabel = false, size = "sm" }: Props = $props();

  const sizes = { sm: "text-xs px-2 py-0.5", md: "text-sm px-2.5 py-0.5", lg: "text-base px-3 py-1" };
</script>

{#if cancelled}
  <span class="inline-flex items-center rounded-full bg-error/12 font-semibold text-error {sizes[size]}">Cancelled</span>
{:else if minutes > 0}
  <span class="tabular inline-flex items-center rounded-full bg-amber-soft font-semibold text-amber-ink {sizes[size]}"
    title="{minutes} min late">{formatDelay(minutes)}</span>
{:else if onTimeLabel}
  <span class="inline-flex items-center rounded-full bg-mint font-medium text-primary {sizes[size]}">On time</span>
{/if}
