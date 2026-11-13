<script lang="ts">
  import { ThemePicker } from "@nexus/ui";
  import { CreditCard, DoorOpen, LogOut, Palette, Plus, UserPlus, Users } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    describeError,
    fetchBahnCards,
    inviteToHousehold,
    leaveHousehold,
    listHouseholdMembers,
    signOut,
    type BahnCard,
    type HouseholdMember,
  } from "../../lib/data";
  import { bahnCardLabel, bahnCardName } from "../../lib/format";
  import BahnCardItem from "../bahncards/BahnCardItem.svelte";
  import BahnCardSheet from "../bahncards/BahnCardSheet.svelte";
  import TicketViewer from "../trip/TicketViewer.svelte";
  import { session } from "../../lib/stores";
  import { getStationMinutes, getTheme, setStationMinutes, setTheme, STATION_MINUTES_CHOICES } from "../../lib/storage";
  import { ABEONA_THEMES } from "../../lib/themes";

  let stationMinutes = $state(getStationMinutes());

  function chooseStationMinutes(minutes: number) {
    stationMinutes = minutes;
    setStationMinutes(minutes);
  }

  let members = $state<HouseholdMember[]>([]);
  let loading = $state(true);
  let error = $state("");

  let inviteEmail = $state("");
  let inviting = $state(false);
  let inviteMessage = $state("");
  let inviteError = $state("");

  let cards = $state<BahnCard[]>([]);
  let cardOpen = $state(false);
  let editingCard = $state<BahnCard | null>(null);
  let shownCard = $state<BahnCard | null>(null);

  let currentTheme = $state(getTheme());

  function openCard(card: BahnCard | null) {
    editingCard = card;
    cardOpen = true;
  }

  function cardSaved(card: BahnCard) {
    cards = cards.some((c) => c.id === card.id) ? cards.map((c) => (c.id === card.id ? card : c)) : [card, ...cards];
    cards = [...cards].sort((a, b) => b.valid_to.localeCompare(a.valid_to));
  }

  const me = $derived($session?.user?.id ?? null);

  function initials(name: string): string {
    return name
      .split(/[\s@.]+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((p) => p[0]!.toUpperCase())
      .join("");
  }

  async function load() {
    loading = true;
    error = "";
    try {
      [members, cards] = await Promise.all([listHouseholdMembers(), fetchBahnCards()]);
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  async function invite(event: Event) {
    event.preventDefault();
    if (!inviteEmail.trim() || inviting) return;
    inviting = true;
    inviteError = "";
    inviteMessage = "";
    try {
      await inviteToHousehold(inviteEmail);
      inviteMessage = `${inviteEmail.trim()} is now in your household.`;
      inviteEmail = "";
      await load();
    } catch (e) {
      inviteError = describeError(e);
    } finally {
      inviting = false;
    }
  }

  async function leave() {
    if (!confirm("Leave this household? Its trips stay with the others, and you start over with an empty household.")) return;
    try {
      await leaveHousehold();
      await load();
    } catch (e) {
      error = describeError(e);
    }
  }

  function chooseTheme(theme: string) {
    currentTheme = theme;
    setTheme(theme);
  }

  async function logout() {
    await signOut().catch(() => {});
    window.location.reload();
  }

  onMount(() => {
    void load();
  });
</script>

<div class="space-y-8">
  <header class="pt-2">
    <h1 class="text-3xl font-semibold tracking-tight text-ink sm:text-4xl">Settings</h1>
  </header>

  {#if error}
    <p class="rounded-2xl bg-error/10 px-5 py-4 text-sm text-error" role="alert">{error}</p>
  {/if}

  <section class="rounded-[2rem] bg-card p-6 shadow-card sm:p-8">
    <div class="flex items-center gap-2">
      <Users class="h-5 w-5 text-primary" />
      <h2 class="text-lg font-semibold text-ink">Household</h2>
    </div>
    <p class="mt-1 text-sm text-base-content/60">Everyone here sees and edits the same trips and tickets.</p>

    {#if loading}
      <div class="mt-5 h-16 animate-pulse rounded-2xl bg-base-200"></div>
    {:else}
      <ul class="mt-5 divide-y divide-base-200">
        {#each members as m (m.user_id)}
          <li class="flex items-center gap-3 py-3">
            <span class="flex h-10 w-10 items-center justify-center rounded-full bg-mint text-sm font-semibold text-primary">
              {initials(m.full_name || m.email || "?")}
            </span>
            <div class="min-w-0 flex-1">
              <p class="truncate font-medium">{m.full_name || m.email}{m.user_id === me ? " (you)" : ""}</p>
              {#if m.full_name && m.email}<p class="truncate text-sm text-base-content/55">{m.email}</p>{/if}
            </div>
            {#if m.role === "owner"}<span class="rounded-full bg-base-200 px-2.5 py-0.5 text-xs font-semibold text-base-content/70">Owner</span>{/if}
          </li>
        {/each}
      </ul>
    {/if}

    <form class="mt-4 flex flex-col gap-2 sm:flex-row" onsubmit={invite}>
      <input type="email" class="input flex-1 rounded-full bg-base-100" placeholder="Invite by email" bind:value={inviteEmail} required />
      <button class="btn btn-primary rounded-full px-5" disabled={inviting}>
        {#if inviting}<span class="loading loading-spinner loading-sm"></span>{:else}<UserPlus class="h-4 w-4" />{/if}
        Invite
      </button>
    </form>
    <p class="mt-2 text-xs text-base-content/55">They need a Nexus account. If they have trips of their own and nobody else in their household, those come along.</p>
    {#if inviteMessage}<p class="mt-2 text-sm text-primary">{inviteMessage}</p>{/if}
    {#if inviteError}<p class="mt-2 text-sm text-error" role="alert">{inviteError}</p>{/if}

    {#if members.length > 1}
      <button class="btn btn-ghost btn-sm mt-4 rounded-full text-error" onclick={leave}>
        <LogOut class="h-4 w-4" /> Leave household
      </button>
    {/if}
  </section>

  <section class="rounded-[2rem] bg-card p-6 shadow-card sm:p-8" id="bahncards">
    <div class="flex items-center gap-2">
      <CreditCard class="h-5 w-5 text-primary" />
      <h2 class="mr-auto text-lg font-semibold text-ink">BahnCards</h2>
      <button class="btn btn-ghost btn-sm rounded-full text-primary" onclick={() => openCard(null)}>
        <Plus class="h-4 w-4" /> Add BahnCard
      </button>
    </div>
    <p class="mt-1 text-sm text-base-content/60">
      The household's cards. Abeona shows them with the tickets they were booked with and reminds you before a
      subscription renews. The digital BahnCard only counts as proof inside the DB Navigator app, so keep that at hand
      on the train.
    </p>

    {#if loading}
      <div class="mt-5 h-24 animate-pulse rounded-2xl bg-base-200"></div>
    {:else}
      {#if cards.length === 0}
        <p class="mt-5 px-1 text-sm text-base-content/50">No BahnCard yet.</p>
      {:else}
        <div class="mt-5 space-y-2">
          {#each cards as card (card.id)}
            <BahnCardItem {card} onShow={() => (shownCard = card)} onEdit={() => openCard(card)} />
          {/each}
        </div>
      {/if}
    {/if}
  </section>

  <section class="scroll-mt-6 rounded-[2rem] bg-card p-6 shadow-card sm:p-8" id="device">
    <div class="flex items-center gap-2">
      <DoorOpen class="h-5 w-5 text-primary" />
      <h2 class="text-lg font-semibold text-ink">Time to the station</h2>
    </div>
    <p class="mt-1 mb-5 text-sm text-base-content/60">
      From your door to the platform. On the day of a trip, the trip shows when to leave. Stored on this device only.
    </p>
    <div class="flex flex-wrap gap-2" role="radiogroup" aria-label="Time to the station">
      {#each STATION_MINUTES_CHOICES as minutes (minutes)}
        <button
          role="radio"
          aria-checked={stationMinutes === minutes}
          class="btn btn-sm rounded-full {stationMinutes === minutes ? 'btn-primary' : 'border-base-300 bg-card'}"
          onclick={() => chooseStationMinutes(minutes)}
        >
          {minutes} min
        </button>
      {/each}
    </div>
  </section>

  <section class="rounded-[2rem] bg-card p-6 shadow-card sm:p-8">
    <div class="flex items-center gap-2">
      <Palette class="h-5 w-5 text-primary" />
      <h2 class="text-lg font-semibold text-ink">Appearance</h2>
    </div>
    <p class="mt-1 mb-5 text-sm text-base-content/60">Stored on this device only.</p>
    <ThemePicker current={currentTheme} onSelect={chooseTheme} set={ABEONA_THEMES} />
  </section>

  <section class="flex flex-wrap items-center justify-between gap-4 rounded-[2rem] bg-card p-6 shadow-card sm:p-8">
    <div class="min-w-0">
      <p class="text-sm text-base-content/60">Signed in as</p>
      <p class="truncate font-medium">{$session?.user?.email ?? ""}</p>
    </div>
    <button class="btn btn-ghost rounded-full" onclick={logout}><LogOut class="h-4 w-4" /> Sign out</button>
  </section>

  <BahnCardSheet
    open={cardOpen}
    card={editingCard}
    onClose={() => { cardOpen = false; editingCard = null; }}
    onSaved={cardSaved}
    onDeleted={(id) => (cards = cards.filter((c) => c.id !== id))}
  />

  {#if shownCard?.file_path}
    <TicketViewer
      path={shownCard.file_path}
      title={shownCard.name ? `${bahnCardName(shownCard)} · ${bahnCardLabel(shownCard)}` : bahnCardLabel(shownCard)}
      label="BahnCard"
      note="This copy is for your reference. The digital BahnCard only counts as proof inside the DB Navigator app."
      onClose={() => (shownCard = null)}
    />
  {/if}

  <footer class="px-2 pb-4 text-center text-xs leading-relaxed text-base-content/45">
    Timetable data: Transitous (transitous.org), © OpenStreetMap contributors, and the respective transit operators.
  </footer>
</div>
