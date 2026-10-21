-- Noctua's statistics, aggregated in Postgres.
--
-- Both clients used to fetch every read and every article of the window as
-- rows and count them themselves. PostgREST answers at most 1,000 rows per
-- request, so once a reader passed 1,000 reads — or their feeds published more
-- than 1,000 articles in the window, which takes about a week — every figure
-- built on those rows was quietly cut short: total reads, streaks, published
-- per week, the per-feed shares and both heatmaps. Android's query for
-- published articles had no order, so it even counted an arbitrary 1,000.
--
-- This returns the aggregates instead, one small JSON document, so the web and
-- the phone show the same numbers however much there is
-- (decisions/2026-04-06-aggregate-in-postgres-rpcs.md).
--
-- It runs as the caller: RLS decides which articles count, exactly as it did
-- for the queries it replaces — an article is visible while one of the
-- caller's sections holds its feed. Reads are the caller's own; a read of an
-- article no longer visible still counts, under no feed.
--
-- Days, weeks (Monday first) and hours are in public.nexus_time_zone(), like
-- every other report. The window is p_weeks whole weeks ending with the
-- current one, or fewer while reading is newer than that.

create or replace function public.noctua_reading_statistics(p_weeks integer default 8)
returns jsonb
language sql
stable
security invoker
set search_path = public
as $$
  with params as (
    select
      public.nexus_time_zone() as tz,
      greatest(1, least(coalesce(p_weeks, 8), 104)) as weeks
  ),
  reads as (
    select
      (r.read_at at time zone p.tz) as local_at,
      a.feed_id,
      a.published_at
    from public.article_reads r
    cross join params p
    left join public.articles a on a.id = r.article_id
    where r.user_id = auth.uid()
  ),
  bounds as (
    select
      p.tz,
      (now() at time zone p.tz)::date as today,
      date_trunc('week', now() at time zone p.tz)::date as week_start,
      greatest(1, least(
        p.weeks,
        coalesce(
          (date_trunc('week', now() at time zone p.tz)::date
            - date_trunc('week', (select min(local_at) from reads))::date) / 7 + 1,
          p.weeks
        )
      )) as weeks
    from params p
  ),
  win as (
    select
      b.*,
      b.week_start - (b.weeks - 1) * 7 as window_start,
      ((b.week_start - (b.weeks - 1) * 7)::timestamp at time zone b.tz) as window_start_at,
      (b.week_start::timestamp at time zone b.tz) as week_start_at
    from bounds b
  ),
  window_reads as (
    select r.*
    from reads r, win w
    where r.local_at >= w.window_start
  ),
  published as (
    select
      a.feed_id,
      (a.published_at at time zone w.tz) as local_at,
      a.published_at >= w.week_start_at as this_week
    from public.articles a, win w
    where a.published_at >= w.window_start_at
  ),
  feed_reads as (
    select
      r.feed_id,
      count(*) as read,
      count(*) filter (where r.published_at >= w.window_start_at) as read_published
    from window_reads r, win w
    group by r.feed_id
  ),
  feed_published as (
    select feed_id, count(*) as published
    from published
    group by feed_id
  )
  select jsonb_build_object(
    'weeks', w.weeks,
    'today', w.today,
    'window_start', w.window_start,
    -- Days of the window so far, today included — not the rest of this week.
    'window_days', w.today - w.window_start + 1,
    'total_read', (select count(*) from reads),
    'total_articles', (select count(*) from public.articles),
    'this_week_read', (select count(*) from reads r where r.local_at >= w.week_start),
    'this_week_published', (select count(*) from published where this_week),
    'window_read', (select count(*) from window_reads),
    'window_published', (select count(*) from published),
    -- Every day with a read, ever, for the streaks.
    'read_days', coalesce(
      (select jsonb_agg(day order by day) from (select distinct local_at::date as day from reads) d),
      '[]'::jsonb
    ),
    -- Feeds read in the window. read_published counts the reads of articles
    -- also published in the window, so read_published / published is a share
    -- of the same articles and never passes 100%.
    'feeds', coalesce(
      (select jsonb_agg(jsonb_build_object(
          'feed', f.name,
          'read', fr.read,
          'read_published', fr.read_published,
          'published', coalesce(fp.published, 0)
        ) order by fr.read desc, f.name)
       from feed_reads fr
       left join public.feeds f on f.id = fr.feed_id
       left join feed_published fp on fp.feed_id = fr.feed_id),
      '[]'::jsonb
    ),
    -- [weekday (0 = Monday), hour, count] for every cell with anything in it.
    'read_hours', coalesce(
      (select jsonb_agg(jsonb_build_array(weekday, hour, n) order by weekday, hour)
       from (
         select extract(isodow from local_at)::int - 1 as weekday, extract(hour from local_at)::int as hour, count(*) as n
         from window_reads group by 1, 2
       ) h),
      '[]'::jsonb
    ),
    'published_hours', coalesce(
      (select jsonb_agg(jsonb_build_array(weekday, hour, n) order by weekday, hour)
       from (
         select extract(isodow from local_at)::int - 1 as weekday, extract(hour from local_at)::int as hour, count(*) as n
         from published group by 1, 2
       ) h),
      '[]'::jsonb
    )
  )
  from win w;
$$;

comment on function public.noctua_reading_statistics(integer) is
  'Noctua statistics for the caller: totals, streak days, per-feed reads and hour-of-week heatmaps over the last p_weeks weeks.';

revoke all on function public.noctua_reading_statistics(integer) from public, anon;
grant execute on function public.noctua_reading_statistics(integer) to authenticated;
