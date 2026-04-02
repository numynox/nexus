<script lang="ts">
  import { Line } from 'svelte-chartjs';
  import {
    Chart as ChartJS,
    Title,
    Tooltip,
    Legend,
    LineElement,
    LinearScale,
    PointElement,
    CategoryScale,
    Filler,
  } from 'chart.js';
  import dayjs from 'dayjs';

  ChartJS.register(
    Title,
    Tooltip,
    Legend,
    LineElement,
    LinearScale,
    PointElement,
    CategoryScale,
    Filler
  );

  let { history } = $props();

  // Group by hour and get MIN price per hour for the chart
  const processedData = $derived.by(() => {
    if (!history || history.length === 0) return { labels: [], datasets: [] };

    const grouped: Record<string, number> = {};
    history.forEach((p: any) => {
      const hour = dayjs(p.checked_at).startOf('hour').format('MM-DD HH:mm');
      if (!grouped[hour] || p.price < grouped[hour]) {
        grouped[hour] = p.price;
      }
    });

    const labels = Object.keys(grouped);
    const data = Object.values(grouped);

    return {
      labels,
      datasets: [
        {
          label: 'Cheapest Price (€)',
          data,
          fill: true,
          borderColor: '#ea580c', // orange-600
          backgroundColor: 'rgba(234, 88, 12, 0.1)',
          tension: 0.4,
          pointRadius: 0,
          pointHoverRadius: 6,
          borderWidth: 3,
        },
      ],
    };
  });

  function yLabelCallback(value: any) {
    return value.toFixed(2) + '€';
  }

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        mode: 'index' as const,
        intersect: false,
        backgroundColor: '#1f2937',
        titleColor: '#9ca3af',
        bodyColor: '#fff',
        borderColor: '#374151',
        borderWidth: 1,
        padding: 12,
        cornerRadius: 8,
      },
    },
    scales: {
      x: {
        display: false,
        grid: { display: false },
      },
      y: {
        beginAtZero: false,
        grid: {
          color: 'rgba(156, 163, 175, 0.1)',
        },
        ticks: {
          color: '#9ca3af',
          font: { size: 10 },
          callback: yLabelCallback,
        },
      },
    },
  };
</script>

{#if history && history.length > 0}
  <div class="w-full h-full">
    <Line data={processedData} {options} />
  </div>
{:else}
  <div class="w-full h-full flex items-center justify-center text-base-content/40 text-sm">
    No history data available for the last 72h
  </div>
{/if}
