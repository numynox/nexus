-- Abeona is scoped to a shared household: every member reads and writes the
-- household's trips, nobody else sees them. These tests pin that, the merge
-- on invite, the statistics, the train-binding rule and the ticket storage
-- policies.
--
-- Run with: npm run test:db

begin;

create extension if not exists pgtap;

select plan(26);

insert into auth.users (id, email) values
  ('abe00000-0000-4000-8000-000000000001', 'abeona-a@test.local'),
  ('abe00000-0000-4000-8000-000000000002', 'abeona-b@test.local'),
  ('abe00000-0000-4000-8000-000000000003', 'abeona-c@test.local');

-- profiles are filled by the signup trigger; make sure the emails are there.
insert into public.profiles (id, email, full_name) values
  ('abe00000-0000-4000-8000-000000000001', 'abeona-a@test.local', 'Ada'),
  ('abe00000-0000-4000-8000-000000000002', 'abeona-b@test.local', 'Ben'),
  ('abe00000-0000-4000-8000-000000000003', 'abeona-c@test.local', 'Cy')
on conflict (id) do update set email = excluded.email, full_name = excluded.full_name;

create temporary table abeona_ids (key text primary key, id uuid);
grant select, insert, update on abeona_ids to authenticated;

-- A two-leg trip that arrived 30 minutes late, a month ago.
create temporary table abeona_legs_fixture as
select jsonb_build_array(
  jsonb_build_object(
    'mode', 'train', 'product', 'ICE', 'line_name', 'ICE 1',
    'from', jsonb_build_object('id', 'x:ffm', 'name', 'Frankfurt Hbf', 'lat', 50.1069, 'lon', 8.6619,
                               'planned', (now() - interval '30 days')::text),
    'to', jsonb_build_object('id', 'x:ful', 'name', 'Fulda', 'lat', 50.5546, 'lon', 9.6839,
                             'planned', (now() - interval '30 days' + interval '1 hour')::text),
    'polyline', '[[8.6619, 50.1069], [9.6839, 50.5546]]'::jsonb
  ),
  jsonb_build_object(
    'mode', 'train', 'product', 'RE', 'line_name', 'RE 5',
    'from', jsonb_build_object('id', 'x:ful', 'name', 'Fulda', 'lat', 50.5546, 'lon', 9.6839,
                               'planned', (now() - interval '30 days' + interval '70 minutes')::text),
    'to', jsonb_build_object('id', 'x:ks', 'name', 'Kassel-Wilhelmshöhe', 'lat', 51.3130, 'lon', 9.4467,
                             'planned', (now() - interval '30 days' + interval '2 hours')::text,
                             'current', (now() - interval '30 days' + interval '150 minutes')::text)
  )
) as legs;
grant select on abeona_legs_fixture to authenticated;

-- ── User A: household on first use, saving a trip ──────────────────────────
set local role authenticated;
set local request.jwt.claims = '{"sub": "abe00000-0000-4000-8000-000000000001", "role": "authenticated"}';

insert into abeona_ids values ('household_a', public.abeona_ensure_household());

select is(public.abeona_ensure_household(), (select id from abeona_ids where key = 'household_a'),
  'ensure_household is idempotent');

select is((select count(*)::int from public.abeona_travellers), 1,
  'a new household starts with its creator as a traveller');

insert into abeona_ids values ('trip_a',
  public.abeona_save_trip('{"title": "To Kassel", "fare_type": "flex", "train_binding": true}'::jsonb,
                          (select legs from abeona_legs_fixture)));

select is((select count(*)::int from public.abeona_legs), 2, 'save_trip stores every leg');

select is((select origin_name || ' → ' || destination_name from public.abeona_trips),
  'Frankfurt Hbf → Kassel-Wilhelmshöhe', 'the trip takes its ends from the first and last leg');

select ok((select distance_km between 86 and 90 from public.abeona_legs where idx = 0),
  'distance comes from the polyline');

select ok((select distance_km between 90 and 100 from public.abeona_legs where idx = 1),
  'without a polyline, distance is 1.1 × the great-circle distance');

select is((public.abeona_statistics(now() - interval '1 year', now())->>'trips')::int, 1,
  'statistics count the arrived trip');

