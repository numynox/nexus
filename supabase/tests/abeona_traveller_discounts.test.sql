-- Abeona: the BahnCard discount per traveller on a trip. Saving with one per
-- traveller, the trip's shared value for older clients, older clients' writes
-- spreading to everybody, and the warnings checking each traveller against
-- their own discount.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(16);

insert into auth.users (id, email) values
  ('abe0d150-0000-4000-8000-000000000001', 'abeona-disc-a@test.local'),
  ('abe0d150-0000-4000-8000-000000000002', 'abeona-disc-b@test.local');

insert into public.profiles (id, email, full_name) values
  ('abe0d150-0000-4000-8000-000000000001', 'abeona-disc-a@test.local', 'Ada'),
  ('abe0d150-0000-4000-8000-000000000002', 'abeona-disc-b@test.local', 'Ben')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table d_ids (key text primary key, id uuid);
grant select, insert, update on d_ids to authenticated;

create temporary table d_today as
select (now() at time zone public.nexus_time_zone())::date as d;
grant select on d_today to authenticated;

-- A one-leg trip ten days from now.
create temporary table d_legs as
select jsonb_build_array(jsonb_build_object(
  'mode', 'train', 'product', 'ICE', 'line_name', 'ICE 1',
  'from', jsonb_build_object('id', 'x:ffm', 'name', 'Frankfurt Hbf', 'lat', 50.1069, 'lon', 8.6619,
    'planned', ((select d from d_today) + 10 + time '12:00') at time zone public.nexus_time_zone()),
  'to', jsonb_build_object('id', 'x:ful', 'name', 'Fulda', 'lat', 50.5546, 'lon', 9.6839,
    'planned', ((select d from d_today) + 10 + time '13:00') at time zone public.nexus_time_zone())
)) as legs;
grant select on d_legs to authenticated;

-- ── User B: a stranger's traveller ─────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0d150-0000-4000-8000-000000000002", "role": "authenticated"}';
select public.abeona_ensure_household();
insert into d_ids select 'ben', id from public.abeona_travellers where user_id = 'abe0d150-0000-4000-8000-000000000002';

-- ── User A: three travellers, two cards ────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0d150-0000-4000-8000-000000000001", "role": "authenticated"}';
insert into d_ids values ('household', public.abeona_ensure_household());
insert into d_ids select 'ada', id from public.abeona_travellers where user_id = 'abe0d150-0000-4000-8000-000000000001';
with x as (
  insert into public.abeona_travellers (household_id, name)
  values ((select id from d_ids where key = 'household'), 'Kid') returning id)
insert into d_ids select 'kid', id from x;
with x as (
  insert into public.abeona_travellers (household_id, name)
  values ((select id from d_ids where key = 'household'), 'Gran') returning id)
insert into d_ids select 'gran', id from x;

-- Ada holds a BahnCard 25; Gran's BahnCard 50 has expired; Kid has none.
insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
values ((select id from d_ids where key = 'ada'), 25, (select d from d_today) - 100, (select d from d_today) + 200);
insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to, subscription)
values ((select id from d_ids where key = 'gran'), 50, (select d from d_today) - 400, (select d from d_today) - 30, false);

-- ── One ticket, three travellers, three discounts ──────────────────────────
insert into d_ids select 'mixed', public.abeona_save_trip(
  jsonb_build_object('fare_type', 'saver', 'travellers', jsonb_build_array(
    jsonb_build_object('id', (select id from d_ids where key = 'ada'), 'bahncard_discount', 25),
    jsonb_build_object('id', (select id from d_ids where key = 'kid'), 'bahncard_discount', 50),
    jsonb_build_object('id', (select id from d_ids where key = 'gran'), 'bahncard_discount', null))),
  (select legs from d_legs));

select results_eq(
  $$ select t.name, tt.bahncard_discount::int from public.abeona_trip_travellers tt
     join public.abeona_travellers t on t.id = tt.traveller_id
     where tt.trip_id = (select id from d_ids where key = 'mixed') order by t.name $$,
  $$ values ('Ada', 25), ('Gran', null::int), ('Kid', 50) $$,
  'save_trip keeps each traveller''s own discount');

select is((select bahncard_discount from public.abeona_trips where id = (select id from d_ids where key = 'mixed')), null::smallint,
  'a trip with mixed discounts shares none');

select results_eq(
  $$ select traveller_name, kind from public.abeona_trip_warnings((select id from d_ids where key = 'mixed')) $$,
  $$ values ('Kid', 'no_card') $$,
  'only Kid is warned: Ada holds her BahnCard 25, Gran travels without one');

-- ── Everybody on the same discount ─────────────────────────────────────────
insert into d_ids select 'shared', public.abeona_save_trip(
  jsonb_build_object('fare_type', 'saver', 'travellers', jsonb_build_array(
    jsonb_build_object('id', (select id from d_ids where key = 'ada'), 'bahncard_discount', 25),
    jsonb_build_object('id', (select id from d_ids where key = 'kid'), 'bahncard_discount', 25))),
  (select legs from d_legs));

select is((select bahncard_discount::int from public.abeona_trips where id = (select id from d_ids where key = 'shared')), 25,
  'a discount every traveller shares is the trip''s too, for older clients');

