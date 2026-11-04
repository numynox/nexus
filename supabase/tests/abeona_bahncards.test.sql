-- Abeona BahnCards: household scoping of the cards, the cancellation
-- deadlines, the trip warnings, who travels on a trip, and the storage paths
-- of card files, including after a merge on invite.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(38);

insert into auth.users (id, email) values
  ('abe0bc00-0000-4000-8000-000000000001', 'abeona-bc-a@test.local'),
  ('abe0bc00-0000-4000-8000-000000000002', 'abeona-bc-b@test.local'),
  ('abe0bc00-0000-4000-8000-000000000003', 'abeona-bc-c@test.local');

insert into public.profiles (id, email, full_name) values
  ('abe0bc00-0000-4000-8000-000000000001', 'abeona-bc-a@test.local', 'Ada'),
  ('abe0bc00-0000-4000-8000-000000000002', 'abeona-bc-b@test.local', 'Ben'),
  ('abe0bc00-0000-4000-8000-000000000003', 'abeona-bc-c@test.local', 'Cy')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table bc_ids (key text primary key, id uuid);
grant select, insert, update on bc_ids to authenticated;

-- "Today" as the RPCs see it.
create temporary table bc_today as
select (now() at time zone public.nexus_time_zone())::date as d;
grant select on bc_today to authenticated;

-- A one-leg trip ten days from now, at noon so it stays on one local date.
create temporary table bc_legs as
select jsonb_build_array(jsonb_build_object(
  'mode', 'train', 'product', 'ICE', 'line_name', 'ICE 1',
  'from', jsonb_build_object('id', 'x:ffm', 'name', 'Frankfurt Hbf', 'lat', 50.1069, 'lon', 8.6619,
    'planned', ((select d from bc_today) + 10 + time '12:00') at time zone public.nexus_time_zone()),
  'to', jsonb_build_object('id', 'x:ful', 'name', 'Fulda', 'lat', 50.5546, 'lon', 9.6839,
    'planned', ((select d from bc_today) + 10 + time '13:00') at time zone public.nexus_time_zone())
)) as legs;
grant select on bc_legs to authenticated;

-- ── User A: travellers and cards ───────────────────────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000001", "role": "authenticated"}';

insert into bc_ids values ('household_a', public.abeona_ensure_household());
insert into bc_ids select 'ada', id from public.abeona_travellers where user_id = 'abe0bc00-0000-4000-8000-000000000001';
with x as (
  insert into public.abeona_travellers (household_id, name)
  values ((select id from bc_ids where key = 'household_a'), 'Kid') returning id)
insert into bc_ids select 'kid', id from x;
with x as (
  insert into public.abeona_travellers (household_id, name)
  values ((select id from bc_ids where key = 'household_a'), 'Gran') returning id)
insert into bc_ids select 'gran', id from x;

-- Ada: a BahnCard 25, 2nd class, running for months.
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, travel_class, valid_from, valid_to)
  values ((select id from bc_ids where key = 'ada'), 25, 2,
          (select d from bc_today) - 100, (select d from bc_today) + 200)
  returning id)
insert into bc_ids select 'card_ada', id from x;

-- Gran: a BahnCard 50 that ends five days from now, before the trip.
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to, subscription)
  values ((select id from bc_ids where key = 'gran'), 50,
          (select d from bc_today) - 360, (select d from bc_today) + 5, false)
  returning id)
insert into bc_ids select 'card_gran', id from x;

select is((select notice_weeks::int from public.abeona_bahncards where id = (select id from bc_ids where key = 'card_ada')),
  4, 'the notice period defaults to four weeks');

select throws_ok(
  $$ insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
     values ((select id from bc_ids where key = 'ada'), 30, current_date, current_date + 1) $$,
  '23514', null, 'a BahnCard is a 25, 50 or 100');

select throws_ok(
  $$ insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
     values ((select id from bc_ids where key = 'ada'), 25, current_date, current_date - 1) $$,
  '23514', null, 'a card cannot end before it starts');

