<script lang="ts">
  import { LoginPanel as SharedLoginPanel } from "@nexus/ui";
  import { onMount } from "svelte";
  import { getSession, signInWithPassword } from "../lib/data";

  interface Props {
    siteTitle?: string;
    baseUrl?: string;
  }

  let { siteTitle = "Noctua", baseUrl = "/" }: Props = $props();

  const normalizedBaseUrl = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
  let homeHref = $state(baseUrl);

  // Noctua signs in on its own page rather than in place, so it has to send the
  // user home itself — and bounce anyone who is already signed in.
  onMount(async () => {
    const rootBaseUrl = document.documentElement.dataset.baseUrl || "/";
    homeHref = baseUrl || rootBaseUrl;

    try {
      const session = await getSession();
      if (session) window.location.replace(homeHref);
    } catch {
      // Let the user sign in manually.
    }
  });
</script>

<SharedLoginPanel
  {siteTitle}
  logoSrc={`${normalizedBaseUrl}/noctua.png`}
  signIn={signInWithPassword}
  onSignedIn={() => window.location.replace(homeHref)}
/>
