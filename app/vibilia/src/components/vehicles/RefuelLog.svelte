<script lang="ts">
  import dayjs from "dayjs";
  import {
    ChevronDown,
    History,
    MapPin,
    PencilLine,
    Plus,
  } from "lucide-svelte";
  import { fetchRefuelEventsForCar } from "../../lib/data";
  import RefuelForm from "./RefuelForm.svelte";

  interface Props {
    car: any;
    cars?: any[];
    onSelectCar?: (car: any) => void;
  }

  let { car, cars = [], onSelectCar }: Props = $props();
  let events = $state<any[]>([]);
  let loading = $state(true);
  let showForm = $state(false);
  let editingEvent = $state<any | null>(null);
  let appliedInitialFormOpen = $state(false);

  function clearNewRefuelQueryParam() {
    if (typeof window === "undefined") return;

    const url = new URL(window.location.href);
    if (!url.searchParams.has("newRefuel")) return;

    url.searchParams.delete("newRefuel");
    const nextSearch = url.searchParams.toString();
    const nextUrl = `${url.pathname}${nextSearch ? `?${nextSearch}` : ""}${url.hash}`;
    window.history.replaceState({}, "", nextUrl);
  }

  $effect(() => {
    if (!car?.id) return;
    fetchEvents();
  });

  $effect(() => {
    if (appliedInitialFormOpen || !car?.id || typeof window === "undefined")
      return;

    const shouldOpenFromUrl =
      new URLSearchParams(window.location.search).get("newRefuel") === "1";

    if (!shouldOpenFromUrl) {
      appliedInitialFormOpen = true;
      return;
    }

    openCreateForm();
    appliedInitialFormOpen = true;
    clearNewRefuelQueryParam();
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

  function formatFuelStation(station: any): string {
    if (!station) return "Other fuel station";
    const brand = station.brand || "";
    const place = station.place || "";
    const street = station.street || "";
    const houseNumber = station.house_number || "";
    return `${brand} ${place} (${street} ${houseNumber})`
      .replace(/\s+/g, " ")
      .trim();
  }

  function formatEventDate(value: string) {
    return dayjs(value).format("DD MMM YYYY");
  }

  function formatEventTime(value: string) {
    return dayjs(value).format("HH:mm");
  }

  function getEventTimestamp(event: any) {
    return event.fueled_at || event.timestamp || event.created_at;
  }

  function formatPricePerLiter(event: any) {
    return (
      event.price_per_liter_calculated ||
      (event.liters ? event.total_price / event.liters : 0)
    ).toFixed(3);
  }

  function formatMileage(value: number) {
    return value.toLocaleString("de-DE");
  }

  function openCreateForm() {
    editingEvent = null;
    showForm = true;
  }

  function openEditForm(event: any) {
    editingEvent = event;
    showForm = true;
  }

  function closeForm() {
    showForm = false;
    editingEvent = null;
  }

  function getPreviousMileage(eventId: number): number | null {
    const index = events.findIndex((event) => event.id === eventId);
    if (index === -1) return null;
    return events[index + 1]?.mileage ?? null;
  }

  function getNextMileage(eventId: number): number | null {
    const index = events.findIndex((event) => event.id === eventId);
    if (index === -1) return null;
    return events[index - 1]?.mileage ?? null;
  }

  let dialogEl = $state<HTMLDialogElement | null>(null);

  $effect(() => {
    if (!dialogEl) return;
    if (showForm) {
      dialogEl.showModal();
    } else {
      dialogEl.close();
    }
  });
</script>

<div class="space-y-6">
  <div class="flex items-center justify-between gap-3">
    {#if cars.length > 1}
      <div class="dropdown">
        <button
          type="button"
          tabindex="0"
          class="btn btn-ghost btn-sm px-0 min-h-0 h-auto gap-2 normal-case text-3xl font-black text-base-content tracking-tight hover:outline hover:outline-2 hover:outline-base-content/20"
        >
          {car.name}
          <ChevronDown class="w-5 h-5" />
        </button>
        <ul
          class="menu dropdown-content z-[1] mt-2 w-64 rounded-box bg-base-200 p-2 shadow-xl border border-base-content/10"
        >
          {#each cars as selectableCar (selectableCar.id)}
            {#if selectableCar.id !== car.id}
              <li>
                <button
                  type="button"
                  onclick={() => onSelectCar?.(selectableCar)}
                >
                  <span class="font-semibold">{selectableCar.name}</span>
                </button>
              </li>
            {/if}
          {/each}
        </ul>
      </div>
    {:else}
      <h2 class="text-3xl font-black text-base-content tracking-tight">
        {car.name}
      </h2>
    {/if}

    <button class="btn btn-primary gap-2" onclick={openCreateForm}>
      <Plus class="w-4 h-4" /> Log Refuel
    </button>
  </div>

  <dialog
    bind:this={dialogEl}
    class="modal modal-bottom sm:modal-middle"
    onclose={closeForm}
  >
    <div
      class="modal-box flex max-h-screen flex-col p-0 sm:max-h-[90vh] sm:max-w-3xl"
    >
      <div class="shrink-0 border-b border-base-content/10 px-4 py-4 sm:px-6">
        <div class="flex items-start justify-between gap-4">
          <div>
            <div
              class="text-xs font-semibold uppercase tracking-[0.24em] text-base-content/50"
            >
              {car.name}
            </div>
            <h3
              class="mt-1 text-2xl font-black tracking-tight text-base-content"
            >
              {editingEvent ? "Edit Refuel" : "Log Refuel"}
            </h3>
          </div>
          <button
            type="button"
            class="btn btn-ghost btn-sm"
            onclick={closeForm}
          >
            Close
          </button>
        </div>
      </div>

      <div class="min-h-0 flex-1 overflow-y-auto px-4 py-4 sm:px-6 sm:py-6">
        {#if showForm}
          <RefuelForm
            {car}
            existingEvent={editingEvent}
            previousMileage={editingEvent
              ? getPreviousMileage(editingEvent.id)
              : (events[0]?.mileage ?? null)}
            nextMileage={editingEvent ? getNextMileage(editingEvent.id) : null}
            submitLabel={editingEvent ? "Update Refuel" : "Save Refuel"}
            onSuccess={() => {
              closeForm();
              fetchEvents();
            }}
            onCancel={closeForm}
          />
        {/if}
      </div>
    </div>
    <button class="modal-backdrop" onclick={closeForm} aria-label="Close"
    ></button>
  </dialog>

  {#if loading}
    <div class="flex justify-center py-8">
      <span class="loading loading-spinner text-primary"></span>
    </div>
  {:else}
    <div class="relative">
      {#if events.length > 0}
        <div
          class="absolute bottom-4 left-[0.875rem] top-4 w-px bg-gradient-to-b from-primary/25 via-base-content/10 to-transparent"
        ></div>
      {/if}

      <div class="space-y-3">
        {#each events as e (e.id)}
          <article class="relative pl-8 sm:pl-10">
            <div
              class="absolute left-[0.875rem] top-5 flex h-4 w-4 -translate-x-1/2 items-center justify-center rounded-full border border-primary/25 bg-base-100"
            >
              <div class="h-2 w-2 rounded-full bg-primary/85"></div>
            </div>

            <div
              class="card overflow-hidden rounded-box border border-base-content/10 bg-gradient-to-br from-base-100 via-base-100 to-base-200/70 shadow-none"
            >
              <div class="p-3.5 sm:p-4">
                <div class="flex items-start justify-between gap-4">
                  <div class="min-w-0 flex-1">
                    <div class="flex flex-wrap items-center gap-2">
                      <span
                        class="text-base font-black tracking-tight text-base-content sm:text-lg"
                      >
                        {formatEventDate(getEventTimestamp(e))}
                      </span>
                      {formatEventTime(getEventTimestamp(e))}
                    </div>

                    <div
                      class="mt-2 flex flex-wrap gap-1.5 text-[11px] sm:text-xs"
                    >
                      {#if e.consumption}
                        <span class="badge badge-info badge-outline">
                          {e.consumption.toFixed(2)} L/100km
                        </span>
                      {/if}
                      <span class="badge badge-secondary badge-outline">
                        {formatPricePerLiter(e)} €/L
                      </span>
                      <span class="badge badge-secondary badge-outline">
                        {formatMileage(e.mileage)} km
                      </span>
                      <span class="badge badge-secondary badge-outline">
                        {e.liters} L
                      </span>
                    </div>

                    <div
                      class="mt-1.5 flex items-center gap-1.5 text-[11px] text-base-content/50"
                    >
                      <MapPin class="h-3 w-3 shrink-0" />
                      <span class="truncate"
                        >{formatFuelStation(e.fuel_station)}</span
                      >
                    </div>
                  </div>

                  <div class="flex flex-col items-end gap-2">
                    <div class="text-right">
                      <div
                        class="text-lg font-black tracking-tight text-base-content sm:text-xl"
                      >
                        {e.total_price.toFixed(2)}€
                      </div>
                    </div>

                    <button
                      type="button"
                      class="btn btn-ghost btn-sm h-8 min-h-8 gap-2 rounded-full border border-base-content/10 bg-base-100/70 px-3"
                      onclick={() => openEditForm(e)}
                    >
                      <PencilLine class="w-4 h-4" /> Edit
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </article>
        {:else}
          <div
            class="text-center py-16 bg-base-200/50 rounded-box border-2 border-dashed border-base-content/10"
          >
            <History class="w-16 h-16 mx-auto text-base-content/10 mb-4" />
            <p class="text-base-content/40 font-medium">No refuel logs yet.</p>
            <p class="text-xs text-base-content/30 mt-1">
              Tap "Log Refuel" to add your first entry.
            </p>
          </div>
        {/each}
      </div>
    </div>
  {/if}
</div>
