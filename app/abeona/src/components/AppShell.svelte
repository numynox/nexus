<script lang="ts">
  import { AppShell as SharedAppShell } from "@nexus/ui";
  import type { Session } from "@supabase/supabase-js";
  import type { Snippet } from "svelte";
  import { getBaseUrl, getWebsiteTitle } from "../lib/config";
  import {
    ensureHousehold,
    getSession,
    onAuthStateChange,
    signInWithPassword,
  } from "../lib/data";
  import { session } from "../lib/stores";
  import Nav, { type NavId } from "./Nav.svelte";

  interface Props {
    activeId?: NavId;
    children?: Snippet;
  }

  let { activeId = "trips", children }: Props = $props();

  const baseUrl = getBaseUrl();
  const siteTitle = getWebsiteTitle();

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

  // Every household-scoped query awaits the same cached promise; starting it
  // here means the household exists before the first panel asks.
  async function prepareHousehold() {
    await ensureHousehold().catch(() => {});
  }
</script>

<SharedAppShell
  getSession={resolveSession}
  onAuthStateChange={subscribeToAuth}
  signIn={signInWithPassword}
  onSignedIn={prepareHousehold}
  {siteTitle}
  logoSrc={`${baseUrl}/abeona.png`}
>
  {#snippet sidebar()}
    <Nav {activeId} {baseUrl} {siteTitle} />
  {/snippet}

  <div class="pb-28 lg:pb-4">
    {@render children?.()}
  </div>
</SharedAppShell>
