-- Group near-identical article titles, so the same story from Heise,
-- Tagesschau and ZDF collapses into one row instead of three.
--
-- Trigram similarity, not machine learning: pg_trgm compares the titles
-- directly. Two articles are the same story when their titles are similar
-- enough *and* they were published close together — the time window is what
-- stops a recurring headline ("Die Lage am Morgen") from folding a whole month
-- into one group.
--
-- The 0.55 default was calibrated against real German headlines:
--
--   0.688  "Bundestag beschließt neues Klimagesetz"
--          vs "Neues Klimagesetz vom Bundestag beschlossen"   same story
--   0.581  "iPhone 17 vorgestellt" vs "Apple stellt iPhone 17 vor"   same story
--   0.500  "Die Lage am Morgen" vs "Die Lage am Abend"    different briefings
--   0.244  "Habeck kündigt Rücktritt an"
--          vs "Scholz kündigt Neuwahlen an"               unrelated
--
-- It sits in the gap between the daily-briefing pair and the loosest genuine
-- match. Raising it splits real duplicates; lowering it folds a recurring
-- headline into one row.
--
-- Grouping is by connected component: if A matches B and B matches C, all three
-- share a group even when A and C are not similar enough to match directly.
-- The group key is the lowest article id in the component, so it is stable
-- across calls.

create extension if not exists pg_trgm with schema extensions;

create or replace function public.get_similar_article_groups(
  p_article_ids bigint[],
  p_threshold real default 0.55,
  p_window_hours integer default 48
)
returns table (
  article_id bigint,
  group_key bigint
)
language sql
stable
set search_path = public, extensions
as $$
  with recursive candidates as (
    select a.id, a.title, coalesce(a.published_at, a.created_at) as at
    from public.articles a
    where a.id = any(p_article_ids)
  ),
  pairs as (
    select x.id as a_id, y.id as b_id
    from candidates x
    join candidates y
      on x.id < y.id
     and abs(extract(epoch from (x.at - y.at))) <= p_window_hours * 3600
     and similarity(x.title, y.title) >= p_threshold
  ),
  edges as (
    select a_id as src, b_id as dst from pairs
    union all
    select b_id as src, a_id as dst from pairs
  ),
  -- Walk each component from every node; UNION dedups, so this terminates and
  -- min(root) per node is the lowest id reachable from it.
  reach as (
    select id as node, id as root from candidates
    union
    select e.dst, r.root
    from reach r
    join edges e on e.src = r.node
  )
  select node as article_id, min(root) as group_key
  from reach
  group by node;
$$;

comment on function public.get_similar_article_groups(bigint[], real, integer) is
  'Clusters articles by trigram title similarity within a time window. Returns one row per input article with the lowest id of its cluster as the group key.';

revoke all on function public.get_similar_article_groups(bigint[], real, integer) from public;
grant execute on function public.get_similar_article_groups(bigint[], real, integer) to authenticated;
grant execute on function public.get_similar_article_groups(bigint[], real, integer) to service_role;
