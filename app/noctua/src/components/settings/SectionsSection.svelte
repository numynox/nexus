<script lang="ts">
  import {
    addFeedToSectionForUser,
    createSectionForUser,
    deleteSectionForUser,
    fetchAvailableFeedsForUser,
    fetchSectionsForUser,
    removeFeedFromSectionForUser,
    reorderFeedInSectionForUser,
    reorderSectionForUser,
    updateSectionForUser,
    describeError,} from "../../lib/data";
  import type { Feed, Section } from "../../lib/types";

  interface Props {
    userId: string;
    onSectionDataChanged?: () => void;
  }

  let { userId, onSectionDataChanged }: Props = $props();

  let sections = $state<Section[]>([]);
  let availableFeeds = $state<Feed[]>([]);
  let sectionsLoading = $state(false);
  let sectionsBusy = $state(false);
  let sectionsError = $state("");
  let sectionsFeedback = $state("");

  let createSectionDialog = $state<HTMLDialogElement | null>(null);
  let createSectionName = $state("");
  let createSectionIcon = $state("🦉");
  let createSectionError = $state("");

  let editSectionDialog = $state<HTMLDialogElement | null>(null);
  let editSectionId = $state<string | null>(null);
  let editSectionName = $state("");
  let editSectionIcon = $state("🦉");
  let editSectionError = $state("");
  let selectedFeedToAddId = $state("");

  let loadedUserId = $state("");

  let editingSection = $derived.by(
    () => sections.find((section) => section.id === editSectionId) || null,
  );

  let feedsAvailableToAdd = $derived.by(() => {
    if (!editingSection) {
      return [] as Feed[];
    }

    const currentIds = new Set(editingSection.feeds.map((feed) => feed.id));
    return availableFeeds.filter((feed) => !currentIds.has(feed.id));
  });

  $effect(() => {
    if (!userId) {
      loadedUserId = "";
      sections = [];
      availableFeeds = [];
      sectionsError = "";
      sectionsFeedback = "";
      closeEditSectionModal();
      return;
    }

    if (loadedUserId !== userId) {
      loadedUserId = userId;
      void loadSectionManagementData(userId);
    }
  });

  async function loadSectionManagementData(currentUserId: string) {
    sectionsLoading = true;
    sectionsError = "";

    try {
      const [loadedSections, loadedFeeds] = await Promise.all([
        fetchSectionsForUser(currentUserId),
        fetchAvailableFeedsForUser(currentUserId),
      ]);

      sections = loadedSections;
      availableFeeds = loadedFeeds;

      if (
        editSectionId &&
        !sections.some((section) => section.id === editSectionId)
      ) {
        closeEditSectionModal();
      }

      if (editingSection) {
        selectedFeedToAddId = feedsAvailableToAdd[0]?.id || "";
      }
    } catch (error) {
      const message = describeError(error);
      sectionsError = message;
      sections = [];
      availableFeeds = [];
    } finally {
      sectionsLoading = false;
    }
  }

  async function handleMoveSection(
    sectionId: string,
    direction: "up" | "down",
  ) {
    if (!userId) {
      return;
    }

    sectionsBusy = true;
    sectionsError = "";
    sectionsFeedback = "";

    try {
      await reorderSectionForUser(userId, sectionId, direction);
      await loadSectionManagementData(userId);
      onSectionDataChanged?.();
    } catch (error) {
      const message = describeError(error);
      sectionsError = message;
    } finally {
      sectionsBusy = false;
    }
  }

  function openCreateSectionModal() {
    createSectionName = "";
    createSectionIcon = "🦉";
    createSectionError = "";
    createSectionDialog?.showModal();
  }

  function closeCreateSectionModal() {
    createSectionDialog?.close();
  }

  async function handleCreateSection() {
    if (!userId) {
      return;
    }

    const title = createSectionName.trim();
    if (!title) {
      createSectionError = "Section name is required.";
      return;
    }

    sectionsBusy = true;
    sectionsError = "";
    sectionsFeedback = "";
    createSectionError = "";

    try {
      await createSectionForUser(userId, title, createSectionIcon);
      await loadSectionManagementData(userId);
      closeCreateSectionModal();
      sectionsFeedback = "Section created.";
      onSectionDataChanged?.();
    } catch (error) {
      const message = describeError(error);
      createSectionError = message;
    } finally {
      sectionsBusy = false;
    }
  }

  function openEditSectionModal(section: Section) {
    editSectionId = section.id;
    editSectionName = section.name;
    editSectionIcon = section.icon || "🦉";
    editSectionError = "";
    selectedFeedToAddId =
      availableFeeds.find(
        (feed) => !section.feeds.some((sFeed) => sFeed.id === feed.id),
      )?.id || "";
    editSectionDialog?.showModal();
  }

  function closeEditSectionModal() {
    editSectionDialog?.close();
    editSectionId = null;
    editSectionError = "";
    selectedFeedToAddId = "";
  }

  async function handleSaveSection() {
    if (!userId || !editSectionId) {
      return;
    }

    const title = editSectionName.trim();
    if (!title) {
      editSectionError = "Section name is required.";
      return;
    }

    sectionsBusy = true;
    sectionsError = "";
    sectionsFeedback = "";
    editSectionError = "";

    try {
      await updateSectionForUser(userId, editSectionId, title, editSectionIcon);
      await loadSectionManagementData(userId);
      sectionsFeedback = "Section updated.";
      onSectionDataChanged?.();
    } catch (error) {
      const message = describeError(error);
      editSectionError = message;
    } finally {
      sectionsBusy = false;
    }
  }

  async function handleDeleteSection() {
    if (!userId || !editSectionId || !editingSection) {
      return;
    }

    const accepted = confirm(
      `Delete section \"${editingSection.name}\"? This also removes its feed assignments.`,
    );
    if (!accepted) {
      return;
    }

    sectionsBusy = true;
    sectionsError = "";
    sectionsFeedback = "";
    editSectionError = "";

    try {
      await deleteSectionForUser(userId, editSectionId);
      await loadSectionManagementData(userId);
      closeEditSectionModal();
      sectionsFeedback = "Section deleted.";
      onSectionDataChanged?.();
    } catch (error) {
      const message = describeError(error);
      editSectionError = message;
    } finally {
      sectionsBusy = false;
    }
  }

  async function handleAddFeedToSection() {
    if (!userId || !editSectionId || !selectedFeedToAddId) {
      return;
    }

    sectionsBusy = true;
    sectionsError = "";
    sectionsFeedback = "";
    editSectionError = "";

    try {
      await addFeedToSectionForUser(userId, editSectionId, selectedFeedToAddId);
      await loadSectionManagementData(userId);
      selectedFeedToAddId = feedsAvailableToAdd[0]?.id || "";
      sectionsFeedback = "Feed added to section.";
      onSectionDataChanged?.();
    } catch (error) {
      const message = describeError(error);
      editSectionError = message;
    } finally {
      sectionsBusy = false;
    }
  }

  async function handleRemoveFeedFromSection(feedId: string) {
    if (!userId || !editSectionId) {
      return;
    }

    sectionsBusy = true;
    sectionsError = "";
    sectionsFeedback = "";
    editSectionError = "";

    try {
      await removeFeedFromSectionForUser(userId, editSectionId, feedId);
      await loadSectionManagementData(userId);
      selectedFeedToAddId = feedsAvailableToAdd[0]?.id || "";
      sectionsFeedback = "Feed removed from section.";
      onSectionDataChanged?.();
    } catch (error) {
      const message = describeError(error);
      editSectionError = message;
    } finally {
      sectionsBusy = false;
    }
  }

  async function handleMoveFeedInSection(
    feedId: string,
    direction: "up" | "down",
  ) {
    if (!userId || !editSectionId) {
      return;
    }

    sectionsBusy = true;
    sectionsError = "";
    sectionsFeedback = "";
    editSectionError = "";

    try {
      await reorderFeedInSectionForUser(
        userId,
        editSectionId,
        feedId,
        direction,
      );
      await loadSectionManagementData(userId);
      sectionsFeedback = "Feed order updated.";
      onSectionDataChanged?.();
    } catch (error) {
      const message = describeError(error);
      editSectionError = message;
    } finally {
      sectionsBusy = false;
    }
  }
