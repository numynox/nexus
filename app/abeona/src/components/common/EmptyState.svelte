<script lang="ts">
  /** Nothing here yet: the scene, one headline, the action. No paragraph. */
  import type { Snippet } from "svelte";
  import type { SceneState } from "../../lib/scene";
  import Scene from "./Scene.svelte";

  interface Props {
    title: string;
    state?: SceneState;
    children?: Snippet;
  }

  let { title, state = "idle", children }: Props = $props();
</script>

<div class="flex flex-col items-center gap-4 text-center">
  <Scene {state} size="full" class="w-full" />
  <h2 class="t-headline text-ink">{title}</h2>
  {#if children}<div class="flex flex-wrap items-center justify-center gap-2">{@render children()}</div>{/if}
</div>
