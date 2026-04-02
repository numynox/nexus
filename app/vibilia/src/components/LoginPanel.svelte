<script lang="ts">
  import { signInWithPassword, signUp } from "../lib/data";

  let email = "";
  let password = "";
  let loading = false;
  let isSignUp = false;
  let message = "";

  async function handleAuth() {
    loading = true;
    message = "";
    try {
      if (isSignUp) {
        await signUp(email, password);
        message = "Check your email for confirmation!";
      } else {
        await signInWithPassword(email, password);
      }
    } catch (error) {
      message = error instanceof Error ? error.message : String(error);
    }

    loading = false;
  }
</script>

<div class="min-h-screen flex items-center justify-center p-4 bg-base-100">
  <div
    class="card w-full max-w-md bg-base-200 shadow-xl border border-primary/10"
  >
    <div class="card-body">
      <h2
        class="card-title text-3xl font-extrabold text-primary mb-4 self-center"
      >
        Vibilia
      </h2>
      <p class="text-base-content/70 text-center mb-6">
        {isSignUp
          ? "Create an account to start tracking fuel prices."
          : "Sign in to access your dashboard."}
      </p>

      <form
        onsubmit={(e) => {
          e.preventDefault();
          handleAuth();
        }}
        class="space-y-4"
      >
        <div class="form-control w-full">
          <label class="label" for="email">
            <span class="label-text font-medium">Email</span>
          </label>
          <input
            type="email"
            id="email"
            bind:value={email}
            placeholder="your@email.com"
            class="input input-bordered w-full focus:input-primary transition-all"
            required
          />
        </div>

        <div class="form-control w-full">
          <label class="label" for="password">
            <span class="label-text font-medium">Password</span>
          </label>
          <input
            type="password"
            id="password"
            bind:value={password}
            placeholder="••••••••"
            class="input input-bordered w-full focus:input-primary transition-all"
            required
          />
        </div>

        <button
          type="submit"
          class="btn btn-primary w-full mt-4"
          disabled={loading}
        >
          {#if loading}
            <span class="loading loading-spinner"></span>
          {/if}
          {isSignUp ? "Sign Up" : "Sign In"}
        </button>
      </form>

      {#if message}
        <div class="alert alert-info mt-6 text-sm">
          <span>{message}</span>
        </div>
      {/if}

      <div class="divider mt-8 text-base-content/50">OR</div>

      <button
        class="btn btn-ghost btn-sm w-full text-base-content/60"
        onclick={() => (isSignUp = !isSignUp)}
      >
        {isSignUp
          ? "Already have an account? Log In"
          : "Need an account? Sign Up"}
      </button>
    </div>
  </div>
</div>
