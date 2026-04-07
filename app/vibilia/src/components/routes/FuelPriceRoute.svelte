<script lang="ts">
  import { getBaseUrl } from "../../lib/config";
  import { getLastSelectedCarId } from "../../lib/storage";
  import AppShell from "../AppShell.svelte";
  import FuelPricePanel from "../fuel-price/FuelPricePanel.svelte";

  const baseUrl = getBaseUrl();

  let { priceBucketMinutes = 10 } = $props();

  function navigateToCars() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    const selectedCarId = getLastSelectedCarId();
    const params = new URLSearchParams({ newRefuel: "1" });

    if (selectedCarId) {
      params.set("car", selectedCarId);
    }

    window.location.href = `${normalizedBase}/cars?${params.toString()}`;
  }
</script>

<AppShell activeId="fuel-price">
  <FuelPricePanel onRefuel={navigateToCars} {priceBucketMinutes} />
</AppShell>
