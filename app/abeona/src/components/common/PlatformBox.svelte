<script lang="ts">
  /**
   * The boxed platform (abeona-design.md §6). The box is the word: no "Pl.".
   * A changed platform is the one filled thing in its row, with the old
   * number struck beside it.
   */
  interface Props {
    planned?: string | null;
    current?: string | null;
    /** row: in a row; lead: beside a headline; step: the live card's active step. */
    size?: "row" | "lead" | "step";
    class?: string;
  }

  let { planned = null, current = null, size = "row", class: klass = "" }: Props = $props();

  const shown = $derived(current || planned);
  const changed = $derived(Boolean(planned && current && planned !== current));
</script>

{#if shown}
  <span
    class="inline-flex shrink-0 items-center gap-1 {klass}"
    role="img"
    aria-label={changed ? `Platform ${shown}, changed from ${planned}` : `Platform ${shown}`}
  >
    {#if changed}
      <span class="tabular text-muted line-through {size === 'row' ? 'text-xs' : 't-body'}" aria-hidden="true">{planned}</span>
    {/if}
    <span
      class="tabular inline-flex items-center justify-center font-semibold
        {size === 'step' ? 'h-12 min-w-12 rounded-xl px-2 text-2xl' : size === 'lead' ? 'h-9 min-w-9 rounded-lg px-2 text-xl' : 'h-6 min-w-6 rounded-lg px-1.5 text-[0.8125rem]'}
        {changed ? 'bg-watch text-watch-content' : 'bg-well text-ink'}"
      aria-hidden="true">{shown}</span>
  </span>
{/if}
