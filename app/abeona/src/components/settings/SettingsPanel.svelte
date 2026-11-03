<script lang="ts">
  import { ThemePicker } from "@nexus/ui";
  import { Check, CreditCard, DoorOpen, LogOut, Palette, Pencil, Plus, Trash2, UserPlus, Users, X } from "lucide-svelte";
  import { onMount } from "svelte";
  import {
    addTraveller,
    deleteTraveller,
    describeError,
    fetchBahnCards,
    fetchTravellers,
    inviteToHousehold,
    leaveHousehold,
    listHouseholdMembers,
    renameTraveller,
    signOut,
    type BahnCard,
    type HouseholdMember,
    type Traveller,
  } from "../../lib/data";
  import { bahnCardLabel } from "../../lib/format";
  import BahnCardItem from "../bahncards/BahnCardItem.svelte";
  import BahnCardSheet from "../bahncards/BahnCardSheet.svelte";
  import TicketViewer from "../trip/TicketViewer.svelte";
  import { DB_NAVIGATOR_HOME } from "../../lib/journey";
  import { session } from "../../lib/stores";
  import { getStationMinutes, getTheme, setStationMinutes, setTheme, STATION_MINUTES_CHOICES } from "../../lib/storage";
  import { ABEONA_THEMES } from "../../lib/themes";

  let stationMinutes = $state(getStationMinutes());

  function chooseStationMinutes(minutes: number) {
    stationMinutes = minutes;
    setStationMinutes(minutes);
  }

  let members = $state<HouseholdMember[]>([]);
  let travellers = $state<Traveller[]>([]);
  let loading = $state(true);
  let error = $state("");

  let inviteEmail = $state("");
  let inviting = $state(false);
  let inviteMessage = $state("");
  let inviteError = $state("");

  let newTraveller = $state("");
  let editingId = $state<string | null>(null);
  let editingName = $state("");
  let travellerError = $state("");

  let cards = $state<BahnCard[]>([]);
  let cardTraveller = $state<Traveller | null>(null);
  let editingCard = $state<BahnCard | null>(null);
  let shownCard = $state<{ card: BahnCard; name: string } | null>(null);

  let currentTheme = $state(getTheme());

  /** Each traveller's cards, the one running longest first. */
  const cardsByTraveller = $derived.by(() => {
    const map = new Map<string, BahnCard[]>();
    for (const c of cards) map.set(c.traveller_id, [...(map.get(c.traveller_id) ?? []), c]);
    return map;
  });

  function openCard(traveller: Traveller, card: BahnCard | null) {
    editingCard = card;
    cardTraveller = traveller;
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
      [members, travellers, cards] = await Promise.all([listHouseholdMembers(), fetchTravellers(), fetchBahnCards()]);
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

  async function add(event: Event) {
    event.preventDefault();
    if (!newTraveller.trim()) return;
    travellerError = "";
    try {
      travellers = [...travellers, await addTraveller(newTraveller)];
      newTraveller = "";
    } catch (e) {
      travellerError = describeError(e);
    }
  }

  async function saveRename(t: Traveller) {
    const name = editingName.trim();
    if (!name || name === t.name) {
      editingId = null;
      return;
    }
    travellerError = "";
    try {
      await renameTraveller(t.id, name);
      travellers = travellers.map((x) => (x.id === t.id ? { ...x, name } : x));
      editingId = null;
    } catch (e) {
      travellerError = describeError(e);
    }
  }

  async function remove(t: Traveller) {
    const owned = cardsByTraveller.get(t.id)?.length ?? 0;
    const extra = owned > 0 ? ` Their ${owned === 1 ? "BahnCard goes" : `${owned} BahnCards go`} with them.` : "";
    if (!confirm(`Remove ${t.name} from the travellers?${extra}`)) return;
    travellerError = "";
    try {
      await deleteTraveller(t.id);
      travellers = travellers.filter((x) => x.id !== t.id);
      cards = cards.filter((c) => c.traveller_id !== t.id);
    } catch (e) {
      travellerError = describeError(e);
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

  <section class="rounded-[2rem] bg-card p-6 shadow-card sm:p-8">
    <h2 class="text-lg font-semibold text-ink">Travellers</h2>
    <p class="mt-1 text-sm text-base-content/60">The people who ride with you — with or without an account.</p>

    <ul class="mt-5 space-y-2">
      {#each travellers as t (t.id)}
        <li class="flex items-center gap-3 rounded-2xl bg-base-100 px-3 py-2">
          <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-sky text-sm font-semibold text-ink">{initials(t.name)}</span>
          {#if editingId === t.id}
            <input
              class="input input-sm flex-1 rounded-full"
              bind:value={editingName}
              onkeydown={(e) => {
                if (e.key === "Enter") void saveRename(t);
                if (e.key === "Escape") editingId = null;
              }}
            />
            <button class="btn btn-ghost btn-circle btn-sm text-primary" aria-label="Save name" onclick={() => saveRename(t)}><Check class="h-4 w-4" /></button>
            <button class="btn btn-ghost btn-circle btn-sm" aria-label="Cancel" onclick={() => (editingId = null)}><X class="h-4 w-4" /></button>
          {:else}
            <span class="min-w-0 flex-1 truncate font-medium">{t.name}</span>
            {#if t.user_id}<span class="text-xs text-base-content/45">account</span>{/if}
            <button class="btn btn-ghost btn-circle btn-sm" aria-label="Rename {t.name}" onclick={() => { editingId = t.id; editingName = t.name; }}>
              <Pencil class="h-4 w-4" />
            </button>
            <button class="btn btn-ghost btn-circle btn-sm text-error" aria-label="Remove {t.name}" onclick={() => remove(t)}>
              <Trash2 class="h-4 w-4" />
            </button>
          {/if}
        </li>
      {/each}
    </ul>

    <form class="mt-4 flex gap-2" onsubmit={add}>
      <input class="input flex-1 rounded-full bg-base-100" placeholder="Name, e.g. Mia" bind:value={newTraveller} maxlength="60" />
      <button class="btn btn-ghost rounded-full text-primary" disabled={!newTraveller.trim()}><Plus class="h-4 w-4" /> Add</button>
    </form>
    {#if travellerError}<p class="mt-2 text-sm text-error" role="alert">{travellerError}</p>{/if}
  </section>

  <section class="rounded-[2rem] bg-card p-6 shadow-card sm:p-8" id="bahncards">
    <div class="flex items-center gap-2">
      <CreditCard class="h-5 w-5 text-primary" />
      <h2 class="text-lg font-semibold text-ink">BahnCards</h2>
    </div>
    <p class="mt-1 text-sm text-base-content/60">
      Abeona checks them against the trips and reminds you before a subscription renews. The digital BahnCard only
      counts as proof inside the DB Navigator app, so keep that at hand on the train.
    </p>

    {#if loading}
      <div class="mt-5 h-24 animate-pulse rounded-2xl bg-base-200"></div>
    {:else}
      <div class="mt-5 space-y-5">
        {#each travellers as t (t.id)}
          {@const owned = cardsByTraveller.get(t.id) ?? []}
          <div>
            <div class="flex items-center justify-between gap-3 px-1">
              <h3 class="font-medium text-ink">{t.name}</h3>
              <button class="btn btn-ghost btn-sm rounded-full text-primary" onclick={() => openCard(t, null)}>
                <Plus class="h-4 w-4" /> Add BahnCard
              </button>
            </div>
            {#if owned.length === 0}
              <p class="px-1 text-sm text-base-content/50">No BahnCard</p>
            {:else}
              <div class="mt-1 space-y-2">
                {#each owned as card (card.id)}
                  <BahnCardItem
                    {card}
                    onShow={() => (shownCard = { card, name: t.name })}
                    onEdit={() => openCard(t, card)}
                  />
                {/each}
              </div>
            {/if}
          </div>
        {/each}
      </div>
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
    traveller={cardTraveller}
    card={editingCard}
    onClose={() => { cardTraveller = null; editingCard = null; }}
    onSaved={cardSaved}
    onDeleted={(id) => (cards = cards.filter((c) => c.id !== id))}
  />

  {#if shownCard?.card.file_path}
    <TicketViewer
      path={shownCard.card.file_path}
      title={`${shownCard.name} · ${bahnCardLabel(shownCard.card)}`}
      navigatorUrl={DB_NAVIGATOR_HOME}
      label="BahnCard"
      note="This copy is for your reference. The digital BahnCard only counts as proof inside the DB Navigator app."
      onClose={() => (shownCard = null)}
    />
  {/if}

  <footer class="px-2 pb-4 text-center text-xs leading-relaxed text-base-content/45">
    Timetable data: Transitous (transitous.org), © OpenStreetMap contributors, and the respective transit operators.
  </footer>
</div>
