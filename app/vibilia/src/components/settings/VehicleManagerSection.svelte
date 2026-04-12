<script lang="ts">
  import { Fuel, PencilLine, Plus, Trash2, Users } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    createCarForUser,
    deleteCarById,
    fetchOwnedCarsForUser,
    findProfileById,
    grantCarAccess,
    updateCarById,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  interface Props {
    selectedFuelType: string;
    savingFuelPreference: boolean;
    onFuelTypeChange: (type: string) => void;
  }

  let { selectedFuelType, savingFuelPreference, onFuelTypeChange }: Props =
    $props();

  let cars = $state<any[]>([]);
  let loading = $state(true);
  let showAddCar = $state(false);
  let newCarName = $state("");
  let newCarCapacity = $state(50);
  let newCarVin = $state("");
  let newCarPlate = $state("");
  let newCarMake = $state("");
  let newCarModel = $state("");
  let newCarYear = $state("");

  let editingCarId = $state<string | null>(null);
  let editCarName = $state("");
  let editCarCapacity = $state(50);
  let editCarVin = $state("");
  let editCarPlate = $state("");
  let editCarMake = $state("");
  let editCarModel = $state("");
  let editCarYear = $state("");

  let sharingCarId = $state<string | null>(null);
  let shareEmail = $state("");
  let shareMessage = $state("");

  onMount(() => {
    fetchCars();
  });

  function toOptionalText(value: unknown): string | null {
    if (value === null || value === undefined) return null;
    const text = String(value).trim();
    return text.length > 0 ? text : null;
  }

  function toOptionalYear(value: unknown): number | null {
    const text = toOptionalText(value);
    if (!text) return null;
    const year = Number.parseInt(text, 10);
    return Number.isFinite(year) ? year : null;
  }

  async function fetchCars() {
    loading = true;
    if (!$session?.user?.id) {
      cars = [];
      loading = false;
      return;
    }

    cars = await fetchOwnedCarsForUser($session.user.id);
    loading = false;
  }

  async function addCar() {
    if (!newCarName || !$session?.user?.id) return;

    try {
      const data = await createCarForUser(
        $session.user.id,
        newCarName,
        newCarCapacity,
        {
          vin: toOptionalText(newCarVin),
          plate: toOptionalText(newCarPlate),
          make: toOptionalText(newCarMake),
          model: toOptionalText(newCarModel),
          year: toOptionalYear(newCarYear),
        },
      );
      cars = [...cars, data];
      showAddCar = false;
      newCarName = "";
      newCarCapacity = 50;
      newCarVin = "";
      newCarPlate = "";
      newCarMake = "";
      newCarModel = "";
      newCarYear = "";
    } catch (error) {
      shareMessage = error instanceof Error ? error.message : String(error);
    }
  }

  function openEditCar(car: any) {
    editingCarId = car.id;
    editCarName = car.name || "";
    editCarCapacity = Number(car.tank_capacity || 0);
    editCarVin = car.vin || "";
    editCarPlate = car.plate || "";
    editCarMake = car.make || "";
    editCarModel = car.model || "";
    editCarYear = car.year ? String(car.year) : "";
  }

  function closeEditCar() {
    editingCarId = null;
  }

  async function saveCarEdits() {
    if (!editingCarId || !editCarName.trim()) return;

    try {
      const updated = await updateCarById(editingCarId, {
        name: editCarName.trim(),
        tank_capacity: editCarCapacity,
        vin: toOptionalText(editCarVin),
        plate: toOptionalText(editCarPlate),
        make: toOptionalText(editCarMake),
        model: toOptionalText(editCarModel),
        year: toOptionalYear(editCarYear),
      });

      cars = cars.map((car) => (car.id === editingCarId ? updated : car));
      editingCarId = null;
    } catch (error) {
      shareMessage = error instanceof Error ? error.message : String(error);
    }
  }

  async function deleteCar(id: string) {
    if (
      !confirm(
        "Are you sure you want to delete this vehicle? All refuel logs for this car will be lost.",
      )
    )
      return;
    try {
      await deleteCarById(id);
      cars = cars.filter((c) => c.id !== id);
    } catch (error) {
      shareMessage = error instanceof Error ? error.message : String(error);
    }
  }

  async function openShare(carId: string) {
    sharingCarId = carId;
    shareEmail = "";
    shareMessage = "";
  }

  async function shareCar() {
    if (!sharingCarId || !shareEmail) return;

    // Find user by email (Simplified: in a real app, you'd use a server-side lookup or RPC)
    // Here we try to find in shared profiles since that's where we store user info
    let profile: { id: string };
    try {
      profile = await findProfileById(shareEmail);
    } catch (_error) {
      shareMessage =
        "User not found. Please use their Supabase User ID for now.";
      return;
    }

    // Fallback: If we don't have a lookup by email established, we tell the user to use the User ID for now
    // In the real system, you'd probably have an rpc find_user_by_email(email)

    try {
      await grantCarAccess(sharingCarId, profile.id);
      shareMessage = "Car shared successfully!";
      setTimeout(() => {
        sharingCarId = null;
      }, 2000);
    } catch (_error) {
      shareMessage = "Error sharing or already shared with this user.";
    }
  }
