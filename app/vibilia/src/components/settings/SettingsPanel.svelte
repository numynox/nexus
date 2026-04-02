<script lang="ts">
  import { signOut, updatePreferredFuelType } from "../../lib/data";
  import { preferredFuelType, session } from "../../lib/stores";
  import AccountSection from "./AccountSection.svelte";
  import FuelPreferencesSection from "./FuelPreferencesSection.svelte";
  import VehicleManagerSection from "./VehicleManagerSection.svelte";

  let saving = false;

  async function updateFuelPreference(type: string) {
    saving = true;
    preferredFuelType.set(type);
    try {
      if ($session?.user?.id) {
        await updatePreferredFuelType($session.user.id, type);
      }
    } finally {
      saving = false;
    }
  }

  async function handleLogout() {
    await signOut();
  }
</script>

<div class="space-y-8 animate-in slide-in-from-bottom duration-500">
  <div>
    <h1 class="text-3xl font-black text-base-content">Settings</h1>
    <p class="text-sm text-base-content/60">
      Manage your profile and preferences
    </p>
  </div>

  <FuelPreferencesSection
    selectedType={$preferredFuelType}
    {saving}
    onSelect={updateFuelPreference}
  />

  <div class="divider"></div>

  <!-- Vehicle Management (Moved from separate tab as requested) -->
  <VehicleManagerSection />

  <!-- Logout for Mobile (duplicated in drawer but useful here) -->
  <AccountSection onLogout={handleLogout} />
</div>
