# ADR: Noctua recommendations — one reader marks an article for everyone

**Date:** 2026-10-31
**Status:** Accepted

## Context

Noctua was strictly per-user: sections, reads and stars are scoped to their
owner, and an article is visible only while one of *your* sections holds its
feed. The request was a way for one reader to point every other reader at an
interesting article: swipe left (on Android this replaced "mark read", which
stays in the long-press menu), the article is marked prominently for
everyone, it surfaces at the top of everyone else's feed on their next load —
even if they had already scrolled past it — they see who recommended it, and
the recommender can withdraw it by swiping again.

The user's word was "notify"; the UI says **Recommend** / "Recommended by …",
which says what it is without suggesting a push notification. There is none.

## Decision

- **`noctua_recommendations (article_id, user_id, recommended_at)`**, one row
  per recommender. Every authenticated user may *read* every row — sharing is
  the point. Inserting is one's own and only for an article one can see (the
  check `article_stars` makes); deleting is one's own.
- **`noctua_recommended_articles(p_days = 30)`**, `SECURITY DEFINER`: the
  recommended articles themselves, with feed name and icon, the recommenders'
  names (profile `full_name`, else the address before the @), `mine` and
  `from_others`. Definer rights because a recipient may not follow the feed;
  this widens article visibility by exactly what someone chose to share. It
  returns nothing to an anonymous caller (`auth.uid() is null`) and is not
  granted to `anon`.
- **Pinning is a client concern.** An article recommended by someone other
  than the reader, and unseen when the feed loads, leads the feed as a
  "Recommended" group in whichever section is open, and is not repeated in its
  day. Once scrolled past it is seen and back to an ordinary, highlighted card
  on the next load.
- **The seen reset happens on the device**, because "seen" lives there
  (`localStorage` / DataStore). Before the feed takes its seen snapshot, each
  article recommended by someone else is removed from the seen set — **once per
  article per device**, recorded in `noctua_resurfaced_recommendations`. A
  second recommender does not bring it back again.
- The highlight (outline plus "Recommended by anna and you") shows in every
  list, the library included, and for the recommender too. The recommender's
  own feed is never reordered.
- The web has no swipe; it has a megaphone button beside the star and the `n`
  shortcut.

## Consequences

- RLS on the table is deliberately open for `select`; `rls.test.sql`'s
  per-user assumptions do not apply to it. `noctua_recommendations.test.sql`
  pins the intended shape.
- Thirty days of recommendations come back on every feed load — a small
  payload with a handful of users; revisit if it grows.
- Pruning: `prune_articles_unread` can still delete an unread recommended
  article older than 90 days; its recommendations cascade away with it.
- Older APKs ignore the table; nothing existing was changed.
