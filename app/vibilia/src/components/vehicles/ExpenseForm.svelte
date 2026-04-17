<script lang="ts">
  import { Banknote, CalendarClock, FileText, Gauge, Tag } from "lucide-svelte";
  import * as dataApi from "../../lib/data";
  import { session } from "../../lib/stores";

  const expenseCategories = [
    "Service",
    "Maintenance",
    "Insurance",
    "Tax",
    "Parking",
    "Toll",
    "Vehicle Wash",
    "Repairs",
    "Accessories",
    "Other",
  ];

  let {
    car,
    existingExpense = null,
    submitLabel = null,
    onSuccess,
    onCancel,
  } = $props<{
    car: any;
    existingExpense?: any | null;
    submitLabel?: string | null;
    onSuccess: () => void;
    onCancel: () => void;
  }>();

  let expensedAtLocal = $state(formatDateTimeLocal(new Date()));
  let title = $state("");
  let amount = $state(0);
  let mileage = $state(0);
  let category = $state("Other");
  let notes = $state("");
  let loading = $state(false);
  let submitted = $state(false);

  function formatDateTimeLocal(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
      date.getHours(),
    )}:${pad(date.getMinutes())}`;
  }

  function applyInitialValues() {
    if (!existingExpense) return;

    expensedAtLocal = formatDateTimeLocal(
      new Date(
        existingExpense.expensed_at || existingExpense.created_at || new Date(),
      ),
    );
    title = existingExpense.title || "";
    amount = Number(existingExpense.amount ?? 0);
    mileage = Number(existingExpense.mileage ?? 0);
    category = existingExpense.category || "Other";
    notes = existingExpense.notes || "";
  }

  applyInitialValues();

  const titleError = $derived(
    !submitted && title.length === 0
      ? null
      : title.trim().length === 0
        ? "Enter a title."
        : title.trim().length > 120
          ? "Title must be at most 120 characters."
          : null,
  );

  const amountError = $derived(
    !submitted && amount === 0
      ? null
      : amount <= 0
        ? "Amount must be greater than 0 €."
        : amount > 100000
          ? "Amount is too large."
          : null,
  );

  const mileageError = $derived(
    !submitted && mileage === 0
      ? null
      : mileage < 0
        ? "Mileage cannot be negative."
        : mileage >= 1_000_000
          ? "Mileage must be below 1,000,000 km."
          : null,
  );

  const formIsValid = $derived(
    title.trim().length > 0 &&
      title.trim().length <= 120 &&
      amount > 0 &&
      amount <= 100000 &&
      mileage >= 0 &&
      mileage < 1_000_000 &&
      expensedAtLocal.length > 0 &&
      category.length > 0,
  );

  async function handleSubmit(event: SubmitEvent) {
    event.preventDefault();
    submitted = true;

    if (!formIsValid) return;

    const userId = $session?.user?.id;
    if (!userId) {
      alert("You must be signed in to add an expense.");
      return;
    }

    loading = true;

    try {
      const payload = {
        expensed_at: new Date(expensedAtLocal).toISOString(),
        title: title.trim(),
        amount,
        mileage,
        category,
        notes: notes.trim() || null,
      };

      if (existingExpense?.id) {
        await (dataApi as any).updateCarExpense(existingExpense.id, payload);
      } else {
        await (dataApi as any).createCarExpense({
          car_id: car.id,
          user_id: userId,
          ...payload,
        });
      }

      onSuccess();
    } catch (error) {
      alert(error instanceof Error ? error.message : String(error));
    } finally {
      loading = false;
    }
  }
</script>

<form onsubmit={handleSubmit} class="space-y-5">
  <div class="grid grid-cols-2 gap-4">
    <div class="form-control w-full">
      <label class="label py-2 px-0" for="expenseTitle">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <FileText class="w-4 h-4 text-primary" />
          Title
        </span>
      </label>
      <input
        id="expenseTitle"
        type="text"
        bind:value={title}
        placeholder="e.g. Inspection"
        class="input input-bordered input-sm w-full {titleError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if titleError}
        <p class="text-xs text-error mt-1">{titleError}</p>
      {/if}
    </div>

    <div class="form-control w-full">
      <label class="label py-2 px-0" for="expenseAmount">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <Banknote class="w-4 h-4 text-success" />
          Amount (€)
        </span>
      </label>
      <input
        id="expenseAmount"
        type="number"
        bind:value={amount}
        step="0.01"
        class="input input-bordered input-sm w-full {amountError
          ? 'input-error'
          : 'focus:input-primary'}"
        required
      />
      {#if amountError}
        <p class="text-xs text-error mt-1">{amountError}</p>
      {/if}
    </div>

    <div class="form-control w-full">
      <label class="label py-2 px-0" for="expenseDate">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <CalendarClock class="w-4 h-4 text-info" />
          Date
        </span>
      </label>
      <input
        id="expenseDate"
        type="datetime-local"
        bind:value={expensedAtLocal}
        class="input input-bordered input-sm w-full focus:input-primary"
        required
      />
    </div>

    <div class="form-control w-full">
      <label class="label py-2 px-0" for="expenseMileage">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <Gauge class="w-4 h-4 text-warning" />
          Mileage (km)
        </span>
      </label>
      <input
        id="expenseMileage"
        type="number"
        bind:value={mileage}
        class="input input-bordered input-sm w-full {mileageError
          ? 'input-error'
          : 'focus:input-primary'}"
      />
      {#if mileageError}
        <p class="text-xs text-error mt-1">{mileageError}</p>
      {/if}
    </div>

    <div class="form-control w-full">
      <label class="label py-2 px-0" for="expenseCategory">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <Tag class="w-4 h-4 text-secondary" />
          Category
        </span>
      </label>
      <select
        id="expenseCategory"
        bind:value={category}
        class="select select-bordered select-sm w-full focus:select-primary"
      >
        {#each expenseCategories as option}
          <option value={option}>{option}</option>
        {/each}
      </select>
    </div>

    <div class="form-control w-full">
      <label class="label py-2 px-0" for="expenseNotes">
        <span
          class="label-text font-semibold text-sm flex items-center gap-2 whitespace-nowrap"
        >
          <FileText class="w-4 h-4 text-base-content/70" />
          Notes
        </span>
      </label>
      <input
        id="expenseNotes"
        type="text"
        bind:value={notes}
        placeholder="Optional"
        class="input input-bordered input-sm w-full focus:input-primary"
      />
    </div>
  </div>

  <div class="flex gap-2 border-t border-base-200 pt-3">
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
        <Banknote class="w-4 h-4" />
      {/if}
      {submitLabel || (existingExpense ? "Update Expense" : "Save Expense")}
    </button>
  </div>
</form>
