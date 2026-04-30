<script lang="ts">
  import {
    LayoutDashboard,
    MapPin,
    Package,
    ScanBarcode,
    Settings,
    Tag,
  } from "lucide-svelte";

  interface Props {
    activeId?:
      | "dashboard"
      | "products"
      | "quick-add"
      | "categories"
      | "locations"
      | "settings";
    baseUrl?: string;
    siteTitle?: string;
  }

  let {
    activeId = "dashboard",
    baseUrl = "/",
    siteTitle = "Annona",
  }: Props = $props();

  let isMobileMenuOpen = $state(false);

  function href(path: string) {
    const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
    return `${normalizedBase}/${path}`;
  }

  function toggleMobileMenu() {
    isMobileMenuOpen = !isMobileMenuOpen;
  }

  function closeMobileMenu() {
    isMobileMenuOpen = false;
  }

  const mainMenuItems = [
    {
      id: "dashboard",
      label: "Dashboard",
      path: "dashboard",
      icon: LayoutDashboard,
    },
    { id: "quick-add", label: "Quick Add", path: "add", icon: ScanBarcode },
    { id: "products", label: "Products", path: "products", icon: Package },
    { id: "locations", label: "Locations", path: "locations", icon: MapPin },
    { id: "categories", label: "Categories", path: "categories", icon: Tag },
  ];
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
      href={href("dashboard")}
      class="flex items-center gap-3 text-2xl font-bold hover:text-primary transition-all duration-300 group"
      onclick={closeMobileMenu}
    >
      <div class="relative w-8 h-8 flex-shrink-0">
        <div
          class="absolute inset-0 bg-primary/30 group-hover:bg-primary rounded-full blur-md transition-all duration-300"
        ></div>
        <span
          class="relative text-2xl flex items-center justify-center w-full h-full"
          >🌾</span
        >
      </div>
      <span>{siteTitle}</span>
    </a>
  </div>

  <nav class="flex-1 overflow-y-auto px-4 pb-6">
    <div class="space-y-2">
      {#each mainMenuItems as item}
        <a
          href={href(item.path)}
          class="flex items-center gap-3 px-4 py-3 rounded-xl transition-all
          {activeId === item.id
            ? 'bg-primary text-primary-content font-semibold shadow-md'
            : 'hover:bg-base-300 text-base-content/80'}"
          onclick={closeMobileMenu}
        >
          <item.icon class="w-5 h-5" />
          <span>{item.label}</span>
        </a>
      {/each}
    </div>
  </nav>

  <div class="p-4 border-t border-base-300">
    <a
      href={href("settings")}
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
