<script lang="ts">
  /**
   * A status in its soft colour, always with its icon (abeona-design.md §3).
   * The visible label is short (minutes, an amount); `title` carries the
   * words the icon replaces, for screen readers and on hover.
   */
  import type { IconComponent } from "./icon";

  interface Props {
    tone: "ok" | "watch" | "bad" | "quiet";
    icon?: IconComponent;
    label?: string;
    title?: string;
    class?: string;
  }

  let { tone, icon: Icon, label = "", title, class: klass = "" }: Props = $props();

  const tones = {
    ok: "bg-ok-soft text-ok-ink",
    watch: "bg-watch-soft text-watch-ink",
    bad: "bg-bad-soft text-bad-ink",
    quiet: "bg-well text-muted",
  };
</script>

<span
  class="tabular inline-flex h-6 shrink-0 items-center gap-1 whitespace-nowrap rounded-full text-xs font-semibold
    {label ? 'px-2' : 'w-6 justify-center'} {tones[tone]} {klass}"
  role={title ? "img" : undefined}
  aria-label={title}
  {title}
>
  {#if Icon}<Icon class="h-3.5 w-3.5 shrink-0" aria-hidden="true" />{/if}
  {#if label}<span aria-hidden={title ? "true" : undefined}>{label}</span>{/if}
</span>