-- ── User B: a stranger sees and touches nothing ────────────────────────────
set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000002", "role": "authenticated"}';
insert into bc_ids values ('household_b', public.abeona_ensure_household());
insert into bc_ids select 'ben', id from public.abeona_travellers where user_id = 'abe0bc00-0000-4000-8000-000000000002';

select is((select count(*)::int from public.abeona_bahncards), 0, 'another household sees no cards');

select throws_ok(
  $$ insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
     values ((select id from bc_ids where key = 'ada'), 25, current_date, current_date + 1) $$,
  '42501', null, 'nobody can add a card to another household''s traveller');

update public.abeona_bahncards set notice_weeks = 0 where id = (select id from bc_ids where key = 'card_ada');
set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000001", "role": "authenticated"}';
select is((select notice_weeks::int from public.abeona_bahncards where id = (select id from bc_ids where key = 'card_ada')),
  4, 'nor change one');
set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000002", "role": "authenticated"}';

-- Ben's own card, with a file under his household's prefix.
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to, subscription)
  values ((select id from bc_ids where key = 'ben'), 50,
          (select d from bc_today) - 300, (select d from bc_today) + 65, true)
  returning id)
insert into bc_ids select 'card_ben', id from x;

select is((select count(*)::int from public.abeona_bahncard_deadlines(365)), 1,
  'deadlines are the household''s own');

-- ── Cancellation deadlines (user A) ────────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000001", "role": "authenticated"}';

-- Subscriptions under Kid, one per boundary. valid_to = today + 28 + n puts the
-- deadline n days away with the default four weeks.
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
  values ((select id from bc_ids where key = 'kid'), 25, (select d from bc_today) - 300, (select d from bc_today) + 28 + 14)
  returning id)
insert into bc_ids select 'dl_14', id from x;
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
  values ((select id from bc_ids where key = 'kid'), 25, (select d from bc_today) - 300, (select d from bc_today) + 28 + 15)
  returning id)
insert into bc_ids select 'dl_15', id from x;
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
  values ((select id from bc_ids where key = 'kid'), 25, (select d from bc_today) - 300, (select d from bc_today) + 10)
  returning id)
insert into bc_ids select 'dl_passed', id from x;
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
  values ((select id from bc_ids where key = 'kid'), 25, (select d from bc_today) - 300, (select d from bc_today))
  returning id)
insert into bc_ids select 'dl_last_day', id from x;
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
  values ((select id from bc_ids where key = 'kid'), 25, (select d from bc_today) - 300, (select d from bc_today) - 1)
  returning id)
insert into bc_ids select 'dl_ended', id from x;
-- Six weeks' notice, deadline today.
with x as (
  insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to, notice_weeks)
  values ((select id from bc_ids where key = 'kid'), 25, (select d from bc_today) - 300, (select d from bc_today) + 42, 6)
  returning id)
insert into bc_ids select 'dl_six_weeks', id from x;

create temporary table bc_deadlines as
select * from public.abeona_bahncard_deadlines(14);

select ok(exists (select 1 from bc_deadlines where card_id = (select id from bc_ids where key = 'dl_14') and days_left = 14),
  'a deadline exactly 14 days away is in a 14-day window');
select ok(not exists (select 1 from bc_deadlines where card_id = (select id from bc_ids where key = 'dl_15')),
  'one 15 days away is not');
select ok(exists (select 1 from bc_deadlines where card_id = (select id from bc_ids where key = 'dl_passed') and days_left = -18),
  'a passed deadline stays while the card runs, with negative days left');
select ok(exists (select 1 from bc_deadlines where card_id = (select id from bc_ids where key = 'dl_last_day')),
  'including on the card''s last day');
select ok(not exists (select 1 from bc_deadlines where card_id = (select id from bc_ids where key = 'dl_ended')),
  'not once the card has ended');
select ok(exists (select 1 from bc_deadlines where card_id = (select id from bc_ids where key = 'dl_six_weeks') and days_left = 0),
  'the notice period is the card''s own');
