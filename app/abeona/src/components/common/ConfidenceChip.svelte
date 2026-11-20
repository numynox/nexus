<script lang="ts">
  /**
   * How a change looks: its icon and the minutes ("✓ 12 min"). The word
   * (Safe, Tight …) shows only when there are no minutes to show, and is
   * always the chip's label for screen readers. With `word`, a change that
   * is not safe says so after its minutes ("6 min · tight"), and an
   * impossible one only "impossible": the live card's change row.
   */
  import { Ban, CircleAlert, CircleCheck, Timer } from "lucide-svelte";
  import type { Confidence } from "../../lib/transfer";
  import StatusChip from "./StatusChip.svelte";

  interface Props {
    confidence: Confidence;
    minutes?: number | null;
    word?: boolean;
    class?: string;
  }

  let { confidence, minutes = null, word = false, class: klass = "" }: Props = $props();

  const meta = {
    safe: { text: "Safe", word: "", tone: "ok", icon: CircleCheck },
    tight: { text: "Tight", word: "tight", tone: "watch", icon: Timer },
    at_risk: { text: "At risk", word: "at risk", tone: "bad", icon: CircleAlert },
    impossible: { text: "Impossible", word: "impossible", tone: "bad", icon: Ban },
  } as const;

  const m = $derived(meta[confidence]);
  const label = $derived.by(() => {
    if (minutes === null) return m.text;
    if (!word || !m.word) return `${minutes} min`;
    return confidence === "impossible" ? m.word : `${minutes} min · ${m.word}`;
  });
</script>

<StatusChip
  tone={m.tone}
  icon={m.icon}
  {label}
  title={minutes === null ? `${m.text} change` : `${m.text} change, ${minutes} min`}
  class={klass}
/>
