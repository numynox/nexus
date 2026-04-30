<script lang="ts">
  import { onMount } from "svelte";
  import { getSession, signInWithPassword } from "../lib/data";

  const siteTitle = "Annona";
  let email = $state("");
  let password = $state("");
  let isBusy = $state(false);
  let authError = $state("");

  let homeHref = $state("/");

  onMount(async () => {
    if (typeof document !== "undefined") {
      const baseUrl = document.documentElement.dataset.baseUrl || "/";
      homeHref = baseUrl;
    }

    try {
      const session = await getSession();
      if (session?.user) {
        window.location.replace(homeHref);
      }
    } catch {
      // Let user continue
    }
  });

  async function handleLogin(event: SubmitEvent) {
    event.preventDefault();
    authError = "";

    if (!email || !password) {
      authError = "Please enter email and password.";
      return;
    }

    isBusy = true;
    try {
      await signInWithPassword(email.trim(), password);
      window.location.replace(homeHref);
    } catch (error) {
      authError = error instanceof Error ? error.message : String(error);
    } finally {
      isBusy = false;
    }
  }
</script>

<div class="min-h-screen flex items-center justify-center p-6 bg-base-100">
  <div class="w-full max-w-md card bg-base-200 shadow-sm">
    <div class="card-body p-6 lg:p-8">
      <div class="flex flex-col items-center text-center mb-4">
        <span class="text-5xl mb-3">🌾</span>
        <h1 class="text-2xl font-bold mb-1">Sign in to {siteTitle}</h1>
      </div>

      <form class="flex flex-col gap-2" onsubmit={handleLogin}>
        <label class="form-control w-full" for="email">
          <span class="label-text">Email</span>
          <input
            class="input input-bordered w-full"
            type="email"
            id="email"
            bind:value={email}
            autocomplete="email"
            required
          />
        </label>

        <label class="form-control w-full" for="password">
          <span class="label-text">Password</span>
          <input
            class="input input-bordered w-full"
            type="password"
            id="password"
            bind:value={password}
            autocomplete="current-password"
            required
          />
        </label>

        {#if authError}
          <div class="alert alert-error text-sm">
            <span>{authError}</span>
          </div>
        {/if}

        <button
          class="btn btn-primary w-full mt-4"
          type="submit"
          disabled={isBusy}
        >
          {isBusy ? "Signing in..." : "Sign in"}
        </button>
      </form>
    </div>
  </div>
</div>
