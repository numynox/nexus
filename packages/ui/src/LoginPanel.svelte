<script>
  /**
   * @typedef {Object} Props
   * @property {string} siteTitle
   * @property {string} [logoSrc]
   * @property {(email: string, password: string) => Promise<unknown>} signIn
   * @property {() => void} [onSignedIn]
   */

  /** @type {Props} */
  let { siteTitle, logoSrc = "", signIn, onSignedIn } = $props();

  let email = $state("");
  let password = $state("");
  let submitting = $state(false);
  let error = $state("");

  async function handleSubmit(event) {
    event.preventDefault();
    if (submitting) return;

    error = "";
    submitting = true;

    try {
      await signIn(email.trim(), password);
      onSignedIn?.();
    } catch (err) {
      error = err instanceof Error ? err.message : "Sign in failed.";
    } finally {
      submitting = false;
    }
  }
</script>

<div class="flex min-h-screen items-center justify-center bg-base-100 p-4">
  <div class="card w-full max-w-sm bg-base-200 shadow-xl">
    <div class="card-body">
      {#if logoSrc}
        <img
          src={logoSrc}
          alt={`${siteTitle} logo`}
          class="mx-auto h-16 w-16 object-contain"
        />
      {/if}

      <h1 class="text-center text-xl font-bold">Sign in to {siteTitle}</h1>

      <form class="mt-4 space-y-4" onsubmit={handleSubmit}>
        <div class="form-control">
          <label class="label py-1" for="nexus-login-email">
            <span class="label-text">Email</span>
          </label>
          <input
            id="nexus-login-email"
            type="email"
            autocomplete="username"
            required
            bind:value={email}
            class="input input-bordered w-full"
          />
        </div>

        <div class="form-control">
          <label class="label py-1" for="nexus-login-password">
            <span class="label-text">Password</span>
          </label>
          <input
            id="nexus-login-password"
            type="password"
            autocomplete="current-password"
            required
            bind:value={password}
            class="input input-bordered w-full"
          />
        </div>

        {#if error}
          <p class="text-sm text-error" role="alert">{error}</p>
        {/if}

        <button
          type="submit"
          class="btn btn-primary w-full"
          disabled={submitting}
        >
          {#if submitting}
            <span class="loading loading-spinner loading-sm"></span>
          {/if}
          Sign in
        </button>
      </form>
    </div>
  </div>
</div>
