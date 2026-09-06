import type { AuthChangeEvent, Session } from "@supabase/supabase-js";
import { getSupabaseClient } from "./supabase";
import type { Article, Feed, Section, UserContent } from "./types";

type MoveDirection = "up" | "down";


/**
 * Re-exported so every call site can keep importing it from the data layer,
 * while the shell components in @nexus/ui use the same implementation — the
 * sign-in panel is the first place a database error is ever seen.
 */
export { describeError } from "@nexus/ui/errors";

export interface ReadArticleStatuses {
  [articleId: string]: {
    timestamp: string;
  };
}

export interface ReadStatisticsRecord {
  articleId: string;
  readAt: string;
  feedId: string | null;
  feedName: string;
}

export interface FeedArticleTotalRecord {
  feedName: string;
  count: number;
}

function toDbId(id: string): number {
  const parsed = Number(id);
  if (!Number.isInteger(parsed)) {
    throw new Error(`Invalid id: ${id}`);
  }
  return parsed;
}

export async function getSession(): Promise<Session | null> {
  const supabase = getSupabaseClient();
  const {
    data: { session },
    error,
  } = await supabase.auth.getSession();

  if (error) {
    throw error;
  }

  return session;
}

export async function signInWithPassword(email: string, password: string) {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password,
  });

  if (error) {
    throw error;
  }

  return data;
}

export async function signOut() {
  const supabase = getSupabaseClient();
  const { error } = await supabase.auth.signOut();

  if (error) {
    throw error;
  }
}

export function onAuthStateChange(
  callback: (event: AuthChangeEvent, session: Session | null) => void,
) {
  const supabase = getSupabaseClient();
  return supabase.auth.onAuthStateChange(callback);
}

async function ensureProfile(userId: string) {
  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("profiles")
    .select("id")
    .eq("id", userId)
    .maybeSingle();

  if (error) {
    throw error;
  }

  if (!data) {
    throw new Error(
      "No profile row found for this user. Verify the auth profile trigger migration is applied.",
    );
  }
}

export async function fetchSectionsForUser(userId: string): Promise<Section[]> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();
  const query = supabase
    .from("sections")
    .select(
      `
      id,
      title,
      icon,
      sort_order,
      sections_feeds (
        sort_order,
        feed_id,
        feeds (
          id,
          name,
          url,
          icon,
          enabled
        )
      )
    `,
    )
    .eq("user_id", userId)
    .order("sort_order", { ascending: true })
    .order("id", { ascending: true })
    .order("sort_order", {
      ascending: true,
      referencedTable: "sections_feeds",
    })
    .order("feed_id", {
      ascending: true,
      referencedTable: "sections_feeds",
    });

  const { data, error } = await query;

  if (error) {
    throw error;
  }

  return (data || []).map((section: any) => {
    const feeds: Feed[] = (section.sections_feeds || [])
      .filter(
        (joinRow: any) => joinRow?.feeds && joinRow.feeds.enabled !== false,
      )
      .map((joinRow: any, index: number) => ({
        id: String(joinRow.feeds.id),
        name: joinRow.feeds.name,
        url: joinRow.feeds.url,
        icon: joinRow.feeds.icon || null,
        sort_order:
          typeof joinRow.sort_order === "number" ? joinRow.sort_order : index,
      }));

    return {
      id: String(section.id),
      name: section.title,
      icon: section.icon || "newspaper",
      sort_order:
        typeof section.sort_order === "number" ? section.sort_order : 0,
      feeds,
    };
  });
}

export async function fetchAvailableFeedsForUser(
  userId: string,
): Promise<Feed[]> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("feeds")
    .select("id,name,url,icon,enabled")
    .eq("enabled", true)
    .order("name", { ascending: true });

  if (error) {
    throw error;
  }

  return (data || []).map((feed: any, index: number) => ({
    id: String(feed.id),
    name: feed.name,
    url: feed.url,
    icon: feed.icon || null,
    sort_order: index,
  }));
}

