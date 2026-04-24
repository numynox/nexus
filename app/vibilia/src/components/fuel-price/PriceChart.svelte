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
    day_key: string;
    bucket_minute: number;
    min_price: number | string;
  }

  interface LastRefuelPoint {
    fueled_at: string;
    price_per_liter: number;
  }

  interface ChartPoint {
    x: number;
    y: number;
  }

  interface PriceDataset {
    label: string;
    data: ChartPoint[];
    stepped?: "after";
    borderColor: string;
    borderWidth: number;
    pointRadius: number;
    pointHoverRadius: number;
    spanGaps: boolean;
    fill: boolean;
    tension: number;
    order: number;
    pointBackgroundColor?: string;
    pointBorderColor?: string;
    pointBorderWidth?: number;
    showLine?: boolean;
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
    lastRefuelPoint = null,
  }: {
    history: FuelPricePoint[];
    previousDays?: number;
    lastRefuelPoint?: LastRefuelPoint | null;
  } = $props();

  let nowTick = $state(Date.now());
  let accentColor = $state("rgb(255, 120, 0)");
  let secondaryColor = $state("rgb(255, 255, 255)");

  // Convert oklch to RGB properly
  function oklchToRgb(oklchStr: string): string {
    const match = oklchStr.match(/oklch\(([\d.]+)\s+([\d.]+)\s+([\d.]+)\)/);
    if (!match) return "rgb(255, 255, 255)";

    const L = parseFloat(match[1]);
    const C = parseFloat(match[2]);
    const H = parseFloat(match[3]);

    // oklch to oklab (polar to cartesian)
    const hRad = (H * Math.PI) / 180;
    const a = C * Math.cos(hRad);
    const b = C * Math.sin(hRad);

    // oklab to linear sRGB
    const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
    const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
    const s_ = L - 0.0894841775 * a - 1.291486575 * b;

    const l = l_ * l_ * l_;
    const m = m_ * m_ * m_;
    const s = s_ * s_ * s_;

    let r = 4.0767416621 * l - 3.3077363322 * m + 0.2309101289 * s;
    let g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193761 * s;
    let blue = -0.004182625034 * l - 0.7418775388 * m + 1.2207787925 * s;

    // Apply sRGB gamma correction
    const gammaCorrect = (c: number): number => {
      if (Math.abs(c) <= 0.0031308) {
        return 12.92 * c;
      }
      return (1 + 0.055) * Math.pow(Math.abs(c), 1 / 2.4) - 0.055;
    };

    r = gammaCorrect(r);
    g = gammaCorrect(g);
    blue = gammaCorrect(blue);

    // Clamp and convert to 0-255
    const toInt = (c: number) => Math.round(Math.max(0, Math.min(1, c)) * 255);
    return `rgb(${toInt(r)}, ${toInt(g)}, ${toInt(blue)})`;
  }

  // Extract background color from a temporary element with DaisyUI class
  function getColorFromClass(className: string): string {
    const temp = document.createElement("div");
    temp.className = className;
    temp.style.position = "absolute";
    temp.style.visibility = "hidden";
    document.body.appendChild(temp);

    const colorStr = getComputedStyle(temp).backgroundColor;
    document.body.removeChild(temp);

    console.log(`Background color from ${className}:`, colorStr);

    // Convert oklch to rgb if needed
    if (colorStr.includes("oklch")) {
      return oklchToRgb(colorStr);
    }
    return colorStr || "rgb(255, 255, 255)";
  }

  onMount(() => {
    const updateThemeColors = () => {
      accentColor = getColorFromClass("btn btn-primary");
      secondaryColor = getColorFromClass("btn btn-secondary");

      console.log("Final accent:", accentColor);
      console.log("Final secondary:", secondaryColor);
    };

    // Update immediately and on theme changes
    updateThemeColors();

    const observer = new MutationObserver(() => {
      updateThemeColors();
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
    accentColor;
    secondaryColor;
    lastRefuelPoint;

    const today = dayjs().startOf("day");
    const dayKeys: string[] = [];
    for (let offset = 0; offset <= previousDays; offset += 1) {
      dayKeys.push(today.subtract(offset, "day").format("YYYY-MM-DD"));
    }

    const minimumByMinuteByDay = new Map<string, Map<number, number>>();
    for (const dayKey of dayKeys) {
      minimumByMinuteByDay.set(dayKey, new Map<number, number>());
    }

    for (const point of history || []) {
      const dayKey = point.day_key;
      const dayMap = minimumByMinuteByDay.get(dayKey);
      if (!dayMap) continue;

      const bucketKey = Number(point.bucket_minute);
      const price = Number(point.min_price);
      if (!Number.isFinite(bucketKey) || !Number.isFinite(price)) continue;

      const currentMin = dayMap.get(bucketKey);
      if (currentMin === undefined || price < currentMin) {
        dayMap.set(bucketKey, price);
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

        // Parse RGB values and add alpha channel (opacity)
        const getRgbaWithAlpha = (rgbStr: string, alpha: number): string => {
          const match = rgbStr.match(/rgb\((\d+),\s*(\d+),\s*(\d+)\)/);
          if (!match) return `rgba(255, 255, 255, ${alpha})`;
          return `rgba(${match[1]}, ${match[2]}, ${match[3]}, ${alpha})`;
        };

        let lineColor: string;
        if (isToday) {
          lineColor = getRgbaWithAlpha(accentColor, 1);
        } else {
          const olderAlpha = Math.max(0.2, 0.95 - (index - 1) * 0.5);
          lineColor = getRgbaWithAlpha(secondaryColor, olderAlpha);
        }

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

    if (lastRefuelPoint) {
      const timestamp = dayjs(lastRefuelPoint.fueled_at);
      const price = Number(lastRefuelPoint.price_per_liter);
      const hourOfDay = timestamp.hour() + timestamp.minute() / 60;

      if (
        timestamp.isValid() &&
        Number.isFinite(price) &&
        Number.isFinite(hourOfDay)
      ) {
        datasets.push({
          label: "last refuel price",
          data: [{ x: hourOfDay, y: price }],
          borderColor: accentColor,
          borderWidth: 0,
          pointRadius: 6,
          pointHoverRadius: 7,
          pointBackgroundColor: accentColor,
          pointBorderColor: "rgb(15, 23, 42)",
          pointBorderWidth: 2,
          spanGaps: false,
          fill: false,
          tension: 0,
          showLine: false,
          order: -1,
        });
      }
    }

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
    animation: false as const,
    transitions: {
      active: { animation: { duration: 0 } },
      resize: { animation: { duration: 0 } },
      show: { animation: { duration: 0 } },
      hide: { animation: { duration: 0 } },
    },
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
        grid: { color: "rgba(148, 163, 184, 0.1)" },
        ticks: {
          stepSize: 2,
          color: "#9ca3af",
          callback: (value: any) => formatHour(Number(value)),
        },
      },
      y: {
        beginAtZero: false,
        grid: { color: "rgba(156, 163, 175, 0.1)" },
        ticks: {
          color: "#9ca3af",
          font: { size: 10 },
          callback: (value: any) => `${Number(value).toFixed(2)}€`,
        },
      },
    },
  }));
</script>

{#if processedData.datasets.length > 0}
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
