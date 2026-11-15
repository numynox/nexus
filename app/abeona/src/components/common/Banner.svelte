<script lang="ts">
  /**
   * A full-width status message (abeona-design.md §6): its icon, one bold
   * line, at most one sentence under it, and at most one action.
   */
  import type { Snippet } from "svelte";
  import type { IconComponent } from "./icon";

  interface Props {
    tone: "watch" | "bad" | "ok";
    icon: IconComponent;
    title: string;
    children?: Snippet;
    action?: Snippet;
    class?: string;
  }

  let { tone, icon: Icon, title, children, action, class: klass = "" }: Props = $props();

  const tones = {
    ok: "bg-ok-soft text-ok-ink",
    watch: "bg-watch-soft text-watch-ink",
    bad: "bg-bad-soft text-bad-ink",
  };
</script>

<div class="flex items-start gap-3 rounded-2xl px-4 py-3 {tones[tone]} {klass}" role={tone === "bad" ? "alert" : "status"}>
  <Icon class="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
  <div class="min-w-0 flex-1">
    <p class="t-body font-semibold">{title}</p>
    {#if children}<div class="text-[0.8125rem] leading-snug">{@render children()}</div>{/if}
  </div>
  {#if action}<div class="shrink-0 self-center">{@render action()}</div>{/if}
</div>
