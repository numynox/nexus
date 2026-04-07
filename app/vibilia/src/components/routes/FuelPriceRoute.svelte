<script lang="ts">
  import { getBaseUrl } from "../../lib/config";
  import AppShell from "../AppShell.svelte";
  import FuelPricePanel from "../fuel-price/FuelPricePanel.svelte";

  const baseUrl = getBaseUrl();

  let { priceBucketMinutes = 10 } = $props();

  function navigateToCars() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    window.location.href = `${normalizedBase}/cars`;
  }
</script>

<AppShell activeId="fuel-price">
  <FuelPricePanel onRefuel={navigateToCars} {priceBucketMinutes} />
</AppShell>