export async function createSectionForUser(
  userId: string,
  title: string,
  icon: string,
): Promise<void> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { data: lastSection, error: lastSectionError } = await db
    .from("sections")
    .select("sort_order")
    .eq("user_id", userId)
    .order("sort_order", { ascending: false })
    .order("id", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (lastSectionError) {
    throw lastSectionError;
  }

  const nextSortOrder =
    typeof lastSection?.sort_order === "number"
      ? lastSection.sort_order + 1
      : 0;

  const { error } = await db.from("sections").insert({
    user_id: userId,
    title: title.trim(),
    icon: icon.trim() || "newspaper",
    sort_order: nextSortOrder,
  });

  if (error) {
    throw error;
  }
}

export async function updateSectionForUser(
  userId: string,
  sectionId: string,
  title: string,
  icon: string,
): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;
  const { error } = await db
    .from("sections")
    .update({
      title: title.trim(),
      icon: icon.trim() || "newspaper",
    })
    .eq("id", toDbId(sectionId))
    .eq("user_id", userId);

  if (error) {
    throw error;
  }
}

export async function deleteSectionForUser(
  userId: string,
  sectionId: string,
): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;
  const { error } = await db
    .from("sections")
    .delete()
    .eq("id", toDbId(sectionId))
    .eq("user_id", userId);

  if (error) {
    throw error;
  }
}

export async function reorderSectionForUser(
  userId: string,
  sectionId: string,
  direction: MoveDirection,
): Promise<boolean> {
  const supabase = getSupabaseClient();
  const db = supabase as any;
  const { data, error } = await db
    .from("sections")
    .select("id,sort_order")
    .eq("user_id", userId)
    .order("sort_order", { ascending: true })
    .order("id", { ascending: true });

  if (error) {
    throw error;
  }

  const rows = data || [];
  const currentIndex = rows.findIndex(
    (row: any) => String(row.id) === sectionId,
  );
  if (currentIndex < 0) {
    return false;
  }

  const offset = direction === "up" ? -1 : 1;
  const nextIndex = currentIndex + offset;
  if (nextIndex < 0 || nextIndex >= rows.length) {
    return false;
  }

  const reordered = [...rows];
  const [moved] = reordered.splice(currentIndex, 1);
  reordered.splice(nextIndex, 0, moved);

  const updates = reordered
    .map((row: any, index: number) => ({ row, index }))
    .filter(({ row, index }) => row.sort_order !== index)
    .map(({ row, index }) =>
      db
        .from("sections")
        .update({ sort_order: index })
        .eq("id", row.id)
        .eq("user_id", userId),
    );

  const results = await Promise.all(updates);
  const failed = results.find((result) => result.error);
  if (failed?.error) {
    throw failed.error;
  }

  return true;
}

export async function addFeedToSectionForUser(
  userId: string,
  sectionId: string,
  feedId: string,
): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;
  const sectionDbId = toDbId(sectionId);
  const feedDbId = toDbId(feedId);

  const { data: ownedSection, error: sectionError } = await db
    .from("sections")
    .select("id")
    .eq("id", sectionDbId)
    .eq("user_id", userId)
    .maybeSingle();

  if (sectionError) {
    throw sectionError;
  }

  if (!ownedSection) {
    throw new Error("Section not found.");
  }

  const { data: existing, error: existingError } = await db
    .from("sections_feeds")
    .select("feed_id")
    .eq("section_id", sectionDbId)
    .eq("feed_id", feedDbId)
    .maybeSingle();

  if (existingError) {
    throw existingError;
  }

  if (existing) {
    return;
  }

  const { data: lastJoin, error: lastJoinError } = await db
    .from("sections_feeds")
    .select("sort_order")
    .eq("section_id", sectionDbId)
    .order("sort_order", { ascending: false })
    .order("feed_id", { ascending: false })
    .limit(1)
    .maybeSingle();

  if (lastJoinError) {
    throw lastJoinError;
  }

  const nextSortOrder =
    typeof lastJoin?.sort_order === "number" ? lastJoin.sort_order + 1 : 0;

  const { error } = await db.from("sections_feeds").insert({
    section_id: sectionDbId,
    feed_id: feedDbId,
    sort_order: nextSortOrder,
  });

  if (error) {
    throw error;
  }
}

