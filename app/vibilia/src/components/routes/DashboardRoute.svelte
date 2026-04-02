<script lang="ts">
  import { getBaseUrl } from "../../lib/config";
  import AppShell from "../AppShell.svelte";
  import DashboardPanel from "../dashboard/DashboardPanel.svelte";

  const baseUrl = getBaseUrl();

  function navigateToCars() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    window.location.href = `${normalizedBase}/cars`;
  }
</script>

<AppShell activeId="dashboard">
  <DashboardPanel onRefuel={navigateToCars} />
</AppShell>
