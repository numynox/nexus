<script lang="ts">
  interface Props {
    year: number;
    months: { month: string; trips: number; km: number }[];
  }

  let { year, months }: Props = $props();

  const LABELS = ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"];
  const NAMES = Array.from({ length: 12 }, (_, i) => new Date(2000, i, 1).toLocaleDateString("en-GB", { month: "long" }));

  const values = $derived(
    Array.from({ length: 12 }, (_, i) => {
      const key = `${year}-${String(i + 1).padStart(2, "0")}`;
      const m = months.find((x) => x.month === key);
      return { trips: m?.trips ?? 0, km: m?.km ?? 0 };
    }),
  );
  const max = $derived(Math.max(1, ...values.map((v) => v.trips)));
  // Highlighted only when one month clearly leads; a tie has no "busiest".
  const busiest = $derived(
    values.filter((v) => v.trips === max).length === 1 ? values.findIndex((v) => v.trips === max) : -1,
  );

  let hovered = $state<number | null>(null);

  const W = 336;
  const H = 150;
  const PLOT = 120;
  const SLOT = W / 12;
  const BAR = SLOT - 8;
</script>

<div class="relative">
  <svg viewBox="0 0 {W} {H}" class="w-full" role="img" aria-label="Trips per month in {year}">
    <line x1="0" x2={W} y1={PLOT} y2={PLOT} stroke="currentColor" stroke-opacity="0.15" stroke-width="1" />
    {#each values as v, i (i)}
      {@const h = v.trips === 0 ? 0 : Math.max(6, (v.trips / max) * (PLOT - 16))}
      {@const x = i * SLOT + 4}
      <g
        role="presentation"
        onmouseenter={() => (hovered = i)}
        onmouseleave={() => (hovered = null)}
      >
        <rect x={i * SLOT} y="0" width={SLOT} height={H} fill="transparent" />
        {#if h > 0}
          <!-- 4px rounded top, square at the baseline -->
          <path
            d="M{x} {PLOT} V{PLOT - h + 4} Q{x} {PLOT - h} {x + 4} {PLOT - h} H{x + BAR - 4} Q{x + BAR} {PLOT - h} {x + BAR} {PLOT - h + 4} V{PLOT} Z"
            class={i === busiest ? "fill-amber" : "fill-current"}
            opacity={hovered === null || hovered === i ? 1 : 0.45}
          />
        {/if}
        <text x={i * SLOT + SLOT / 2} y={H - 8} text-anchor="middle" class="fill-current text-[10px] opacity-60">{LABELS[i]}</text>
      </g>
    {/each}
  </svg>
  {#if hovered !== null}
    <div
      class="pointer-events-none absolute -top-2 z-10 -translate-x-1/2 -translate-y-full rounded-xl bg-neutral px-3 py-1.5 text-xs whitespace-nowrap text-neutral-content shadow-lg"
      style="left: {((hovered + 0.5) / 12) * 100}%"
    >
      <span class="font-semibold">{NAMES[hovered]}</span> · {values[hovered].trips} {values[hovered].trips === 1 ? "trip" : "trips"} · {values[hovered].km} km
    </div>
  {/if}
</div>
