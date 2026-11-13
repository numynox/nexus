<script lang="ts">
  /**
   * A time with its delay, the same way everywhere (glossary: delay) — the
   * web's copy of Android's ShiftedTime. The new time is the one to read.
   * Block, where there is room: the new time, and beside it a column with
   * the minutes late above the planned time, struck through and grey (sized
   * from the time's own font size). Inline, in rows and lists: the planned
   * time struck through, then the new one. On time: the time alone.
   * Cancelled: the planned time struck through (the caller says so).
   */
  import { delayMinutes } from "../../lib/transfer";
  import { formatTime } from "../../lib/format";

  interface Props {
    planned: string | null | undefined;
    current: string | null | undefined;
    block?: boolean;
    cancelled?: boolean;
    /** Size, weight and colour of the time itself. */
    class?: string;
  }

  let { planned, current, block = true, cancelled = false, class: klass = "" }: Props = $props();

  const delay = $derived(cancelled ? 0 : delayMinutes(planned ?? null, current ?? null));
</script>

{#if cancelled}
  <span class="tabular text-base-content/50 line-through {klass}">{formatTime(planned)}</span>
{:else if delay <= 0}
  <span class="tabular {klass}">{formatTime(planned ?? current)}</span>
{:else if block}
  <span class="tabular inline-flex items-center gap-[0.25em] whitespace-nowrap {klass}" aria-label={`${formatTime(current)}, ${delay} min late`}>
    <span>{formatTime(current)}</span>
    <span class="flex flex-col text-[max(0.42em,0.7rem)] leading-[1.1]" aria-hidden="true">
      <span class="font-bold text-amber-ink">+{delay}</span>
      <span class="font-normal text-base-content/50 line-through">{formatTime(planned)}</span>
    </span>
  </span>
{:else}
  <span class="tabular inline-flex items-baseline gap-[0.3em] whitespace-nowrap {klass}" aria-label={`${formatTime(current)}, ${delay} min late`}>
    <span class="text-[0.82em] font-normal text-base-content/50 line-through" aria-hidden="true">{formatTime(planned)}</span>
    <span aria-hidden="true">{formatTime(current)}</span>
  </span>
{/if}
