<script lang="ts">
  import { CalendarClock } from "lucide-svelte";
  import type { BahnCardDeadline } from "../../lib/data";
  import { bahnCardName, deadlineText } from "../../lib/format";
  import Banner from "../common/Banner.svelte";

  interface Props {
    deadlines: BahnCardDeadline[];
    settingsHref: string;
  }

  let { deadlines, settingsHref }: Props = $props();

  const line = (d: BahnCardDeadline) => `${d.name ? `${bahnCardName(d)} (BahnCard ${d.kind})` : bahnCardName(d)}: ${deadlineText(d)}`;
</script>

<Banner
  tone="watch"
  icon={CalendarClock}
  title={deadlines.length === 1 ? line(deadlines[0]) : `${deadlines.length} BahnCards renew soon`}
>
  {#if deadlines.length > 1}
    <ul>
      {#each deadlines as d (d.card_id)}<li>{line(d)}.</li>{/each}
    </ul>
  {/if}
  <a href={settingsHref} class="underline underline-offset-2">Already cancelled? Turn off "Renews automatically" on the card.</a>
</Banner>
