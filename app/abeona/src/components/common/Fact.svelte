<script lang="ts">
  /**
   * A supporting fact: an icon from the vocabulary (abeona-design.md §5) and
   * its value. `label` is what the icon stands for, read out before the value.
   */
  import type { Snippet } from "svelte";
  import type { IconComponent } from "./icon";

  interface Props {
    icon: IconComponent;
    label: string;
    value?: string | number;
    class?: string;
    children?: Snippet;
  }

  let { icon: Icon, label, value, class: klass = "", children }: Props = $props();
</script>

<span class="t-meta inline-flex shrink-0 items-center gap-1 whitespace-nowrap {klass}" title={label}>
  <Icon class="h-4 w-4 shrink-0" aria-hidden="true" />
  <span class="sr-only">{label}</span>
  {#if children}{@render children()}{:else if value !== undefined}<span>{value}</span>{/if}
</span>
