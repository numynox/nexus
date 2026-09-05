<script lang="ts">
  import { TriangleAlert } from "lucide-svelte";
  interface Props {
    onClearHistory: () => void | Promise<void>;
  }

  let { onClearHistory }: Props = $props();
</script>

<section class="card bg-base-200 shadow-sm border border-error/20">
  <div class="card-body p-6 lg:p-8">
    <h2 class="text-xl font-bold text-error mb-4 flex items-center gap-2">
      <TriangleAlert class="h-5 w-5" /> Danger Zone
    </h2>
    <p class="text-sm text-base-content/70 mb-6">
      Once you clear your read history, you cannot recover it. This will mark
      all articles as unread.
    </p>
    <button class="btn btn-error btn-outline w-full" onclick={onClearHistory}>
      Clear Read History
    </button>
  </div>
</section>