</script>

<section class="card bg-base-200 shadow-sm overflow-hidden">
  <div class="card-body p-6 lg:p-8 space-y-6">
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold flex items-center gap-2">
        <span>🚗</span> Vehicles
      </h2>
      <button
        class="btn btn-primary btn-sm rounded-full gap-2"
        onclick={() => (showAddCar = !showAddCar)}
      >
        <Plus class="w-4 h-4" /> Add Car
      </button>
    </div>

    {#if showAddCar}
      <div class="card bg-base-300 border border-primary/20">
        <div class="card-body p-4">
          <h4 class="font-bold mb-2">New Vehicle</h4>
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="form-control">
              <label class="label" for="mgr-newCarName"
                ><span class="label-text">Name</span></label
              >
              <input
                type="text"
                id="mgr-newCarName"
                bind:value={newCarName}
                class="input input-bordered input-sm"
                placeholder="e.g. Blue Golf"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-newCarCapacity"
                ><span class="label-text">Tank capacity (L)</span></label
              >
              <input
                type="number"
                id="mgr-newCarCapacity"
                bind:value={newCarCapacity}
                class="input input-bordered input-sm"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-newCarPlate"
                ><span class="label-text">Plate</span></label
              >
              <input
                type="text"
                id="mgr-newCarPlate"
                bind:value={newCarPlate}
                class="input input-bordered input-sm"
                placeholder="e.g. MKK DD 95"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-newCarVin"
                ><span class="label-text">VIN</span></label
              >
              <input
                type="text"
                id="mgr-newCarVin"
                bind:value={newCarVin}
                class="input input-bordered input-sm"
                placeholder="Optional"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-newCarMake"
                ><span class="label-text">Make</span></label
              >
              <input
                type="text"
                id="mgr-newCarMake"
                bind:value={newCarMake}
                class="input input-bordered input-sm"
                placeholder="e.g. Volkswagen"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-newCarModel"
                ><span class="label-text">Model</span></label
              >
              <input
                type="text"
                id="mgr-newCarModel"
                bind:value={newCarModel}
                class="input input-bordered input-sm"
                placeholder="e.g. Passat Variant"
              />
            </div>
            <div class="form-control sm:col-span-2">
              <label class="label" for="mgr-newCarYear"
                ><span class="label-text">Year</span></label
              >
              <input
                type="number"
                id="mgr-newCarYear"
                bind:value={newCarYear}
                class="input input-bordered input-sm"
                placeholder="e.g. 2020"
              />
            </div>
          </div>
          <div class="card-actions justify-end mt-4">
            <button
              class="btn btn-ghost btn-sm"
              onclick={() => (showAddCar = false)}>Cancel</button
            >
            <button class="btn btn-primary btn-sm" onclick={addCar}
              >Save Vehicle</button
            >
          </div>
        </div>
      </div>
    {/if}

    {#if sharingCarId}
      <div class="modal modal-open">
        <div class="modal-box">
          <h3 class="font-bold text-lg flex items-center gap-2">
            <Users class="w-5 h-5" /> Share {cars.find(
              (c) => c.id === sharingCarId,
            )?.name}
          </h3>
          <p class="py-4 text-sm text-base-content/70">
            Enter the User ID of the person you want to share this vehicle with.
            They will be able to see prices and log refuels.
          </p>
          <div class="form-control">
            <input
              type="text"
              bind:value={shareEmail}
              class="input input-bordered"
              placeholder="User ID"
            />
            {#if shareMessage}
              <p
                class="mt-2 text-xs {shareMessage.includes('success')
                  ? 'text-success'
                  : 'text-error'}"
              >
                {shareMessage}
              </p>
            {/if}
          </div>
          <div class="modal-action">
            <button class="btn btn-ghost" onclick={() => (sharingCarId = null)}
              >Close</button
            >
            <button class="btn btn-primary" onclick={shareCar}
              >Share Access</button
            >
          </div>
        </div>
      </div>
    {/if}

    {#if loading}
      <div class="flex justify-center py-4">
        <span class="loading loading-spinner text-primary"></span>
      </div>
    {:else}
      <div class="space-y-3">
        {#each cars as car (car.id)}
          <div class="card bg-base-200 border border-base-content/5">
            <div
              class="card-body p-4 flex flex-row items-center justify-between"
            >
              <div>
                <div class="font-bold">{car.name}</div>
                <div class="text-xs text-base-content/50">
                  {car.tank_capacity}L capacity
                </div>
                <div class="text-xs text-base-content/50">
                  {car.year || ""}
                  {car.make || ""}
                  {car.model || ""}
                </div>
                {#if car.plate}
                  <div class="text-xs text-base-content/60">
                    {car.plate}
                  </div>
                {/if}
              </div>
              <div class="flex gap-1">
                <button
                  class="btn btn-ghost btn-sm btn-square"
                  onclick={() => openEditCar(car)}
                  title="Edit"
                >
                  <PencilLine class="w-4 h-4" />
                </button>
                <button
                  class="btn btn-ghost btn-sm btn-square"
                  onclick={() => openShare(car.id)}
                  title="Share"
                >
                  <Users class="w-4 h-4" />
                </button>
                <button
                  class="btn btn-ghost btn-sm btn-square text-error"
                  onclick={() => deleteCar(car.id)}
                  title="Delete"
                >
                  <Trash2 class="w-4 h-4" />
                </button>
              </div>
            </div>
          </div>
        {:else}
          <p class="text-center py-4 text-sm text-base-content/40 italic">
            You haven't added any vehicles yet.
          </p>
        {/each}
      </div>
    {/if}

    {#if editingCarId}
      <div class="modal modal-open">
        <div class="modal-box">
          <h3 class="font-bold text-lg">Edit Vehicle</h3>
          <div class="mt-4 grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div class="form-control">
              <label class="label" for="mgr-editCarName"
                ><span class="label-text">Name</span></label
              >
              <input
                type="text"
                id="mgr-editCarName"
                bind:value={editCarName}
                class="input input-bordered input-sm"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-editCarCapacity"
                ><span class="label-text">Tank Capacity (L)</span></label
              >
              <input
                type="number"
                id="mgr-editCarCapacity"
                bind:value={editCarCapacity}
                class="input input-bordered input-sm"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-editCarPlate"
                ><span class="label-text">Plate</span></label
              >
              <input
                type="text"
                id="mgr-editCarPlate"
                bind:value={editCarPlate}
                class="input input-bordered input-sm"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-editCarVin"
                ><span class="label-text">VIN</span></label
              >
              <input
                type="text"
                id="mgr-editCarVin"
                bind:value={editCarVin}
                class="input input-bordered input-sm"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-editCarMake"
                ><span class="label-text">Make</span></label
              >
              <input
                type="text"
                id="mgr-editCarMake"
                bind:value={editCarMake}
                class="input input-bordered input-sm"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-editCarModel"
                ><span class="label-text">Model</span></label
              >
              <input
                type="text"
                id="mgr-editCarModel"
                bind:value={editCarModel}
                class="input input-bordered input-sm"
              />
            </div>
            <div class="form-control">
              <label class="label" for="mgr-editCarYear"
                ><span class="label-text">Year</span></label
              >
              <input
                type="number"
                id="mgr-editCarYear"
                bind:value={editCarYear}
                class="input input-bordered input-sm"
              />
            </div>
          </div>
          <div class="modal-action">
            <button class="btn btn-ghost" onclick={closeEditCar}>Cancel</button>
            <button class="btn btn-primary" onclick={saveCarEdits}>Save</button>
          </div>
        </div>
      </div>
    {/if}

    <div class="divider my-0"></div>

    <div class="space-y-3">
      <h3 class="text-base font-semibold flex items-center gap-2">
        <Fuel class="w-4 h-4 text-primary" /> Preferred Fuel Type
      </h3>
      <p class="text-sm text-base-content/70">
        Select the fuel type to show in Fuel Price and statistics.
      </p>
      <div class="flex flex-wrap gap-2">
        {#each ["E5", "E10", "Diesel"] as type}
          <button
            class="btn {selectedFuelType === type ? 'btn-primary' : 'btn-soft'}"
            disabled={savingFuelPreference}
            onclick={() => onFuelTypeChange(type)}
          >
            {type}
          </button>
        {/each}
      </div>
    </div>
  </div>
</section>
