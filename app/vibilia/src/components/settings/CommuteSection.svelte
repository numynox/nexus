<script lang="ts">
  import { Briefcase, House, LocateFixed, Trash2 } from "lucide-svelte";
  import { onMount } from "svelte";
  import type {
    CommuteDirection,
    CommuteExpectation,
    VibiliaCommute,
  } from "../../lib/data";
  import {
    deleteCommute,
    describeError,
    fetchCarsForUser,
    fetchCommuteExpectation,
    fetchCommutes,
    saveCommute,
  } from "../../lib/data";
  import { session } from "../../lib/stores";

  // Your commute in one car: the weekdays, a window each way, and where home
  // and work are. The Android app speaks the arrival time through the car
  // when you connect inside a window near where that trip starts; here it is
  // edited, and the expected travel time is shown.

  const WEEKDAYS = [
    { iso: 1, label: "Mo" },
    { iso: 2, label: "Tu" },
    { iso: 3, label: "We" },
    { iso: 4, label: "Th" },
    { iso: 5, label: "Fr" },
    { iso: 6, label: "Sa" },
    { iso: 7, label: "Su" },
  ];

  let cars = $state<any[]>([]);
  let commutes = $state<VibiliaCommute[]>([]);
  let carId = $state<string>("");
  let loading = $state(true);
  let saving = $state(false);
  let message = $state("");
  let messageIsError = $state(false);
  let expectations = $state<CommuteExpectation[]>([]);

  let weekdays = $state<number[]>([1, 2, 3, 4, 5]);
  let toWorkFrom = $state("06:30");
  let toWorkUntil = $state("09:30");
  let toHomeFrom = $state("16:00");
  let toHomeUntil = $state("19:00");
  let homeLat = $state<number | null>(null);
  let homeLng = $state<number | null>(null);
  let homeRadius = $state(300);
  let workLat = $state<number | null>(null);
  let workLng = $state<number | null>(null);
  let workRadius = $state(300);

  const existing = $derived(commutes.find((c) => c.car_id === carId) ?? null);

  onMount(async () => {
    if (!$session?.user?.id) {
      loading = false;
      return;
    }
    [cars, commutes] = await Promise.all([
      fetchCarsForUser($session.user.id),
      fetchCommutes(),
    ]);
    if (cars.length > 0) selectCar(cars[0].id);
    loading = false;
  });

  function selectCar(id: string) {
    carId = id;
    message = "";
    const commute = commutes.find((c) => c.car_id === id);
    weekdays = commute ? [...commute.weekdays] : [1, 2, 3, 4, 5];
    toWorkFrom = commute?.to_work_from ?? "06:30";
    toWorkUntil = commute?.to_work_until ?? "09:30";
    toHomeFrom = commute?.to_home_from ?? "16:00";
    toHomeUntil = commute?.to_home_until ?? "19:00";
    homeLat = commute?.home_lat ?? null;
    homeLng = commute?.home_lng ?? null;
    homeRadius = commute?.home_radius_m ?? 300;
    workLat = commute?.work_lat ?? null;
    workLng = commute?.work_lng ?? null;
    workRadius = commute?.work_radius_m ?? 300;
    loadExpectations();
  }

  async function loadExpectations() {
    expectations =
      carId && commutes.some((c) => c.car_id === carId)
        ? await fetchCommuteExpectation(carId)
        : [];
  }

  function toggleWeekday(iso: number) {
    weekdays = weekdays.includes(iso)
      ? weekdays.filter((d) => d !== iso)
      : [...weekdays, iso].sort();
  }

  function useCurrentLocation(place: "home" | "work") {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      showMessage("This browser cannot tell its location.", true);
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        const lat = Number(position.coords.latitude.toFixed(6));
        const lng = Number(position.coords.longitude.toFixed(6));
        if (place === "home") {
          homeLat = lat;
          homeLng = lng;
        } else {
          workLat = lat;
          workLng = lng;
        }
      },
      () => showMessage("Could not get your location.", true),
      { enableHighAccuracy: true, timeout: 15000 },
    );
  }

  function showMessage(text: string, isError = false) {
    message = text;
    messageIsError = isError;
  }

  function validationError(): string | null {
    if (weekdays.length === 0) return "Pick at least one day.";
    if (!toWorkFrom || !toWorkUntil || toWorkFrom >= toWorkUntil)
      return "The way to work must end after it starts.";
    if (!toHomeFrom || !toHomeUntil || toHomeFrom >= toHomeUntil)
      return "The way home must end after it starts.";
    if (!validPoint(homeLat, homeLng)) return "Set where home is.";
    if (!validPoint(workLat, workLng)) return "Set where work is.";
    if (!validRadius(homeRadius) || !validRadius(workRadius))
      return "A radius is between 50 and 5000 m.";
    return null;
  }

  function validPoint(lat: number | null, lng: number | null): boolean {
    return (
      lat !== null &&
      lng !== null &&
      Number.isFinite(lat) &&
      Number.isFinite(lng) &&
      Math.abs(lat) <= 90 &&
      Math.abs(lng) <= 180
    );
  }

  function validRadius(radius: number): boolean {
    return Number.isInteger(radius) && radius >= 50 && radius <= 5000;
  }

  async function save() {
    const invalid = validationError();
    if (invalid) {
      showMessage(invalid, true);
      return;
    }
    const commute: VibiliaCommute = {
      car_id: carId,
      weekdays: [...weekdays].sort(),
      to_work_from: toWorkFrom,
      to_work_until: toWorkUntil,
      to_home_from: toHomeFrom,
      to_home_until: toHomeUntil,
      home_lat: homeLat!,
      home_lng: homeLng!,
      home_radius_m: homeRadius,
      work_lat: workLat!,
      work_lng: workLng!,
      work_radius_m: workRadius,
    };
    saving = true;
    try {
      await saveCommute(commute);
      commutes = [...commutes.filter((c) => c.car_id !== carId), commute];
      showMessage("Commute saved.");
      await loadExpectations();
    } catch (error) {
      showMessage(describeError(error), true);
    } finally {
      saving = false;
    }
  }

  async function remove() {
    if (!confirm("Remove your commute for this vehicle?")) return;
    try {
      await deleteCommute(carId);
      commutes = commutes.filter((c) => c.car_id !== carId);
      selectCar(carId);
      showMessage("Commute removed.");
    } catch (error) {
      showMessage(describeError(error), true);
    }
  }

  function expectationText(direction: CommuteDirection): string {
    const expectation = expectations.find((e) => e.direction === direction);
    if (!expectation || expectation.medianSeconds === null)
      return "no drives in this window yet";
    const minutes = Math.round(expectation.medianSeconds / 60);
    const drives = expectation.sampleCount === 1 ? "drive" : "drives";
    return `about ${minutes} min · median of ${expectation.sampleCount} ${drives}`;
  }