select ok(not exists (select 1 from bc_deadlines where card_id = (select id from bc_ids where key = 'card_gran')),
  'a card without a subscription has no deadline');
select is((select deadline from bc_deadlines where card_id = (select id from bc_ids where key = 'dl_14')),
  (select d + 14 from bc_today), 'the deadline is valid_to minus the notice period');
select is((select card_id from bc_deadlines limit 1), (select id from bc_ids where key = 'dl_last_day'),
  'soonest deadline first');

-- Kid's deadline cards would satisfy a BahnCard 25 trip; take them away again.
delete from public.abeona_bahncards where traveller_id = (select id from bc_ids where key = 'kid');

-- ── Who travels: the creator by default ────────────────────────────────────
insert into bc_ids values ('trip_default',
  public.abeona_save_trip('{"fare_type": "flex"}'::jsonb, (select legs from bc_legs)));

select results_eq(
  $$ select traveller_id from public.abeona_trip_travellers where trip_id = (select id from bc_ids where key = 'trip_default') $$,
  $$ select id from bc_ids where key = 'ada' $$,
  'a new trip has its creator''s traveller on it');

select is((select count(*)::int from public.abeona_trip_warnings((select id from bc_ids where key = 'trip_default'))), 0,
  'a fare without a BahnCard discount has no warnings');

-- ── Trip warnings ──────────────────────────────────────────────────────────
insert into bc_ids values ('trip_bc25',
  public.abeona_save_trip(
    jsonb_build_object('fare_type', 'saver', 'bahncard_discount', 25,
      'traveller_ids', jsonb_build_array((select id from bc_ids where key = 'ada'),
                                         (select id from bc_ids where key = 'kid'),
                                         (select id from bc_ids where key = 'gran'))),
    (select legs from bc_legs)));

select is((select bahncard_discount::int from public.abeona_trips where id = (select id from bc_ids where key = 'trip_bc25')),
  25, 'save_trip stores the BahnCard discount');
select is((select count(*)::int from public.abeona_trip_travellers where trip_id = (select id from bc_ids where key = 'trip_bc25')),
  3, 'and the travellers it was given');

create temporary table bc_warnings as
select * from public.abeona_trip_warnings((select id from bc_ids where key = 'trip_bc25'));

select ok(not exists (select 1 from bc_warnings where traveller_id = (select id from bc_ids where key = 'ada')),
  'a valid card of the kind raises nothing');
select results_eq(
  $$ select kind, message from bc_warnings where traveller_id = (select id from bc_ids where key = 'kid') $$,
  $$ values ('no_card'::text, 'Kid has no BahnCard 25.'::text) $$,
  'a traveller without a card is named');
select results_eq(
  $$ select kind, card_id from bc_warnings where traveller_id = (select id from bc_ids where key = 'gran') $$,
  $$ values ('expired'::text, (select id from bc_ids where key = 'card_gran')) $$,
  'a card that ends before the trip is called out (and a BahnCard 50 counts for a 25 fare)');
select ok((select message from bc_warnings where kind = 'expired') like 'Gran''s BahnCard 50 expires on %, before this trip.',
  'with its end date in the message');

-- 1st class on a 2nd-class card.
update public.abeona_trips set travel_class = 1 where id = (select id from bc_ids where key = 'trip_bc25');
select ok(exists (select 1 from public.abeona_trip_warnings((select id from bc_ids where key = 'trip_bc25'))
                  where kind = 'class' and traveller_id = (select id from bc_ids where key = 'ada')),
  'a 2nd-class card on a 1st-class ticket is a warning');

-- BahnCard 100: a 25 does not do.
insert into bc_ids values ('trip_bc100',
  public.abeona_save_trip('{"fare_type": "bahncard100"}'::jsonb, (select legs from bc_legs)));
select results_eq(
  $$ select kind, message from public.abeona_trip_warnings((select id from bc_ids where key = 'trip_bc100')) $$,
  $$ values ('no_card'::text, 'Ada has no BahnCard 100.'::text) $$,
  'a BahnCard 100 fare needs a BahnCard 100');
