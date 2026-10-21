-- Noctua's statistics: counted in Postgres, so nothing is cut off at
-- PostgREST's 1,000 rows, and scoped to the caller.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(9);

insert into auth.users (id, email) values
  ('cccc0000-0000-4000-8000-000000000001', 'stats-reader@test.local'),
  ('cccc0000-0000-4000-8000-000000000002', 'stats-other@test.local');

insert into public.feeds (id, name, url, enabled) values
  (920001, 'Stats Feed A', 'https://example.invalid/stats-a', true),
  (920002, 'Stats Feed B', 'https://example.invalid/stats-b', true);

insert into public.sections (id, title, user_id) values
  (920001, 'Stats Section', 'cccc0000-0000-4000-8000-000000000001');
insert into public.sections_feeds (section_id, feed_id) values (920001, 920001), (920001, 920002);

-- 1,200 articles in feed A this week, all read: more than one PostgREST page.
insert into public.articles (id, feed_id, title, url, published_at)
select 920000 + n, 920001, 'Stats A ' || n, 'https://example.invalid/stats-a/' || n,
       date_trunc('week', now() at time zone public.nexus_time_zone()) at time zone public.nexus_time_zone() + interval '1 second' * n
from generate_series(1, 1200) n
where date_trunc('week', now() at time zone public.nexus_time_zone()) at time zone public.nexus_time_zone() + interval '1 second' * n <= now();

-- Feed B: 4 articles in the window, 1 read; and one read of an old article.
insert into public.articles (id, feed_id, title, url, published_at) values
  (930001, 920002, 'Stats B 1', 'https://example.invalid/stats-b/1', now() - interval '1 hour'),
  (930002, 920002, 'Stats B 2', 'https://example.invalid/stats-b/2', now() - interval '2 hours'),
  (930003, 920002, 'Stats B 3', 'https://example.invalid/stats-b/3', now() - interval '3 hours'),
  (930004, 920002, 'Stats B 4', 'https://example.invalid/stats-b/4', now() - interval '4 hours'),
  (930005, 920002, 'Stats B old', 'https://example.invalid/stats-b/old', now() - interval '200 days');

insert into public.article_reads (user_id, article_id, read_at)
select 'cccc0000-0000-4000-8000-000000000001', id, least(now(), published_at + interval '1 second')
from public.articles where feed_id = 920001;

insert into public.article_reads (user_id, article_id, read_at) values
  ('cccc0000-0000-4000-8000-000000000001', 930001, now()),
  ('cccc0000-0000-4000-8000-000000000001', 930005, now()),
  -- Someone else's read never counts.
  ('cccc0000-0000-4000-8000-000000000002', 930002, now());

set local role authenticated;
set local request.jwt.claims to '{"sub":"cccc0000-0000-4000-8000-000000000001","role":"authenticated"}';

create temporary table stats as select public.noctua_reading_statistics(8) as s;

select is(
  (select (s->>'total_read')::int from stats),
  (select count(*)::int from public.article_reads where user_id = 'cccc0000-0000-4000-8000-000000000001'),
  'every read counts, past 1,000'
);

select is((select (s->>'weeks')::int from stats), 1, 'reading began this week, so the window is one week');

select is(
  (select (s->>'this_week_read')::int from stats),
  (select (s->>'total_read')::int from stats),
  'all of it was read this week'
);

select is(
  (select (f->>'read')::int from stats, jsonb_array_elements(s->'feeds') f where f->>'feed' = 'Stats Feed B'),
  2,
  'feed B: the new read and the old one'
);

select is(
  (select (f->>'read_published')::int from stats, jsonb_array_elements(s->'feeds') f where f->>'feed' = 'Stats Feed B'),
  1,
  'feed B: only one read was of an article published in the window'
);

select is(
  (select (f->>'published')::int from stats, jsonb_array_elements(s->'feeds') f where f->>'feed' = 'Stats Feed B'),
  4,
  'feed B published four in the window'
);

select is(
  (select (s->>'window_days')::int from stats),
  (select (now() at time zone public.nexus_time_zone())::date - date_trunc('week', now() at time zone public.nexus_time_zone())::date + 1),
  'the window counts the days so far, not the rest of the week'
);

select is(
  (select sum((h->>2)::int)::int from stats, jsonb_array_elements(s->'read_hours') h),
  (select (s->>'window_read')::int from stats),
  'the heatmap holds every read of the window'
);

reset role;
set local role anon;
select throws_ok(
  'select public.noctua_reading_statistics(8)',
  '42501',
  null,
  'signed out, there are no statistics'
);

select * from finish();
rollback;
