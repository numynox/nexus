<script lang="ts">
  import { LogOut } from "lucide-svelte";

  interface Props {
    onLogout: () => void;
  }

  let { onLogout }: Props = $props();
</script>

<div class="pt-6 border-t border-base-300">
  <button class="btn btn-outline w-full gap-2" onclick={onLogout}>
    <LogOut class="w-4 h-4" />
    Logout
  </button>
</div>
