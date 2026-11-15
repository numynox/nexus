<script lang="ts">
  import { ThemePicker } from "@nexus/ui";
  import { ChevronDown, CircleAlert, CircleCheck, CreditCard, DoorOpen, Info, LogOut, Palette, Plus, User, UserPlus, Users } from "lucide-svelte";
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
  import Banner from "../common/Banner.svelte";
  import SectionHeader from "../common/SectionHeader.svelte";
  import StatusChip from "../common/StatusChip.svelte";
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
  let themeOpen = $state(false);
  const themeLabel = $derived(
    currentTheme === "auto" ? "Auto" : (ABEONA_THEMES.themes?.find((t) => t.name === currentTheme)?.label ?? currentTheme),
  );

  /** The explanation that is open, by its setting; they sit behind the ⓘ of their row. */
  let info = $state<string | null>(null);
  const row = "flex min-h-12 items-center gap-3 px-4";

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

{#snippet infoButton(key: string, about: string)}
  <button
    type="button"
    class="btn btn-circle btn-ghost btn-sm text-muted"
    aria-label="About {about}"
    aria-expanded={info === key}
    onclick={() => (info = info === key ? null : key)}
  >
    <Info class="h-5 w-5" aria-hidden="true" />
  </button>
{/snippet}

<!-- Groups in the order of the moments (abeona-design.md §9); a group the web has no setting for is left out. -->
<div class="mx-auto max-w-2xl space-y-3">
  <h1 class="t-headline px-1 pt-2 text-ink">Settings</h1>

  {#if error}
    <Banner tone="bad" icon={CircleAlert} title={error} />
  {/if}

  <section class="scroll-mt-6 space-y-2" id="device">
    <SectionHeader title="Before the trip" />
    <div class="rounded-3xl bg-card shadow-card">
      <div class={row}>
        <DoorOpen class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
        <label class="t-body min-w-0 flex-1 truncate text-ink" for="station-minutes">Time to the station</label>
        {@render infoButton("station", "the time to the station")}
        <select
          id="station-minutes"
          class="select select-sm tabular h-9 w-24 rounded-xl border-transparent bg-well text-ink"
          value={stationMinutes}
          onchange={(e) => chooseStationMinutes(Number(e.currentTarget.value))}
        >
          {#each STATION_MINUTES_CHOICES as minutes (minutes)}
            <option value={minutes}>{minutes} min</option>
          {/each}
        </select>
      </div>
      {#if info === "station"}
        <p class="t-meta px-4 pb-3">From your door to the platform. On the day of a trip, the trip shows when to leave. Stored on this device only.</p>
      {/if}
    </div>
  </section>

  <section class="space-y-2">
    <SectionHeader title="Household" />

    <div class="divide-y divide-line rounded-3xl bg-card shadow-card">
      <div>
        <div class={row}>
          <Users class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
          <h3 class="t-body flex-1 font-semibold text-ink">Members</h3>
          {@render infoButton("household", "the household")}
        </div>
        {#if info === "household"}
          <p class="t-meta px-4 pb-3">
            Everyone here sees and edits the same trips and tickets. Someone you invite needs a Nexus account. If they have trips of their own and nobody else in their household, those come along.
          </p>
        {/if}
      </div>
      {#if loading}
        <div class="m-4 h-10 animate-pulse rounded-2xl bg-well"></div>
      {:else}
        {#each members as m (m.user_id)}
          <div class={row}>
            <span class="t-meta flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-well font-semibold text-ink" aria-hidden="true">
              {initials(m.full_name || m.email || "?")}
            </span>
            <div class="min-w-0 flex-1">
              <p class="t-body truncate text-ink">{m.full_name || m.email}{m.user_id === me ? " (you)" : ""}</p>
              {#if m.full_name && m.email}<p class="t-meta truncate">{m.email}</p>{/if}
            </div>
            {#if m.role === "owner"}<StatusChip tone="quiet" label="Owner" />{/if}
          </div>
        {/each}
      {/if}
      <div>
        <form class="{row} py-2" onsubmit={invite}>
          <input type="email" class="input t-body h-10 min-w-0 flex-1 rounded-xl border-transparent bg-well" placeholder="Invite by email" aria-label="Invite by email" bind:value={inviteEmail} required />
          <button class="btn h-10 rounded-full border-transparent bg-well px-4 text-ink" disabled={inviting}>
            {#if inviting}<span class="loading loading-spinner loading-sm"></span>{:else}<UserPlus class="h-5 w-5" aria-hidden="true" />{/if}
            Invite
          </button>
        </form>
        {#if inviteMessage}<Banner tone="ok" icon={CircleCheck} title={inviteMessage} class="mx-4 mb-3" />{/if}
        {#if inviteError}<Banner tone="bad" icon={CircleAlert} title={inviteError} class="mx-4 mb-3" />{/if}
      </div>
      {#if members.length > 1}
        <button class="{row} w-full text-left text-bad-ink" onclick={leave}>
          <LogOut class="h-5 w-5 shrink-0" aria-hidden="true" />
          <span class="t-body">Leave household</span>
        </button>
      {/if}
    </div>

    <div class="scroll-mt-6 rounded-3xl bg-card shadow-card" id="bahncards">
      <div class={row}>
        <CreditCard class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
        <h3 class="t-body flex-1 font-semibold text-ink">BahnCards</h3>
        {@render infoButton("bahncards", "BahnCards")}
        <button class="btn btn-ghost btn-sm h-9 rounded-full px-3 text-brand" onclick={() => openCard(null)}>
          <Plus class="h-5 w-5" aria-hidden="true" /> Add
        </button>
      </div>
      {#if info === "bahncards"}
        <p class="t-meta px-4 pb-3">
          The household's cards. Abeona shows them with the tickets they were booked with and reminds you before a
          subscription renews. The digital BahnCard only counts as proof inside the DB Navigator app, so keep that at hand
          on the train.
        </p>
      {/if}
      {#if loading}
        <div class="m-4 mt-0 h-10 animate-pulse rounded-2xl bg-well"></div>
      {:else if cards.length === 0}
        <p class="t-meta px-4 pb-4">No BahnCard yet.</p>
      {:else}
        <div class="divide-y divide-line border-t border-line pr-1 pl-4">
          {#each cards as card (card.id)}
            <BahnCardItem {card} onShow={() => (shownCard = card)} onEdit={() => openCard(card)} />
          {/each}
        </div>
      {/if}
    </div>
  </section>

  <section class="space-y-2">
    <SectionHeader title="App" />
    <div class="divide-y divide-line rounded-3xl bg-card shadow-card">
      <div>
        <button class="{row} w-full text-left" aria-expanded={themeOpen} onclick={() => (themeOpen = !themeOpen)}>
          <Palette class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
          <span class="t-body flex-1 text-ink">Theme</span>
          <span class="t-meta">{themeLabel}</span>
          <ChevronDown class="h-5 w-5 shrink-0 text-muted transition-transform {themeOpen ? 'rotate-180' : ''}" aria-hidden="true" />
        </button>
        {#if themeOpen}
          <div class="space-y-2 px-4 pb-4">
            <ThemePicker current={currentTheme} onSelect={chooseTheme} set={ABEONA_THEMES} />
            <p class="t-meta">Stored on this device only.</p>
          </div>
        {/if}
      </div>
      <div class={row}>
        <User class="h-5 w-5 shrink-0 text-muted" aria-hidden="true" />
        <span class="sr-only">Signed in as</span>
        <span class="t-body min-w-0 flex-1 truncate text-ink">{$session?.user?.email ?? ""}</span>
        <button class="btn btn-ghost btn-sm h-9 rounded-full px-3 text-brand" onclick={logout}>
          <LogOut class="h-5 w-5" aria-hidden="true" /> Sign out
        </button>
      </div>
    </div>
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

  <footer class="t-meta px-2 pb-4 text-center text-xs">
    Timetable data: Transitous (transitous.org), © OpenStreetMap contributors, and the respective transit operators.
  </footer>
</div>
