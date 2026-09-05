# ADR: Collapse duplicate stories with trigram similarity in Postgres

**Date:** 2026-09-05
**Status:** Accepted

## Context
Heise, Tagesschau and ZDF cover the same story within minutes of each other, so
a section reads as three of everything. The volume, not the features, is what
makes Noctua tiring to scan.

## Decision
`get_similar_article_groups(article_ids, threshold, window_hours)` clusters
titles with `pg_trgm` and returns one group key per article — the lowest id in
its cluster, so the key is stable across calls. The client groups by that value
and shows the leader with a "+N similar" expander.

Two details carry the quality:

- **A time window** (48 hours by default). Without it a recurring headline
  folds a month of articles into one row.
- **Connected components**, not pairwise matching. If A matches B and B matches
  C, all three group even when A and C are not similar enough to match
  directly — which is exactly how a story mutates as outlets reword it.

The 0.55 threshold was calibrated against real German headlines rather than
guessed:

| Similarity | Pair | Same story? |
| ---: | :--- | :--- |
| 0.688 | "Bundestag beschließt neues Klimagesetz" / "Neues Klimagesetz vom Bundestag beschlossen" | yes |
| 0.581 | "iPhone 17 vorgestellt" / "Apple stellt iPhone 17 vor" | yes |
| 0.500 | "Die Lage am Morgen" / "Die Lage am Abend" | no |
| 0.244 | "Habeck kündigt Rücktritt an" / "Scholz kündigt Neuwahlen an" | no |

It sits in the gap between the daily-briefing pair and the loosest genuine
match.

## Consequences
- Nothing is hidden: the expander names how many were folded in and opens them.
- Titles that differ only by a number group (a series, "Folge 12" / "Folge 13").
  That is the same measurement that makes reworded headlines group, and the
  expander makes it visible rather than lossy.
- The join is O(n²) similarity comparisons over the page — a few hundred rows,
  filtered by the time window first, so milliseconds. It would need an index
  strategy if a page ever meant thousands.
- Keyboard navigation walks leaders only; a collapsed article is not a stop.
- Pinned by `supabase/tests/article_similarity.test.sql`, including that an
  identical headline from a month ago stays a separate story.
