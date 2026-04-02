<script lang="ts">
  import dayjs from "dayjs";
  import { Banknote, Droplets, Gauge, History, Plus } from "lucide-svelte";
  import { onMount } from "svelte";
  import { fetchRefuelEventsForCar } from "../../lib/data";
  import RefuelForm from "./RefuelForm.svelte";

  let { car } = $props();
  let events = $state<any[]>([]);
  let loading = $state(true);
  let showForm = $state(false);

  onMount(() => {
    fetchEvents();
  });

  async function fetchEvents() {
    loading = true;
    const data: any[] = await fetchRefuelEventsForCar(car.id);

    // Calculate consumption for each entry
    events = data.map((e: any, i: number) => {
      let consumption = null;
      const next = data[i + 1]; // next in terms of historical order (earlier event)
      if (next) {
        const km = e.mileage - next.mileage;
        if (km > 0) {
          consumption = (e.liters / km) * 100;
        }
      }
      return { ...e, consumption };
    });

    loading = false;
  }
</script>

<div class="space-y-6">
  <div class="flex items-center justify-between">
    <div>
      <h2 class="text-2xl font-black">{car.name}</h2>
      <p class="text-sm text-base-content/60">Refuel History & Logs</p>
    </div>
    <button
      class="btn btn-primary rounded-full gap-2"
      onclick={() => (showForm = true)}
    >
      <Plus class="w-4 h-4" /> Log Refuel
    </button>
  </div>

  {#if showForm}
    <div class="card bg-base-200 shadow-xl border border-primary/20">
      <div class="card-body">
        <RefuelForm
          {car}
          onSuccess={() => {
            showForm = false;
            fetchEvents();
          }}
          onCancel={() => (showForm = false)}
        />
      </div>
    </div>
  {/if}

  {#if loading}
    <div class="flex justify-center py-8">
      <span class="loading loading-spinner text-primary"></span>
    </div>
  {:else}
    <div class="space-y-4">
      {#each events as e (e.id)}
        <div
          class="collapse collapse-arrow bg-base-200 border border-base-content/5"
        >
          <input type="radio" name="refuel-accordion" />
          <div class="collapse-title flex items-center justify-between pr-10">
            <div class="flex items-center gap-3">
              <div class="p-2 bg-success/10 text-success rounded-lg">
                <History class="w-4 h-4" />
              </div>
              <div>
                <div class="font-bold">
                  {dayjs(e.timestamp).format("DD.MM.YYYY")}
                </div>
                <div
                  class="text-[10px] text-base-content/50 uppercase tracking-wider"
                >
                  {e.mileage} KM
                </div>
              </div>
            </div>
            <div class="text-right">
              <div class="font-bold text-lg">{e.total_price.toFixed(2)}€</div>
              {#if e.consumption}
                <div class="text-[10px] text-secondary font-bold">
                  {e.consumption.toFixed(2)} L/100km
                </div>
              {/if}
            </div>
          </div>
          <div class="collapse-content bg-base-300/30">
            <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 py-4">
              <div class="flex flex-col">
                <span class="text-[10px] text-base-content/50 uppercase"
                  >Liters</span
                >
                <span class="font-bold flex items-center gap-1"
                  ><Droplets class="w-3 h-3 text-info" /> {e.liters}L</span
                >
              </div>
              <div class="flex flex-col">
                <span class="text-[10px] text-base-content/50 uppercase"
                  >Price/L</span
                >
                <span class="font-bold flex items-center gap-1"
                  ><Banknote class="w-3 h-3 text-success" />
                  {e.price_per_liter_calculated?.toFixed(3) ||
                    (e.total_price / e.liters).toFixed(3)}€</span
                >
              </div>
              <div class="flex flex-col">
                <span class="text-[10px] text-base-content/50 uppercase"
                  >Mileage</span
                >
                <span class="font-bold flex items-center gap-1"
                  ><Gauge class="w-3 h-3 text-warning" /> {e.mileage} km</span
                >
              </div>
              <div class="flex flex-col">
                <span class="text-[10px] text-base-content/50 uppercase"
                  >Fuel Level</span
                >
                <span class="font-bold"
                  >{((e.fuel_level_after / car.tank_capacity) * 100).toFixed(
                    0,
                  )}%</span
                >
              </div>
            </div>
          </div>
        </div>
      {:else}
        <p class="text-center py-8 text-base-content/40 italic">
          No refuel events logged yet.
        </p>
      {/each}
    </div>
  {/if}
</div>
