# An article's id says nothing about how recent it is

**Status:** Resolved
**Discovered:** 2026-11-27

## Problem
`fetch-rss` upserts on `(feed_id, url)`, so an article keeps its id while its
feed rewrites it. A liveblog (ZDF's Ukraine and Middle East ones, ids 90 and
109) was first fetched in March and has had a fresh `published_at` every day
since: one of the lowest ids, always at the top of the feed.

Android capped its seen set at 5,000 by keeping the highest ids. With more
than 5,000 seen, marking such an article seen dropped it in the same write, so
it was back after every refresh — scrolled past, opened and read, it stayed.

## Impact
Anything that orders, caps or prunes articles by id treats long-lived articles
as old. Use `published_at`, or the time the reader acted, instead.

## Resolution
The seen list keeps the order articles were seen in and lets go of the ones
seen longest ago (`withSeen` in `android/noctua/.../data/NoctuaPreferences.kt`),
under a new key; the old set is read once as seen before everything else. The
web keeps every seen id and was never affected.
