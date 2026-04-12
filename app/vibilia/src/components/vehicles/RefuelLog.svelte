<script lang="ts">
  import dayjs from "dayjs";
  import {
    ChevronDown,
    History,
    MapPin,
    PencilLine,
    Plus,
    ReceiptText,
  } from "lucide-svelte";
  import {
    fetchCarExpensesForCar,
    fetchRefuelEventsForCar,
  } from "../../lib/data";
  import ExpenseForm from "./ExpenseForm.svelte";
  import RefuelForm from "./RefuelForm.svelte";

  interface Props {
    car: any;
    cars?: any[];
    onSelectCar?: (car: any) => void;
  }

  let { car, cars = [], onSelectCar }: Props = $props();
  let events = $state<any[]>([]);
  let refuelEvents = $state<any[]>([]);
  let loading = $state(true);
  let showForm = $state(false);
  let formType = $state<"refuel" | "expense">("refuel");
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
    const [refuels, expenses] = await Promise.all([
      fetchRefuelEventsForCar(car.id),
      fetchCarExpensesForCar(car.id),
    ]);

    // Calculate interval consumption using fuel level deltas so partial refuels are handled correctly.
    refuelEvents = refuels.map((e: any, i: number) => {
      let consumption = null;
      if (e.missed_previous_refuel) {
        return { ...e, consumption: null };
      }

      const next = refuels[i + 1]; // next in terms of historical order (earlier event)
      if (next) {
        const currentMileage = Number(e.mileage);
        const previousMileage = Number(next.mileage);
        const km = currentMileage - previousMileage;

        if (Number.isFinite(km) && km > 0) {
          const litersFilled = Number(e.liters);
          const currentFuelLevelAfter = Number(e.fuel_level_after);
          const previousFuelLevelAfter = Number(next.fuel_level_after);

          // fuelUsed = litersFilled + previousFuelAfter - currentFuelAfter
          let fuelUsed = litersFilled;
          if (
            Number.isFinite(previousFuelLevelAfter) &&
            Number.isFinite(currentFuelLevelAfter)
          ) {
            fuelUsed =
              litersFilled + previousFuelLevelAfter - currentFuelLevelAfter;
          }

          if (Number.isFinite(fuelUsed) && fuelUsed > 0) {
            consumption = (fuelUsed / km) * 100;
          }
        }
      }

      return { ...e, consumption };
    });

    events = [
      ...refuelEvents.map((entry) => ({
        ...entry,
        entryType: "refuel" as const,
      })),
      ...expenses.map((entry: any) => ({
        ...entry,
        entryType: "expense" as const,
      })),
    ].sort(
      (a, b) =>
        dayjs(getEventTimestamp(b)).valueOf() -
        dayjs(getEventTimestamp(a)).valueOf(),
    );

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
    if (event.entryType === "expense") {
      return event.expensed_at || event.created_at;
    }
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
    formType = "refuel";
    editingEvent = null;
    showForm = true;
  }

  function openExpenseForm() {
    formType = "expense";
    editingEvent = null;
    showForm = true;
  }

  function openEditForm(event: any) {
    formType = "refuel";
    editingEvent = event;
    showForm = true;
  }

  function openEditExpenseForm(event: any) {
    formType = "expense";
    editingEvent = event;
    showForm = true;
  }

  function closeForm() {
    showForm = false;
    editingEvent = null;
  }

  function getPreviousMileage(eventId: number): number | null {
    const index = refuelEvents.findIndex((event) => event.id === eventId);
    if (index === -1) return null;
    return refuelEvents[index + 1]?.mileage ?? null;
  }

  function getNextMileage(eventId: number): number | null {
    const index = refuelEvents.findIndex((event) => event.id === eventId);
    if (index === -1) return null;
    return refuelEvents[index - 1]?.mileage ?? null;
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
      <div class="flex flex-col items-start gap-1">
        {#if car.plate}
          <span
            class="inline-flex items-center rounded border-2 border-base-content/60 bg-base-100 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content"
          >
            {car.plate}
          </span>
        {/if}
        <div class="dropdown">
          <button
            type="button"
            tabindex="0"
            class="btn btn-ghost btn-sm px-0 min-h-0 h-auto normal-case hover:outline hover:outline-2 hover:outline-base-content/20"
          >
            <span
              class="flex items-center gap-2 text-3xl font-black tracking-tight text-base-content"
            >
              {car.name}
              <ChevronDown class="w-5 h-5" />
            </span>
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
      </div>
    {:else}
      <div class="flex flex-col items-start gap-1">
        {#if car.plate}
          <span
            class="inline-flex items-center rounded border-2 border-base-content/60 bg-base-100 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content"
          >
            {car.plate}
          </span>
        {/if}
        <h2 class="text-3xl font-black text-base-content tracking-tight">
          {car.name}
        </h2>
      </div>
    {/if}

    <div class="flex items-center gap-2">
      <button class="btn btn-outline gap-2" onclick={openExpenseForm}>
        <ReceiptText class="w-4 h-4" /> Log Expense
      </button>
      <button class="btn btn-primary gap-2" onclick={openCreateForm}>
        <Plus class="w-4 h-4" /> Log Refuel
      </button>
    </div>
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
              {car.plate ? `${car.plate} • ${car.name}` : car.name}
            </div>
            <h3
              class="mt-1 text-2xl font-black tracking-tight text-base-content"
            >
              {formType === "expense"
                ? editingEvent
                  ? "Edit Expense"
                  : "Log Expense"
                : editingEvent
                  ? "Edit Refuel"
                  : "Log Refuel"}
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
          {#if formType === "refuel"}
            <RefuelForm
              {car}
              existingEvent={editingEvent}
              previousMileage={editingEvent
                ? getPreviousMileage(editingEvent.id)
                : (refuelEvents[0]?.mileage ?? null)}
              nextMileage={editingEvent
                ? getNextMileage(editingEvent.id)
                : null}
              submitLabel={editingEvent ? "Update Refuel" : "Save Refuel"}
              onSuccess={() => {
                closeForm();
                fetchEvents();
              }}
              onCancel={closeForm}
            />
          {:else}
            <ExpenseForm
              {car}
              existingExpense={editingEvent}
              submitLabel={editingEvent ? "Update Expense" : "Save Expense"}
              onSuccess={() => {
                closeForm();
                fetchEvents();
              }}
              onCancel={closeForm}
            />
          {/if}
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
        {#each events as e (`${e.entryType}-${e.id}`)}
          <article class="relative pl-8 sm:pl-10">
            <div
              class="absolute left-[0.875rem] top-[0.5rem] flex h-4 w-4 -translate-x-1/2 items-center justify-center rounded-full border border-primary/25 bg-base-100"
            >
              <div
                class="h-2 w-2 rounded-full {e.entryType === 'expense'
                  ? 'bg-secondary/85'
                  : 'bg-primary/85'}"
              ></div>
            </div>

            <div class="pb-4 pt-1 sm:pb-5">
              <div class="flex items-center justify-between gap-3">
                <div
                  class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-sm text-base-content/80"
                >
                  <span class="font-semibold text-base-content sm:text-base">
                    {formatEventDate(getEventTimestamp(e))}
                  </span>
                  <span class="text-base-content/40">•</span>
                  <span>{formatEventTime(getEventTimestamp(e))}</span>
                </div>

                {#if e.entryType === "refuel"}
                  <button
                    type="button"
                    class="btn btn-ghost btn-xs gap-1.5 rounded-full border border-base-content/10 px-2.5 text-base-content/70 hover:text-base-content"
                    onclick={() => openEditForm(e)}
                  >
                    <PencilLine class="h-3.5 w-3.5" /> Edit
                  </button>
                {:else}
                  <button
                    type="button"
                    class="btn btn-ghost btn-xs gap-1.5 rounded-full border border-base-content/10 px-2.5 text-base-content/70 hover:text-base-content"
                    onclick={() => openEditExpenseForm(e)}
                  >
                    <PencilLine class="h-3.5 w-3.5" /> Edit
                  </button>
                {/if}
              </div>

              {#if e.entryType === "refuel"}
                <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div
                    class="rounded-xl border border-success/20 bg-success/5 px-3.5 py-3"
                  >
                    <div
                      class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
                    >
                      Spent
                    </div>
                    <div
                      class="mt-1 text-3xl font-black tracking-tight text-success sm:text-[2rem]"
                    >
                      {e.total_price.toFixed(2)}€
                    </div>
                  </div>

                  <div
                    class="rounded-xl border border-info/20 bg-info/5 px-3.5 py-3"
                  >
                    <div
                      class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
                    >
                      Consumption
                    </div>
                    <div
                      class="mt-1 text-2xl font-black tracking-tight text-info sm:text-[1.7rem]"
                    >
                      {#if e.consumption}
                        {e.consumption.toFixed(2)}
                        <span class="text-base font-bold">L/100km</span>
                      {:else}
                        <span class="text-base-content/35">—</span>
                      {/if}
                    </div>
                  </div>
                </div>

                <div
                  class="mt-4 rounded-xl border border-base-content/10 bg-base-200/35 p-3"
                >
                  <div
                    class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-4"
                  >
                    <div>
                      <div
                        class="text-[11px] uppercase tracking-[0.12em] text-base-content/50"
                      >
                        Mileage
                      </div>
                      <div class="mt-0.5 font-semibold text-base-content">
                        {formatMileage(e.mileage)} km
                      </div>
                    </div>
                    <div>
                      <div
                        class="text-[11px] uppercase tracking-[0.12em] text-base-content/50"
                      >
                        Amount
                      </div>
                      <div class="mt-0.5 font-semibold text-base-content">
                        {e.liters} L
                      </div>
                    </div>
                    <div>
                      <div
                        class="text-[11px] uppercase tracking-[0.12em] text-base-content/50"
                      >
                        Price per liter
                      </div>
                      <div class="mt-0.5 font-semibold text-base-content">
                        {formatPricePerLiter(e)} €/L
                      </div>
                    </div>
                    <div>
                      <div
                        class="text-[11px] uppercase tracking-[0.12em] text-base-content/50"
                      >
                        Fuel station
                      </div>
                      <div
                        class="mt-0.5 flex items-center gap-1.5 text-base-content/80"
                      >
                        <MapPin
                          class="h-3.5 w-3.5 shrink-0 text-base-content/50"
                        />
                        <span class="truncate"
                          >{formatFuelStation(e.fuel_station)}</span
                        >
                      </div>
                    </div>
                  </div>
                </div>
              {:else}
                <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div
                    class="rounded-xl border border-secondary/20 bg-secondary/5 px-3.5 py-3"
                  >
                    <div
                      class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
                    >
                      Expense
                    </div>
                    <div
                      class="mt-1 text-3xl font-black tracking-tight text-secondary sm:text-[2rem]"
                    >
                      {Number(e.amount).toFixed(2)}€
                    </div>
                  </div>

                  <div
                    class="rounded-xl border border-base-content/15 bg-base-200/35 px-3.5 py-3"
                  >
                    <div
                      class="text-[11px] font-semibold uppercase tracking-[0.14em] text-base-content/55"
                    >
                      Title
                    </div>
                    <div
                      class="mt-1 text-xl font-black tracking-tight text-base-content"
                    >
                      {e.title}
                    </div>
                  </div>
                </div>

                <div
                  class="mt-4 rounded-xl border border-base-content/10 bg-base-200/35 p-3"
                >
                  <div
                    class="grid grid-cols-2 gap-x-4 gap-y-2 text-sm sm:grid-cols-4"
                  >
                    <div>
                      <div
                        class="text-[11px] uppercase tracking-[0.12em] text-base-content/50"
                      >
                        Mileage
                      </div>
                      <div class="mt-0.5 font-semibold text-base-content">
                        {e.mileage ? `${formatMileage(e.mileage)} km` : "—"}
                      </div>
                    </div>
                    <div>
                      <div
                        class="text-[11px] uppercase tracking-[0.12em] text-base-content/50"
                      >
                        Category
                      </div>
                      <div class="mt-0.5 font-semibold text-base-content">
                        {e.category || "Other"}
                      </div>
                    </div>
                    <div class="col-span-2 sm:col-span-2">
                      <div
                        class="text-[11px] uppercase tracking-[0.12em] text-base-content/50"
                      >
                        Notes
                      </div>
                      <div class="mt-0.5 font-semibold text-base-content/80">
                        {e.notes || "—"}
                      </div>
                    </div>
                  </div>
                </div>
              {/if}
            </div>
          </article>
        {:else}
          <div
            class="text-center py-16 bg-base-200/50 rounded-box border-2 border-dashed border-base-content/10"
          >
            <History class="w-16 h-16 mx-auto text-base-content/10 mb-4" />
            <p class="text-base-content/40 font-medium">No entries yet.</p>
            <p class="text-xs text-base-content/30 mt-1">
              Tap "Log Refuel" or "Log Expense" to add your first entry.
            </p>
          </div>
        {/each}
      </div>
    </div>
  {/if}
</div>
