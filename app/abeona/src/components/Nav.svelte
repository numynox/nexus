<script lang="ts" module>
  export type NavId = "trips" | "search" | "review" | "settings";
</script>

<script lang="ts">
  import { ChartNoAxesColumn, Search, Settings, TrainFront } from "lucide-svelte";

  interface Props {
    activeId?: NavId;
    baseUrl?: string;
    siteTitle?: string;
  }

  let { activeId = "trips", baseUrl = "/", siteTitle = "Abeona" }: Props = $props();

  function href(path: string) {
    const base = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${base}/${path}`;
  }

  // The same four words in the tab bar and the sidebar (abeona-design.md §9).
  const items: { id: NavId; label: string; path: string; icon: typeof TrainFront }[] = [
    { id: "trips", label: "Trips", path: "", icon: TrainFront },
    { id: "search", label: "Plan", path: "search", icon: Search },
    { id: "review", label: "Year", path: "review", icon: ChartNoAxesColumn },
    { id: "settings", label: "Settings", path: "settings", icon: Settings },
  ];
</script>

<!-- Wide screens: a quiet rail on the left. -->
<aside class="sticky top-0 hidden h-screen w-64 shrink-0 flex-col px-5 py-8 lg:flex">
  <a href={href("")} class="mb-10 flex items-center gap-3 px-3">
    <img src={`${baseUrl}/abeona.png`} alt="" class="h-9 w-9 rounded-xl" />
    <span class="text-xl font-semibold tracking-tight text-ink">{siteTitle}</span>
  </a>

  <nav class="flex flex-col gap-1">
    {#each items as item (item.id)}
      <a
        href={href(item.path)}
        class="t-body flex min-h-12 items-center gap-3 rounded-full px-4 transition-colors
          {activeId === item.id ? 'bg-brand-soft font-semibold text-brand' : 'text-muted hover:bg-well hover:text-ink'}"
        aria-current={activeId === item.id ? "page" : undefined}
      >
        <item.icon class="h-5 w-5" strokeWidth={activeId === item.id ? 2.4 : 2} />
        <span>{item.label}</span>
      </a>
    {/each}
  </nav>
</aside>

<!-- Phones: a tab bar, thumbs-first. -->
<nav
  class="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-base-100/90 backdrop-blur-lg lg:hidden"
  style="padding-bottom: env(safe-area-inset-bottom)"
>
  <div class="mx-auto flex max-w-md items-stretch justify-around px-2">
    {#each items as item (item.id)}
      <a
        href={href(item.path)}
        class="flex flex-1 flex-col items-center gap-1 py-2 text-xs transition-colors
          {activeId === item.id ? 'font-semibold text-brand' : 'font-medium text-muted'}"
        aria-current={activeId === item.id ? "page" : undefined}
      >
        <span
          class="flex h-8 w-14 items-center justify-center rounded-full transition-colors
            {activeId === item.id ? 'bg-brand-soft' : ''}"
        >
          <item.icon class="h-5 w-5" strokeWidth={activeId === item.id ? 2.4 : 2} />
        </span>
        {item.label}
      </a>
    {/each}
  </div>
</nav>
