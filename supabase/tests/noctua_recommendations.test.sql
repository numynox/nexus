-- Noctua recommendations are shared on purpose: everyone sees who recommended
-- what, and noctua_recommended_articles hands the article itself to readers
-- who do not follow its feed. Writing stays one's own, and only for an article
-- the recommender can see.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(9);

insert into auth.users (id, email) values
  ('cccc0000-0000-4000-8000-000000000001', 'rec-sender@test.local'),
  ('cccc0000-0000-4000-8000-000000000002', 'rec-other@test.local');

insert into public.feeds (id, name, url, enabled)
values (910001, 'REC T Feed', 'https://example.invalid/rec-test-feed', true);

insert into public.articles (id, feed_id, title, url)
values (910001, 910001, 'REC T Article', 'https://example.invalid/rec-test-article');

-- Only the sender follows the feed.
insert into public.sections (id, title, user_id)
values (910001, 'REC T Section', 'cccc0000-0000-4000-8000-000000000001');
insert into public.sections_feeds (section_id, feed_id) values (910001, 910001);

-- ── The sender ─────────────────────────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims to '{"sub":"cccc0000-0000-4000-8000-000000000001","role":"authenticated"}';

select lives_ok(
  $$ insert into public.noctua_recommendations (article_id, user_id)
     values (910001, 'cccc0000-0000-4000-8000-000000000001') $$,
  'A reader can recommend an article they can see'
);

select throws_ok(
  $$ insert into public.noctua_recommendations (article_id, user_id)
     values (910001, 'cccc0000-0000-4000-8000-000000000002') $$,
  '42501', null,
  'A reader cannot recommend in someone else''s name'
);

select is(
  (select mine::text || '/' || from_others::text from public.noctua_recommended_articles() where article_id = 910001),
  'true/false',
  'The sender sees their own recommendation as theirs, not from others'
);

-- ── Another reader, who does not follow the feed ───────────────────────────
set local request.jwt.claims to '{"sub":"cccc0000-0000-4000-8000-000000000002","role":"authenticated"}';

select is(
  (select count(*) from public.articles where id = 910001), 0::bigint,
  'The other reader cannot see the article directly'
);

select is(
  (select count(*) from public.noctua_recommendations where article_id = 910001), 1::bigint,
  'The other reader sees that it was recommended'
);

select is(
  (select title || '/' || from_others::text || '/' || (recommenders -> 0 ->> 'name')
   from public.noctua_recommended_articles() where article_id = 910001),
  'REC T Article/true/rec-sender',
  'The RPC hands them the article, flagged as from others, with the sender''s name'
);

select throws_ok(
  $$ insert into public.noctua_recommendations (article_id, user_id)
     values (910001, 'cccc0000-0000-4000-8000-000000000002') $$,
  '42501', null,
  'A reader cannot recommend an article outside their own sections'
);

delete from public.noctua_recommendations where article_id = 910001;
reset role;
select is(
  (select count(*) from public.noctua_recommendations where article_id = 910001), 1::bigint,
  'Nobody can withdraw someone else''s recommendation'
);

-- ── Anonymous ──────────────────────────────────────────────────────────────
set local role anon;
select throws_ok(
  $$ select * from public.noctua_recommended_articles() $$,
  '42501', null,
  'Anonymous callers cannot list recommendations'
);

select * from finish();

rollback;
