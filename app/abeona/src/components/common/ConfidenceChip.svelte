<script lang="ts">
  import type { Confidence } from "../../lib/transfer";

  interface Props {
    confidence: Confidence;
    /** Prefix, e.g. "Changes". */
    label?: string;
  }

  let { confidence, label = "" }: Props = $props();

  const meta: Record<Confidence, { text: string; tone: string; dot: string }> = {
    safe: { text: "Safe", tone: "bg-mint text-primary", dot: "bg-primary" },
    tight: { text: "Tight", tone: "bg-amber-soft text-amber-ink", dot: "bg-amber" },
    at_risk: { text: "At risk", tone: "bg-error/12 text-error", dot: "bg-error" },
  };
</script>

<span class="inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold {meta[confidence].tone}">
  <span class="h-1.5 w-1.5 rounded-full {meta[confidence].dot}"></span>
  {label ? `${label}: ` : ""}{meta[confidence].text}
</span>
