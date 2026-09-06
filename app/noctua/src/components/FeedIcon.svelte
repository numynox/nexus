<script lang="ts">
  /**
   * A feed's favicon, falling back to a generic RSS mark when the feed has none
   * or the image fails to load. Both the sidebar and the article cards need it,
   * and an inline SVG copied into two files drifts.
   */
  interface Props {
    /** `feeds.icon` — a favicon URL, or null when the fetch never found one. */
    icon?: string | null;
    /** Sizing classes; the caller decides how big it is. */
    class?: string;
  }

  let { icon = null, class: className = "h-4 w-4" }: Props = $props();

  let failed = $state(false);
</script>

{#if icon && !failed}
  <img
    src={icon}
    alt=""
    class="shrink-0 {className}"
    onerror={() => (failed = true)}
  />
{:else}
  <svg
    xmlns="http://www.w3.org/2000/svg"
    fill="currentColor"
    class="shrink-0 {className}"
    viewBox="0 0 16 16"
    aria-hidden="true"
  >
    <path
      d="M2 0a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V2a2 2 0 0 0-2-2zm1.5 2.5c5.523 0 10 4.477 10 10a1 1 0 1 1-2 0 8 8 0 0 0-8-8 1 1 0 0 1 0-2m0 4a6 6 0 0 1 6 6 1 1 0 1 1-2 0 4 4 0 0 0-4-4 1 1 0 0 1 0-2m.5 7a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3"
    />
  </svg>
{/if}
