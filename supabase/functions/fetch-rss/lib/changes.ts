import { ArticleInsert } from "./types.ts";

/** An article as stored, in the columns fetch-rss writes. */
export type StoredArticle = Omit<ArticleInsert, "feed_id">;

/** The columns compared; feed_id and url are the key. */
export const STORED_COLUMNS =
  "url, title, published_at, updated_at, author, summary, image_url, tags";

function sameText(a: string | null | undefined, b: string | null | undefined) {
  return (a ?? null) === (b ?? null);
}

// Postgres answers "2026-11-05T07:00:00+00:00", the feed gives "…:00.000Z".
function sameTime(a: string | null | undefined, b: string | null | undefined) {
  if (!a || !b) return !a && !b;
  return Date.parse(a) === Date.parse(b);
}

function sameTags(a: string[] | null | undefined, b: string[] | null | undefined) {
  const x = a ?? [];
  const y = b ?? [];
  return x.length === y.length && x.every((tag, i) => tag === y[i]);
}

export function isUnchanged(fresh: ArticleInsert, stored: StoredArticle) {
  return (
    sameText(fresh.title, stored.title) &&
    sameTime(fresh.published_at, stored.published_at) &&
    sameTime(fresh.updated_at, stored.updated_at) &&
    sameText(fresh.author, stored.author) &&
    sameText(fresh.summary, stored.summary) &&
    sameText(fresh.image_url, stored.image_url) &&
    sameTags(fresh.tags, stored.tags)
  );
}

/**
 * The articles worth writing: new ones and ones whose content changed.
 * Rewriting an identical row costs a new row version, WAL and later a vacuum
 * (decisions/2026-11-05-scheduled-jobs-share-minutes.md).
 */
export function articlesToWrite(
  fresh: ArticleInsert[],
  stored: StoredArticle[],
): ArticleInsert[] {
  const byUrl = new Map(stored.map((row) => [row.url, row]));
  return fresh.filter((article) => {
    const existing = byUrl.get(article.url);
    return !existing || !isUnchanged(article, existing);
  });
}
