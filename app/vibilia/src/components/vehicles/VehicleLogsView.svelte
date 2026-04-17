<script lang="ts">
  import { Car } from "lucide-svelte";
  import { onMount } from "svelte";
  import { fetchCarsForUser } from "../../lib/data";
  import {
    clearLastSelectedCarId,
    getLastSelectedCarId,
    setLastSelectedCarId,
  } from "../../lib/storage";
  import { session } from "../../lib/stores";
  import RefuelLog from "./RefuelLog.svelte";

  const RefuelLogView = RefuelLog as any;

  let cars = $state<any[]>([]);
  let loading = $state(true);
  let selectedCar = $state<any>(null);

  onMount(() => {
    fetchCars();
  });

  function selectCar(car: any) {
    selectedCar = car;
    setLastSelectedCarId(car.id);
  }

  async function fetchCars() {
    loading = true;
    if (!$session?.user?.id) {
      cars = [];
      selectedCar = null;
      loading = false;
      return;
    }

    cars = await fetchCarsForUser($session.user.id);

    const searchParams =
      typeof window === "undefined"
        ? new URLSearchParams()
        : new URLSearchParams(window.location.search);

    const requestedCarId = searchParams.get("car");

    const persistedCarId = getLastSelectedCarId();
    const currentSelectedId = selectedCar?.id;

    const resolvedCar =
      cars.find((car) => car.id === requestedCarId) ||
      cars.find((car) => car.id === currentSelectedId) ||
      cars.find((car) => car.id === persistedCarId) ||
      cars[0] ||
      null;

    selectedCar = resolvedCar;

    if (resolvedCar) {
      setLastSelectedCarId(resolvedCar.id);
    } else {
      clearLastSelectedCarId();
    }

    loading = false;
  }
</script>

<div class="space-y-6 animate-in slide-in-from-right duration-500">
  {#if loading}
    <div class="h-32 flex items-center justify-center">
      <span class="loading loading-spinner text-primary"></span>
    </div>
  {:else if selectedCar}
    <div class="space-y-4">
      <RefuelLogView car={selectedCar} {cars} onSelectCar={selectCar} />
    </div>
  {:else}
    <div class="space-y-4">
      <h1 class="text-3xl font-black text-base-content tracking-tight">
        Vehicle Logs
      </h1>
      <div
        class="text-center py-16 bg-base-200/50 rounded-3xl border-2 border-dashed border-base-content/10"
      >
        <Car class="w-16 h-16 mx-auto text-base-content/10 mb-4" />
        <p class="text-base-content/40 font-medium">No vehicles found.</p>
        <p class="text-xs text-base-content/30 mt-1">
          Add one in Settings to get started.
        </p>
      </div>
    </div>
  {/if}
</div>
