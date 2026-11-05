import { assertEquals } from "jsr:@std/assert@1";
import { articlesToWrite, type StoredArticle } from "./changes.ts";
import type { ArticleInsert } from "./types.ts";

const fresh: ArticleInsert = {
  feed_id: 1,
  url: "https://example.org/a",
  title: "A",
  published_at: "2026-11-05T07:00:00.000Z",
  updated_at: "2026-11-05T07:30:00.000Z",
  author: null,
  summary: "Text",
  image_url: null,
  tags: ["x", "y"],
};

// As PostgREST returns it: other timestamp spelling, no feed_id.
const stored: StoredArticle = {
  url: "https://example.org/a",
  title: "A",
  published_at: "2026-11-05T07:00:00+00:00",
  updated_at: "2026-11-05T07:30:00+00:00",
  author: null,
  summary: "Text",
  image_url: null,
  tags: ["x", "y"],
};

Deno.test("an identical article is not written again", () => {
  assertEquals(articlesToWrite([fresh], [stored]), []);
});

Deno.test("a new article is written", () => {
  const other = { ...fresh, url: "https://example.org/b" };
  assertEquals(articlesToWrite([other], [stored]), [other]);
});

Deno.test("a changed title, time or tag list is written", () => {
  for (const change of [
    { title: "A, corrected" },
    { updated_at: "2026-11-05T08:00:00.000Z" },
    { tags: ["x"] },
    { image_url: "https://example.org/a.jpg" },
  ]) {
    const changed = { ...fresh, ...change };
    assertEquals(articlesToWrite([changed], [stored]), [changed]);
  }
});

Deno.test("no tags and an empty tag list are the same", () => {
  assertEquals(articlesToWrite([{ ...fresh, tags: null }], [{ ...stored, tags: [] }]), []);
});
