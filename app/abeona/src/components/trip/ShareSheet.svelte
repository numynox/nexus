<script lang="ts">
  /** Share from the trip page's ⋮ menu: the live status page and the calendar, as token links. */
  import { CalendarPlus, Copy, Link2, Radio, Share2, X } from "lucide-svelte";
  import { getBaseUrl } from "../../lib/config";
  import {
    calendarSubscribeUrl,
    createShareLink,
    describeError,
    fetchShareLinks,
    revokeShareLink,
    shareLinkUrl,
    type ShareKind,
    type ShareLink,
    type Trip,
  } from "../../lib/data";
  import { formatShortDate, formatTime } from "../../lib/format";

  interface Props {
    trip: Trip;
    open: boolean;
    onClose: () => void;
  }

  let { trip, open, onClose }: Props = $props();

  let dialog: HTMLDialogElement | undefined = $state();

  const base = getBaseUrl();
  let links = $state<ShareLink[]>([]);
  let loading = $state(true);
  let busy = $state<ShareKind | null>(null);
  let copied = $state<ShareKind | null>(null);
  let error = $state("");

  const KINDS: { kind: ShareKind; title: string; text: string }[] = [
    {
      kind: "status",
      title: "Live status",
      text: "A page with the current train, the expected arrival and the delay — for whoever picks you up. It stops working six hours after the trip.",
    },
    {
      kind: "ics",
      title: "Calendar",
      text: "Every ride as an event at its live times. Subscribe to it and the calendar follows delays.",
    },
  ];

  $effect(() => {
    if (!dialog) return;
    if (open && !dialog.open) {
      dialog.showModal();
      void load();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  });

  async function load() {
    loading = true;
    error = "";
    try {
      links = await fetchShareLinks(trip.id);
    } catch (e) {
      error = describeError(e);
    } finally {
      loading = false;
    }
  }

  const linkOf = (kind: ShareKind) => links.find((l) => l.kind === kind) ?? null;
  const urlOf = (link: ShareLink) => shareLinkUrl(link, window.location.origin, base);

  async function create(kind: ShareKind) {
    busy = kind;
    error = "";
    try {
      const link = await createShareLink(trip.id, kind);
      links = [...links.filter((l) => l.kind !== kind), link];
      await copy(link);
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = null;
    }
  }

  async function copy(link: ShareLink) {
    const url = urlOf(link);
    try {
      if (navigator.share && link.kind === "status" && matchMedia("(pointer: coarse)").matches) {
        await navigator.share({ title: `${trip.origin_name} → ${trip.destination_name}`, url });
        return;
      }
      await navigator.clipboard.writeText(url);
      copied = link.kind;
      setTimeout(() => (copied = null), 2000);
    } catch {
      // Cancelled share sheet, or no clipboard: the link is shown to copy by hand.
    }
  }

  async function revoke(link: ShareLink) {
    busy = link.kind;
    error = "";
    try {
      await revokeShareLink(link.token);
      links = links.filter((l) => l.token !== link.token);
    } catch (e) {
      error = describeError(e);
    } finally {
      busy = null;
    }
  }
</script>

<dialog bind:this={dialog} class="modal modal-bottom sm:modal-middle" onclose={onClose} aria-labelledby="share-title">
  <div class="modal-box max-w-lg rounded-t-[2rem] bg-base-100 p-6 sm:rounded-[2rem]">
    <div class="flex items-center gap-2">
      <Share2 class="h-5 w-5 text-primary" />
      <h2 class="mr-auto font-semibold text-ink" id="share-title">Share</h2>
      <button type="button" class="btn btn-circle btn-ghost btn-sm" aria-label="Close" onclick={() => dialog?.close()}>
        <X class="h-4 w-4" />
      </button>
    </div>
    <p class="mt-1 text-sm text-base-content/60">
      Anyone with a link sees the route and the times, nothing else. Revoke it at any time.
    </p>

    {#if loading}
      <div class="mt-4 h-20 animate-pulse rounded-2xl bg-base-200"></div>
    {:else}
      <ul class="mt-4 space-y-3">
        {#each KINDS as k (k.kind)}
          {@const link = linkOf(k.kind)}
          <li class="rounded-2xl bg-card p-4">
            <div class="flex items-start gap-3">
              {#if k.kind === "status"}<Radio class="mt-0.5 h-4 w-4 shrink-0 text-primary" />{:else}<CalendarPlus class="mt-0.5 h-4 w-4 shrink-0 text-primary" />{/if}
              <div class="min-w-0 flex-1">
                <p class="font-medium">{k.title}</p>
                {#if link}
                  <p class="mt-0.5 truncate text-xs text-base-content/60" title={urlOf(link)}>{urlOf(link)}</p>
                  <p class="mt-0.5 text-xs text-base-content/50">Works until {formatShortDate(link.expires_at)}, {formatTime(link.expires_at)}</p>
                {:else}
                  <p class="mt-0.5 text-xs text-base-content/60">{k.text}</p>
                {/if}
              </div>
            </div>
            <div class="mt-3 flex flex-wrap gap-2">
              {#if link}
                <button class="btn btn-sm btn-primary rounded-full" disabled={busy !== null} onclick={() => copy(link)}>
                  <Copy class="h-3.5 w-3.5" /> {copied === k.kind ? "Copied" : "Copy link"}
                </button>
                {#if k.kind === "ics"}
                  <a class="btn btn-sm rounded-full border-base-300 bg-base-100" href={calendarSubscribeUrl(link.token)}>
                    <CalendarPlus class="h-3.5 w-3.5" /> Subscribe
                  </a>
                {/if}
                <button class="btn btn-sm btn-ghost rounded-full text-error" disabled={busy !== null} onclick={() => revoke(link)}>
                  <X class="h-3.5 w-3.5" /> Revoke
                </button>
              {:else}
                <button class="btn btn-sm rounded-full border-base-300 bg-base-100" disabled={busy !== null} onclick={() => create(k.kind)}>
                  {#if busy === k.kind}<span class="loading loading-spinner loading-xs"></span>{:else}<Link2 class="h-3.5 w-3.5" />{/if}
                  Create link
                </button>
              {/if}
            </div>
          </li>
        {/each}
      </ul>
    {/if}

    {#if error}<p class="mt-3 text-sm text-error" role="alert">{error}</p>{/if}
  </div>
  <form method="dialog" class="modal-backdrop"><button>close</button></form>
</dialog>