</script>

<section class="card bg-base-200 shadow-sm overflow-hidden">
  <div class="card-body p-6 lg:p-8 space-y-6">
    <div>
      <h2 class="text-xl font-bold flex items-center gap-2">
        <span>🧭</span> Commute
      </h2>
      <p class="text-sm text-base-content/70 mt-1">
        When you connect to the car inside one of these windows, near home or
        work, the Android app says the arrival time through the car's speakers.
        The travel time is the median of your own drives in the window. Only
        you see your commute.
      </p>
    </div>

    {#if loading}
      <div class="flex justify-center py-4">
        <span class="loading loading-spinner text-primary"></span>
      </div>
    {:else if cars.length === 0}
      <p class="text-sm text-base-content/50 italic">
        Add a vehicle first.
      </p>
    {:else}
      {#if cars.length > 1}
        <label class="form-control">
          <span class="label-text text-sm mb-1">Vehicle</span>
          <select
            class="select select-bordered select-sm w-full sm:w-64"
            value={carId}
            onchange={(e) => selectCar((e.currentTarget as HTMLSelectElement).value)}
          >
            {#each cars as car (car.id)}
              <option value={car.id}>{car.name}</option>
            {/each}
          </select>
        </label>
      {/if}

      <div class="space-y-2">
        <div class="font-semibold text-sm">Days</div>
        <div class="flex flex-wrap gap-1">
          {#each WEEKDAYS as day (day.iso)}
            <button
              type="button"
              class="btn btn-sm w-11 {weekdays.includes(day.iso)
                ? 'btn-primary'
                : 'btn-soft'}"
              aria-pressed={weekdays.includes(day.iso)}
              onclick={() => toggleWeekday(day.iso)}>{day.label}</button
            >
          {/each}
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-6">
        {#each [{ key: "to_work" as const, title: "Way to work", icon: Briefcase }, { key: "to_home" as const, title: "Way home", icon: House }] as leg (leg.key)}
          <div class="space-y-2">
            <div class="font-semibold text-sm flex items-center gap-2">
              <leg.icon class="w-4 h-4 text-primary" />
              {leg.title}, leaving between
            </div>
            <div class="flex items-center gap-2">
              {#if leg.key === "to_work"}
                <input type="time" class="input input-bordered input-sm" bind:value={toWorkFrom} aria-label="To work from" />
                <span class="text-sm">and</span>
                <input type="time" class="input input-bordered input-sm" bind:value={toWorkUntil} aria-label="To work until" />
              {:else}
                <input type="time" class="input input-bordered input-sm" bind:value={toHomeFrom} aria-label="Home from" />
                <span class="text-sm">and</span>
                <input type="time" class="input input-bordered input-sm" bind:value={toHomeUntil} aria-label="Home until" />
              {/if}
            </div>
            {#if existing}
              <div class="text-xs text-base-content/60">
                Travel time: {expectationText(leg.key)}
              </div>
            {/if}
          </div>
        {/each}
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 gap-6">
        <div class="space-y-2">
          <div class="font-semibold text-sm flex items-center gap-2">
            <House class="w-4 h-4 text-primary" /> Home
          </div>
          <div class="grid grid-cols-2 gap-2">
            <input type="number" step="any" class="input input-bordered input-sm" placeholder="Latitude" aria-label="Home latitude" bind:value={homeLat} />
            <input type="number" step="any" class="input input-bordered input-sm" placeholder="Longitude" aria-label="Home longitude" bind:value={homeLng} />
          </div>
          <label class="flex items-center gap-2 text-sm">
            Radius
            <input type="number" min="50" max="5000" step="50" class="input input-bordered input-sm w-24" bind:value={homeRadius} />
            m
          </label>
          <button type="button" class="btn btn-ghost btn-xs gap-1" onclick={() => useCurrentLocation("home")}>
            <LocateFixed class="w-3 h-3" /> Use my location
          </button>
        </div>
        <div class="space-y-2">
          <div class="font-semibold text-sm flex items-center gap-2">
            <Briefcase class="w-4 h-4 text-primary" /> Work
          </div>
          <div class="grid grid-cols-2 gap-2">
            <input type="number" step="any" class="input input-bordered input-sm" placeholder="Latitude" aria-label="Work latitude" bind:value={workLat} />
            <input type="number" step="any" class="input input-bordered input-sm" placeholder="Longitude" aria-label="Work longitude" bind:value={workLng} />
          </div>
          <label class="flex items-center gap-2 text-sm">
            Radius
            <input type="number" min="50" max="5000" step="50" class="input input-bordered input-sm w-24" bind:value={workRadius} />
            m
          </label>
          <button type="button" class="btn btn-ghost btn-xs gap-1" onclick={() => useCurrentLocation("work")}>
            <LocateFixed class="w-3 h-3" /> Use my location
          </button>
        </div>
      </div>

      {#if message}
        <p class="text-sm {messageIsError ? 'text-error' : 'text-success'}">
          {message}
        </p>
      {/if}

      <div class="flex justify-end gap-2">
        {#if existing}
          <button type="button" class="btn btn-ghost btn-sm text-error gap-1" onclick={remove}>
            <Trash2 class="w-4 h-4" /> Remove
          </button>
        {/if}
        <button type="button" class="btn btn-primary btn-sm" disabled={saving} onclick={save}>
          {saving ? "Saving…" : "Save commute"}
        </button>
      </div>
    {/if}
  </div>
</section>
