<script lang="ts">
  import { getBaseUrl } from "../../lib/config";
  import { getLastSelectedCarId } from "../../lib/storage";
  import AppShell from "../AppShell.svelte";
  import FuelPricePanel from "../fuel-price/FuelPricePanel.svelte";
  import NearbyFuelPricePanel from "../fuel-price/NearbyFuelPricePanel.svelte";

  const baseUrl = getBaseUrl();

  let {
    priceBucketMinutes = 10,
    searchRadiusKm = 3,
    mode = "history",
  } = $props();
  function navigateToVehicleLogs() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    const selectedCarId = getLastSelectedCarId();
    const params = new URLSearchParams({ newRefuel: "1" });

    if (selectedCarId) {
      params.set("car", selectedCarId);
    }

    window.location.href = `${normalizedBase}/vehicle-logs?${params.toString()}`;
  }
</script>

<AppShell activeId="fuel-price" activeSubmenuId={mode}>
  {#if mode === "nearby"}
    <NearbyFuelPricePanel {searchRadiusKm} />
  {:else}
    <FuelPricePanel onRefuel={navigateToVehicleLogs} {priceBucketMinutes} />
  {/if}
</AppShell>
