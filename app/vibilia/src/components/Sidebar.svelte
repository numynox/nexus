<script lang="ts">
  import { Car, ChartColumnBig, Settings } from "lucide-svelte";

  interface Props {
    activeId?:
      | "fuel-price"
      | "vehicle-logs"
      | "vehicle-statistics"
      | "settings";
    activeSubmenuId?: string | null;
    baseUrl?: string;
    siteTitle?: string;
  }

  interface MenuItem {
    id: string;
    label: string;
    href: string;
    icon: typeof Car;
    children?: Array<{
      id: string;
      label: string;
      href: string;
    }>;
  }

  let {
    activeId = "fuel-price",
    activeSubmenuId = null,
    baseUrl = "/",
    siteTitle = "Vibilia",
  }: Props = $props();

  let isMobileMenuOpen = $state(false);

  function rootHref() {
    return fuelPriceHref();
  }

  function fuelPriceHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/fuel-price`;
  }

  function fuelPriceNearbyHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/fuel-price/nearby`;
  }

  function vehicleLogsHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/vehicle-logs`;
  }

  function settingsHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/settings`;
  }

  function vehicleStatisticsHref() {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/vehicle-statistics`;
  }

  function toggleMobileMenu() {
    isMobileMenuOpen = !isMobileMenuOpen;
  }

  function closeMobileMenu() {
    isMobileMenuOpen = false;
  }

  const mainMenuItems = $derived.by<MenuItem[]>(() => [
    {
      id: "fuel-price",
      label: "Fuel Price",
      href: fuelPriceHref(),
      icon: ChartColumnBig,
      children: [
        {
          id: "history",
          label: "History",
          href: fuelPriceHref(),
        },
        {
          id: "nearby",
          label: "Nearby",
          href: fuelPriceNearbyHref(),
        },
      ],
    },
    {
      id: "vehicle-logs",
      label: "Vehicle Logs",
      href: vehicleLogsHref(),
      icon: Car,
    },
    {
      id: "vehicle-statistics",
      label: "Vehicle Statistics",
      href: vehicleStatisticsHref(),
      icon: ChartColumnBig,
    },
  ]);
</script>

<div class="lg:hidden fixed bottom-6 right-6 z-50">
  <button
    onclick={toggleMobileMenu}
    class="btn btn-primary btn-circle shadow-lg"
    aria-label="Toggle Menu"
  >
    {#if isMobileMenuOpen}
      <span class="text-2xl">✕</span>
    {:else}
      <span class="text-2xl">☰</span>
    {/if}
  </button>
</div>

{#if isMobileMenuOpen}
  <div
    class="lg:hidden fixed inset-0 bg-black/50 z-40 backdrop-blur-sm"
    onclick={closeMobileMenu}
    onkeydown={(e) => e.key === "Escape" && closeMobileMenu()}
    role="button"
    tabindex="0"
    aria-label="Close Menu"
  ></div>
{/if}

<aside
  class="fixed lg:sticky top-0 left-0 z-40 w-80 h-screen bg-base-200 border-r border-base-300 transform transition-transform duration-300 ease-in-out flex flex-col
    {isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}"
>
  <div class="p-6">
    <a
      href={rootHref()}
      class="flex items-center gap-3 text-2xl font-bold hover:text-primary transition-all duration-300 group"
      onclick={closeMobileMenu}
    >
      <div class="relative w-8 h-8 flex-shrink-0">
        <div
          class="absolute inset-0 bg-primary/30 group-hover:bg-primary rounded-full blur-md transition-all duration-300"
        ></div>
        <img
          src={`${baseUrl}/vibilia.png`}
          alt={`${siteTitle} Logo`}
          class="relative w-full h-full object-contain"
        />
      </div>
      <span>{siteTitle}</span>
    </a>
  </div>

  <nav class="flex-1 overflow-y-auto px-4 pb-6">
    <div class="space-y-2">
      {#each mainMenuItems as item}
        <div class="space-y-1">
          <a
            href={item.href}
            class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
            {activeId === item.id
              ? 'bg-primary text-primary-content font-semibold shadow-md'
              : 'hover:bg-base-300 text-base-content/80'}"
            onclick={closeMobileMenu}
          >
            <item.icon class="w-5 h-5" />
            <span>{item.label}</span>
          </a>

          {#if activeId === item.id && item.children && item.children.length > 0}
            <div class="ml-4 pl-4 border-l-2 border-primary/20 space-y-1 my-2">
              {#each item.children as child}
                <a
                  href={child.href}
                  class="block rounded-lg px-3 py-2 text-sm transition-all
                  {activeSubmenuId === child.id
                    ? 'bg-primary/15 text-primary font-semibold'
                    : 'text-base-content/65 hover:bg-base-300 hover:text-base-content'}"
                  onclick={closeMobileMenu}
                >
                  {child.label}
                </a>
              {/each}
            </div>
          {/if}
        </div>
      {/each}
    </div>
  </nav>

  <div class="p-4 border-t border-base-300">
    <a
      href={settingsHref()}
      class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
        {activeId === 'settings'
        ? 'bg-primary text-primary-content font-semibold shadow-md'
        : 'hover:bg-base-300 text-base-content/80'}"
      onclick={closeMobileMenu}
    >
      <Settings class="w-5 h-5" />
      <span>Settings</span>
    </a>
  </div>
</aside>