export async function removeFeedFromSectionForUser(
  userId: string,
  sectionId: string,
  feedId: string,
): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;
  const sectionDbId = toDbId(sectionId);
  const feedDbId = toDbId(feedId);

  const { data: ownedSection, error: sectionError } = await db
    .from("sections")
    .select("id")
    .eq("id", sectionDbId)
    .eq("user_id", userId)
    .maybeSingle();

  if (sectionError) {
    throw sectionError;
  }

  if (!ownedSection) {
    throw new Error("Section not found.");
  }

  const { error } = await db
    .from("sections_feeds")
    .delete()
    .eq("section_id", sectionDbId)
    .eq("feed_id", feedDbId);

  if (error) {
    throw error;
  }
}

export async function reorderFeedInSectionForUser(
  userId: string,
  sectionId: string,
  feedId: string,
  direction: MoveDirection,
): Promise<boolean> {
  const supabase = getSupabaseClient();
  const db = supabase as any;
  const sectionDbId = toDbId(sectionId);
  const feedDbId = toDbId(feedId);

  const { data: ownedSection, error: sectionError } = await db
    .from("sections")
    .select("id")
    .eq("id", sectionDbId)
    .eq("user_id", userId)
    .maybeSingle();

  if (sectionError) {
    throw sectionError;
  }

  if (!ownedSection) {
    throw new Error("Section not found.");
  }

  const { data, error } = await db
    .from("sections_feeds")
    .select("section_id,feed_id,sort_order")
    .eq("section_id", sectionDbId)
    .order("sort_order", { ascending: true })
    .order("feed_id", { ascending: true });

  if (error) {
    throw error;
  }

  const rows = data || [];
  const currentIndex = rows.findIndex((row: any) => row.feed_id === feedDbId);
  if (currentIndex < 0) {
    return false;
  }

  const offset = direction === "up" ? -1 : 1;
  const nextIndex = currentIndex + offset;
  if (nextIndex < 0 || nextIndex >= rows.length) {
    return false;
  }

  const reordered = [...rows];
  const [moved] = reordered.splice(currentIndex, 1);
  reordered.splice(nextIndex, 0, moved);

  const updates = reordered
    .map((row: any, index: number) => ({ row, index }))
    .filter(({ row, index }) => row.sort_order !== index)
    .map(({ row, index }) =>
      db
        .from("sections_feeds")
        .update({ sort_order: index })
        .eq("section_id", sectionDbId)
        .eq("feed_id", row.feed_id),
    );

  const results = await Promise.all(updates);
  const failed = results.find((result) => result.error);
  if (failed?.error) {
    throw failed.error;
  }

  return true;
}

export async function fetchArticlesForSections(
  sections: Section[],
  selectedSectionId?: string | null,
  limit = 300,
): Promise<Article[]> {
  const scopedSections = selectedSectionId
    ? sections.filter((section) => section.id === selectedSectionId)
    : sections;

  const feedMap = new Map<string, Feed>();
  for (const section of scopedSections) {
    for (const feed of section.feeds) {
      feedMap.set(feed.id, feed);
    }
  }

  const feedIds = Array.from(feedMap.keys());
  if (feedIds.length === 0) {
    return [];
  }

  const supabase = getSupabaseClient();
  const { data, error } = await supabase
    .from("articles")
    .select(
      "id,feed_id,title,url,published_at,updated_at,author,summary,image_url,tags",
    )
    .in("feed_id", feedIds)
    .order("published_at", { ascending: false })
    // A stable tiebreak, so two articles published in the same second do not
    // trade places between loads.
    .order("id", { ascending: false })
    .limit(limit);

  if (error) {
    throw error;
  }

  return (data || []).map((article: any) => {
    const feedId = String(article.feed_id);
    const feed = feedMap.get(feedId);

    return {
      id: String(article.id),
      feed_id: feedId,
      feed_name: feed?.name || "Unknown feed",
      feed_icon: feed?.icon || null,
      title: article.title || "Untitled",
      url: article.url,
      published: article.published_at,
      updated: article.updated_at,
      author: article.author,
      summary: article.summary,
      image_url: article.image_url,
      tags: Array.isArray(article.tags) ? article.tags : [],
    };
  });
}

