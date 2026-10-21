<script>
  import { onMount } from "svelte";
  import LoginPanel from "./LoginPanel.svelte";

  /**
   * Session gate shared by the apps: resolve the session, show a spinner while
   * it is unknown, then either the sign-in form or the app itself.
   *
   * Auth functions are passed in rather than imported so that each app keeps
   * its own Supabase client and data layer — `src/lib/data.ts` stays the only
   * module that talks to Supabase.
   *
   * @typedef {Object} Props
   * @property {() => Promise<any>} getSession
   * @property {(cb: (event: string, session: any) => void) => { data: { subscription: { unsubscribe: () => void } } }} onAuthStateChange
   * @property {(email: string, password: string) => Promise<unknown>} signIn
   * @property {string} siteTitle
   * @property {string} [logoSrc]
   * @property {(session: any) => void | Promise<void>} [onSignedIn] - runs once per resolved session
   * @property {import("svelte").Snippet<[any]>} [sidebar]
   * @property {import("svelte").Snippet} [children]
   */

  /** @type {Props} */
  let {
    getSession,
    onAuthStateChange,
    signIn,
    siteTitle,
    logoSrc = "",
    onSignedIn,
    sidebar,
    children,
  } = $props();

  let session = $state(null);
  let loading = $state(true);
  // getSession() failing is not "signed out": an expired token whose refresh
  // could not reach Supabase — offline, or a network still waking up. The
  // sign-in form would be a lie; the stored session is still there.
  let unreachable = $state(false);
  let active = true;

  function resolve() {
    loading = true;
    unreachable = false;
    getSession()
      .then(async (resolved) => {
        if (!active) return;
        session = resolved;
        if (resolved?.user) {
          await Promise.resolve(onSignedIn?.(resolved)).catch(() => {});
        }
        loading = false;
      })
      .catch(() => {
        if (!active) return;
        unreachable = true;
        loading = false;
      });
  }

  onMount(() => {
    active = true;
    resolve();

    const {
      data: { subscription },
    } = onAuthStateChange((_event, next) => {
      session = next;
      // The client's own retry got through.
      if (next?.user) unreachable = false;
    });

    return () => {
      active = false;
      subscription.unsubscribe();
    };
  });
</script>

{#if loading}
  <div class="flex min-h-screen items-center justify-center bg-base-100">
    <span class="loading loading-infinity loading-lg text-primary"></span>
  </div>
{:else if unreachable && !session}
  <div class="flex min-h-screen flex-col items-center justify-center gap-4 bg-base-100 p-6 text-center">
    <p class="text-base-content/70">Could not reach the server to renew your session.</p>
    <button class="btn btn-primary btn-sm rounded-full" onclick={resolve}>Try again</button>
  </div>
{:else if !session}
  <LoginPanel {siteTitle} {logoSrc} {signIn} />
{:else}
  <div class="flex min-h-screen">
    {@render sidebar?.(session)}

    <div class="flex min-w-0 flex-1 flex-col">
      <main class="mx-auto w-full max-w-5xl flex-1 p-4 md:p-6 lg:p-8">
        {@render children?.()}
      </main>
    </div>
  </div>
{/if}
