<script lang="ts">
  import {
    BarElement,
    CategoryScale,
    Chart as ChartJS,
    Filler,
    Legend,
    LineElement,
    LinearScale,
    PointElement,
    Tooltip,
  } from "chart.js";
  import dayjs from "dayjs";
  import { onMount } from "svelte";
  import { Bar, Line } from "svelte-chartjs";

  interface Point {
    x: number;
    y: number;
  }

  interface Props {
    title: string;
    subtitle?: string;
    points: Point[];
    secondaryPoints?: Point[];
    secondaryLabel?: string;
    plotStyle?: "line" | "stepped-line" | "bar";
    yDecimals?: number;
    yUnit?: string;
    showAverageLine?: boolean;
  }

  const averageLabelPlugin = {
    id: "averageLabel",
    afterDatasetsDraw(chart: any) {
      const datasets = chart?.data?.datasets || [];
      const averageDatasetIndex = datasets.findIndex(
        (dataset: any) => dataset?.label === "Average",
      );
      if (averageDatasetIndex === -1) return;

      const averageDataset = datasets[averageDatasetIndex];
      const avgRaw = averageDataset?.data?.[0];
      const avgValue = Number(
        typeof avgRaw === "object" && avgRaw !== null
          ? (avgRaw as any).y
          : avgRaw,
      );
      if (!Number.isFinite(avgValue)) return;

      const yScale = chart?.scales?.y;
      const chartArea = chart?.chartArea;
      if (!yScale || !chartArea) return;

      const meta = chart.getDatasetMeta(averageDatasetIndex);
      if (meta?.hidden) return;

      const decimals = Number.isFinite(Number(averageDataset?.avgDecimals))
        ? Number(averageDataset.avgDecimals)
        : 2;
      const unit =
        typeof averageDataset?.avgUnit === "string"
          ? averageDataset.avgUnit
          : "";
      const locale =
        typeof averageDataset?.avgLocale === "string"
          ? averageDataset.avgLocale
          : "en-US";
      const useGrouping = Boolean(averageDataset?.avgUseGrouping);
      const label = `${formatNumber(avgValue, decimals, locale, useGrouping)}${unit ? ` ${unit}` : ""}`;

      const y = yScale.getPixelForValue(avgValue);
      const ctx = chart.ctx;

      if (averageDataset?.avgFullWidth) {
        ctx.save();
        ctx.strokeStyle =
          typeof averageDataset?.avgLineColor === "string"
            ? averageDataset.avgLineColor
            : "rgba(255, 255, 255, 0.75)";
        ctx.lineWidth = 1.6;
        ctx.setLineDash([6, 4]);
        ctx.beginPath();
        ctx.moveTo(chartArea.left, y);
        ctx.lineTo(chartArea.right, y);
        ctx.stroke();
        ctx.restore();
      }

      const paddingX = 6;
      const paddingY = 3;
      const fontSize = 11;

      ctx.save();
      ctx.font = `600 ${fontSize}px system-ui, sans-serif`;

      const textWidth = ctx.measureText(label).width;
      const boxWidth = textWidth + paddingX * 2;
      const boxHeight = fontSize + paddingY * 2;
      const x = chartArea.left + 2;

      let yBox = y - boxHeight - 5;
      if (yBox < chartArea.top + 2) {
        yBox = y + 5;
      }
      if (yBox + boxHeight > chartArea.bottom - 2) {
        yBox = chartArea.bottom - boxHeight - 2;
      }

      ctx.fillStyle = "rgba(15, 23, 42, 0.86)";
      ctx.strokeStyle = "rgba(148, 163, 184, 0.55)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.roundRect(x, yBox, boxWidth, boxHeight, 5);
      ctx.fill();
      ctx.stroke();

      ctx.fillStyle = "rgba(255, 255, 255, 0.96)";
      ctx.fillText(label, x + paddingX, yBox + boxHeight - paddingY - 1);
      ctx.restore();
    },
  };

  let {
    title,
    subtitle = "",
    points,
    secondaryPoints = [],
    secondaryLabel = "",
    plotStyle = "line",
    yDecimals = 2,
    yUnit = "",
    showAverageLine = false,
  }: Props = $props();

  const isTotalMileageChart = $derived.by(
    () => yUnit === "km" && title === "Total mileage over time",
  );
  const numberLocale = $derived.by(() =>
    isTotalMileageChart ? "de-DE" : "en-US",
  );
  const useThousandsSeparator = $derived.by(() => isTotalMileageChart);

  function formatNumber(
    value: number,
    decimals: number,
    locale: string,
    useGrouping: boolean,
  ): string {
    return new Intl.NumberFormat(locale, {
      minimumFractionDigits: decimals,
      maximumFractionDigits: decimals,
      useGrouping,
    }).format(value);
  }

  ChartJS.register(
    Tooltip,
    Legend,
    LineElement,
    BarElement,
    CategoryScale,
    LinearScale,
    PointElement,
    Filler,
    averageLabelPlugin,
  );

  let accentColor = $state("rgb(255, 120, 0)");
  let secondaryColor = $state("rgb(255, 255, 255)");

  function oklchToRgb(oklchStr: string): string {
    const match = oklchStr.match(/oklch\(([\d.]+)\s+([\d.]+)\s+([\d.]+)\)/);
    if (!match) return "rgb(255, 255, 255)";

    const L = parseFloat(match[1]);
    const C = parseFloat(match[2]);
    const H = parseFloat(match[3]);

    const hRad = (H * Math.PI) / 180;
    const a = C * Math.cos(hRad);
    const b = C * Math.sin(hRad);

    const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
    const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
    const s_ = L - 0.0894841775 * a - 1.291486575 * b;

    const l = l_ * l_ * l_;
    const m = m_ * m_ * m_;
    const s = s_ * s_ * s_;

    let r = 4.0767416621 * l - 3.3077363322 * m + 0.2309101289 * s;
    let g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193761 * s;
    let blue = -0.004182625034 * l - 0.7418775388 * m + 1.2207787925 * s;

    const gammaCorrect = (c: number): number => {
      if (Math.abs(c) <= 0.0031308) {
        return 12.92 * c;
      }
      return (1 + 0.055) * Math.pow(Math.abs(c), 1 / 2.4) - 0.055;
    };

    r = gammaCorrect(r);
    g = gammaCorrect(g);
    blue = gammaCorrect(blue);

    const toInt = (c: number) => Math.round(Math.max(0, Math.min(1, c)) * 255);
    return `rgb(${toInt(r)}, ${toInt(g)}, ${toInt(blue)})`;
  }

  function getColorFromClass(className: string): string {
    const temp = document.createElement("div");
    temp.className = className;
    temp.style.position = "absolute";
    temp.style.visibility = "hidden";
    document.body.appendChild(temp);

    const colorStr = getComputedStyle(temp).backgroundColor;
    document.body.removeChild(temp);

    if (colorStr.includes("oklch")) {
      return oklchToRgb(colorStr);
    }
    return colorStr || "rgb(255, 255, 255)";
  }

  onMount(() => {
    const updateThemeColors = () => {
      accentColor = getColorFromClass("btn btn-primary");
      secondaryColor = getColorFromClass("btn btn-secondary");
    };

    updateThemeColors();

    const observer = new MutationObserver(() => {
      updateThemeColors();
    });
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-theme", "class"],
    });

    return () => {
      observer.disconnect();
    };
  });

  const chartData = $derived.by(() => {
    const lineColor = accentColor.replace("rgb(", "rgba(").replace(")", ", 1)");
    const fillColor = accentColor
      .replace("rgb(", "rgba(")
      .replace(")", ", 0.14)");
    const values = points.map((point) => point.y);
    const avg =
      values.length > 0
        ? values.reduce((sum, value) => sum + value, 0) / values.length
        : null;
    const avgLineColor = accentColor
      .replace("rgb(", "rgba(")
      .replace(")", ", 0.75)");
    const secondaryLineColor = secondaryColor
      .replace("rgb(", "rgba(")
      .replace(")", ", 0.9)");
    const isStepped = plotStyle === "stepped-line";
    const isBar = plotStyle === "bar";

    let barLabels: string[] = [];
    let barValues: Array<number | null> = [];
    if (isBar && points.length > 0) {
      const monthlyTotals = new Map<string, number>();
      for (const point of points) {
        const monthKey = dayjs(point.x).startOf("month").format("YYYY-MM");
        monthlyTotals.set(
          monthKey,
          (monthlyTotals.get(monthKey) || 0) + point.y,
        );
      }

      const firstMonth = dayjs(
        Math.min(...points.map((point) => point.x)),
      ).startOf("month");
      const lastMonth = dayjs(
        Math.max(...points.map((point) => point.x)),
      ).startOf("month");

      let cursor = firstMonth;
      while (cursor.isBefore(lastMonth) || cursor.isSame(lastMonth)) {
        const monthKey = cursor.format("YYYY-MM");
        barLabels.push(cursor.format("MMM YYYY"));
        barValues.push(
          monthlyTotals.has(monthKey)
            ? (monthlyTotals.get(monthKey) ?? null)
            : null,
        );
        cursor = cursor.add(1, "month");
      }
    }

    const datasets: any[] = [
      {
        type: isBar ? "bar" : "line",
        label: title,
        data: isBar ? barValues : points,
        borderColor: lineColor,
        backgroundColor: fillColor,
        borderWidth: 2.2,
        pointRadius: isBar ? 0 : 0,
        pointHoverRadius: 4,
        fill: !isBar,
        tension: isStepped ? 0 : 0.2,
        stepped: isStepped ? ("after" as const) : false,
        spanGaps: true,
        borderSkipped: isBar ? false : undefined,
        categoryPercentage: isBar ? 0.92 : undefined,
        barPercentage: isBar ? 0.95 : undefined,
      },
    ];

    if (showAverageLine && avg !== null) {
      datasets.push({
        type: "line",
        label: "Average",
        data: isBar
          ? barValues.map(() => avg)
          : points.map((point) => ({ x: point.x, y: avg })),
        borderColor: avgLineColor,
        borderWidth: isBar ? 0 : 1.6,
        pointRadius: 0,
        pointHoverRadius: 0,
        borderDash: [6, 4],
        fill: false,
        tension: 0,
        avgFullWidth: isBar,
        avgLineColor: avgLineColor,
        avgDecimals: yDecimals,
        avgUnit: yUnit,
        avgLocale: numberLocale,
        avgUseGrouping: useThousandsSeparator,
      });
    }

    if (secondaryPoints.length > 0) {
      datasets.push({
        type: "line",
        label: secondaryLabel || "Reference",
        data: secondaryPoints,
        borderColor: secondaryLineColor,
        borderWidth: 1.7,
        pointRadius: 0,
        pointHoverRadius: 3,
        borderDash: [4, 3],
        fill: false,
        tension: isStepped ? 0 : 0.12,
        stepped: isStepped ? ("after" as const) : false,
        spanGaps: true,
      });
    }

    return {
      labels: isBar ? barLabels : undefined,
      datasets,
    };
  });

  const xDomain = $derived.by(() => {
    const xs = points
      .map((point) => Number(point.x))
      .filter((value) => Number.isFinite(value));

    if (xs.length === 0) {
      return {
        min: undefined as number | undefined,
        max: undefined as number | undefined,
      };
    }

    const min = Math.min(...xs);
    const max = Math.max(...xs);

    if (plotStyle === "bar") {
      const halfMonthMs = 15 * 24 * 60 * 60 * 1000;
      return { min: min - halfMonthMs, max: max + halfMonthMs };
    }

    // Avoid a degenerate axis when only one x value exists.
    if (min === max) {
      const oneDayMs = 24 * 60 * 60 * 1000;
      return { min: min - oneDayMs, max: max + oneDayMs };
    }

    return { min, max };
  });

  const options = $derived.by(() => ({
    locale: numberLocale,
    responsive: true,
    maintainAspectRatio: false,
    animation: false as const,
    plugins: {
      legend: {
        display: false,
        labels: {
          color: "#cbd5e1",
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
        callbacks: {
          title: (items: any[]) => {
            if (!items?.length) return "";
            if (plotStyle === "bar") {
              const index = Number(items[0]?.dataIndex);
              const label = chartData.labels?.[index];
              return typeof label === "string" ? label : "";
            }
            const x = Number(items[0]?.parsed?.x);
            return Number.isFinite(x) ? dayjs(x).format("DD MMM YYYY") : "";
          },
          label: (item: any) => {
            const value = Number(item?.parsed?.y);
            const formatted = Number.isFinite(value)
              ? formatNumber(
                  value,
                  yDecimals,
                  numberLocale,
                  useThousandsSeparator,
                )
              : "-";
            return `${item.dataset.label}: ${formatted}${yUnit ? ` ${yUnit}` : ""}`;
          },
        },
      },
    },
    scales: {
      x: {
        type: plotStyle === "bar" ? ("category" as const) : ("linear" as const),
        bounds: "data" as const,
        offset: plotStyle === "bar",
        min: plotStyle === "bar" ? undefined : xDomain.min,
        max: plotStyle === "bar" ? undefined : xDomain.max,
        grid: { color: "rgba(148, 163, 184, 0.10)" },
        ticks: {
          color: "#9ca3af",
          maxTicksLimit: 7,
          callback: (value: any) => {
            if (plotStyle === "bar") {
              const label = chartData.labels?.[Number(value)];
              return typeof label === "string" ? label : "";
            }
            return dayjs(Number(value)).format("MMM YYYY");
          },
        },
      },
      y: {
        beginAtZero: plotStyle === "bar",
        grid: { color: "rgba(148, 163, 184, 0.10)" },
        ticks: {
          color: "#9ca3af",
          format: {
            minimumFractionDigits: yDecimals,
            maximumFractionDigits: yDecimals,
            useGrouping: useThousandsSeparator,
          },
        },
      },
    },
  }));
</script>

<div class="card bg-base-200 shadow-xl border border-primary/5 overflow-hidden">
  <div class="card-body p-4 sm:p-5">
    <div class="mb-3">
      <h3 class="text-lg font-bold text-base-content">{title}</h3>
      {#if subtitle}
        <p class="text-xs text-base-content/55">{subtitle}</p>
      {/if}
    </div>

    {#if points.length > 0}
      <div class="h-64">
        {#if plotStyle === "bar"}
          <Bar data={chartData} {options} />
        {:else}
          <Line data={chartData} {options} />
        {/if}
      </div>
    {:else}
      <div
        class="h-64 flex items-center justify-center text-sm text-base-content/40"
      >
        No data in selected range
      </div>
    {/if}
  </div>
</div>
