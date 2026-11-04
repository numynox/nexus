<script lang="ts">
  import { CreditCard } from "lucide-svelte";
  import type { TripWarning } from "../../lib/data";

  interface Props {
    warnings: TripWarning[];
    settingsHref: string;
  }

  let { warnings, settingsHref }: Props = $props();
</script>

<div class="flex items-start gap-3 rounded-2xl bg-amber-soft px-4 py-3 text-amber-ink" role="status">
  <span class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber text-accent-content">
    <CreditCard class="h-4 w-4" />
  </span>
  <div class="min-w-0 flex-1 text-sm leading-snug">
    <p class="font-semibold">Check the BahnCards for this trip</p>
    <ul class="mt-1 space-y-0.5 opacity-90">
      {#each warnings as w, i (i)}
        <li>{w.message}</li>
      {/each}
    </ul>
    <a href={settingsHref} class="mt-1 inline-block font-medium underline underline-offset-2">BahnCards in Settings</a>
  </div>
</div>
