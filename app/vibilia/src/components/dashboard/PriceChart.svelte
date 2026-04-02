<script lang="ts">
  import {
    Chart as ChartJS,
    Legend,
    LineElement,
    LinearScale,
    PointElement,
    Tooltip,
  } from "chart.js";
  import dayjs from "dayjs";
  import { onMount } from "svelte";
  import { Line } from "svelte-chartjs";

  interface FuelPricePoint {
    price: number;
    checked_at: string;
  }

  interface ChartPoint {
    x: number;
    y: number;
  }

  interface PriceDataset {
    label: string;
    data: ChartPoint[];
    stepped: "after";
    borderColor: string;
    borderWidth: number;
    pointRadius: number;
    pointHoverRadius: number;
    spanGaps: boolean;
    fill: boolean;
    tension: number;
    order: number;
  }

  const currentTimeLinePlugin = {
    id: "currentTimeLine",
    afterDraw: (chart: any) => {
      const xScale = chart.scales.x;
      const chartArea = chart.chartArea;
      if (!xScale || !chartArea) return;

      const now = dayjs();
      const currentHour = now.hour() + now.minute() / 60;
      const x = xScale.getPixelForValue(currentHour);
      if (!Number.isFinite(x)) return;

      const ctx = chart.ctx;
      ctx.save();
      ctx.strokeStyle = "rgba(244, 63, 94, 0.85)";
      ctx.lineWidth = 1.5;
      ctx.setLineDash([5, 5]);
      ctx.beginPath();
      ctx.moveTo(x, chartArea.top);
      ctx.lineTo(x, chartArea.bottom);
      ctx.stroke();
      ctx.restore();
    },
  };

  ChartJS.register(
    Tooltip,
    Legend,
    LineElement,
    LinearScale,
    PointElement,
    currentTimeLinePlugin,
  );

  let {
    history,
    previousDays = 3,
  }: { history: FuelPricePoint[]; previousDays?: number } = $props();

  let nowTick = $state(Date.now());
  let isDarkTheme = $state(true);

  onMount(() => {
    const updateThemeMode = () => {
      const html = document.documentElement;
      const themeName = html.getAttribute("data-theme") || "";
      if (themeName === "light") {
        isDarkTheme = false;
        return;
      }

      if (themeName === "dark" || html.classList.contains("dark")) {
        isDarkTheme = true;
        return;
      }

      isDarkTheme = window.matchMedia("(prefers-color-scheme: dark)").matches;
    };

    updateThemeMode();

    const observer = new MutationObserver(() => {
      updateThemeMode();
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-theme", "class"],
    });

    const timer = window.setInterval(() => {
      nowTick = Date.now();
    }, 60_000);

    return () => {
      window.clearInterval(timer);
      observer.disconnect();
    };
  });

  const processedData = $derived.by(() => {
    nowTick;
    isDarkTheme;
    if (!history || history.length === 0) return { datasets: [] };

    const today = dayjs().startOf("day");
    const dayKeys: string[] = [];
    for (let offset = 0; offset <= previousDays; offset += 1) {
      dayKeys.push(today.subtract(offset, "day").format("YYYY-MM-DD"));
    }

    const minimumByMinuteByDay = new Map<string, Map<number, number>>();
    for (const dayKey of dayKeys) {
      minimumByMinuteByDay.set(dayKey, new Map<number, number>());
    }

    for (const point of history) {
      const parsed = dayjs(point.checked_at);
      const dayKey = parsed.format("YYYY-MM-DD");
      const dayMap = minimumByMinuteByDay.get(dayKey);
      if (!dayMap) continue;

      const minuteOfDay = parsed.hour() * 60 + parsed.minute();
      const currentMin = dayMap.get(minuteOfDay);
      if (currentMin === undefined || point.price < currentMin) {
        dayMap.set(minuteOfDay, point.price);
      }
    }

    const datasets: PriceDataset[] = dayKeys
      .map((dayKey, index) => {
        const dayMap = minimumByMinuteByDay.get(dayKey);
        if (!dayMap || dayMap.size === 0) return null;

        const points: ChartPoint[] = Array.from(dayMap.entries())
          .sort((a, b) => a[0] - b[0])
          .map(([minuteOfDay, price]) => ({
            x: minuteOfDay / 60,
            y: price,
          }));

        const isToday = index === 0;
        const isYesterday = index === 1;
        const label = isToday ? "Today" : dayjs(dayKey).format("ddd, MMM D");
        const yesterdayBase = isDarkTheme
          ? "rgba(255, 255, 255"
          : "rgba(15, 23, 42";
        const olderAlpha = Math.max(0.25, 0.95 - (index - 1) * 0.2);
        const lineColor = isToday
          ? "rgba(255, 120, 0, 1)"
          : isYesterday
            ? `${yesterdayBase}, 0.95)`
            : `${yesterdayBase}, ${olderAlpha})`;

        return {
          label,
          data: points,
          stepped: "after" as const,
          borderColor: lineColor,
          borderWidth: isToday ? 4 : 1.4,
          pointRadius: 0,
          pointHoverRadius: isToday ? 5 : 2,
          spanGaps: false,
          fill: false,
          tension: 0,
          order: isToday ? 0 : 1,
        };
      })
      .filter((dataset): dataset is PriceDataset => dataset !== null);

    return { datasets };
  });

  function formatHour(value: number): string {
    const hours = Math.floor(value);
    const minutes = Math.round((value - hours) * 60);
    return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
  }

  const options = $derived.by(() => ({
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        display: false,
        position: "top" as const,
        labels: {
          color: "rgba(148, 163, 184, 0.95)",
          usePointStyle: true,
          boxWidth: 10,
        },
      },
      tooltip: {
        mode: "nearest" as const,
        intersect: false,
        backgroundColor: "#1f2937",
        titleColor: "#9ca3af",
        bodyColor: "#fff",
        borderColor: "#374151",
        borderWidth: 1,
        padding: 12,
        cornerRadius: 8,
        callbacks: {
          title: (items: any[]) => {
            if (items.length === 0) return "";
            const x = items[0].parsed?.x;
            return `Time: ${formatHour(x)}`;
          },
          label: (item: any) =>
            `${item.dataset.label}: ${item.parsed.y.toFixed(3)}€`,
        },
      },
    },
    scales: {
      x: {
        type: "linear" as const,
        min: 0,
        max: 24,
        grid: {
          color: "rgba(148, 163, 184, 0.1)",
        },
        ticks: {
          stepSize: 2,
          color: "#9ca3af",
          callback: (value: any) => formatHour(Number(value)),
        },
      },
      y: {
        beginAtZero: false,
        grid: {
          color: "rgba(156, 163, 175, 0.1)",
        },
        ticks: {
          color: "#9ca3af",
          font: { size: 10 },
          callback: (value: any) => `${Number(value).toFixed(2)}€`,
        },
      },
    },
  }));
</script>

{#if history && history.length > 0 && processedData.datasets.length > 0}
  <div class="w-full h-full">
    <Line data={processedData} {options} />
  </div>
{:else}
  <div
    class="w-full h-full flex items-center justify-center text-base-content/40 text-sm"
  >
    No history data available for the selected day range
  </div>
{/if}
