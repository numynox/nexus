<script lang="ts">
  import dayjs from "dayjs";
  import { Clock } from "lucide-svelte";
  import { fetchCarLastDrive, type CarLastDrive } from "../../lib/data";

  let { carId = null } = $props<{ carId?: string | null }>();

  let drive = $state<CarLastDrive | null>(null);
  let loadToken = 0;

  function formatDuration(startIso: string, endIso: string): string {
    const minutes = Math.round(dayjs(endIso).diff(dayjs(startIso), "minute"));
    const hours = Math.floor(minutes / 60);
    return hours > 0 ? `${hours} h ${minutes % 60} min` : `${minutes} min`;
  }

  const text = $derived.by(() => {
    if (!drive) return "";
    return [
      `Last drive ${dayjs(drive.startedAt).format("DD MMM YYYY, HH:mm")}`,
      formatDuration(drive.startedAt, drive.endedAt),
      drive.estimatedKm !== null ? `≈ ${Math.round(drive.estimatedKm)} km` : null,
    ]
      .filter(Boolean)
      .join(" · ");
  });

  $effect(() => {
    const nextCarId = carId;
    const token = ++loadToken;

    if (!nextCarId) {
      drive = null;
      return;
    }

    void (async () => {
      const next = await fetchCarLastDrive(nextCarId).catch(() => null);
      if (token !== loadToken) return;
      drive = next;
    })();
  });
</script>

{#if drive}
  <p
    class="flex items-center gap-1.5 text-xs tabular-nums text-base-content/50"
    title="Recorded over Bluetooth by the Android app; the distance is estimated from driving time"
  >
    <Clock class="h-3.5 w-3.5 shrink-0" />
    {text}
  </p>
{/if}