select is((public.abeona_statistics(now() - interval '1 year', now())->>'delay_minutes')::int, 30,
  'statistics take the delay at the final arrival');

select is((public.abeona_statistics(now() - interval '1 year', now())->'top_station'->>'name'), 'Fulda',
  'the most frequent station is the change');

select lives_ok(
  $$ select public.abeona_update_trip_live(
       (select id from abeona_ids where key = 'trip_a'),
       '[{"idx": 1, "arrival": null, "arrival_platform": "4", "realtime": true}]'::jsonb) $$,
  'live values can be written');

select is((select arrival_platform from public.abeona_legs where idx = 1), '4',
  'update_trip_live sets the current platform');

-- ── User B: a stranger sees nothing ────────────────────────────────────────
set local request.jwt.claims = '{"sub": "abe00000-0000-4000-8000-000000000002", "role": "authenticated"}';

insert into abeona_ids values ('household_b', public.abeona_ensure_household());

select is((select count(*)::int from public.abeona_trips), 0, 'another household sees no trips');
select is((select count(*)::int from public.abeona_legs), 0, 'another household sees no legs');

select throws_ok(
  $$ insert into public.abeona_trips (household_id, origin_name, destination_name, planned_departure, planned_arrival)
     values ((select id from abeona_ids where key = 'household_a'), 'X', 'Y', now(), now()) $$,
  '42501', null, 'nobody can write into a household they are not in');

insert into abeona_ids values ('trip_b',
  public.abeona_save_trip('{}'::jsonb, (select legs from abeona_legs_fixture)));

-- ── A invites B: B's household and trips move over ─────────────────────────
set local request.jwt.claims = '{"sub": "abe00000-0000-4000-8000-000000000001", "role": "authenticated"}';

select lives_ok($$ select public.abeona_invite_to_household('ABEONA-B@test.local') $$,
  'invite by email, case-insensitively');

select is((select count(*)::int from public.abeona_trips), 2, 'the invitee''s trips join the household');
select is((select count(*)::int from public.abeona_list_household_members()), 2, 'both are members');

set local request.jwt.claims = '{"sub": "abe00000-0000-4000-8000-000000000002", "role": "authenticated"}';

select is(public.abeona_current_household(), (select id from abeona_ids where key = 'household_a'),
  'the invitee now belongs to the inviter''s household');
select is((select count(*)::int from public.abeona_trips), 2, 'and sees all of its trips');

set local request.jwt.claims = '{"sub": "abe00000-0000-4000-8000-000000000001", "role": "authenticated"}';
select ok(
  public.abeona_user_in_household_path(
    (select id from abeona_ids where key = 'household_b')::text || '/'
    || (select id from abeona_ids where key = 'trip_b')::text || '/ticket.pdf'),
  'a merged trip''s ticket follows the trip, not its old household prefix');

-- ── C cannot be pulled out of a shared household ───────────────────────────
set local request.jwt.claims = '{"sub": "abe00000-0000-4000-8000-000000000003", "role": "authenticated"}';
select public.abeona_ensure_household();
select is((select count(*)::int from public.abeona_trips), 0, 'a third user still sees nothing');

-- ── Rules ──────────────────────────────────────────────────────────────────
select ok(public.abeona_train_binding_lifted('saver', true, 20), 'binding lifted at 20 minutes');
select ok(not public.abeona_train_binding_lifted('saver', true, 19), 'not at 19 minutes');
select ok(not public.abeona_train_binding_lifted('deutschlandticket', true, 90),
  'never for the Deutschlandticket');

-- ── Ticket storage: path must start with one's own household ───────────────
set local request.jwt.claims = '{"sub": "abe00000-0000-4000-8000-000000000003", "role": "authenticated"}';
select ok(
  not public.abeona_user_in_household_path((select id from abeona_ids where key = 'household_a')::text || '/x/ticket.pdf'),
  'a stranger may not touch another household''s tickets');
select ok(
  not public.abeona_user_in_household_path(
    (select id from abeona_ids where key = 'household_b')::text || '/'
    || (select id from abeona_ids where key = 'trip_a')::text || '/ticket.pdf'),
  'nor reach one through a trip id under a different prefix');

select * from finish();
rollback;
