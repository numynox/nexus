<script lang="ts">
  /** The train binding is lifted: words with legal weight, so they stay words. */
  import { ArrowRightLeft } from "lucide-svelte";
  import Banner from "./Banner.svelte";

  interface Props {
    delay: number;
    /** Why, when it is not the delay: an impossible change, a short change missed. */
    because?: string | null;
  }

  let { delay, because = null }: Props = $props();
</script>

<Banner tone="watch" icon={ArrowRightLeft} title="You may take another train">
  {because ?? `Expected ${delay} min late at your destination`}, so the train binding of your ticket is lifted.
</Banner>
