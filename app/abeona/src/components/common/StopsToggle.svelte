<script lang="ts">
  /**
   * A ride's stops, folded until asked for: "5 stops ⌄", quiet, in the brand
   * colour. Under a ride in the trip's timeline and on the live card
   * (abeona-design.md §8, §9); the stops it opens are `StopRow`s on the
   * ride's rail. Android's is the "n stops" row in ui/trip/TripScreen.kt. To
   * a screen reader it says what it does and to which train: "Show 5 stops
   * of ICE 510".
   */
  import { ChevronDown, GitCommitHorizontal } from "lucide-svelte";

  interface Props {
    count: number;
    open: boolean;
    /** The ride, as its line chip says it ("ICE 510"). */
    ride: string;
    ontoggle: () => void;
    class?: string;
  }

  let { count, open, ride, ontoggle, class: klass = "" }: Props = $props();

  const stops = $derived(`${count} ${count === 1 ? "stop" : "stops"}`);
</script>

<button
  type="button"
  class="t-meta inline-flex min-h-8 shrink-0 items-center gap-1 rounded-full text-brand hover:underline {klass}"
  aria-expanded={open}
  aria-label={`${open ? "Hide" : "Show"} ${stops} of ${ride}`}
  onclick={ontoggle}
>
  <GitCommitHorizontal class="h-4 w-4" aria-hidden="true" />
  {stops}
  <ChevronDown class="h-4 w-4 transition-transform motion-reduce:transition-none {open ? 'rotate-180' : ''}" aria-hidden="true" />
</button>