-- ── Changing it from the trip page ─────────────────────────────────────────
select public.abeona_save_trip_travellers((select id from d_ids where key = 'shared'), jsonb_build_array(
  jsonb_build_object('id', (select id from d_ids where key = 'ada'), 'bahncard_discount', 50),
  jsonb_build_object('id', (select id from d_ids where key = 'gran'), 'bahncard_discount', null)));

select results_eq(
  $$ select t.name, tt.bahncard_discount::int from public.abeona_trip_travellers tt
     join public.abeona_travellers t on t.id = tt.traveller_id
     where tt.trip_id = (select id from d_ids where key = 'shared') order by t.name $$,
  $$ values ('Ada', 50), ('Gran', null::int) $$,
  'save_trip_travellers replaces who travels and with which discount');

select is((select bahncard_discount from public.abeona_trips where id = (select id from d_ids where key = 'shared')), null::smallint,
  'the trip''s shared value follows, and its trigger leaves the travellers'' values alone');

select results_eq(
  $$ select traveller_name, kind from public.abeona_trip_warnings((select id from d_ids where key = 'shared')) $$,
  $$ values ('Ada', 'no_card') $$,
  'Ada''s BahnCard 25 does not cover a BahnCard 50 share');

select throws_ok(
  format($$ select public.abeona_save_trip_travellers(%L, jsonb_build_array(jsonb_build_object('id', %L, 'bahncard_discount', 25))) $$,
         (select id from d_ids where key = 'shared'), (select id from d_ids where key = 'ben')),
  '42501', null, 'another household''s traveller cannot be put on the trip');

select throws_ok(
  $$ select public.abeona_save_trip_travellers('00000000-0000-4000-8000-000000000000', '[]') $$,
  'P0001', 'Trip not found.', 'an unknown trip is refused');

-- ── Older clients ──────────────────────────────────────────────────────────
insert into d_ids select 'legacy', public.abeona_save_trip(
  jsonb_build_object('fare_type', 'saver', 'bahncard_discount', 50,
    'traveller_ids', jsonb_build_array((select id from d_ids where key = 'ada'), (select id from d_ids where key = 'kid'))),
  (select legs from d_legs));

select results_eq(
  $$ select bahncard_discount::int from public.abeona_trip_travellers
     where trip_id = (select id from d_ids where key = 'legacy') $$,
  $$ values (50), (50) $$,
  'an older client''s trip discount goes to every traveller');

update public.abeona_trips set bahncard_discount = 25 where id = (select id from d_ids where key = 'legacy');

select results_eq(
  $$ select bahncard_discount::int from public.abeona_trip_travellers
     where trip_id = (select id from d_ids where key = 'legacy') $$,
  $$ values (25), (25) $$,
  'an older trip page writing the trip''s discount changes everybody''s');

select public.abeona_set_trip_travellers((select id from d_ids where key = 'legacy'),
  array[(select id from d_ids where key = 'ada'), (select id from d_ids where key = 'kid'), (select id from d_ids where key = 'gran')]);

select is(
  (select bahncard_discount::int from public.abeona_trip_travellers
   where trip_id = (select id from d_ids where key = 'legacy') and traveller_id = (select id from d_ids where key = 'gran')),
  25, 'whoever an older client adds gets the trip''s discount');

insert into d_ids select 'default', public.abeona_save_trip(
  jsonb_build_object('fare_type', 'saver', 'bahncard_discount', 25), (select legs from d_legs));

select results_eq(
  $$ select traveller_id, bahncard_discount::int from public.abeona_trip_travellers
     where trip_id = (select id from d_ids where key = 'default') $$,
  $$ select (select id from d_ids where key = 'ada'), 25 $$,
  'without a list, the caller travels with the trip''s discount');

-- ── Neither BahnCard 100 nor nobody is lost ────────────────────────────────
insert into d_ids select 'bc100', public.abeona_save_trip(
  jsonb_build_object('fare_type', 'bahncard100', 'travellers', jsonb_build_array(
    jsonb_build_object('id', (select id from d_ids where key = 'kid'), 'bahncard_discount', null))),
  (select legs from d_legs));

select results_eq(
  $$ select traveller_name, kind from public.abeona_trip_warnings((select id from d_ids where key = 'bc100')) $$,
  $$ values ('Kid', 'no_card') $$,
  'a BahnCard 100 fare still needs a card for everybody');

select public.abeona_set_trip_travellers((select id from d_ids where key = 'legacy'), '{}');
select results_eq(
  $$ select kind from public.abeona_trip_warnings((select id from d_ids where key = 'legacy')) $$,
  $$ values ('no_travellers') $$,
  'a discounted fare with nobody on it still says so');

-- ── Grants ─────────────────────────────────────────────────────────────────
reset role;
select ok(
  not has_function_privilege('anon', 'public.abeona_save_trip_travellers(uuid, jsonb)', 'execute')
  and has_function_privilege('authenticated', 'public.abeona_save_trip_travellers(uuid, jsonb)', 'execute')
  and not has_function_privilege('authenticated', 'public.abeona_trips_spread_discount()', 'execute'),
  'only signed-in users save travellers; nobody calls the trigger function');

select * from finish();
rollback;
