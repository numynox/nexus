-- RLS is the whole authorization story in this project: there is no server, and
-- the anon key ships in the browser bundle. These tests assert the three apps'
-- deliberately different scoping models, including Annona's deliberate sharing
-- — so that "everyone can see it" stays a decision rather than an accident.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(13);

-- ── Fixtures, created as postgres (RLS does not apply) ─────────────────────
insert into auth.users (id, email) values
  ('bbbb0000-0000-4000-8000-000000000001', 'rls-owner@test.local'),
  ('bbbb0000-0000-4000-8000-000000000002', 'rls-other@test.local');

-- Noctua: the feed catalogue is shared, the arrangement of it is personal.
insert into public.feeds (id, name, url, enabled)
values (900001, 'RLS T Feed', 'https://example.invalid/rls-test-feed', true);

insert into public.articles (id, feed_id, title, url)
values (900001, 900001, 'RLS T Article', 'https://example.invalid/rls-test-article');

insert into public.sections (id, title, user_id)
values (900001, 'RLS T Section', 'bbbb0000-0000-4000-8000-000000000001');

insert into public.sections_feeds (section_id, feed_id) values (900001, 900001);

insert into public.article_reads (user_id, article_id)
values ('bbbb0000-0000-4000-8000-000000000001', 900001);

-- Vibilia: access is per car, via owner_id or a car_access row.
insert into public.cars (id, name, owner_id, tank_capacity)
values ('dddd0000-0000-4000-8000-000000000001', 'RLS T Car', 'bbbb0000-0000-4000-8000-000000000001', 50);

insert into public.refuel_events
  (car_id, user_id, fueled_at, mileage, liters, total_price, price_per_liter_calculated)
values
  ('dddd0000-0000-4000-8000-000000000001', 'bbbb0000-0000-4000-8000-000000000001',
   '2026-01-01 10:00+00', 1000, 30, 45.00, 1.500);

-- Annona: shared household data, attributed through the item log.
-- Its ids are GENERATED ALWAYS, so keep the generated one to assert against.
create temporary table rls_fixture_ids as
with inserted as (
  insert into public.annona_products (name, brand)
  values ('RLS T Product', 'RLS T Brand')
  returning id
)
select id as product_id from inserted;

grant select on rls_fixture_ids to authenticated;

-- ── Noctua: personal arrangement, shared catalogue ─────────────────────────
set local role authenticated;
set local request.jwt.claims to '{"sub":"bbbb0000-0000-4000-8000-000000000001","role":"authenticated"}';

select is(
  (select count(*) from public.sections where id = 900001), 1::bigint,
  'Noctua: a user sees their own section'
);

select is(
  (select count(*) from public.article_reads where article_id = 900001), 1::bigint,
  'Noctua: a user sees their own read marks'
);

select is(
  (select count(*) from public.articles where id = 900001), 1::bigint,
  'Noctua: a user sees articles from a feed in one of their own sections'
);

set local request.jwt.claims to '{"sub":"bbbb0000-0000-4000-8000-000000000002","role":"authenticated"}';

select is(
  (select count(*) from public.sections where id = 900001), 0::bigint,
  'Noctua: another user cannot see that section'
);

select is(
  (select count(*) from public.article_reads where article_id = 900001), 0::bigint,
  'Noctua: another user cannot see those read marks'
);

select is(
  (select count(*) from public.feeds where id = 900001), 1::bigint,
  'Noctua: the feed catalogue is readable by any authenticated user'
);

select is(
  (select count(*) from public.articles where id = 900001), 0::bigint,
  'Noctua: articles are NOT global — a user with no section covering the feed sees none'
);

-- ── Vibilia: per-car ACL ───────────────────────────────────────────────────
select is(
  (select count(*) from public.cars where id = 'dddd0000-0000-4000-8000-000000000001'), 0::bigint,
  'Vibilia: a car is invisible to a user who neither owns it nor was granted access'
);

select is(
  (select count(*) from public.refuel_events where car_id = 'dddd0000-0000-4000-8000-000000000001'), 0::bigint,
  'Vibilia: that car''s refuel events are invisible too'
);

-- Grant access, then look again as the same user.
reset role;
insert into public.car_access (car_id, user_id)
values ('dddd0000-0000-4000-8000-000000000001', 'bbbb0000-0000-4000-8000-000000000002');

set local role authenticated;
set local request.jwt.claims to '{"sub":"bbbb0000-0000-4000-8000-000000000002","role":"authenticated"}';

select is(
  (select count(*) from public.cars where id = 'dddd0000-0000-4000-8000-000000000001'), 1::bigint,
  'Vibilia: a car_access row makes the car visible'
);

select is(
  (select count(*) from public.refuel_events where car_id = 'dddd0000-0000-4000-8000-000000000001'), 1::bigint,
  'Vibilia: and its refuel events with it'
);

-- ── Annona: shared on purpose ──────────────────────────────────────────────
select is(
  (select count(*) from public.annona_products
     where id = (select product_id from rls_fixture_ids)), 1::bigint,
  'Annona: products are shared — a second user sees what the first created'
);

update public.annona_products set quantity = '500g'
where id = (select product_id from rls_fixture_ids);

select is(
  (select quantity from public.annona_products
     where id = (select product_id from rls_fixture_ids)), '500g',
  'Annona: and can change it — the household model is read/write for everyone'
);

select * from finish();

rollback;
