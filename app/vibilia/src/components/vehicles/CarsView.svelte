<script lang="ts">
  import { Car, ChevronRight } from "lucide-svelte";
  import { onMount } from "svelte";
  import { fetchCarsForUser } from "../../lib/data";
  import { session } from "../../lib/stores";
  import RefuelLog from "./RefuelLog.svelte";

  let cars = $state<any[]>([]);
  let loading = $state(true);
  let selectedCar = $state<any>(null);

  onMount(() => {
    fetchCars();
  });

  async function fetchCars() {
    loading = true;
    if (!$session?.user?.id) {
      cars = [];
      loading = false;
      return;
    }

    cars = await fetchCarsForUser($session.user.id);
    loading = false;
  }
</script>

{#if selectedCar}
  <div class="space-y-4">
    <button
      class="btn btn-ghost btn-sm gap-2"
      onclick={() => (selectedCar = null)}
    >
      <ChevronRight class="w-4 h-4 rotate-180" /> Back to Vehicles
    </button>
    <RefuelLog car={selectedCar} />
  </div>
{:else}
  <div class="space-y-6 animate-in slide-in-from-right duration-500">
    <div>
      <h1
        class="text-3xl font-black text-base-content uppercase tracking-tight"
      >
        Vehicles
      </h1>
      <p class="text-sm text-base-content/60">
        Select a vehicle to log refuels and see history
      </p>
    </div>

    {#if loading}
      <div class="h-32 flex items-center justify-center">
        <span class="loading loading-spinner text-primary"></span>
      </div>
    {:else}
      <div class="grid grid-cols-1 gap-4">
        {#each cars as car (car.id)}
          <button
            class="card bg-base-200 border border-base-content/5 hover:border-primary/40 hover:bg-base-300/50 transition-all text-left"
            onclick={() => (selectedCar = car)}
          >
            <div class="card-body p-4 flex flex-row items-center gap-4">
              <div
                class="p-4 bg-primary/10 rounded-2xl text-primary shadow-inner"
              >
                <Car class="w-8 h-8" />
              </div>
              <div class="flex-grow">
                <h3 class="font-black text-xl text-base-content">{car.name}</h3>
                <div class="flex items-center gap-2 mt-1">
                  <span class="badge badge-sm badge-outline opacity-70"
                    >{car.tank_capacity}L Tank</span
                  >
                  {#if car.owner_id !== $session?.user.id}
                    <span class="badge badge-sm badge-secondary"
                      >Shared with you</span
                    >
                  {/if}
                </div>
              </div>
              <ChevronRight class="w-5 h-5 text-base-content/30" />
            </div>
          </button>
        {:else}
          <div
            class="text-center py-16 bg-base-200/50 rounded-3xl border-2 border-dashed border-base-content/10"
          >
            <Car class="w-16 h-16 mx-auto text-base-content/10 mb-4" />
            <p class="text-base-content/40 font-medium">No vehicles found.</p>
            <p class="text-xs text-base-content/30 mt-1">
              Add one in Settings to get started.
            </p>
          </div>
        {/each}
      </div>
    {/if}
  </div>
{/if}
