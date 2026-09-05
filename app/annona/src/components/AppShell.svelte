<script lang="ts">
  import { AppShell as SharedAppShell } from "@nexus/ui";
  import type { Session } from "@supabase/supabase-js";
  import type { Snippet } from "svelte";
  import { getBaseUrl, getWebsiteTitle } from "../lib/config";
  import {
    getSession,
    onAuthStateChange,
    seedDefaultCategories,
    signInWithPassword,
  } from "../lib/data";
  import { session } from "../lib/stores";
  import Sidebar from "./Sidebar.svelte";

  interface Props {
    activeId?:
      | "dashboard"
      | "products"
      | "quick-add"
      | "consume"
      | "locations"
      | "settings";
    children?: Snippet;
  }

  let { activeId = "dashboard", children }: Props = $props();

  const baseUrl = getBaseUrl();
  const siteTitle = getWebsiteTitle();

  // The shared shell owns the gate; the app keeps its own session store in sync
  // so components that read $session keep working unchanged.
  async function resolveSession(): Promise<Session | null> {
    const resolved = await getSession();
    session.set(resolved);
    return resolved;
  }

  function subscribeToAuth(cb: (event: string, next: Session | null) => void) {
    return onAuthStateChange((event, next) => {
      session.set(next);
      cb(event, next);
    });
  }

  async function seedOnFirstLoad() {
    await seedDefaultCategories().catch(() => {});
  }
</script>

<SharedAppShell
  getSession={resolveSession}
  onAuthStateChange={subscribeToAuth}
  signIn={signInWithPassword}
  onSignedIn={seedOnFirstLoad}
  {siteTitle}
  logoSrc={`${baseUrl}/annona.png`}
>
  {#snippet sidebar()}
    <Sidebar {activeId} {baseUrl} {siteTitle} />
  {/snippet}

  {@render children?.()}
</SharedAppShell>