insert into public.abeona_bahncards (traveller_id, kind, valid_from, valid_to)
values ((select id from bc_ids where key = 'ada'), 100, (select d from bc_today), (select d from bc_today) + 365);
select is((select count(*)::int from public.abeona_trip_warnings((select id from bc_ids where key = 'trip_bc100'))), 0,
  'and is fine with one');

-- Nobody on the trip.
select lives_ok(
  $$ select public.abeona_set_trip_travellers((select id from bc_ids where key = 'trip_bc100'), '{}') $$,
  'travellers can be cleared');
select results_eq(
  $$ select kind from public.abeona_trip_warnings((select id from bc_ids where key = 'trip_bc100')) $$,
  $$ values ('no_travellers'::text) $$,
  'a discounted trip with nobody on it says so');

select public.abeona_set_trip_travellers((select id from bc_ids where key = 'trip_bc100'),
  array[(select id from bc_ids where key = 'kid'), (select id from bc_ids where key = 'gran')]);
select is((select count(*)::int from public.abeona_trip_travellers where trip_id = (select id from bc_ids where key = 'trip_bc100')),
  2, 'set_trip_travellers replaces the travellers');

select throws_ok(
  $$ select public.abeona_set_trip_travellers((select id from bc_ids where key = 'trip_bc100'),
       array[(select id from bc_ids where key = 'ben')]) $$,
  '42501', null, 'another household''s traveller cannot be put on a trip');

-- ── Storage paths of card files ────────────────────────────────────────────
select ok(public.abeona_user_in_household_path(
    (select id from bc_ids where key = 'household_a')::text || '/bahncards/'
    || (select id from bc_ids where key = 'card_ada')::text || '/card.pdf'),
  'a member reaches their household''s card file');

select ok(not public.abeona_user_in_household_path(
    (select id from bc_ids where key = 'household_a')::text || '/bahncards/'
    || (select id from bc_ids where key = 'card_ben')::text || '/card.pdf'),
  'but not another household''s card through their own prefix');

-- C, a stranger, cannot reach A's card file.
set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000003", "role": "authenticated"}';
select public.abeona_ensure_household();
select ok(not public.abeona_user_in_household_path(
    (select id from bc_ids where key = 'household_a')::text || '/bahncards/'
    || (select id from bc_ids where key = 'card_ada')::text || '/card.pdf'),
  'a stranger cannot reach a card file');

-- ── A invites B: B's card moves with its traveller, and so does its file ───
set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000001", "role": "authenticated"}';
select public.abeona_invite_to_household('abeona-bc-b@test.local');

select ok(exists (select 1 from public.abeona_bahncards where id = (select id from bc_ids where key = 'card_ben')),
  'the invitee''s cards join the household with their traveller');
select ok(public.abeona_user_in_household_path(
    (select id from bc_ids where key = 'household_b')::text || '/bahncards/'
    || (select id from bc_ids where key = 'card_ben')::text || '/card.pdf'),
  'a merged card''s file follows the card, not its old household prefix');

set local request.jwt.claims = '{"sub": "abe0bc00-0000-4000-8000-000000000003", "role": "authenticated"}';
select ok(not public.abeona_user_in_household_path(
    (select id from bc_ids where key = 'household_b')::text || '/bahncards/'
    || (select id from bc_ids where key = 'card_ben')::text || '/card.pdf'),
  'and stays out of reach for strangers');

-- ── Grants: nothing for signed-out callers ─────────────────────────────────
reset role;
select ok(
  not has_function_privilege('anon', 'public.abeona_bahncard_deadlines(integer)', 'execute')
  and not has_function_privilege('anon', 'public.abeona_trip_warnings(uuid)', 'execute')
  and not has_function_privilege('anon', 'public.abeona_set_trip_travellers(uuid, uuid[])', 'execute')
  and not has_function_privilege('anon', 'public.abeona_user_in_household_path(text)', 'execute'),
  'anon cannot call the BahnCard RPCs');

select * from finish();
rollback;