</script>

<section class="card bg-base-200 shadow-sm overflow-hidden">
  <div class="card-body p-6 lg:p-8">
    <div class="flex items-center justify-between gap-4 mb-4">
      <h2 class="text-xl font-bold flex items-center gap-2">
        <span>🗂️</span> Sections
      </h2>
      <button
        class="btn btn-primary btn-sm"
        onclick={openCreateSectionModal}
        disabled={sectionsBusy || sectionsLoading || !userId}
      >
        New Section
      </button>
    </div>

    {#if sectionsError}
      <div class="alert alert-error text-sm mb-4">
        <span>{sectionsError}</span>
      </div>
    {/if}

    {#if sectionsFeedback}
      <div class="alert alert-success text-sm mb-4">
        <span>{sectionsFeedback}</span>
      </div>
    {/if}

    {#if sectionsLoading}
      <div class="flex items-center justify-center py-8">
        <span class="loading loading-spinner"></span>
      </div>
    {:else if sections.length === 0}
      <p class="text-sm text-base-content/70">
        No sections yet. Create one to start organizing feeds.
      </p>
    {:else}
      <div class="space-y-3">
        {#each sections as section, index}
          <div class="bg-base-100 rounded-xl px-4 py-3 border border-base-300">
            <div class="flex items-center gap-3">
              <span class="text-xl">{section.icon || "🦉"}</span>
              <div class="flex-1 min-w-0">
                <p class="font-semibold truncate">{section.name}</p>
                <p class="text-xs text-base-content/60">
                  {section.feeds.length}
                  {section.feeds.length === 1 ? "feed" : "feeds"}
                </p>
              </div>
              <div class="flex items-center gap-1">
                <button
                  class="btn btn-xs btn-ghost"
                  onclick={() => handleMoveSection(section.id, "up")}
                  disabled={sectionsBusy || index === 0}
                  aria-label="Move section up"
                >
                  ↑
                </button>
                <button
                  class="btn btn-xs btn-ghost"
                  onclick={() => handleMoveSection(section.id, "down")}
                  disabled={sectionsBusy || index === sections.length - 1}
                  aria-label="Move section down"
                >
                  ↓
                </button>
                <button
                  class="btn btn-xs btn-outline"
                  onclick={() => openEditSectionModal(section)}
                  disabled={sectionsBusy}
                >
                  Edit
                </button>
              </div>
            </div>
          </div>
        {/each}
      </div>
    {/if}
  </div>
</section>

<dialog class="modal" bind:this={createSectionDialog}>
  <div class="modal-box">
    <h3 class="font-bold text-lg mb-4">Create Section</h3>

    <div class="space-y-3">
      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">Name</span>
        </div>
        <input
          type="text"
          class="input input-bordered w-full"
          bind:value={createSectionName}
          placeholder="Section name"
          maxlength="80"
        />
      </label>

      <label class="form-control w-full">
        <div class="label">
          <span class="label-text">Icon</span>
        </div>
        <input
          type="text"
          class="input input-bordered w-full"
          bind:value={createSectionIcon}
          placeholder="🦉"
          maxlength="8"
        />
      </label>
    </div>

    {#if createSectionError}
      <div class="alert alert-error text-sm mt-4">
        <span>{createSectionError}</span>
      </div>
    {/if}

    <div class="modal-action">
      <button class="btn btn-ghost" onclick={closeCreateSectionModal}
        >Cancel</button
      >
      <button
        class="btn btn-primary"
        onclick={handleCreateSection}
        disabled={sectionsBusy}
      >
        Create
      </button>
    </div>
  </div>
</dialog>

<dialog class="modal" bind:this={editSectionDialog}>
  <div class="modal-box max-w-2xl">
    <h3 class="font-bold text-lg mb-4">Edit Section</h3>

    {#if editingSection}
      <div class="space-y-5">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <label class="form-control w-full">
            <div class="label">
              <span class="label-text">Name</span>
            </div>
            <input
              type="text"
              class="input input-bordered w-full"
              bind:value={editSectionName}
              maxlength="80"
            />
          </label>

          <label class="form-control w-full">
            <div class="label">
              <span class="label-text">Icon</span>
            </div>
            <input
              type="text"
              class="input input-bordered w-full"
              bind:value={editSectionIcon}
              maxlength="8"
            />
          </label>
        </div>

        <div class="flex items-center gap-2">
          <button
            class="btn btn-primary btn-sm"
            onclick={handleSaveSection}
            disabled={sectionsBusy}
          >
            Save
          </button>
          <button
            class="btn btn-error btn-outline btn-sm"
            onclick={handleDeleteSection}
            disabled={sectionsBusy}
          >
            Delete Section
          </button>
        </div>

        <div class="divider my-1"></div>

        <div class="space-y-3">
          <h4 class="font-semibold">Feeds in this section</h4>

          {#if editingSection.feeds.length === 0}
            <p class="text-sm text-base-content/70">No feeds assigned yet.</p>
          {:else}
            <div class="space-y-2">
              {#each editingSection.feeds as feed, index}
                <div
                  class="bg-base-100 rounded-xl px-3 py-2 border border-base-300 flex items-center gap-2"
                >
                  <div class="flex-1 min-w-0">
                    <p class="text-sm font-medium truncate">{feed.name}</p>
                    <p class="text-xs text-base-content/60 truncate">
                      {feed.url}
                    </p>
                  </div>
                  <button
                    class="btn btn-xs btn-ghost"
                    onclick={() => handleMoveFeedInSection(feed.id, "up")}
                    disabled={sectionsBusy || index === 0}
                    aria-label="Move feed up"
                  >
                    ↑
                  </button>
                  <button
                    class="btn btn-xs btn-ghost"
                    onclick={() => handleMoveFeedInSection(feed.id, "down")}
                    disabled={sectionsBusy ||
                      index === editingSection.feeds.length - 1}
                    aria-label="Move feed down"
                  >
                    ↓
                  </button>
                  <button
                    class="btn btn-xs btn-outline"
                    onclick={() => handleRemoveFeedFromSection(feed.id)}
                    disabled={sectionsBusy}
                  >
                    Remove
                  </button>
                </div>
              {/each}
            </div>
          {/if}

          <div class="divider my-2"></div>

          <div class="flex flex-col sm:flex-row gap-2">
            <select
              class="select select-bordered w-full"
              bind:value={selectedFeedToAddId}
              disabled={sectionsBusy || feedsAvailableToAdd.length === 0}
            >
              {#if feedsAvailableToAdd.length === 0}
                <option value="">No more feeds available</option>
              {:else}
                {#each feedsAvailableToAdd as feed}
                  <option value={feed.id}>{feed.name}</option>
                {/each}
              {/if}
            </select>
            <button
              class="btn btn-outline"
              onclick={handleAddFeedToSection}
              disabled={sectionsBusy ||
                !selectedFeedToAddId ||
                feedsAvailableToAdd.length === 0}
            >
              Add Feed
            </button>
          </div>
        </div>

        {#if editSectionError}
          <div class="alert alert-error text-sm">
            <span>{editSectionError}</span>
          </div>
        {/if}
      </div>
    {/if}

    <div class="modal-action">
      <button class="btn btn-ghost" onclick={closeEditSectionModal}
        >Close</button
      >
    </div>
  </div>
</dialog>