export async function loadUserContent(
  selectedSectionId?: string | null,
  articleLimitPerSection = 300,
): Promise<UserContent> {
  const session = await getSession();
  if (!session?.user) {
    return {
      isLoggedIn: false,
      sections: [],
      articles: [],
      selectedSectionId: null,
    };
  }

  const sections = await fetchSectionsForUser(session.user.id);
  const firstSectionId = sections[0]?.id || null;
  const normalizedSelectedSectionId =
    selectedSectionId &&
    sections.some((section) => section.id === selectedSectionId)
      ? selectedSectionId
      : firstSectionId;
  const articles = await fetchArticlesForSections(
    sections,
    normalizedSelectedSectionId,
    articleLimitPerSection,
  );

  return {
    isLoggedIn: true,
    sections,
    articles,
    selectedSectionId: normalizedSelectedSectionId,
  };
}

export function getLoginHref(baseUrl = "/") {
  const normalizedBase = baseUrl === "/" ? "" : baseUrl.replace(/\/$/, "");
  return `${normalizedBase}/login`;
}

export async function fetchReadArticlesForUser(
  userId: string,
  articleIds: string[],
): Promise<ReadArticleStatuses> {
  if (!articleIds.length) {
    return {};
  }

  const supabase = getSupabaseClient();
  const db = supabase as any;
  const articleDbIds = articleIds
    .map((id) => Number(id))
    .filter((id) => Number.isInteger(id));

  if (!articleDbIds.length) {
    return {};
  }

  const { data, error } = await db
    .from("article_reads")
    .select("article_id,read_at")
    .eq("user_id", userId)
    .in("article_id", articleDbIds);

  if (error) {
    throw error;
  }

  const statuses: ReadArticleStatuses = {};
  for (const row of data || []) {
    statuses[String(row.article_id)] = {
      timestamp: row.read_at,
    };
  }

  return statuses;
}

export async function markArticleAsReadForUser(
  userId: string,
  articleId: string,
): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { error } = await db.from("article_reads").upsert(
    {
      user_id: userId,
      article_id: toDbId(articleId),
      read_at: new Date().toISOString(),
    },
    {
      onConflict: "user_id,article_id",
      ignoreDuplicates: false,
    },
  );

  if (error) {
    throw error;
  }
}

/**
 * Ask Postgres which of these articles are the same story.
 *
 * Returns article id → group key (the lowest id in its cluster). Articles with
 * no similar neighbour map to themselves, so the caller can group by the value
 * without special cases.
 */
export async function fetchSimilarArticleGroups(
  articleIds: string[],
): Promise<Record<string, string>> {
  if (articleIds.length === 0) return {};

  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { data, error } = await db.rpc("get_similar_article_groups", {
    p_article_ids: articleIds.map((id) => toDbId(id)),
  });

  if (error) {
    throw error;
  }

  const groups: Record<string, string> = {};
  (data ?? []).forEach((row: { article_id: number; group_key: number }) => {
    groups[String(row.article_id)] = String(row.group_key);
  });

  return groups;
}

/** Articles the user has starred, as a set for quick membership tests. */
export async function fetchStarredArticleIdsForUser(
  userId: string,
): Promise<Record<string, true>> {
  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { data, error } = await db
    .from("article_stars")
    .select("article_id")
    .eq("user_id", userId);

  if (error) {
    throw error;
  }

  const starred: Record<string, true> = {};
  (data ?? []).forEach((row: { article_id: number }) => {
    starred[String(row.article_id)] = true;
  });

  return starred;
}

export async function starArticleForUser(
  userId: string,
  articleId: string,
): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { error } = await db.from("article_stars").upsert(
    {
      user_id: userId,
      article_id: toDbId(articleId),
      starred_at: new Date().toISOString(),
    },
    { onConflict: "user_id,article_id", ignoreDuplicates: true },
  );

  if (error) {
    throw error;
  }
}

export async function unstarArticleForUser(
  userId: string,
  articleId: string,
): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { error } = await db
    .from("article_stars")
    .delete()
    .eq("user_id", userId)
    .eq("article_id", toDbId(articleId));

  if (error) {
    throw error;
  }
}

/**
 * Mark a whole batch read in one round trip — a section or a feed at a time.
 *
 * Separate from markArticleAsReadForUser because "clear this burst of noise"
 * is a different action from "I read this", and because doing it one request
 * per article would be dozens of round trips.
 */
