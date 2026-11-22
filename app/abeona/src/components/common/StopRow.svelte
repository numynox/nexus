<script lang="ts">
  /**
   * The row grid (abeona-design.md §4): time 52, mark 24, the name taking
   * the room, the platform boxed at the right edge. Every row about a stop
   * or a train is one of these, so the columns line up on every screen. A
   * column with nothing to say stays empty; it does not collapse.
   *
   * In a narrow container (a phone at 200 % text) the time moves above the
   * name, which then wraps instead of being cut: four columns of large text
   * leave the name no room.
   */
  import type { Snippet } from "svelte";
  import PlatformBox from "./PlatformBox.svelte";
  import ShiftedTime from "./ShiftedTime.svelte";

  interface Props {
    planned?: string | null;
    current?: string | null;
    cancelled?: boolean;
    name?: string;
    platformPlanned?: string | null;
    platformCurrent?: string | null;
    /** A stop that matters: its name in title. */
    strong?: boolean;
    muted?: boolean;
    /** The mark column: a rail dot, an icon from the vocabulary. It may fill the row's height (a rail). */
    mark?: Snippet;
    /** Before the name, in its column: a train's line chip, a change's chip. */
    line?: Snippet;
    /** Between the name and the platform: a chip, a fact. */
    trailing?: Snippet;
    /** A second line under the name, in its column. */
    children?: Snippet;
    class?: string;
  }

  let {
    planned = null,
    current = null,
    cancelled = false,
    name = "",
    platformPlanned = null,
    platformCurrent = null,
    strong = false,
    muted = false,
    mark,
    line,
    trailing,
    children,
    class: klass = "",
  }: Props = $props();
</script>

<div class="row grid min-h-10 grid-cols-[3.25rem_1.5rem_minmax(0,1fr)_auto] items-center gap-x-2 {klass}">
  <span class="time t-body leading-tight {strong ? 'font-semibold' : ''} {muted ? 'text-muted' : 'text-ink'}">
    {#if planned || current}
      <!-- A delay wraps under its time, so the column keeps its width. -->
      <ShiftedTime {planned} {current} {cancelled} block={false} class="flex-wrap gap-y-0" />
    {/if}
  </span>
  <span class="mark relative flex items-center justify-center self-stretch {muted ? 'text-muted' : 'text-ink'}">
    {#if mark}{@render mark()}{/if}
  </span>
  <span class="body min-w-0 py-1">
    <span class="head flex min-w-0 items-center gap-2">
      {#if line}{@render line()}{/if}
      {#if name}
        <span
          class="name min-w-0 truncate {strong ? 't-title' : 't-body'} {muted || cancelled ? 'text-muted' : 'text-ink'} {cancelled ? 'line-through' : ''}"
          >{name}</span
        >
      {/if}
    </span>
    {#if children}<span class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">{@render children()}</span>{/if}
  </span>
  <span class="end flex items-center justify-end gap-2">
    {#if trailing}{@render trailing()}{/if}
    <PlatformBox planned={platformPlanned} current={platformCurrent} />
  </span>
</div>

<style>
  @container (width < 16rem) {
    .row {
      grid-template-columns: 1.5rem minmax(0, 1fr) auto;
    }
    .time {
      grid-column: 2;
      grid-row: 1;
    }
    .time:empty {
      display: none;
    }
    .mark {
      grid-column: 1;
      grid-row: 1 / span 2;
    }
    .body {
      grid-column: 2;
      grid-row: 2;
    }
    .end {
      grid-column: 3;
      grid-row: 1 / span 2;
    }
    .head {
      flex-wrap: wrap;
      row-gap: 0.125rem;
    }
    .name {
      white-space: normal;
      overflow-wrap: anywhere;
    }
  }
</style>
