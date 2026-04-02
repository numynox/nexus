<script lang="ts">
  import { createRefuelEvent } from "../../lib/data";
  import { session } from "../../lib/stores";

  let { car, onSuccess, onCancel } = $props();

  let mileage = $state(0);
  let liters = $state(0);
  let totalPrice = $state(0);
  let fuelLevelPercent = $state(100);
  let isFull = $state(true);
  let loading = $state(false);

  async function handleSubmit(e: SubmitEvent) {
    e.preventDefault();

    const userId = $session?.user?.id;
    if (!userId) {
      alert("You must be signed in to add a refuel entry.");
      return;
    }

    loading = true;

    // Logic: Convert percentage to liters (current level in tank after refuel)
    // Percentage * tank_capacity
    const levelInLiters = isFull
      ? car.tank_capacity
      : (fuelLevelPercent / 100) * car.tank_capacity;
    const pricePerLiter = totalPrice / liters;

    try {
      await createRefuelEvent({
        car_id: car.id,
        user_id: userId,
        mileage,
        liters,
        total_price: totalPrice,
        fuel_level_after: levelInLiters,
        price_per_liter_calculated: pricePerLiter,
      });
      onSuccess();
    } catch (error) {
      alert(error instanceof Error ? error.message : String(error));
    } finally {
      loading = false;
    }
  }

  $effect(() => {
    if (isFull) fuelLevelPercent = 100;
  });
</script>

<form onsubmit={handleSubmit} class="space-y-4">
  <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
    <div class="form-control">
      <label class="label" for="mileage"
        ><span class="label-text">Total Mileage (km)</span></label
      >
      <input
        type="number"
        id="mileage"
        bind:value={mileage}
        class="input input-bordered"
        required
      />
    </div>
    <div class="form-control">
      <label class="label" for="liters"
        ><span class="label-text">Liters Filled</span></label
      >
      <input
        type="number"
        id="liters"
        step="0.01"
        bind:value={liters}
        class="input input-bordered"
        required
      />
    </div>
    <div class="form-control">
      <label class="label" for="totalPrice"
        ><span class="label-text">Total Price (€)</span></label
      >
      <input
        type="number"
        id="totalPrice"
        step="0.01"
        bind:value={totalPrice}
        class="input input-bordered"
        required
      />
    </div>
    <div class="form-control">
      <label class="label flex justify-between" for="fuelLevel">
        <span class="label-text">Fuel Level After</span>
        <span class="label-text-alt font-bold text-primary"
          >{fuelLevelPercent}%</span
        >
      </label>
      <div class="flex items-center gap-4 px-1">
        <input
          type="range"
          id="fuelLevel"
          min="0"
          max="100"
          bind:value={fuelLevelPercent}
          class="range range-primary range-sm"
          disabled={isFull}
        />
        <label class="label cursor-pointer gap-2" for="isFull">
          <span class="label-text text-xs">Full</span>
          <input
            type="checkbox"
            id="isFull"
            bind:checked={isFull}
            class="checkbox checkbox-sm checkbox-primary"
          />
        </label>
      </div>
    </div>
  </div>

  <div class="card-actions justify-end mt-6">
    <button type="button" class="btn btn-ghost" onclick={onCancel}
      >Cancel</button
    >
    <button type="submit" class="btn btn-primary px-8" disabled={loading}>
      {#if loading}<span class="loading loading-spinner"></span>{/if}
      Save Entry
    </button>
  </div>
</form>
