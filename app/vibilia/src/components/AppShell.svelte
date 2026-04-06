<script lang="ts">
  import type { Snippet } from "svelte";
  import { onMount } from "svelte";
  import { getBaseUrl, getWebsiteTitle } from "../lib/config";
  import { fetchProfile, getSession, onAuthStateChange } from "../lib/data";
  import { session, userProfile } from "../lib/stores";
  import LoginPanel from "./LoginPanel.svelte";
  import Sidebar from "./Sidebar.svelte";

  interface Props {
    activeId?: "dashboard" | "cars" | "settings";
    children?: Snippet;
  }

  let { activeId = "dashboard", children }: Props = $props();

  const baseUrl = getBaseUrl();
  const siteTitle = getWebsiteTitle();
  let loading = $state(true);

  onMount(() => {
    getSession()
      .then((s) => {
        session.set(s);
        if (s) fetchAndStoreProfile(s.user.id);
        loading = false;
      })
      .catch(() => {
        loading = false;
      });

    const {
      data: { subscription },
    } = onAuthStateChange((_event, s) => {
      session.set(s);
      if (s) fetchAndStoreProfile(s.user.id);
      else userProfile.set(null);
    });

    return () => subscription.unsubscribe();
  });

  async function fetchAndStoreProfile(userId: string) {
    try {
      const data = await fetchProfile(userId);
      if (data) {
        userProfile.set(data);
      }
    } catch {
      // Keep UI functional even when profile loading fails.
    }
  }
</script>

{#if loading}
  <div class="min-h-screen flex items-center justify-center bg-base-100">
    <span class="loading loading-infinity loading-lg text-primary"></span>
  </div>
{:else if !$session}
  <LoginPanel />
{:else}
  <div class="flex min-h-screen">
    <Sidebar {activeId} {baseUrl} {siteTitle} />

    <div class="flex-1 flex flex-col min-w-0">
      <main class="flex-1 p-4 md:p-6 lg:p-8 max-w-5xl mx-auto w-full">
        {@render children?.()}
      </main>
    </div>
  </div>
{/if}
