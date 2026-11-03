<script lang="ts">
  import { CalendarClock } from "lucide-svelte";
  import type { BahnCardDeadline } from "../../lib/data";
  import { deadlineText } from "../../lib/format";

  interface Props {
    deadlines: BahnCardDeadline[];
    settingsHref: string;
  }

  let { deadlines, settingsHref }: Props = $props();
</script>

<section class="flex items-start gap-3 rounded-2xl bg-amber-soft px-4 py-3 text-amber-ink" aria-label="BahnCard cancellation deadlines">
  <span class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-amber text-accent-content">
    <CalendarClock class="h-4 w-4" />
  </span>
  <div class="min-w-0 flex-1 text-sm leading-snug">
    <p class="font-semibold">
      {deadlines.length === 1 ? "A BahnCard renews soon" : `${deadlines.length} BahnCards renew soon`}
    </p>
    <ul class="mt-1 space-y-0.5 opacity-90">
      {#each deadlines as d (d.card_id)}
        <li>{d.traveller_name}'s BahnCard {d.kind}: {deadlineText(d)}.</li>
      {/each}
    </ul>
    <a href={settingsHref} class="mt-1 inline-block font-medium underline underline-offset-2">
      Already cancelled? Turn off "Renews automatically" on the card.
    </a>
  </div>
</section>
