<script lang="ts">
  import { CalendarClock, FileText, Gauge, Trash2 } from "lucide-svelte";
  import {
    createOdometerReading,
    deleteOdometerReading,
    describeError,
    updateOdometerReading,
    type OdometerReading,
  } from "../../lib/data";

  /**
   * A dated odometer value without a cost — from a service record, say. It
   * extends the odometer chart and changes no statistics. Mirrors
   * OdometerReadingEditor.kt.
   */
  let {
    car,
    existingReading = null,
    onSuccess,
    onCancel,
  }: {
    car: any;
    existingReading?: OdometerReading | null;
    onSuccess: () => void;
    onCancel: () => void;
  } = $props();

  function formatDateTimeLocal(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours(),
    )}:${pad(date.getMinutes())}`;
  }

  let readAtLocal = $state(formatDateTimeLocal(new Date()));
  let mileage = $state<number | null>(null);
  let notes = $state("");

  // The form is created per open, so the reading it edits never changes.
  function applyInitialValues() {
    if (!existingReading) return;
    readAtLocal = formatDateTimeLocal(new Date(existingReading.read_at));
    mileage = existingReading.mileage;
    notes = existingReading.notes ?? "";
  }

  applyInitialValues();
  let loading = $state(false);
  let submitted = $state(false);

  const mileageError = $derived(
    !submitted && mileage === null
      ? null
      : mileage === null || !Number.isInteger(mileage) || mileage < 0
        ? "Enter the odometer value in whole kilometres."
        : mileage >= 1_000_000
          ? "Mileage must be below 1,000,000 km."
          : null,
  );

  const notesError = $derived(
    notes.trim().length > 500 ? "Notes must be at most 500 characters." : null,
  );

  const formIsValid = $derived(
    mileage !== null &&
      Number.isInteger(mileage) &&
      mileage >= 0 &&
      mileage < 1_000_000 &&
      notes.trim().length <= 500 &&
      readAtLocal.length > 0,
  );

  async function handleSubmit(event: SubmitEvent) {
    event.preventDefault();
    submitted = true;
    if (!formIsValid || mileage === null) return;

    loading = true;
    try {
      const payload = {
        read_at: new Date(readAtLocal).toISOString(),
        mileage,
        notes: notes.trim() || null,
      };
      if (existingReading) {
        await updateOdometerReading(existingReading.id, payload);
      } else {
        await createOdometerReading(car.id, payload);
      }
      onSuccess();
    } catch (error) {
      alert(describeError(error));
    } finally {
      loading = false;
    }
  }

  async function handleDelete() {
    if (!existingReading) return;
    if (!confirm("Delete this odometer reading?")) return;

    loading = true;
    try {
      await deleteOdometerReading(existingReading.id);
      onSuccess();
    } catch (error) {
      alert(describeError(error));
    } finally {
      loading = false;
    }
  }
</script>

<form onsubmit={handleSubmit} class="space-y-5">
  <p class="text-sm text-base-content/70">
    An odometer value without a cost, for example from a service record from
    before you had the car. It appears on the odometer chart and changes no
    statistics.
  </p>

  <div class="grid grid-cols-2 gap-4">
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="readingMileage">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <Gauge class="w-4 h-4 text-warning" />
          Mileage (km)
        </span>
      </label>
      <input
        id="readingMileage"
        type="number"
        min="0"
        step="1"
        bind:value={mileage}
        class="input input-bordered input-sm w-full {mileageError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if mileageError}
        <p class="text-xs text-error mt-1">{mileageError}</p>
      {/if}
    </div>

    <div class="form-control w-full">
      <label class="label py-2 px-0" for="readingDate">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <CalendarClock class="w-4 h-4 text-info" />
          Date
        </span>
      </label>
      <input
        id="readingDate"
        type="datetime-local"
        bind:value={readAtLocal}
        class="input input-bordered input-sm w-full focus:input-primary"
        required
      />
    </div>

    <div class="form-control col-span-2 w-full">
      <label class="label py-2 px-0" for="readingNotes">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <FileText class="w-4 h-4 text-base-content/70" />
          Notes
        </span>
      </label>
      <input
        id="readingNotes"
        type="text"
        bind:value={notes}
        placeholder="Optional, e.g. Service record"
        class="input input-bordered input-sm w-full {notesError
          ? 'input-error'
          : 'focus:input-primary'}"
      />
      {#if notesError}
        <p class="text-xs text-error mt-1">{notesError}</p>
      {/if}
    </div>
  </div>

  <div class="flex gap-2 border-t border-base-200 pt-3">
    {#if existingReading}
      <button
        type="button"
        class="btn btn-ghost btn-sm text-error"
        onclick={handleDelete}
        disabled={loading}
      >
        <Trash2 class="w-4 h-4" /> Delete
      </button>
    {/if}
    <button
      type="button"
      class="btn btn-outline btn-sm flex-1"
      onclick={onCancel}
    >
      Cancel
    </button>
    <button
      type="submit"
      class="btn btn-primary btn-sm flex-1"
      disabled={loading || (submitted && !formIsValid)}
    >
      {#if loading}
        <span class="loading loading-spinner loading-sm"></span>
      {:else}
        <Gauge class="w-4 h-4" />
      {/if}
      {existingReading ? "Update Reading" : "Save Reading"}
    </button>
  </div>
</form>
