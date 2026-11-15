<script lang="ts">
  /** A section's title, with its facts on the right. No supporting sentence. */
  import type { Snippet } from "svelte";

  interface Props {
    title: string;
    children?: Snippet;
  }

  let { title, children }: Props = $props();
</script>

<div class="flex min-h-8 items-center justify-between gap-3 px-1">
  <h2 class="t-title text-ink">{title}</h2>
  {#if children}<div class="flex items-center gap-3">{@render children()}</div>{/if}
</div>
