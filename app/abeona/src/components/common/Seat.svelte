<script lang="ts">
  /**
   * The ride's seat reservation, as the ticket gives it: "Coach 24 · Seat 66"
   * (lib/intake/seat.ts seatText). A `Fact` whose words say it all, so the
   * armchair is decorative, and it may wrap: a long list of seats does not
   * push the row out. Nothing when neither is known.
   */
  import { Armchair } from "lucide-svelte";
  import { seatText } from "../../lib/intake/seat";

  interface Props {
    coach?: string | null;
    seats?: string | null;
    class?: string;
  }

  let { coach = null, seats = null, class: klass = "" }: Props = $props();

  const text = $derived(seatText(coach, seats));
</script>

{#if text}
  <span class="t-meta inline-flex min-w-0 items-start gap-1 {klass}">
    <Armchair class="mt-px h-4 w-4 shrink-0" aria-hidden="true" />
    <span class="min-w-0">{text}</span>
  </span>
{/if}
