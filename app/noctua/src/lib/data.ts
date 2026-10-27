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

/** One of today's reads: a star over the end of the feed. */
export interface TodayRead {
  id: string;
  title: string;
  url: string;
  feed_name: string;
  read_at: string;
}

/** What was read since `since`, oldest first — the stars over the end of the feed. */
export async function fetchReadsSinceForUser(
  userId: string,
  since: Date,
): Promise<TodayRead[]> {
  const supabase = getSupabaseClient();
  const db = supabase as any;
  const { data, error } = await db
    .from("article_reads")
    .select("article_id,read_at,articles(id,title,url,feeds(name))")
    .eq("user_id", userId)
    .gte("read_at", since.toISOString())
    .order("read_at", { ascending: true })
    .limit(150);

  if (error) {
    throw error;
  }

  // An article outside the reader's sections is hidden by RLS: no star for it.
  return (data || [])
    .filter((row: any) => row.articles)
    .map((row: any) => ({
      id: String(row.article_id),
      title: row.articles.title || "Untitled",
      url: row.articles.url,
      feed_name: row.articles.feeds?.name || "Unknown feed",
      read_at: row.read_at,
    }));
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

/** One feed's reads in the statistics window, and what it published there. */
export interface FeedReadStatistics {
  feed: string | null;
  read: number;
  /** Reads of articles that were also published in the window. */
  readPublished: number;
  published: number;
}

/**
 * Noctua's statistics, aggregated by `noctua_reading_statistics` — counted in
 * Postgres, so nothing is cut off at PostgREST's 1,000 rows. Days, weeks and
 * hours are in the backend's reporting time zone.
 */
export interface ReadingStatistics {
  /** The window in whole weeks, Monday first: the configured number, or fewer while reading is newer. */
  weeks: number;
  /** Today and the window's first day, as YYYY-MM-DD. */
  today: string;
  windowStart: string;
  /** Days of the window so far, today included. */
  windowDays: number;
  totalRead: number;
  totalArticles: number;
  thisWeekRead: number;
  thisWeekPublished: number;
  windowRead: number;
  windowPublished: number;
  /** Every day with a read, ever, ascending, as YYYY-MM-DD. */
  readDays: string[];
  feeds: FeedReadStatistics[];
  /** [weekday (0 = Monday), hour, count] for the window's non-empty cells. */
  readHours: [number, number, number][];
  publishedHours: [number, number, number][];
}

export async function fetchReadingStatisticsForUser(
  userId: string,
  weeks: number,
): Promise<ReadingStatistics> {
  await ensureProfile(userId);

  const supabase = getSupabaseClient();
  const db = supabase as any;

  const { data, error } = await db.rpc("noctua_reading_statistics", {
    p_weeks: weeks,
  });

  if (error) {
    throw error;
  }

  const row = data || {};
  return {
    weeks: Number(row.weeks) || 1,
    today: String(row.today ?? ""),
    windowStart: String(row.window_start ?? ""),
    windowDays: Number(row.window_days) || 0,
    totalRead: Number(row.total_read) || 0,
    totalArticles: Number(row.total_articles) || 0,
    thisWeekRead: Number(row.this_week_read) || 0,
    thisWeekPublished: Number(row.this_week_published) || 0,
    windowRead: Number(row.window_read) || 0,
    windowPublished: Number(row.window_published) || 0,
    readDays: (row.read_days || []).map(String),
    feeds: (row.feeds || []).map((feed: any) => ({
      feed: feed.feed ?? null,
      read: Number(feed.read) || 0,
      readPublished: Number(feed.read_published) || 0,
      published: Number(feed.published) || 0,
    })),
    readHours: row.read_hours || [],
    publishedHours: row.published_hours || [],
  };
}
