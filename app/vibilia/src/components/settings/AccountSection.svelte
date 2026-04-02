<script lang="ts">
  interface Props {
    userEmail: string;
    authError: string;
    isBusy: boolean;
    onLogout: () => void | Promise<void>;
  }

  let { userEmail, authError, isBusy, onLogout }: Props = $props();
</script>

<section class="card bg-base-200 shadow-sm overflow-hidden">
  <div class="card-body p-6 lg:p-8">
    <h2 class="text-xl font-bold mb-4 flex items-center gap-2">
      <span>👤</span> Account
    </h2>
    <p class="text-sm text-base-content/70 mb-4">Signed in as {userEmail}</p>

    {#if authError}
      <div class="alert alert-error text-sm mb-4">
        <span>{authError}</span>
      </div>
    {/if}

    <button class="btn btn-outline w-full" onclick={onLogout} disabled={isBusy}>
      {isBusy ? "Signing out..." : "Log out"}
    </button>
  </div>
</section>
