<script lang="ts">
  import type { Snippet } from "svelte";
  import { onMount } from "svelte";
  import { getBaseUrl, getWebsiteTitle } from "../lib/config";
  import { getSession, onAuthStateChange } from "../lib/data";
  import { session } from "../lib/stores";
  import LoginPanel from "./LoginPanel.svelte";
  import Sidebar from "./Sidebar.svelte";

  interface Props {
    activeId?: "fuel-price" | "cars" | "settings";
    children?: Snippet;
  }

  let { activeId = "fuel-price", children }: Props = $props();

  const baseUrl = getBaseUrl();
  const siteTitle = getWebsiteTitle();
  let loading = $state(true);

  onMount(() => {
    getSession()
      .then((s) => {
        session.set(s);
        loading = false;
      })
      .catch(() => {
        loading = false;
      });

    const {
      data: { subscription },
    } = onAuthStateChange((_event, s) => {
      session.set(s);
    });

    return () => subscription.unsubscribe();
  });
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
