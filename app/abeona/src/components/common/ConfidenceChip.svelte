<script lang="ts">
  /**
   * How a change looks: its icon and the minutes ("✓ 12 min"). The word
   * (Safe, Tight …) shows only when there are no minutes to show, and is
   * always the chip's label for screen readers.
   */
  import { Ban, CircleAlert, CircleCheck, Timer } from "lucide-svelte";
  import type { Confidence } from "../../lib/transfer";
  import StatusChip from "./StatusChip.svelte";

  interface Props {
    confidence: Confidence;
    minutes?: number | null;
    class?: string;
  }

  let { confidence, minutes = null, class: klass = "" }: Props = $props();

  const meta = {
    safe: { text: "Safe", tone: "ok", icon: CircleCheck },
    tight: { text: "Tight", tone: "watch", icon: Timer },
    at_risk: { text: "At risk", tone: "bad", icon: CircleAlert },
    impossible: { text: "Impossible", tone: "bad", icon: Ban },
  } as const;

  const m = $derived(meta[confidence]);
</script>

<StatusChip
  tone={m.tone}
  icon={m.icon}
  label={minutes === null ? m.text : `${minutes} min`}
  title={minutes === null ? `${m.text} change` : `${m.text} change, ${minutes} min`}
  class={klass}
/>