export async function markArticlesAsReadForUser(
  userId: string,
  articleIds: string[],
): Promise<void> {
  if (articleIds.length === 0) return;

  const supabase = getSupabaseClient();
  const db = supabase as any;
  const readAt = new Date().toISOString();

  const { error } = await db.from("article_reads").upsert(
    articleIds.map((articleId) => ({
      user_id: userId,
      article_id: toDbId(articleId),
      read_at: readAt,
    })),
    { onConflict: "user_id,article_id", ignoreDuplicates: false },
  );

  if (error) {
    throw error;
  }
}

/** Undo for the above: removes read marks for exactly these articles. */
export async function unmarkArticlesAsReadForUser(
  userId: string,
  articleIds: string[],
): Promise<void> {
  if (articleIds.length === 0) return;

  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { error } = await db
    .from("article_reads")
    .delete()
    .eq("user_id", userId)
    .in(
      "article_id",
      articleIds.map((id) => toDbId(id)),
    );

  if (error) {
    throw error;
  }
}

export async function clearReadArticlesForUser(userId: string): Promise<void> {
  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { error } = await db
    .from("article_reads")
    .delete()
    .eq("user_id", userId);

  if (error) {
    throw error;
  }
}

export async function fetchReadStatisticsForUser(
  userId: string,
): Promise<ReadStatisticsRecord[]> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { data, error } = await db
    .from("article_reads")
    .select(
      `
      article_id,
      read_at,
      articles (
        feed_id,
        feeds (
          name
        )
      )
    `,
    )
    .eq("user_id", userId)
    .order("read_at", { ascending: false });

  if (error) {
    throw error;
  }

  return (data || []).map((row: any) => ({
    articleId: String(row.article_id),
    readAt: row.read_at,
    feedId:
      row.articles && row.articles.feed_id !== null
        ? String(row.articles.feed_id)
        : null,
    feedName: row.articles?.feeds?.name || "Unknown feed",
  }));
}

export async function fetchOldestReadAtForUser(
  userId: string,
): Promise<string | null> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();

  const { data, error } = await supabase
    .from("article_reads")
    .select("read_at")
    .eq("user_id", userId)
    .order("read_at", { ascending: true })
    .limit(1);

  if (error) {
    throw error;
  }

  return (data as { read_at: string }[] | null) &&
    (data as { read_at: string }[]).length > 0
    ? (data as { read_at: string }[])[0].read_at
    : null;
}

export async function fetchArticleCountForUser(
  userId: string,
  publishedSinceIso?: string,
): Promise<number> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();

  let query = supabase
    .from("articles")
    .select("id", { count: "exact", head: true });

  if (publishedSinceIso) {
    query = query.gte("published_at", publishedSinceIso);
  }

  const { count, error } = await query;

  if (error) {
    throw error;
  }

  return count || 0;
}

export async function fetchArticleTotalsByFeedForUser(
  userId: string,
  publishedSinceIso: string,
): Promise<FeedArticleTotalRecord[]> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { data, error } = await db
    .from("articles")
    .select(
      `
      feed_id,
      feeds (
        name
      )
    `,
    )
    .gte("published_at", publishedSinceIso);

  if (error) {
    throw error;
  }

  const counts = new Map<string, number>();
  for (const row of data || []) {
    const feedName = row.feeds?.name || "Unknown feed";
    counts.set(feedName, (counts.get(feedName) || 0) + 1);
  }

  return Array.from(counts.entries()).map(([feedName, count]) => ({
    feedName,
    count,
  }));
}

export interface PublishedArticleStatisticsRecord {
  publishedAt: string;
}

export async function fetchPublishedArticleStatisticsForUser(
  userId: string,
  publishedSinceIso: string,
): Promise<PublishedArticleStatisticsRecord[]> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();

  const { data, error } = await supabase
    .from("articles")
    .select("published_at")
    .gte("published_at", publishedSinceIso)
    .order("published_at", { ascending: false });

  if (error) {
    throw error;
  }

  return (data || []).map((row: any) => ({
    publishedAt: row.published_at,
  }));
}
