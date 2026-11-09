<script lang="ts">
  import { Repeat } from "lucide-svelte";

  interface Props {
    delay: number;
    /** Why, when it is not the delay: an impossible change, a short change missed. */
    because?: string | null;
  }

  let { delay, because = null }: Props = $props();
</script>

<div class="flex items-start gap-3 rounded-2xl bg-amber-soft px-4 py-3 text-amber-ink">
  <span class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber text-accent-content">
    <Repeat class="h-4 w-4" />
  </span>
  <div class="text-sm leading-snug">
    <p class="font-semibold">You may take another train</p>
    <p class="opacity-90">
      {because ?? `Expected ${delay} min late at your destination`}, so the train binding of your ticket is lifted.
    </p>
  </div>
</div>
